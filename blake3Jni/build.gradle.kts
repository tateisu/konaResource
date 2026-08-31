plugins {
    base
}

val sourceDirectory = file("src/main/c")
val blake3SourceDirectory = sourceDirectory.resolve("blake3")
val blake3SourceFiles = listOf(
    "blake3.c",
    "blake3_dispatch.c",
    "blake3_portable.c",
    "blake3_sse2_x86-64_unix.S",
    "blake3_sse41_x86-64_unix.S",
    "blake3_avx2_x86-64_unix.S",
    "blake3_avx512_x86-64_unix.S",
).map { blake3SourceDirectory.resolve(it) }
val jniSource = sourceDirectory.resolve("blake3_jni.c")
val nativeSources = blake3SourceFiles + jniSource
val nativeHeaders = listOf(
    blake3SourceDirectory.resolve("blake3.h"),
    blake3SourceDirectory.resolve("blake3_impl.h"),
)
val nativeBuildDirectory = layout.buildDirectory.dir("native")
val sharedLibrary = nativeBuildDirectory.map { it.file("libblake3_jni.so") }
val nativeBuildScript = file("build-native.sh")

val buildBlake3Jni = tasks.register("buildBlake3Jni") {
    group = "build"
    description = "Builds the Linux/x64 BLAKE3 JNI shared library"
    inputs.files(nativeSources, nativeHeaders, nativeBuildScript)
    outputs.file(sharedLibrary)

    doLast {
        val command = arrayOf(
            "bash",
            nativeBuildScript.absolutePath,
            sharedLibrary.get().asFile.absolutePath,
            System.getProperty("java.home"),
        )
        check(ProcessBuilder(*command).inheritIO().start().waitFor() == 0) {
            "Command failed: ${command.joinToString(" ")}"
        }
    }
}

tasks.assemble {
    dependsOn(buildBlake3Jni)
}

tasks.check {
    dependsOn(buildBlake3Jni)
}
