package jp.juggler.konaArchive.util

import jp.juggler.konaResource.sha256intrinsics.kona_sha256_process
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

/** SHA-256 using SHA-NI when available, with a generic native fallback. */
class KonaSha256Intrinsics : KonaDigest() {
    override fun digest(
        updater: (updateDigest: (ba: ByteArray, start: Int, end: Int) -> Unit) -> Unit,
    ): ByteArray {
        val state = KonaSha256IntrinsicsState()
        updater { ba, start, end -> state.update(ba, start, end) }
        return state.finish()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class KonaSha256IntrinsicsState {
    private companion object {
        const val BLOCK_SIZE = 64
        const val DIGEST_SIZE = 32
    }

    private val state = intArrayOf(
        0x6a09e667,
        0xbb67ae85.toInt(),
        0x3c6ef372,
        0xa54ff53a.toInt(),
        0x510e527f,
        0x9b05688c.toInt(),
        0x1f83d9ab,
        0x5be0cd19,
    )
    private val block = ByteArray(BLOCK_SIZE)
    private var blockSize = 0
    private var byteCount = 0L
    private var finished = false

    fun update(ba: ByteArray, start: Int, end: Int) {
        check(!finished) { "KonaSha256Intrinsics has already been finished" }
        require(start in 0..ba.size && end in start..ba.size) {
            "Invalid SHA-256 input range"
        }

        var offset = start
        if (blockSize > 0) {
            val copied = minOf(BLOCK_SIZE - blockSize, end - offset)
            ba.copyInto(block, blockSize, offset, offset + copied)
            blockSize += copied
            offset += copied
            if (blockSize == BLOCK_SIZE) {
                process(block, 0, BLOCK_SIZE)
                blockSize = 0
            }
        }

        val available = end - offset
        val processLength = available - (available % BLOCK_SIZE)
        if (processLength > 0) {
            process(ba, offset, processLength)
            offset += processLength
        }

        val remaining = end - offset
        if (remaining > 0) {
            ba.copyInto(block, 0, offset, end)
            blockSize = remaining
        }
        byteCount += (end - start).toLong()
    }

    fun finish(): ByteArray {
        check(!finished) { "KonaSha256Intrinsics has already been finished" }
        finished = true

        val bitCount = byteCount shl 3
        block[blockSize++] = 0x80.toByte()
        if (blockSize > 56) {
            block.fill(0, blockSize, BLOCK_SIZE)
            process(block, 0, BLOCK_SIZE)
            blockSize = 0
        }
        block.fill(0, blockSize, 56)
        for (index in 0 until Long.SIZE_BYTES) {
            block[63 - index] = (bitCount ushr (index * 8)).toByte()
        }
        process(block, 0, BLOCK_SIZE)

        val result = ByteArray(DIGEST_SIZE)
        for (index in state.indices) {
            val value = state[index].toUInt()
            result[index * 4] = (value shr 24).toByte()
            result[index * 4 + 1] = (value shr 16).toByte()
            result[index * 4 + 2] = (value shr 8).toByte()
            result[index * 4 + 3] = value.toByte()
        }
        return result
    }

    private fun process(data: ByteArray, start: Int, length: Int) {
        state.usePinned { statePinned ->
            data.usePinned { dataPinned ->
                kona_sha256_process(
                    statePinned.addressOf(0),
                    dataPinned.addressOf(start),
                    length.toUInt(),
                )
            }
        }
    }
}
