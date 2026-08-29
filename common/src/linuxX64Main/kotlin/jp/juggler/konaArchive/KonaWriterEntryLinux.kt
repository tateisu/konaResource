package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import platform.posix.closedir
import platform.posix.opendir
import platform.posix.readdir

@OptIn(ExperimentalForeignApi::class)
internal fun listDirectory(path: String): List<KonaWriterEntry> = memScoped {
    val directory = opendir(path) ?: throw ErrnoException("Unable to open directory: $path")
    try {
        buildList {
            while (true) {
                val entry = readdir(directory) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name != "" && name != "." && name != "..") {
                    add(joinPath(path, name).toKonaWriterEntry())
                }
            }
        }
    } finally {
        closedir(directory)
    }
}

/**
 * pathをKonaWriterEntryに変換する
 */
fun String.toKonaWriterEntry(): KonaWriterEntry {
    val source = this
    return when (fileType(source)) {
        FileType.Directory -> KonaWriterDirectory(fileName(source)) {
            listDirectory(source)
        }

        FileType.Regular -> KonaWriterFile(fileName(source)) {
            FileRandomAccess(source, isReadOnly = true)
        }

        FileType.Other -> error("Unsupported file type: $source")
    }
}
