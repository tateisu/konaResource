@file:Suppress("MatchingDeclarationName")

package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.FileRandomAccessMingw
import jp.juggler.konaArchive.util.FileType
import jp.juggler.konaArchive.util.WindowsErrorException
import jp.juggler.konaArchive.util.fileName
import jp.juggler.konaArchive.util.fileType
import jp.juggler.konaArchive.util.joinPath
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.windows.FindClose
import platform.windows.FindFirstFileA
import platform.windows.FindNextFileA
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.WIN32_FIND_DATAA

@OptIn(ExperimentalForeignApi::class)
private fun listDirectory(path: String): List<KonaWriterEntry> = memScoped {
    val findData = alloc<WIN32_FIND_DATAA>()
    val findHandle = FindFirstFileA(joinPath(path, "*"), findData.ptr)
    if (findHandle == INVALID_HANDLE_VALUE) {
        throw WindowsErrorException("Unable to open directory: $path")
    }
    try {
        buildList {
            while (true) {
                val name = findData.cFileName.toKString()
                if (name != "" && name != "." && name != "..") {
                    add(joinPath(path, name).toKonaWriterEntry())
                }
                if (FindNextFileA(findHandle, findData.ptr) == 0) break
            }
        }
    } finally {
        FindClose(findHandle)
    }
}

actual fun String.toKonaWriterEntry(): KonaWriterEntry {
    val source = this
    return when (fileType(source)) {
        FileType.Directory -> KonaWriterDirectory(fileName(source)) {
            listDirectory(source)
        }

        FileType.Regular -> KonaWriterFile(fileName(source)) {
            FileRandomAccessMingw(source, isReadOnly = true)
        }

        FileType.Other -> error("Unsupported file type: $source")
    }
}
