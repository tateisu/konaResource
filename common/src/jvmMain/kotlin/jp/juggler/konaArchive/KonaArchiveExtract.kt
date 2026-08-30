package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.FileRandomAccess
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.use
import kotlin.use

fun konaArchiveExtract(
    archivePath: Path,
    outputDirectory: Path,
) {
    val root = outputDirectory.toAbsolutePath().normalize()
    Files.createDirectories(root)
    FileRandomAccess(archivePath.toFile(), isReadOnly = true).use { access ->
        access.decodeKonaArchive().use { archive ->
            extractEntries(archive.root, "", root)
        }
    }
}
private fun extractEntries(
    directory: KonaArchiveDir,
    prefix: String,
    root: Path,
) {
    for (i in directory.indices) {
        val entry = directory[i]
        val relativePath = when {
            prefix.isEmpty() -> entry.name
            else -> "$prefix/${entry.name}"
        }
        when (entry) {
            is KonaArchiveDir -> extractEntries(
                directory = entry,
                prefix = relativePath,
                root = root,
            )

            is KonaArchiveFile -> {
                val target = root.resolve(relativePath).normalize()
                require(target.startsWith(root)) {
                    "Invalid archive path: $relativePath"
                }
                Files.createDirectories(target.parent)
                target.toFile().outputStream().use { output ->
                    entry.content { buffer ->
                        output.write(buffer.readByteArray())
                    }
                }
            }
        }
    }
}
