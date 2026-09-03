@file:Suppress("Filename", "MatchingDeclarationName")

package jp.juggler.konaArchive.util

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

class FileRandomAccess private constructor(
    private val sharedAccess: SharedAccess,
    // subRangeで使われる
    val file: File,
    // 読み込み専用なら真
    override val isReadOnly: Boolean,
    // subRangeで使われる
    private val baseOffset: Long,
    // subRangeで使われる
    private val clipSize: Long?,
) : KonaRandomAccess() {
    constructor(
        file: File,
        isReadOnly: Boolean = false,
    ) : this(
        sharedAccess = SharedAccess(file, isReadOnly),
        file = file,
        isReadOnly = isReadOnly,
        baseOffset = 0L,
        clipSize = null,
    )

    override val size: Long
        get() = sharedAccess.channel.size().minus(baseOffset)
            .coerceAtMost(clipSize ?: Long.MAX_VALUE)

    override fun seek(offset: Long) {
        checkOpen()
        pos = offset.coerceIn(0L, size)
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            sharedAccess.release()
        }
    }

    override fun truncate() {
        if (isReadOnly) error("stream is read-only.")
        checkOpen()
        sharedAccess.channel.truncate(baseOffset + pos)
        pos = pos.coerceAtMost(size)
    }

    override fun writeByteArray(
        b: ByteArray,
        start: Int,
        end: Int,
    ) {
        if (isReadOnly) error("stream is read-only.")
        b.checkRange(start, end)
        val length = end - start
        if (length > 0) {
            checkOpen()
            val source = ByteBuffer.wrap(b, start, length)
            while (source.hasRemaining()) {
                val written = sharedAccess.channel.write(source, baseOffset + pos)
                if (written <= 0) error("failed to write file")
                pos += written
            }
        }
    }

    override fun readByteArray(
        b: ByteArray,
        start: Int,
        end: Int,
    ): Int {
        b.checkRange(start, end)
        val available = size - pos
        val length = minOf((end - start).toLong(), available).toInt()
        return when {
            length <= 0 -> 0
            else -> {
                checkOpen()
                val destination = ByteBuffer.wrap(b, start, length)
                var nRead = 0
                while (destination.hasRemaining()) {
                    val result = sharedAccess.channel.read(destination, baseOffset + pos)
                    if (result <= 0) break
                    nRead += result
                    pos += result
                }
                nRead
            }
        }
    }

    override fun subRange(
        start: Long,
        end: Long,
    ): FileRandomAccess {
        checkOpen()
        require(start in 0L..end && end <= size) { "sub range not valid. [$start, $end) / [0,$size)" }
        sharedAccess.retain()
        return try {
            FileRandomAccess(
                sharedAccess = sharedAccess,
                file = file,
                isReadOnly = true,
                baseOffset = baseOffset + start,
                clipSize = end - start,
            )
        } catch (ex: Throwable) {
            sharedAccess.release()
            throw ex
        }
    }

    private var closed = false

    private fun checkOpen() {
        check(!closed) { "stream is closed." }
    }

    private class SharedAccess(
        file: File,
        isReadOnly: Boolean,
    ) {
        val randomAccessFile = RandomAccessFile(file, if (isReadOnly) "r" else "rw")
        val channel = randomAccessFile.channel

        private var references = 1
        private var closed = false

        @Synchronized
        fun retain() {
            check(!closed) { "stream is closed." }
            references++
        }

        @Synchronized
        fun release() {
            if (references == 0) return
            references--
            if (references == 0) {
                closed = true
                randomAccessFile.close()
            }
        }
    }

    companion object {
        /**
         * ByteArray の [start..end) 指定が適切かチェック
         */
        private fun ByteArray.checkRange(start: Int, end: Int) {
            require(start in 0..size) { "start is outside the array" }
            require(end in start..size) { "end is outside the array" }
        }
    }
}
