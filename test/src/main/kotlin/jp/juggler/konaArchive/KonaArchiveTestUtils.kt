package jp.juggler.konaArchive

internal interface KonaArchiveTestUtils {
    fun sourceFiles(root: String): List<TestSourceFile>
    fun tempDirectory(name: String): String
    fun tempArchive(name: String): String
    fun resolve(parent: String, child: String): String
    fun deleteTree(path: String)
    fun deleteFile(path: String)
    fun writeFile(path: String, bytes: ByteArray)
    fun encodeDirectory(
        root: String,
        archivePath: String,
        previous: KonaArchive? = null,
    )

    fun decodeArchive(path: String): KonaArchive
}

internal class TestSourceFile(
    val path: String,
    val bytes: ByteArray,
)

internal expect val konaArchiveTestUtils: KonaArchiveTestUtils
