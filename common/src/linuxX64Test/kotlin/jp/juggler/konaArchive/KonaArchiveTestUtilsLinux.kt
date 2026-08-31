package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.ErrnoException
import jp.juggler.konaArchive.util.FILE_PERMISSION_U_RWX
import jp.juggler.konaArchive.util.FileRandomAccess
import jp.juggler.konaArchive.util.FileType
import jp.juggler.konaArchive.util.fileType
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import platform.posix.EEXIST
import platform.posix.ENOENT
import platform.posix.closedir
import platform.posix.errno
import platform.posix.getpid
import platform.posix.mkdir
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.rmdir
import platform.posix.unlink

internal actual val konaArchiveTestUtils: KonaArchiveTestUtils =
    KonaArchiveTestUtilsLinux

@OptIn(ExperimentalForeignApi::class)
@Suppress("TooManyFunctions")
private object KonaArchiveTestUtilsLinux : KonaArchiveTestUtils {
    private var nextTemporaryDirectoryId = 0

    override fun sourceFiles(root: String): List<TestSourceFile> =
        sourceFilesRecursive(sourceRoot(root)).sortedBy { it.path }

    private fun sourceRoot(root: String): String {
        val directory = opendir(root)
        if (directory != null) {
            closedir(directory)
            return root
        }
        val parentRoot = "../$root"
        val parentDirectory = opendir(parentRoot)
        if (parentDirectory != null) {
            closedir(parentDirectory)
            return parentRoot
        }
        error("Unable to find source directory: $root")
    }

    private fun sourceFilesRecursive(path: String): List<TestSourceFile> = when (fileType(path)) {
        FileType.Directory -> {
            val result = mutableListOf<TestSourceFile>()
            val directory = opendir(path)
                ?: throw ErrnoException("Unable to open directory: $path")
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
            val access = FileRandomAccess(path, isReadOnly = true)
            try {
                require(access.size <= Int.MAX_VALUE) { "Test file is too large: $path" }
                listOf(TestSourceFile(path, access.readBytes(access.size.toInt(), path)))
            } finally {
                access.close()
            }
        }

        FileType.Other -> emptyList()
    }

    fun joinPath(parent: String, child: String): String =
        if (parent.endsWith('/')) parent + child else "$parent/$child"

    fun makeDirectory(path: String) {
        if (path.isEmpty() || path == "/") return
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty() && parent != path) makeDirectory(parent)
        if (mkdir(path, FILE_PERMISSION_U_RWX) != 0 && errno != EEXIST) {
            throw ErrnoException("Unable to create directory: $path")
        }
    }

    override fun tempDirectory(name: String): String {
        val path = "/tmp/kona-resource-$name-${getpid()}-${nextTemporaryDirectoryId++}"
        makeDirectory(path)
        return path
    }

    override fun tempArchive(name: String): String =
        "/tmp/kona-resource-$name-${getpid()}-${nextTemporaryDirectoryId++}.archive"

    override fun resolve(parent: String, child: String): String = joinPath(parent, child)

    @OptIn(ExperimentalForeignApi::class)
    override fun deleteTree(path: String) {
        when (fileType(path)) {
            FileType.Directory -> {
                val directory = opendir(path)
                    ?: throw ErrnoException("Unable to open directory: $path")
                try {
                    while (true) {
                        val entry = readdir(directory) ?: break
                        val name = entry.pointed.d_name.toKString()
                        if (name != "." && name != "..") {
                            deleteTree(joinPath(path, name))
                        }
                    }
                } finally {
                    closedir(directory)
                }
                check(rmdir(path) == 0) {
                    "Unable to remove directory: $path"
                }
            }
            // Regular, Other
            else -> deleteFile(path)
        }
    }

    override fun deleteFile(path: String) {
        if (unlink(path) != 0 && errno != ENOENT) {
            throw ErrnoException("Unable to remove file: $path")
        }
    }

    override fun writeFile(path: String, bytes: ByteArray) {
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) makeDirectory(parent)
        val access = FileRandomAccess(path, isReadOnly = false)
        try {
            access.writeByteArray(bytes)
            access.truncate()
        } finally {
            access.close()
        }
    }

    override fun encodeDirectory(
        root: String,
        archivePath: String,
        previous: KonaArchive?,
    ) {
        val writer = root.toKonaWriterEntry() as KonaWriterDirectory
        FileRandomAccess(archivePath, isReadOnly = false)
            .encodeKonaArchive(writer, previous = previous)
    }

    override fun decodeArchive(path: String): KonaArchive =
        FileRandomAccess(path, isReadOnly = true)
            .decodeKonaArchiveOrClose()
}
