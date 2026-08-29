package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.ErrnoException
import jp.juggler.konaArchive.util.FileRandomAccess
import jp.juggler.konaArchive.util.FileType
import jp.juggler.konaArchive.util.fileType
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import platform.posix.*

private object LinuxKonaArchiveTestUtils : KonaArchiveTestUtils {
    private var nextTemporaryDirectoryId = 0

    fun joinPath(parent: String, child: String): String =
        if (parent.endsWith('/')) parent + child else "$parent/$child"

    fun makeDirectory(path: String) {
        if (path.isEmpty() || path == "/") return
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty() && parent != path) makeDirectory(parent)
        if (mkdir(path, 448U) != 0 && errno != EEXIST) {
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
                        if (name != "." && name != "..") deleteTree(joinPath(path, name))
                    }
                } finally {
                    closedir(directory)
                }
                check(rmdir(path) == 0) { "Unable to remove directory: $path" }
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
        previous: KonaArchive?
    ) {
        val writer = root.toKonaWriterEntry() as KonaWriterDirectory
        FileRandomAccess(archivePath, isReadOnly = false)
            .encodeKonaArchive(writer, previous = previous)
    }

    override fun decodeArchive(path: String): KonaArchive =
        FileRandomAccess(path, isReadOnly = true)
            .decodeKonaArchiveOrClose()
}

internal actual val konaArchiveTestUtils: KonaArchiveTestUtils = LinuxKonaArchiveTestUtils
