package jp.juggler.konaArchive.util

/** A streaming SHA-256 implementation with no platform dependencies. */
class KonaSha256Kotlin : KonaDigest() {
    override fun digest(
        updater: (updateDigest: (ba: ByteArray, start: Int, end: Int) -> Unit) -> Unit,
    ): ByteArray {
        val state = KonaSha256KotlinState()
        updater { ba, start, end -> state.update(ba, start, end) }
        return state.finish()
    }
}

private class KonaSha256KotlinState {
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
    private val schedule = IntArray(64)
    private var blockSize = 0
    private var byteCount = 0L
    private var finished = false

    fun update(ba: ByteArray, start: Int, end: Int) {
        check(!finished) { "KonaSha256 has already been finished" }
        require(start in 0..ba.size && end in start..ba.size) {
            "Invalid SHA-256 input range"
        }

        var offset = start
        while (offset < end) {
            val copied = minOf(BLOCK_SIZE - blockSize, end - offset)
            ba.copyInto(block, blockSize, offset, offset + copied)
            blockSize += copied
            offset += copied
            byteCount += copied.toLong()
            if (blockSize == BLOCK_SIZE) {
                processBlock()
                blockSize = 0
            }
        }
    }

    fun finish(): ByteArray {
        check(!finished) { "KonaSha256 has already been finished" }
        finished = true

        val bitCount = byteCount shl 3
        block[blockSize++] = 0x80.toByte()
        if (blockSize > 56) {
            block.fill(0, blockSize, BLOCK_SIZE)
            processBlock()
            blockSize = 0
        }
        block.fill(0, blockSize, 56)
        for (index in 0 until Long.SIZE_BYTES) {
            block[63 - index] = (bitCount ushr (index * 8)).toByte()
        }
        processBlock()

        val result = ByteArray(DIGEST_SIZE)
        for (index in state.indices) {
            val value = state[index]
            result[index * 4] = (value ushr 24).toByte()
            result[index * 4 + 1] = (value ushr 16).toByte()
            result[index * 4 + 2] = (value ushr 8).toByte()
            result[index * 4 + 3] = value.toByte()
        }
        return result
    }

    private fun processBlock() {
        for (index in 0 until 16) {
            val offset = index * 4
            schedule[index] = (block[offset].toInt() and 0xff shl 24) or
                (block[offset + 1].toInt() and 0xff shl 16) or
                (block[offset + 2].toInt() and 0xff shl 8) or
                (block[offset + 3].toInt() and 0xff)
        }
        for (index in 16 until schedule.size) {
            val value1 = schedule[index - 15]
            val value2 = schedule[index - 2]
            val smallSigma0 = rotateRight(value1, 7) xor
                rotateRight(value1, 18) xor (value1 ushr 3)
            val smallSigma1 = rotateRight(value2, 17) xor
                rotateRight(value2, 19) xor (value2 ushr 10)
            schedule[index] = schedule[index - 16] + smallSigma0 +
                schedule[index - 7] + smallSigma1
        }

        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]
        for (index in schedule.indices) {
            val bigSigma1 = rotateRight(e, 6) xor rotateRight(e, 11) xor rotateRight(e, 25)
            val choose = (e and f) xor (e.inv() and g)
            val temp1 = h + bigSigma1 + choose + ROUND_CONSTANTS[index] + schedule[index]
            val bigSigma0 = rotateRight(a, 2) xor rotateRight(a, 13) xor rotateRight(a, 22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temp2 = bigSigma0 + majority

            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
        state[4] += e
        state[5] += f
        state[6] += g
        state[7] += h
    }

    private fun rotateRight(value: Int, distance: Int): Int =
        (value ushr distance) or (value shl (32 - distance))

    private companion object {
        const val BLOCK_SIZE = 64
        const val DIGEST_SIZE = 32
        val ROUND_CONSTANTS = intArrayOf(
            0x428a2f98.toInt(), 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
            0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
            0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
            0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
            0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
            0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
            0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
            0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
            0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
            0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
            0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
            0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
        )
    }
}
