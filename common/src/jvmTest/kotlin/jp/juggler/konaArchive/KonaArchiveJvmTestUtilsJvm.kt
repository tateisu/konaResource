package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.FileRandomAccess
import java.io.File
import java.nio.file.Files

internal actual val konaArchiveTestUtils: KonaArchiveTestUtils = KonaArchiveTestUtilsJvm

private object KonaArchiveTestUtilsJvm : KonaArchiveTestUtils {
    override fun tempDirectory(name: String): String =
        Files.createTempDirectory("kona-resource-$name-").toFile().path

    override fun tempArchive(name: String): String =
        Files.createTempFile("kona-resource-$name-", ".archive").toFile().path

    override fun resolve(parent: String, child: String): String = File(parent, child).path

    override fun deleteTree(path: String) {
        File(path).deleteRecursively()
    }

    override fun deleteFile(path: String) {
        File(path).delete()
    }

    override fun writeFile(path: String, bytes: ByteArray) {
        val file = File(path)
        file.parentFile?.mkdirs()
        FileRandomAccess(file).use { access ->
            access.writeByteArray(bytes)
            access.truncate()
        }
    }

    override fun encodeDirectory(
        root: String,
        archivePath: String,
        previous: KonaArchive?,
    ) {
        val writer = File(root).toKonaWriterEntry() as KonaWriterDirectory
        FileRandomAccess(File(archivePath)).encodeKonaArchive(writer, previous = previous)
    }

    override fun decodeArchive(path: String): KonaArchive =
        FileRandomAccess(File(path), isReadOnly = true)
            .decodeKonaArchiveOrClose()
}
