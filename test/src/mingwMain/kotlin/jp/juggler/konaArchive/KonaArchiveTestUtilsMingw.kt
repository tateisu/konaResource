@file:Suppress("MatchingDeclarationName")

package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.FileRandomAccessMingw
import jp.juggler.konaArchive.util.FileType
import jp.juggler.konaArchive.util.WindowsErrorException
import jp.juggler.konaArchive.util.fileName
import jp.juggler.konaArchive.util.fileType
import jp.juggler.konaArchive.util.joinPath
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.windows.CreateDirectoryA
import platform.windows.DeleteFileA
import platform.windows.FindClose
import platform.windows.FindFirstFileA
import platform.windows.FindNextFileA
import platform.windows.GetCurrentProcessId
import platform.windows.GetLastError
import platform.windows.GetTempPathA
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.RemoveDirectoryA
import platform.windows.WIN32_FIND_DATAA

internal actual val konaArchiveTestUtils: KonaArchiveTestUtils = KonaArchiveTestUtilsMingw

@OptIn(ExperimentalForeignApi::class)
@Suppress("TooManyFunctions", "MagicNumber")
private object KonaArchiveTestUtilsMingw : KonaArchiveTestUtils {
    private var nextTemporaryDirectoryId = 0

    private const val ERROR_ALREADY_EXISTS = 183u
    private const val ERROR_FILE_NOT_FOUND = 2u

    @OptIn(ExperimentalForeignApi::class)
    fun listDirectory(path: String): List<KonaWriterEntry> = memScoped {
        val findData = alloc<WIN32_FIND_DATAA>()
        val searchPath = joinPath(path, "*")
        val findHandle = FindFirstFileA(searchPath, findData.ptr)
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

    fun String.toKonaWriterEntry(): KonaWriterEntry {
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

    private fun tempBase(): String = memScoped {
        val buffer = allocArray<ByteVar>(1024)
        GetTempPathA(1024u, buffer)
        buffer.toKString().trimEnd('\\', '/')
    }

    override fun sourceFiles(root: String): List<TestSourceFile> =
        sourceFilesRecursive(sourceRoot(root)).sortedBy { it.path }

    private fun sourceRoot(root: String): String {
        if (directoryExists(root)) return root
        val parentRoot = "../$root"
        if (directoryExists(parentRoot)) return parentRoot
        error("Unable to find source directory: $root")
    }

    private fun directoryExists(path: String): Boolean = memScoped {
        val findData = alloc<WIN32_FIND_DATAA>()
        val findHandle = FindFirstFileA(joinPath(path, "*"), findData.ptr)
        if (findHandle == INVALID_HANDLE_VALUE) {
            false
        } else {
            FindClose(findHandle)
            true
        }
    }

    private inline fun directoryEntries(path: String, block: (String) -> Unit) {
        memScoped {
            val findData = alloc<WIN32_FIND_DATAA>()
            val findHandle = FindFirstFileA(joinPath(path, "*"), findData.ptr)
            if (findHandle == INVALID_HANDLE_VALUE) {
                throw WindowsErrorException("Unable to open directory: $path")
            }
            try {
                while (true) {
                    val name = findData.cFileName.toKString()
                    if (name != "." && name != "..") block(name)
                    if (FindNextFileA(findHandle, findData.ptr) == 0) break
                }
            } finally {
                FindClose(findHandle)
            }
        }
    }

    private fun sourceFilesRecursive(path: String): List<TestSourceFile> = when (fileType(path)) {
        FileType.Directory -> {
            val result = mutableListOf<TestSourceFile>()
            directoryEntries(path) { name ->
                result += sourceFilesRecursive(joinPath(path, name))
            }
            result
        }

        FileType.Regular -> {
            val access = FileRandomAccessMingw(path, isReadOnly = true)
            try {
                require(access.size <= Int.MAX_VALUE) { "Test file is too large: $path" }
                listOf(TestSourceFile(path, access.readBytes(access.size.toInt(), path)))
            } finally {
                access.close()
            }
        }

        FileType.Other -> emptyList()
    }

    private fun makeDirectory(path: String) {
        if (path.isEmpty() || path == "/" || path == "\\") return
        val parent = path.substringBeforeLast('/', "").substringBeforeLast('\\', "")
        if (parent.isNotEmpty() && parent != path) makeDirectory(parent)
        if (CreateDirectoryA(path, null) == 0 && GetLastError() != ERROR_ALREADY_EXISTS) {
            throw WindowsErrorException("Unable to create directory: $path")
        }
    }

    override fun tempDirectory(name: String): String {
        val path = "${tempBase()}/kona-resource-$name-${GetCurrentProcessId()}-${nextTemporaryDirectoryId++}"
        makeDirectory(path)
        return path
    }

    override fun tempArchive(name: String): String =
        "${tempBase()}/kona-resource-$name-${GetCurrentProcessId()}-${nextTemporaryDirectoryId++}.archive"

    override fun resolve(parent: String, child: String): String = joinPath(parent, child)

    override fun deleteTree(path: String) {
        when (fileType(path)) {
            FileType.Directory -> {
                directoryEntries(path) { name ->
                    deleteTree(joinPath(path, name))
                }
                if (RemoveDirectoryA(path) == 0) {
                    throw WindowsErrorException("Unable to remove directory: $path")
                }
            }

            else -> deleteFile(path)
        }
    }

    override fun deleteFile(path: String) {
        if (DeleteFileA(path) == 0 && GetLastError() != ERROR_FILE_NOT_FOUND) {
            throw WindowsErrorException("Unable to remove file: $path")
        }
    }

    override fun writeFile(path: String, bytes: ByteArray) {
        val parent = path.substringBeforeLast('/', "").substringBeforeLast('\\', "")
        if (parent.isNotEmpty()) makeDirectory(parent)
        val access = FileRandomAccessMingw(path, isReadOnly = false)
        try {
            access.writeByteArray(bytes)
            access.truncate()
        } finally {
            access.close()
        }
    }

    override fun encodeDirectory(root: String, archivePath: String, previous: KonaArchive?) {
        val writer = root.toKonaWriterEntry() as KonaWriterDirectory
        FileRandomAccessMingw(archivePath, isReadOnly = false)
            .encodeKonaArchive(writer, previous = previous)
    }

    override fun decodeArchive(path: String): KonaArchive =
        FileRandomAccessMingw(path, isReadOnly = true).decodeKonaArchiveOrClose()
}
