package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.ErrnoException
import jp.juggler.konaArchive.util.FileRandomAccessPosix
import jp.juggler.konaArchive.util.FileType
import jp.juggler.konaArchive.util.fileName
import jp.juggler.konaArchive.util.fileType
import jp.juggler.konaArchive.util.joinPath
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import platform.posix.closedir
import platform.posix.opendir
import platform.posix.readdir
import kotlin.collections.buildList

@OptIn(ExperimentalForeignApi::class)
private fun listDirectory(path: String): List<KonaWriterEntry> = memScoped {
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

actual fun String.toKonaWriterEntry(): KonaWriterEntry {
    val source = this
    return when (fileType(source)) {
        FileType.Directory -> KonaWriterDirectory(fileName(source)) {
            listDirectory(source)
        }

        FileType.Regular -> KonaWriterFile(fileName(source)) {
            FileRandomAccessPosix(source, isReadOnly = true)
        }

        FileType.Other -> error("Unsupported file type: $source")
    }
}
