package jp.juggler.konaArchive.util

import net.jpountz.lz4.LZ4Compressor
import net.jpountz.lz4.LZ4Factory
import net.jpountz.lz4.LZ4FrameInputStream
import net.jpountz.lz4.LZ4FrameOutputStream
import net.jpountz.xxhash.XXHash32
import net.jpountz.xxhash.XXHashFactory
import okio.Buffer
import java.io.InputStream
import java.io.OutputStream

internal object Lz4CodecJvm : Lz4Codec {
    private const val IO_CHUNK_SIZE = 64 * SIZE_KIB

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun compress(
        inputSize: Int,
        options: Lz4Options,
        input: (Buffer) -> Int,
        output: (Buffer) -> Unit,
    ): Buffer {
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
        val outputBuffer = Buffer()
        val destination = object : OutputStream() {
            override fun write(b: Int) {
                outputBuffer.writeByte(b)
                emitOutput()
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                outputBuffer.write(b, off, len)
                emitOutput()
            }

            private fun emitOutput() {
                if (outputBuffer.size >= IO_CHUNK_SIZE) output(outputBuffer)
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
            val source = Buffer()
            var consumedInput = 0
            var inputFinished = false
            while (!inputFinished && consumedInput < inputSize) {
                when {
                    input(source) <= 0 -> inputFinished = true
                    else -> while (!source.exhausted()) {
                        val step = minOf(IO_CHUNK_SIZE.toLong(), source.size).toInt()
                        frame.write(source.readByteArray(step.toLong()))
                        consumedInput += step
                        if (options.autoFlush) frame.flush()
                    }
                }
            }
            require(!options.contentSizeFlag || consumedInput == inputSize) {
                "LZ4 input size mismatch: expected $inputSize, got $consumedInput"
            }
        }
        if (!outputBuffer.exhausted()) output(outputBuffer)
        return outputBuffer
    }

    override fun decompress(
        expectedSize: Int,
        input: (Buffer) -> Int,
        output: (Buffer) -> Unit,
    ): Buffer {
        val inputStream = object : InputStream() {
            val source = Buffer()
            val tmpArray = ByteArray(1)
            var inputFinished = false

            override fun read(): Int = when {
                read(tmpArray, 0, 1) > 0 ->
                    tmpArray[0].toInt().and(0xff)

                else -> -1
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                return when {
                    length <= 0 -> 0
                    else -> {
                        require(offset in 0..buffer.size)
                        require(length in 0..buffer.size - offset)
                        if (source.exhausted() && !inputFinished) {
                            if (input(source) <= 0) inputFinished = true
                        }
                        when {
                            source.exhausted() -> -1
                            else -> source.read(
                                buffer,
                                offset,
                                minOf(length.toLong(), source.size).toInt(),
                            )
                        }
                    }
                }
            }
        }
        val outputBuffer = Buffer()
        var decodedSize = 0
        LZ4FrameInputStream(inputStream).use { frame ->
            val buffer = ByteArray(64 * SIZE_KIB)
            while (true) {
                val read = frame.read(buffer)
                if (read <= 0) break
                decodedSize += read
                outputBuffer.write(buffer, 0, read)
                output(outputBuffer)
            }
        }
        require(decodedSize == expectedSize) { "LZ4 size mismatch" }
        return outputBuffer
    }
}
