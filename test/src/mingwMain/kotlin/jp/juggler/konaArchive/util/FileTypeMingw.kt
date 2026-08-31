@file:Suppress("MatchingDeclarationName")

package jp.juggler.konaArchive.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.windows.DWORD
import platform.windows.FILE_ATTRIBUTE_DIRECTORY
import platform.windows.GetFileAttributesA

internal enum class FileType {
    Directory,
    Regular,
    Other,
}

private const val INVALID_FILE_ATTRIBUTES: DWORD = 0xFFFF_FFFFu

@OptIn(ExperimentalForeignApi::class)
internal fun fileType(path: String): FileType {
    val attributes = GetFileAttributesA(path)
    return when {
        attributes == INVALID_FILE_ATTRIBUTES ->
            throw WindowsErrorException("Unable to stat file: $path")

        attributes and FILE_ATTRIBUTE_DIRECTORY.toUInt() != 0u -> FileType.Directory
        else -> FileType.Regular
    }
}

internal fun fileName(path: String): String =
    path.trimEnd('/', '\\').substringAfterLast('\\').substringAfterLast('/')

internal fun joinPath(parent: String, child: String): String = when {
    parent.isEmpty() -> child
    parent.endsWith('/') || parent.endsWith('\\') -> parent + child
    else -> "$parent/$child"
}
