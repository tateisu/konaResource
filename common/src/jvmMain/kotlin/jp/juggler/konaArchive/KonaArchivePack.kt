package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.FileRandomAccess
import jp.juggler.konaArchive.util.Lz4Options
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.isDirectory

fun konaArchivePack(
    archivePath: Path,
    inputDirectory: Path,
    previousArchivePath: Path? = null,
    options: Lz4Options = Lz4Options(),
) {
    require(inputDirectory.isDirectory()) {
        "Not a directory: $inputDirectory"
    }
    val writerRoot = inputDirectory.toFile().toKonaWriterEntry()
    require(writerRoot is KonaWriterDirectory) {
        "Not a directory: $inputDirectory"
    }
    val archive = archivePath.toFile()
    val previousArchiveFile = previousArchivePath?.toFile()
    if (previousArchiveFile != null) {
        require(previousArchiveFile.isFile) {
            "Previous archive is not a file: $previousArchivePath"
        }
    }
    val temporaryArchive = Files.createTempFile(
        archive.absoluteFile.parentFile.toPath(),
        ".${archive.name}.",
        ".tmp",
    ).toFile()
    try {
        FileRandomAccess(temporaryArchive).use { access ->
            if (previousArchiveFile == null) {
                access.encodeKonaArchive(writerRoot, options = options)
            } else {
                FileRandomAccess(
                    file = previousArchiveFile,
                    isReadOnly = true,
                ).use { previousAccess ->
                    previousAccess.decodeKonaArchive().use { previous ->
                        access.encodeKonaArchive(
                            root = writerRoot,
                            options = options,
                            previous = previous,
                        )
                    }
                }
            }
        }
        moveFile(temporaryArchive, archive)
    } finally {
        temporaryArchive.delete()
    }
}

private fun moveFile(source: File, target: File) {
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
