@file:Suppress("MatchingDeclarationName")

package jp.juggler.konaArchive.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.stat

enum class FileType {
    Directory,
    Regular,
    Other,
}

@OptIn(ExperimentalForeignApi::class)
fun fileType(path: String): FileType = memScoped {
    val info = alloc<stat>()
    if (stat(path, info.ptr) != 0) {
        throw ErrnoException("Unable to stat file: $path")
    }
    val mode = info.st_mode.toULong()
    when {
        mode and S_IFMT.toULong() == S_IFDIR.toULong() -> FileType.Directory
        mode and S_IFMT.toULong() == S_IFREG.toULong() -> FileType.Regular
        else -> FileType.Other
    }
}

fun fileName(path: String): String =
    path.trimEnd('/').substringAfterLast('/')

fun joinPath(parent: String, child: String): String = when {
    parent.isEmpty() -> child
    parent.endsWith('/') -> parent + child
    else -> "$parent/$child"
}
