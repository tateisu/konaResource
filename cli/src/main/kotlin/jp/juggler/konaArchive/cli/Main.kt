package jp.juggler.konaArchive.cli

import jp.juggler.konaArchive.*
import jp.juggler.konaArchive.util.FileRandomAccess
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.isDirectory

private const val PROGRAM_NAME = "konaArchive"

private fun usage(): Nothing = error("Usage: $PROGRAM_NAME <pack|list|extract> ...")

fun main(args: Array<String>) = runBlocking {
    when (args.firstOrNull()) {
        "pack" -> {
            // TODO previous archive を指定可能にしたい
            require(args.size == 3) { "Usage: $PROGRAM_NAME pack <archive> <input-directory>" }
            val archive = Path.of(args[1]).toFile()
            val root = Path.of(args[2])
            require(root.isDirectory()) { "Not a directory: $root" }
            val writerRoot = root.toFile().toKonaWriterEntry()
            require(writerRoot is KonaWriterDirectory) { "Not a directory: $root" }
            val temporaryArchive = Files.createTempFile(
                archive.absoluteFile.parentFile.toPath(),
                ".${archive.name}.",
                ".tmp",
            ).toFile()
            try {
                FileRandomAccess(temporaryArchive).use { access ->
                    access.encodeKonaArchive(writerRoot)
                }
                replaceArchive(temporaryArchive, archive)
            } finally {
                temporaryArchive.delete()
            }
        }

        "list" -> {
            require(args.size == 2) { "Usage: $PROGRAM_NAME list <archive>" }
            FileRandomAccess(Path.of(args[1]).toFile(), isReadOnly = true).use { access ->
                access.decodeKonaArchive().use { archive ->
                    listEntries(archive.root, "")
                }
            }
        }

        "extract" -> {
            require(args.size == 3) { "Usage: $PROGRAM_NAME extract <archive> <output-directory>" }
            val root = Path.of(args[2]).toAbsolutePath().normalize()
            Files.createDirectories(root)
            FileRandomAccess(Path.of(args[1]).toFile(), isReadOnly = true).use { access ->
                access.decodeKonaArchive().use { archive ->
                    extractEntries(archive.root, "", root)
                }
            }
        }

        else -> usage()
    }
}

private fun replaceArchive(source: File, target: File) {
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}

private fun listEntries(directory: KonaArchiveDir, prefix: String) {
    for( i in directory.indices){
        val entry = directory[i]
        val path = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
        when (entry) {
            is KonaArchiveDir -> listEntries(entry, path)
            is KonaArchiveFile -> println("$path\t${entry.uncompressedSize} bytes")
        }
    }
}

private fun extractEntries(directory: KonaArchiveDir, prefix: String, root: Path) {
    for( i in directory.indices){
        val entry = directory[i]
        val relativePath = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
        when (entry) {
            is KonaArchiveDir -> extractEntries(entry, relativePath, root)
            is KonaArchiveFile -> {
                val target = root.resolve(relativePath).normalize()
                require(target.startsWith(root)) { "Invalid archive path: $relativePath" }
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
