package jp.juggler.konaArchive

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import jp.juggler.konaArchive.util.Lz4Options
import jp.juggler.konaArchive.util.defaultLz4Codec

class Lz4CodecTest : FreeSpec() {
    init {
        "frameOptionsAreApplied" {
            val input = ByteArray(128 * 1024) { index -> (index * 31).toByte() }
            val options = Lz4Options(
                compressionLevel = 3,
                blockSize = 64 * 1024,
                blockLinked = false,
                contentSizeFlag = false,
                contentChecksumFlag = false,
                blockChecksumFlag = true,
                autoFlush = true,
                favorDecSpeed = true,
            )

            val frame = defaultLz4Codec.compressByteArray(
                options = options,
                src = input,
            )

            val frameBytes = frame.readByteArray()
            var frameOffset = 0
            val restored = defaultLz4Codec.decompressBuffer(
                expectedSize = input.size,
                input = { buffer ->
                    val remain = frameBytes.size - frameOffset
                    when {
                        remain <= 0 -> -1
                        else -> {
                            buffer.write(
                                source = frameBytes,
                                offset = frameOffset,
                                byteCount = remain,
                            )
                            frameOffset += remain
                            remain
                        }
                    }
                },
            )
            restored.readByteArray().toList() shouldBe input.toList()
        }

        "compressAcceptsEndOfInputSignal" {
            val input = ByteArray(128 * 1024) { index -> (index * 17).toByte() }
            var supplied = false
            val frame = defaultLz4Codec.compressBuffer(
                inputSize = input.size + 1,
                options = Lz4Options(contentSizeFlag = false),
                input = { buffer ->
                    if (supplied) {
                        -1
                    } else {
                        supplied = true
                        buffer.write(input)
                        input.size
                    }
                },
            )
            val frameBytes = frame.readByteArray()
            var frameOffset = 0
            val restored = defaultLz4Codec.decompressBuffer(
                expectedSize = input.size,
                input = { buffer ->
                    if (frameOffset == frameBytes.size) {
                        -1
                    } else {
                        val size = frameBytes.size - frameOffset
                        buffer.write(frameBytes, frameOffset, size)
                        frameOffset += size
                        size
                    }
                },
            )
            restored.readByteArray().toList() shouldBe input.toList()
        }

        "largeInputIsConsumedAndProducedInChunks" {
            val input = ByteArray(32 * 1024 * 1024) { index ->
                ((index / 257) xor (index / 4099)).toByte()
            }
            var inputOffset = 0
            var inputCalls = 0
            var compressOutputCalls = 0
            val compressedOutput = okio.Buffer()
            val compressed = defaultLz4Codec.compressBuffer(
                inputSize = input.size,
                input = { buffer ->
                    inputCalls++
                    val size = minOf(32 * 1024, input.size - inputOffset)
                    buffer.write(input, inputOffset, size)
                    inputOffset += size
                    size
                },
                output = { buffer ->
                    compressOutputCalls++
                    compressedOutput.write(buffer, buffer.size)
                    buffer.clear()
                },
            )

            inputOffset shouldBe input.size
            (inputCalls > 1) shouldBe true
            (compressOutputCalls > 1) shouldBe true

            compressed.readByteArray() shouldBe ByteArray(0)
            val compressedBytes = compressedOutput.readByteArray()
            var compressedOffset = 0
            var outputCalls = 0
            val restored = defaultLz4Codec.decompressBuffer(
                expectedSize = input.size,
                input = { buffer ->
                    val size = minOf(11 * 1024, compressedBytes.size - compressedOffset)
                    if (size > 0) {
                        buffer.write(compressedBytes, compressedOffset, size)
                        compressedOffset += size
                        size
                    } else {
                        -1
                    }
                },
                output = { ++outputCalls },
            )

            (outputCalls > 1) shouldBe true
            compressedOffset shouldBe compressedBytes.size
            restored.readByteArray().toList() shouldBe input.toList()
        }
        "LZ4 compresses and decompresses data" {
            val input = ByteArray(128 * 1024) { index -> (index * 31).toByte() }
            val compressed = defaultLz4Codec.compressByteArray(
                src = input,
            )

            val compressedBytes = compressed.readByteArray()
            var offset = 0
            val restored = defaultLz4Codec.decompressBuffer(
                expectedSize = input.size,
                input = { buffer ->
                    val step = compressedBytes.size - offset
                    when {
                        step <= 0L -> -1
                        else -> {
                            buffer.write(compressedBytes, offset, step)
                            offset += step
                            step
                        }
                    }
                },
            )

            offset shouldBe compressedBytes.size
            restored.readByteArray().toList() shouldBe input.toList()
        }
    }
}
