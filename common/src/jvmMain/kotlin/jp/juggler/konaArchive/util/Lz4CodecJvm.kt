package jp.juggler.konaArchive.util

import net.jpountz.lz4.LZ4Compressor
import net.jpountz.lz4.LZ4Factory
import net.jpountz.lz4.LZ4FrameInputStream
import net.jpountz.lz4.LZ4FrameOutputStream
import net.jpountz.xxhash.XXHash32
import net.jpountz.xxhash.XXHashFactory
import okio.Buffer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** JVM-only implementation for the Gradle plugin and CLI build tools. */
internal actual val defaultLz4Codec: Lz4Codec = Lz4CodecJvm

private object Lz4CodecJvm: Lz4Codec {
    override fun compress(
        inputSize: Int,
        options: Lz4Options,
        input: (Buffer) -> Int,
        output: (Buffer) -> Unit
    ) : Buffer{
        val source = Buffer()
        while (source.size < inputSize) {
            val before = source.size
            val read = input(source)
            val added = (source.size - before).toInt()
            require(read == added) { "LZ4 input callback size mismatch" }
            require(read > 0) { "LZ4 input ended before the expected input size was consumed" }
        }
        require(source.size == inputSize.toLong()) { "LZ4 input size mismatch" }

        val factory = LZ4Factory.fastestInstance()
        val compressor: LZ4Compressor = if (options.compressionLevel > 0) {
            factory.highCompressor(options.compressionLevel)
        } else {
            factory.fastCompressor()
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
        val sourceBytes = source.readByteArray()
        val encoded = ByteArrayOutputStream()
        encoded.use { destination ->
            LZ4FrameOutputStream(destination, blockSize, knownSize, compressor, checksum, *features).use { frame ->
                var offset = 0
                while (offset < sourceBytes.size) {
                    val length = minOf(64 * 1024, sourceBytes.size - offset)
                    frame.write(sourceBytes, offset, length)
                    offset += length
                    if (options.autoFlush) frame.flush()
                }
            }
        }
        outputBuffer.write(encoded.toByteArray())
        output(outputBuffer)
        return outputBuffer
    }

    override fun decompress(
        expectedSize: Int,
        input: (Buffer) -> Int,
        output: (Buffer) -> Unit
    ) : Buffer{
        val source = Buffer()
        while (true) {
            val before = source.size
            val read = input(source)
            val added = (source.size - before).toInt()
            require(read == added || read == -1 && source.size == before) { "LZ4 input callback size mismatch" }
            if (read == -1) break
            require(read > 0) { "LZ4 input callback returned no data" }
        }
        val outputBuffer = Buffer()
        var decodedSize = 0
        val buffer = ByteArray(64 * 1024)
        LZ4FrameInputStream(ByteArrayInputStream(source.readByteArray())).use { frame ->
            while (true) {
                val read = frame.read(buffer)
                if (read == -1) break
                require(read > 0) { "LZ4 decompressor made no progress" }
                decodedSize += read
                require(decodedSize <= expectedSize) { "LZ4 output exceeds expected size" }
                outputBuffer.write(buffer, 0, read)
                output(outputBuffer)
            }
        }
        require(decodedSize == expectedSize) { "LZ4 size mismatch" }
        return outputBuffer
    }
}
