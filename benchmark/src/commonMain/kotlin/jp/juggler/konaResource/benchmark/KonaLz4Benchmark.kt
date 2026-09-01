package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.readKonaFiles
import jp.juggler.konaArchive.util.Lz4Codec
import jp.juggler.konaArchive.util.defaultLz4Codec
import okio.Buffer

open class KonaLz4Benchmark(
    private val codec: Lz4Codec = defaultLz4Codec,
) {
    private lateinit var input: ByteArray
    private lateinit var compressed: ByteArray

    fun setup() {
        val source = Buffer()
        sourceFiles().forEach { source.write(it) }
        input = source.readByteArray()
        check(input.isNotEmpty()) { "Unable to find common/src source files" }
        compressed = compressInput().readByteArray()
    }

    open fun compress(): Int = compressInput().size.toInt()

    open fun decompress(): Int {
        val source = Buffer().write(compressed)
        return codec.decompress(
            expectedSize = input.size,
            input = { destination -> readInput(source, destination) },
        ).size.toInt()
    }

    private fun compressInput(): Buffer {
        val source = Buffer().write(input)
        return codec.compress(
            inputSize = input.size,
            input = { destination -> readInput(source, destination) },
        )
    }

    private fun readInput(source: Buffer, destination: Buffer): Int {
        if (source.exhausted()) return -1
        val count = minOf(source.size, INPUT_CHUNK_SIZE.toLong())
        destination.write(source, count)
        return count.toInt()
    }

    private fun sourceFiles(): List<ByteArray> {
        for (candidate in listOf("common/src", "../common/src")) {
            runCatching { return readKonaFiles(candidate) }
        }
        error("Unable to find common/src source files")
    }

    private companion object {
        const val INPUT_CHUNK_SIZE = 64 * 1024
    }
}
