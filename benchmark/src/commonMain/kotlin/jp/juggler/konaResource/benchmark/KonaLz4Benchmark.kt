package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.readKonaFiles
import jp.juggler.konaArchive.util.Lz4Codec
import jp.juggler.konaArchive.util.defaultLz4Codec
import okio.Buffer

internal class KonaLz4CompressBenchmark(
    override val name: String = "lz4-compress",
    private val codec: Lz4Codec = defaultLz4Codec,
) : BenchmarkItem {
    private lateinit var input: ByteArray
    private lateinit var compressed: ByteArray
    override fun setup() {
        val source = Buffer()
        sourceFiles().forEach { source.write(it) }
        input = source.readByteArray()
        check(input.isNotEmpty()) { "Unable to find common/src source files" }
        compressed = codec.compressByteArray(src = input).readByteArray()
        println("compress: ${input.size} => ${compressed.size} bytes")
    }

    override fun bytes(): Long = input.size.toLong()

    override fun run(): Int {
        var nRead = 0
        codec.compress(
            inputSize = input.size,
            input = { buf, offset, maxlength ->
                val step = (input.size - nRead).coerceAtMost(maxlength)
                when {
                    step <= 0L -> -1
                    else -> {
                        input.copyInto(
                            destination = buf,
                            destinationOffset = offset,
                            startIndex = nRead,
                            endIndex = nRead + step,
                        )
                        nRead += step
                        step
                    }
                }
            },
            output = { _, _, _ -> },
        )
        return nRead
    }

}

internal class KonaLz4DecompressBenchmark(
    override val name: String = "lz4-decompress",
    private val codec: Lz4Codec = defaultLz4Codec,
) : BenchmarkItem {
    private lateinit var input: ByteArray
    private lateinit var compressed: ByteArray

    override fun setup() {
        val source = Buffer()
        sourceFiles().forEach { source.write(it) }
        input = source.readByteArray()
        check(input.isNotEmpty()) { "Unable to find common/src source files" }
        compressed = codec.compressByteArray(src = input).readByteArray()
        println("decompress: ${compressed.size} => ${input.size} bytes")
    }

    override fun bytes(): Long = compressed.size.toLong()

    override fun run(): Int {
        var nRead = 0
        return codec.decompress(
            expectedSize = input.size,
            input = { buf, offset, maxlength ->
                val step = (compressed.size - nRead).coerceAtMost(maxlength)
                when {
                    step <= 0 -> -1
                    else -> {
                        compressed.copyInto(
                            destination = buf,
                            destinationOffset = offset,
                            startIndex = nRead,
                            endIndex = nRead + step,
                        )
                        nRead += step
                        step
                    }
                }
            },
            output = { _, _, _ -> },
        )
    }
}

private fun sourceFiles(): List<ByteArray> {
    for (candidate in listOf("common/src", "../common/src")) {
        runCatching { return readKonaFiles(candidate) }
    }
    error("Unable to find common/src source files")
}
