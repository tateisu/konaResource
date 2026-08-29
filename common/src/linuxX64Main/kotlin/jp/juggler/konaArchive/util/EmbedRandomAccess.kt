package jp.juggler.konaArchive.util

import kotlinx.cinterop.*
import platform.posix.dlsym
import platform.posix.memcpy

/**
 * 実行ファイル内のバイト領域を直接読む KonaRandomAccess
 */
@OptIn(ExperimentalForeignApi::class)
class EmbedRandomAccess(
    val addressRange: LongRange,
) : KonaRandomAccess() {
    override val isReadOnly = true
    override val size = when {
        addressRange.isEmpty() -> 0L
        else -> (addressRange.last - addressRange.first) + 1
    }

    override fun close() = Unit

    override fun seek(offset: Long) {
        pos = offset.coerceIn(0L, size)
    }

    private fun writeError(): Nothing =
        throw IllegalStateException("EmbedRandomAccess has no support of write access.")

    override fun truncate() = writeError()
    override fun writeByteArray(
        b: ByteArray,
        start: Int,
        end: Int,
    ) = writeError()

    override fun readByteArray(b: ByteArray, start: Int, end: Int): Int {
        require(start in 0..b.size) { "start is outside the destination array" }
        require(end in start..b.size) { "end is outside the destination array" }
        val available = size - pos
        val length = minOf((end - start).toLong(), available).toInt()
        if (length <= 0) return 0
        b.usePinned { destination ->
            memcpy(
                destination.addressOf(start),
                (addressRange.first + pos).toCPointer<ByteVar>(),
                length.toULong(),
            )
        }
        pos += length
        return length
    }

    override fun subRange(start: Long, end: Long): EmbedRandomAccess {
        require(start in 0L..end && end <= size) { "sub-range incorrect. [$start, $end) / [0,$size)" }
        return EmbedRandomAccess(
            addressRange = addressRange.first + start until addressRange.first + end
        )
    }
}
