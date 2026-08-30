package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.FileRandomAccess
import java.io.File

fun konaArchiveList(
    archiveFile: File,
) {
    FileRandomAccess(
        file = archiveFile,
        isReadOnly = true,
    ).use { access ->
        access.decodeKonaArchive().use { archive ->
            listEntries(archive.root, "")
        }
    }
}

private fun listEntries(directory: KonaArchiveDir, prefix: String) {
    for (i in directory.indices) {
        val entry = directory[i]
        val path = when {
            prefix.isEmpty() -> entry.name
            else -> "$prefix/${entry.name}"
        }
        when (entry) {
            is KonaArchiveDir -> listEntries(entry, path)
            is KonaArchiveFile -> println(
                "$path\t${entry.uncompressedSize} bytes",
            )
        }
    }
}
