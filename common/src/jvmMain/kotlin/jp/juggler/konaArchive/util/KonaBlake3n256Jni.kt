package jp.juggler.konaArchive.util

import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
        val libraryResource = bundledLibraryResource()
        val input = libraryResource?.let { Blake3Jni::class.java.getResourceAsStream(it) }
        if (input == null) {
            System.loadLibrary(LIBRARY_NAME)
            return
        }
        val extension = libraryResource.substringAfterLast('.', missingDelimiterValue = "")
        val libraryFile = Files.createTempFile("kona_common_jni-", ".$extension")
        libraryFile.toFile().deleteOnExit()
        input.use {
            Files.copy(it, libraryFile, StandardCopyOption.REPLACE_EXISTING)
        }
        System.load(libraryFile.toAbsolutePath().toString())
    }

    private fun bundledLibraryResource(): String? {
        val osName = System.getProperty("os.name").lowercase()
        val architecture = System.getProperty("os.arch").lowercase()
        return when {
            osName.contains("linux") -> when (architecture) {
                "amd64", "x86_64" -> "$LIBRARY_RESOURCE_ROOT/linux-x86_64/libkona_common_jni.so"
                "aarch64", "arm64" -> "$LIBRARY_RESOURCE_ROOT/linux-aarch64/libkona_common_jni.so"
                else -> null
            }

            osName.contains("windows") -> when (architecture) {
                "amd64", "x86_64" -> "$LIBRARY_RESOURCE_ROOT/windows-x86_64/kona_common_jni.dll"
                "aarch64", "arm64" -> "$LIBRARY_RESOURCE_ROOT/windows-aarch64/kona_common_jni.dll"
                else -> null
            }

            osName.contains("mac") || osName.contains("darwin") ->
                "$LIBRARY_RESOURCE_ROOT/macos-universal/libkona_common_jni.dylib"

            else -> null
        }
    }

    private const val LIBRARY_NAME = "kona_common_jni"
    private const val LIBRARY_PATH_PROPERTY = "kona.blake3.jni.path"
    private const val LIBRARY_RESOURCE_ROOT = "/jp/juggler/konaArchive/native"
}
