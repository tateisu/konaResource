package jp.juggler.konaArchive.util

import net.jpountz.lz4.LZ4Compressor
import net.jpountz.lz4.LZ4Factory
import net.jpountz.lz4.LZ4FrameInputStream
import net.jpountz.lz4.LZ4FrameOutputStream
import net.jpountz.xxhash.XXHash32
import net.jpountz.xxhash.XXHashFactory
import java.io.InputStream
import java.io.OutputStream

internal object Lz4CodecJvm : Lz4Codec() {

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun compress(
        inputSize: Int,
        options: Lz4Options,
        input: (ByteArray, offset: Int, maxLength: Int) -> Int,
        output: (ByteArray, offset: Int, length: Int) -> Unit,
    ): Int {
        val factory = LZ4Factory.fastestInstance()
        val compressor: LZ4Compressor = when {
            options.compressionLevel > 0 -> factory.highCompressor(options.compressionLevel)
            else -> factory.fastCompressor()
        }
        val features = buildList {
            // lz4-java does not support dependent blocks and always requires this flag.
            add(LZ4FrameOutputStream.FLG.Bits.BLOCK_INDEPENDENCE)
            if (options.contentSizeFlag) add(LZ4FrameOutputStream.FLG.Bits.CONTENT_SIZE)
            if (options.contentChecksumFlag) add(LZ4FrameOutputStream.FLG.Bits.CONTENT_CHECKSUM)
            if (options.blockChecksumFlag) add(LZ4FrameOutputStream.FLG.Bits.BLOCK_CHECKSUM)
        }.toTypedArray()
        val blockSize = LZ4FrameOutputStream.BLOCKSIZE.valueOf(options.blockSizeId)
        val knownSize = if (options.contentSizeFlag) inputSize.toLong() else -1L
        val checksum: XXHash32 = XXHashFactory.fastestInstance().hash32()
        val destination = object : OutputStream() {
            var outCount = 0
            val tmpArray = ByteArray(1)
            override fun write(b: Int) {
                tmpArray[0] = b.toByte()
                output(tmpArray, 0, 1)
                ++outCount
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                output(b, off, len)
                outCount += len
            }
        }
        LZ4FrameOutputStream(
            destination,
            blockSize,
            knownSize,
            compressor,
            checksum,
            *features,
        ).use { frame ->
            val tmpArray = ByteArray(MAX_CHUNK_SIZE)
            var consumedInput = 0
            while (true) {
                val step = input(tmpArray, 0, tmpArray.size)
                if (step <= 0) break
                frame.write(tmpArray, 0, step)
                consumedInput += step
            }
            require(!options.contentSizeFlag || consumedInput == inputSize) {
                "LZ4 input size mismatch: expected $inputSize, got $consumedInput"
            }
        }
        return destination.outCount
    }

    override fun decompress(
        expectedSize: Int,
        input: (ByteArray, offset: Int, maxLength: Int) -> Int,
        output: (ByteArray, offset: Int, length: Int) -> Unit,
    ): Int {
        val inputStream = object : InputStream() {
            val tmpArray = ByteArray(1)
            var inputFinished = false
            override fun read(): Int = when {
                read(tmpArray, 0, 1) > 0 ->
                    tmpArray[0].toInt().and(0xff)

                else -> -1
            }

            override fun read(
                buffer: ByteArray,
                offset: Int,
                length: Int,
            ): Int = when {
                length <= 0 -> 0
                inputFinished -> -1
                else -> {
                    val r = input(buffer, offset, length)
                    when {
                        r <= 0 -> {
                            inputFinished = true
                            -1
                        }

                        else -> r
                    }
                }
            }
        }
        var decodedSize = 0
        LZ4FrameInputStream(inputStream).use { frame ->
            val tmpArray = ByteArray(MAX_CHUNK_SIZE)
            while (true) {
                val step = frame.read(tmpArray, 0, tmpArray.size)
                if (step <= 0) break
                output(tmpArray, 0, step)
                decodedSize += step
            }
        }
        require(decodedSize == expectedSize) { "LZ4 size mismatch" }
        return decodedSize
    }
}
