package jp.juggler.konaArchive.util

import java.io.File
import java.io.RandomAccessFile

@Suppress("BlockingMethodInNonBlockingContext")
class FileRandomAccess private constructor(
    private val access: RandomAccessFile,
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
        access = RandomAccessFile(file, if (isReadOnly) "r" else "rw"),
        file = file,
        isReadOnly = isReadOnly,
        baseOffset = 0L,
        clipSize = null,
    )

    override val size: Long
        get() = access.length().minus(baseOffset)
            .coerceAtMost(clipSize ?: Long.MAX_VALUE)


    override fun seek(offset: Long) {
        pos = offset.coerceIn(0L, size)
        // 実際にseekするのはread/write時
    }

    override fun close() {
        access.close()
    }

    private fun seekImpl() {
        val rawPosition = baseOffset + pos
        if (access.filePointer != rawPosition) {
            access.seek(rawPosition)
        }
    }

    override fun truncate() {
        if (isReadOnly) error("stream is read-only.")
        access.setLength(baseOffset + pos)
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
            seekImpl()
            access.write(b, start, length)
            pos += length
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
                var nRead = 0
                while (true) {
                    val remaining = length - nRead
                    if (remaining <= 0) break
                    seekImpl()
                    val result = access.read(b, start + nRead, remaining)
                    when {
                        result <= 0L -> break
                        result >= 0 -> {
                            nRead += result
                            pos += result
                        }
                    }
                }
                nRead
            }
        }
    }

    override fun subRange(
        start: Long,
        end: Long,
    ): FileRandomAccess {
        require(start in 0L..end && end <= size) { "sub range not valid. [$start, $end) / [0,$size)" }
        return FileRandomAccess(
            access = RandomAccessFile(file, "r"),
            file = file,
            isReadOnly = true,
            baseOffset = baseOffset + start,
            clipSize = end - start
        )
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
