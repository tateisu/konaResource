package jp.juggler.konaArchive.util

class ByteArrayRandomAccess(
    private val bytes: ByteArray,
    private val baseOffset: Int = 0,
    private val endOffset: Int = bytes.size,
) : KonaRandomAccess() {
    override val isReadOnly: Boolean = true
    override val size: Long = (endOffset - baseOffset).toLong()

    override fun close() = Unit

    override fun seek(offset: Long) {
        pos = offset.coerceIn(0L, size)
    }

    override fun truncate(): Nothing =
        error("ByteArrayRandomAccess is read-only")

    override fun writeByteArray(b: ByteArray, start: Int, end: Int): Nothing =
        error("ByteArrayRandomAccess is read-only")

    override fun readByteArray(b: ByteArray, start: Int, end: Int): Int {
        require(start in 0..b.size) { "start is outside the destination array" }
        require(end in start..b.size) { "end is outside the destination array" }
        val length = minOf((end - start).toLong(), size - pos).toInt()
        if (length <= 0) return 0
        val sourceStart = baseOffset + pos.toInt()
        bytes.copyInto(
            destination = b,
            destinationOffset = start,
            startIndex = sourceStart,
            endIndex = sourceStart + length,
        )
        pos += length
        return length
    }

    override fun subRange(start: Long, end: Long): ByteArrayRandomAccess {
        require(start in 0L..end && end <= size) {
            "sub-range incorrect. [$start, $end) / [0,$size)"
        }
        return ByteArrayRandomAccess(
            bytes = bytes,
            baseOffset = baseOffset + start.toInt(),
            endOffset = baseOffset + end.toInt(),
        )
    }
}
