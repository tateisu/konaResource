package jp.juggler.konaArchive.util

import java.nio.file.Files
import java.nio.file.StandardCopyOption

actual val defaultKonaBlake3n256: KonaDigest = KonaBlake3n256Jni()

/** BLAKE3-256 implementation backed by the native BLAKE3 JNI library. */
class KonaBlake3n256Jni : KonaDigest() {
    override fun digest(
        updater: (updateDigest: (ba: ByteArray, start: Int, end: Int) -> Unit) -> Unit,
    ): ByteArray {
        val handle = Blake3Jni.nativeCreate()
        try {
            updater { ba, start, end ->
                require(start in 0..ba.size && end in start..ba.size) {
                    "Invalid BLAKE3 input range"
                }
                Blake3Jni.nativeUpdate(handle, ba, start, end - start)
            }
            return ByteArray(BLAKE3_OUT_LEN).also {
                Blake3Jni.nativeFinalize(handle, it, 0)
            }
        } finally {
            Blake3Jni.nativeFree(handle)
        }
    }

    private companion object {
        const val BLAKE3_OUT_LEN = 32
    }
}

private object Blake3Jni {
    init {
        val libraryPath = System.getProperty(LIBRARY_PATH_PROPERTY)
        if (libraryPath.isNullOrBlank()) {
            loadBundledLibrary()
        } else {
            System.load(libraryPath)
        }
    }

    @JvmStatic
    external fun nativeCreate(): Long

    @JvmStatic
    external fun nativeReset(handle: Long)

    @JvmStatic
    external fun nativeUpdate(handle: Long, input: ByteArray, offset: Int, length: Int)

    @JvmStatic
    external fun nativeFinalize(handle: Long, output: ByteArray, offset: Int)

    @JvmStatic
    external fun nativeFree(handle: Long)

    @JvmStatic
    external fun nativeHash(
        input: ByteArray,
        inputOffset: Int,
        inputLength: Int,
        output: ByteArray,
        outputOffset: Int,
    )

    private fun loadBundledLibrary() {
        val input = Blake3Jni::class.java.getResourceAsStream(LIBRARY_RESOURCE)
        if (input == null) {
            System.loadLibrary(LIBRARY_NAME)
            return
        }
        val libraryFile = Files.createTempFile("blake3_jni-", ".so")
        libraryFile.toFile().deleteOnExit()
        input.use {
            Files.copy(it, libraryFile, StandardCopyOption.REPLACE_EXISTING)
        }
        System.load(libraryFile.toAbsolutePath().toString())
    }

    private const val LIBRARY_NAME = "blake3_jni"
    private const val LIBRARY_PATH_PROPERTY = "kona.blake3.jni.path"
    private const val LIBRARY_RESOURCE = "/jp/juggler/konaArchive/native/linux-x86_64/libblake3_jni.so"
}
