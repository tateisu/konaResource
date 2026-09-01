@file:Suppress("MatchingDeclarationName", "MagicNumber")

package jp.juggler.konaArchive.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValue
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.windows.CloseHandle
import platform.windows.CreateFileA
import platform.windows.DWORD
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_BEGIN
import platform.windows.GetFileSizeEx
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.LARGE_INTEGER
import platform.windows.OPEN_ALWAYS
import platform.windows.OPEN_EXISTING
import platform.windows.ReadFile
import platform.windows.SetEndOfFile
import platform.windows.SetFilePointerEx
import platform.windows.WriteFile

private const val GENERIC_READ: DWORD = 0x8000_0000u
private const val GENERIC_WRITE: DWORD = 0x4000_0000u
private const val FILE_SHARE_READ: DWORD = 0x1u
private const val FILE_SHARE_WRITE: DWORD = 0x2u

@OptIn(ExperimentalForeignApi::class)
@Suppress("MatchingDeclarationName")
class FileRandomAccessMingw private constructor(
    val path: String,
    var handle: HANDLE?,
    override val isReadOnly: Boolean,
    val baseOffset: Long,
    val clipSize: Long?,
) : KonaRandomAccess() {
    constructor(path: String, isReadOnly: Boolean) : this(
        path = path,
        handle = CreateFileA(
            path,
            if (isReadOnly) GENERIC_READ else (GENERIC_READ or GENERIC_WRITE),
            FILE_SHARE_READ or FILE_SHARE_WRITE,
            null,
            if (isReadOnly) OPEN_EXISTING.toUInt() else OPEN_ALWAYS.toUInt(),
            FILE_ATTRIBUTE_NORMAL.toUInt(),
            null,
        ) ?: INVALID_HANDLE_VALUE,
        isReadOnly = isReadOnly,
        baseOffset = 0L,
        clipSize = null,
    ) {
        if (handle == INVALID_HANDLE_VALUE) throw WindowsErrorException("Unable to open file: $path")
    }

    override val size: Long
        get() = memScoped {
            val fileSize = alloc<LARGE_INTEGER>()
            if (GetFileSizeEx(handle!!, fileSize.ptr) == 0) {
                throw WindowsErrorException("GetFileSizeEx failed")
            }
            fileSize.QuadPart
        }.minus(baseOffset)
            .coerceAtMost(clipSize ?: Long.MAX_VALUE)

    private fun ensureSize(width: Int, name: String) {
        when {
            handle == INVALID_HANDLE_VALUE -> error("handle was closed.")
            isReadOnly && pos + width > size ->
                error("missing $name. pos=$pos + width=$width > size=$size")
        }
    }

    private fun checkRange(size: Int, start: Int, end: Int) {
        require(start in 0..size) { "start is outside the array" }
        require(end in start..size) { "end is outside the array" }
    }

    override fun close() {
        if (handle != INVALID_HANDLE_VALUE) {
            CloseHandle(handle!!)
            handle = INVALID_HANDLE_VALUE
        }
    }

    override fun seek(offset: Long) {
        pos = offset.coerceIn(0L, size)
    }

    override fun truncate() {
        if (isReadOnly) error("stream is read-only.")
        ensureSize(0, "truncate")
        if (SetFilePointerEx(
                handle!!,
                cValue { QuadPart = baseOffset + pos },
                null,
                FILE_BEGIN.toUInt(),
            ) == 0
        ) {
            throw WindowsErrorException("SetFilePointerEx failed.")
        }
        if (SetEndOfFile(handle!!) == 0) {
            throw WindowsErrorException("SetEndOfFile failed.")
        }
        pos = pos.coerceAtMost(size)
    }

    override fun writeByteArray(b: ByteArray, start: Int, end: Int) {
        if (isReadOnly) error("stream is read-only.")
        checkRange(b.size, start, end)
        val length = end - start
        if (length <= 0) return
        ensureSize(length, "bytes[$length]")
        var nWrite = 0
        while (nWrite < length) {
            val remaining = length - nWrite
            val bytesWritten = memScoped {
                val written = alloc<UIntVar>()
                val result = b.usePinned { pinned ->
                    WriteFile(
                        handle!!,
                        pinned.addressOf(start + nWrite),
                        remaining.toUInt(),
                        written.ptr,
                        null,
                    )
                }
                if (result == 0) {
                    throw WindowsErrorException("WriteFile failed.")
                }
                written.value.toInt()
            }
            when {
                bytesWritten == 0 -> throw WindowsErrorException("WriteFile wrote 0 bytes.")
                else -> {
                    nWrite += bytesWritten
                    pos += bytesWritten.toLong()
                }
            }
        }
    }

    override fun readByteArray(b: ByteArray, start: Int, end: Int): Int {
        checkRange(b.size, start, end)
        val length = end - start
        if (length <= 0) return 0
        if (handle == INVALID_HANDLE_VALUE) error("handle was closed.")
        var nRead = 0
        var done = false
        while (nRead < length && !done) {
            val remaining = length - nRead
            val available = size - pos
            if (available <= 0L) {
                done = true
            } else {
                val requestSize = minOf(remaining.toLong(), available).toInt()
                val bytesRead = memScoped {
                    val read = alloc<UIntVar>()
                    val result = b.usePinned { pinned ->
                        ReadFile(
                            handle!!,
                            pinned.addressOf(start + nRead),
                            requestSize.toUInt(),
                            read.ptr,
                            null,
                        )
                    }
                    if (result == 0) {
                        throw WindowsErrorException("ReadFile failed.")
                    }
                    read.value.toInt()
                }
                when {
                    bytesRead == 0 -> done = true
                    else -> {
                        nRead += bytesRead
                        pos += bytesRead.toLong()
                    }
                }
            }
        }
        return nRead
    }

    override fun subRange(start: Long, end: Long): FileRandomAccessMingw {
        require(end <= size && start in 0L..end) { "Invalid sub-range: [$start, $end)" }
        return FileRandomAccessMingw(
            path = path,
            handle = CreateFileA(
                path,
                GENERIC_READ,
                FILE_SHARE_READ or FILE_SHARE_WRITE,
                null,
                OPEN_EXISTING.toUInt(),
                FILE_ATTRIBUTE_NORMAL.toUInt(),
                null,
            ) ?: INVALID_HANDLE_VALUE,
            isReadOnly = true,
            baseOffset = baseOffset + start,
            clipSize = end - start,
        ).also {
            if (it.handle == INVALID_HANDLE_VALUE) throw WindowsErrorException("Unable to open file: $path")
        }
    }
}
