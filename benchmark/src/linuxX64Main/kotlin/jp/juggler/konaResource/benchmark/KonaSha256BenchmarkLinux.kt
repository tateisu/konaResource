package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.util.KonaBlake3n256Linux
import jp.juggler.konaArchive.util.KonaDigest
import jp.juggler.konaArchive.util.KonaSha256Intrinsics
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.O_RDONLY
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.close
import platform.posix.closedir
import platform.posix.open
import platform.posix.opendir
import platform.posix.read
import platform.posix.readdir
import platform.posix.stat

internal actual fun defaultSha256(): KonaDigest = KonaSha256Intrinsics()

internal actual fun defaultBlake3(): KonaDigest = KonaBlake3n256Linux()

internal actual fun benchmarkSourceFiles(): List<ByteArray> =
    sourceFilesRecursive(sourceRoot())

@OptIn(ExperimentalForeignApi::class)
private fun sourceRoot(): String {
    listOf("common/src", "../common/src").forEach { path ->
        val directory = opendir(path)
        if (directory != null) {
            closedir(directory)
            return path
        }
    }
    error("Unable to find common/src")
}

@OptIn(ExperimentalForeignApi::class)
private fun sourceFilesRecursive(path: String): List<ByteArray> = when (fileType(path)) {
    FileType.Directory -> {
        val result = mutableListOf<ByteArray>()
        val directory = opendir(path) ?: error("Unable to open directory: $path")
        try {
            while (true) {
                val entry = readdir(directory) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name != "." && name != "..") {
                    result += sourceFilesRecursive(joinPath(path, name))
                }
            }
        } finally {
            closedir(directory)
        }
        result
    }

    FileType.Regular -> {
        listOf(readFile(path))
    }

    FileType.Other -> emptyList()
}

@OptIn(ExperimentalForeignApi::class)
private fun fileType(path: String): FileType = memScoped {
    val info = alloc<stat>()
    check(stat(path, info.ptr) == 0) { "Unable to stat file: $path" }
    val mode = info.st_mode.toULong()
    when {
        mode and S_IFMT.toULong() == S_IFDIR.toULong() -> FileType.Directory
        mode and S_IFMT.toULong() == S_IFREG.toULong() -> FileType.Regular
        else -> FileType.Other
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun readFile(path: String): ByteArray {
    val size = memScoped {
        val info = alloc<stat>()
        check(stat(path, info.ptr) == 0) { "Unable to stat file: $path" }
        require(info.st_size <= Int.MAX_VALUE) { "Benchmark file is too large: $path" }
        info.st_size.toInt()
    }
    val descriptor = open(path, O_RDONLY)
    check(descriptor >= 0) { "Unable to open file: $path" }
    try {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < bytes.size) {
            val count = bytes.usePinned { pinned ->
                read(descriptor, pinned.addressOf(offset), (bytes.size - offset).toULong())
            }
            check(count > 0) { "Unable to read file: $path" }
            offset += count.toInt()
        }
        return bytes
    } finally {
        close(descriptor)
    }
}

private enum class FileType {
    Directory,
    Regular,
    Other,
}

private fun joinPath(parent: String, child: String): String =
    if (parent.endsWith('/')) parent + child else "$parent/$child"
