#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#include "blake3/blake3.h"

static void throw_exception(JNIEnv *env, const char *class_name,
                            const char *message) {
  jclass exception_class = (*env)->FindClass(env, class_name);
  if (exception_class != NULL) {
    (*env)->ThrowNew(env, exception_class, message);
  }
}

static blake3_hasher *get_hasher(JNIEnv *env, jlong handle) {
  if (handle == 0) {
    throw_exception(env, "java/lang/IllegalStateException",
                    "BLAKE3 JNI handle is closed");
    return NULL;
  }
  return (blake3_hasher *)(uintptr_t)handle;
}

static int check_range(JNIEnv *env, jarray array, jint offset, jint length,
                       const char *name) {
  if (array == NULL) {
    throw_exception(env, "java/lang/NullPointerException", name);
    return 0;
  }
  jsize array_length = (*env)->GetArrayLength(env, array);
  if (offset < 0 || length < 0 || (jlong)offset + length > array_length) {
    throw_exception(env, "java/lang/IndexOutOfBoundsException", name);
    return 0;
  }
  return 1;
}

JNIEXPORT jlong JNICALL
Java_jp_juggler_konaArchive_util_Blake3Jni_nativeCreate(JNIEnv *env,
                                                        jclass clazz) {
  (void)clazz;
  blake3_hasher *hasher = malloc(sizeof(blake3_hasher));
  if (hasher == NULL) {
    throw_exception(env, "java/lang/OutOfMemoryError",
                    "Unable to allocate BLAKE3 JNI context");
    return 0;
  }
  blake3_hasher_init(hasher);
  return (jlong)(uintptr_t)hasher;
}

JNIEXPORT void JNICALL
Java_jp_juggler_konaArchive_util_Blake3Jni_nativeReset(JNIEnv *env,
                                                       jclass clazz,
                                                       jlong handle) {
  (void)clazz;
  blake3_hasher *hasher = get_hasher(env, handle);
  if (hasher != NULL) {
    blake3_hasher_reset(hasher);
  }
}

JNIEXPORT void JNICALL
Java_jp_juggler_konaArchive_util_Blake3Jni_nativeUpdate(
    JNIEnv *env, jclass clazz, jlong handle, jbyteArray input, jint offset,
    jint length) {
  (void)clazz;
  blake3_hasher *hasher = get_hasher(env, handle);
  if (hasher == NULL || !check_range(env, input, offset, length, "input")) {
    return;
  }
  if (length == 0) {
    return;
  }
  jbyte *bytes = (*env)->GetPrimitiveArrayCritical(env, input, NULL);
  if (bytes == NULL) {
    return;
  }
  blake3_hasher_update(hasher, bytes + offset, (size_t)length);
  (*env)->ReleasePrimitiveArrayCritical(env, input, bytes, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_jp_juggler_konaArchive_util_Blake3Jni_nativeFinalize(
    JNIEnv *env, jclass clazz, jlong handle, jbyteArray output,
    jint output_offset) {
  (void)clazz;
  blake3_hasher *hasher = get_hasher(env, handle);
  if (hasher == NULL ||
      !check_range(env, output, output_offset, BLAKE3_OUT_LEN, "output")) {
    return;
  }
  uint8_t digest[BLAKE3_OUT_LEN];
  blake3_hasher_finalize(hasher, digest, sizeof(digest));
  (*env)->SetByteArrayRegion(env, output, output_offset, BLAKE3_OUT_LEN,
                             (const jbyte *)digest);
}

JNIEXPORT void JNICALL
Java_jp_juggler_konaArchive_util_Blake3Jni_nativeFree(JNIEnv *env,
                                                      jclass clazz,
                                                      jlong handle) {
  (void)env;
  (void)clazz;
  free((void *)(uintptr_t)handle);
}

JNIEXPORT void JNICALL
Java_jp_juggler_konaArchive_util_Blake3Jni_nativeHash(
    JNIEnv *env, jclass clazz, jbyteArray input, jint input_offset,
    jint input_length, jbyteArray output, jint output_offset) {
  (void)clazz;
  if (!check_range(env, input, input_offset, input_length, "input") ||
      !check_range(env, output, output_offset, BLAKE3_OUT_LEN, "output")) {
    return;
  }

  blake3_hasher hasher;
  blake3_hasher_init(&hasher);
  if (input_length > 0) {
    jbyte *bytes = (*env)->GetPrimitiveArrayCritical(env, input, NULL);
    if (bytes == NULL) {
      return;
    }
    blake3_hasher_update(&hasher, bytes + input_offset, (size_t)input_length);
    (*env)->ReleasePrimitiveArrayCritical(env, input, bytes, JNI_ABORT);
  }

  uint8_t digest[BLAKE3_OUT_LEN];
  blake3_hasher_finalize(&hasher, digest, sizeof(digest));
  (*env)->SetByteArrayRegion(env, output, output_offset, BLAKE3_OUT_LEN,
                             (const jbyte *)digest);
}
