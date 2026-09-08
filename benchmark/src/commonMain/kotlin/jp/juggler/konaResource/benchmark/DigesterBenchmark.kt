package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.util.KonaDigest

internal open class DigesterBenchmark(
    override val name: String,
    private val digester: KonaDigest,
) : BenchmarkItem {
    private lateinit var files: List<ByteArray>

    override fun setup() {
        files = benchmarkSourceFiles()
        check(files.isNotEmpty()) {
            "Unable to find common/src source files"
        }
    }

    override fun bytes(): Long = files.sumOf { it.size.toLong() }
    override fun run(): Int {
        var result = 1
        files.forEach { input ->
            val digest = digester.digest { updateDigest ->
                updateDigest(input, 0, input.size)
            }
            result = 31 * result + digest[0].toInt()
        }
        return result
    }
}
