package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.readKonaFiles
import jp.juggler.konaArchive.util.Lz4Options
import jp.juggler.konaArchive.util.defaultLz4Codec
import jp.juggler.konaResource.lz4.cinterop.LZ4F_createDecompressionContext
import jp.juggler.konaResource.lz4.cinterop.LZ4F_decompress
import jp.juggler.konaResource.lz4.cinterop.LZ4F_decompressionContext_tVar
import jp.juggler.konaResource.lz4.cinterop.LZ4F_freeDecompressionContext
import jp.juggler.konaResource.lz4.cinterop.LZ4F_isError
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import okio.Buffer

@OptIn(ExperimentalForeignApi::class)
internal class KonaLz4FrameBenchmark(
    override val name: String = "lz4-frame",
) : BenchmarkItem {
    private lateinit var source: ByteArray
    private lateinit var compressed: ByteArray
    private lateinit var destination: ByteArray

    override fun setup() {
        val sourceBuffer = Buffer()
        for (candidate in listOf("common/src", "../common/src")) {
            val files = runCatching { readKonaFiles(candidate) }.getOrNull()
            if (files != null) {
                files.forEach(sourceBuffer::write)
                break
            }
        }
        source = sourceBuffer.readByteArray()
        check(source.isNotEmpty()) { "Unable to find common/src" }
        compressed = defaultLz4Codec.compressBuffer(
            inputSize = source.size,
            options = Lz4Options(
                blockLinked = false,
                contentChecksumFlag = false,
                blockChecksumFlag = false,
            ),
            input = { buffer ->
                if (buffer.size >= source.size) {
                    0
                } else {
                    buffer.write(source)
                    source.size
                }
            },
        ).readByteArray()
        destination = ByteArray(source.size)
    }

    override fun bytes(): Long = source.size.toLong()
    override fun run(): Int = memScoped {
        val context = alloc<LZ4F_decompressionContext_tVar>()
        check(LZ4F_createDecompressionContext(context.ptr, 100u) == 0uL)
        try {
            val sourceSize = allocArray<ULongVar>(1)
            val destinationSize = allocArray<ULongVar>(1)
            var sourceOffset = 0
            var destinationOffset = 0
            compressed.usePinned { sourcePinned ->
                destination.usePinned { destinationPinned ->
                    while (true) {
                        sourceSize.pointed.value = (compressed.size - sourceOffset).toULong()
                        destinationSize.pointed.value = (destination.size - destinationOffset).toULong()
                        val result = LZ4F_decompress(
                            context.value,
                            destinationPinned.addressOf(destinationOffset),
                            destinationSize,
                            sourcePinned.addressOf(sourceOffset),
                            sourceSize,
                            null,
                        )
                        check(LZ4F_isError(result) == 0u)
                        val consumed = sourceSize.pointed.value.toInt()
                        val decoded = destinationSize.pointed.value.toInt()
                        sourceOffset += consumed
                        destinationOffset += decoded
                        if (result == 0uL) break
                        check(consumed > 0 || decoded > 0)
                    }
                }
            }
            check(destinationOffset == source.size)
            (destination.firstOrNull()?.toInt() ?: 0) +
                (destination.lastOrNull()?.toInt() ?: 0)
        } finally {
            LZ4F_freeDecompressionContext(context.value)
        }
    }
}
