@file:Suppress("MatchingDeclarationName")

package jp.juggler.konaArchive.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.posix.EINTR
import platform.posix.O_CREAT
import platform.posix.O_RDONLY
import platform.posix.O_RDWR
import platform.posix.close
import platform.posix.errno
import platform.posix.fstat
import platform.posix.ftruncate
import platform.posix.open
import platform.posix.pread
import platform.posix.pwrite
import platform.posix.stat

@OptIn(ExperimentalForeignApi::class)
@Suppress("MatchingDeclarationName", "MagicNumber")
class FileRandomAccess private constructor(
    val path: String,
    var fileDescriptor: Int,
    override val isReadOnly: Boolean,
    // subRangeで使う
    val baseOffset: Long,
    // subRangeで使う
    val clipSize: Long?,
) : KonaRandomAccess() {
    constructor(path: String, isReadOnly: Boolean) : this(
        path = path,
        fileDescriptor = open(
            path,
            when {
                isReadOnly -> O_RDONLY
                else -> O_RDWR or O_CREAT
            },
            438,
        ),
        isReadOnly = isReadOnly,
        baseOffset = 0L,
        clipSize = null,
    ) {
        if (fileDescriptor < 0) throw ErrnoException("Unable to open file: $path")
    }

    override val size: Long
        get() = memScoped {
            val st = alloc<stat>()
            if (fstat(fileDescriptor, st.ptr) != 0) {
                throw ErrnoException("fstat failed")
            }
            st.st_size
        }.minus(baseOffset)
            .coerceAtMost(clipSize ?: Long.MAX_VALUE)

    private fun ensureSize(width: Int, name: String) {
        when {
            fileDescriptor < 0 ->
                error("fileDescriptor was closed.")

            isReadOnly && pos + width > size ->
                error("missing $name. pos=$pos + width=$width > size=$size")
        }
    }

    private fun checkRange(size: Int, start: Int, end: Int) {
        require(start in 0..size) { "start is outside the array" }
        require(end in start..size) { "end is outside the array" }
    }

    override fun close() {
        if (fileDescriptor >= 0) {
            close(fileDescriptor)
            fileDescriptor = -1
        }
    }

    override fun seek(offset: Long) {
        pos = offset.coerceIn(0L, size)
    }

    override fun truncate() {
        if (isReadOnly) error("stream is read-only.")
        ensureSize(0, "truncate")
        if (ftruncate(fileDescriptor, baseOffset + pos) != 0) {
            throw ErrnoException("ftruncate failed.")
        }
        pos = pos.coerceAtMost(size)
    }

    override fun writeByteArray(
        b: ByteArray,
        start: Int,
        end: Int,
    ) {
        if (isReadOnly) error("stream is read-only.")
        checkRange(b.size, start, end)
        val length = end - start
        if (length <= 0) return
        ensureSize(length, "bytes[$length]")
        var nWrite = 0
        while (nWrite < length) {
            val remaining = length - nWrite
            val result = b.usePinned { pinned ->
                pwrite(
                    fileDescriptor,
                    pinned.addressOf(start + nWrite),
                    remaining.toULong(),
                    baseOffset + pos,
                )
            }
            when {
                result < 0L -> when (errno) {
                    EINTR -> continue
                    else -> throw ErrnoException("write failed.")
                }

                else -> {
                    nWrite += result.toInt()
                    pos += result
                }
            }
        }
    }

    override fun readByteArray(
        b: ByteArray,
        start: Int,
        end: Int,
    ): Int {
        checkRange(b.size, start, end)
        val length = end - start
        if (length <= 0) return 0
        if (fileDescriptor < 0) error("fileDescriptor was closed.")
        var nRead = 0
        var done = false
        while (nRead < length && !done) {
            val remaining = length - nRead
            val available = size - pos
            if (available <= 0L) {
                done = true
            } else {
                val requestSize = minOf(remaining.toLong(), available).toInt()
                val result = b.usePinned { pinned ->
                    pread(
                        fileDescriptor,
                        pinned.addressOf(start + nRead),
                        requestSize.toULong(),
                        baseOffset + pos,
                    )
                }
                when {
                    result < 0L -> when (errno) {
                        EINTR -> Unit
                        else -> throw ErrnoException("read failed.")
                    }

                    result == 0L -> done = true

                    else -> {
                        nRead += result.toInt()
                        pos += result
                    }
                }
            }
        }
        return nRead
    }

    override fun subRange(start: Long, end: Long): FileRandomAccess {
        require(end <= size && start in 0L..end) { "Invalid sub-range: [$start, $end)" }
        return FileRandomAccess(
            path = path,
            fileDescriptor = open(
                path,
                O_RDONLY,
                438,
            ),
            isReadOnly = true,
            baseOffset = baseOffset + start,
            clipSize = end - start,
        ).also {
            if (fileDescriptor < 0) throw ErrnoException("Unable to open file: $path")
        }
    }
}
