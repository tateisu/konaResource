package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.util.KonaBlake3n256Linux
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

@State(Scope.Benchmark)
open class KonaBlake3BenchmarkLinux {
    private lateinit var files: List<ByteArray>

    @Setup
    fun setup() {
        files = benchmarkSourceFiles()
        check(files.isNotEmpty()) { "Unable to find common/src source files" }
    }

    @Benchmark
    open fun officialBlake3l256(): Int {
        var result = 1
        files.forEach { input ->
            val digest = digest(input)
            result = 31 * result + digest[0].toInt()
        }
        return result
    }

    private fun digest(input: ByteArray): ByteArray =
        KonaBlake3n256Linux().digest { updateDigest ->
            updateDigest(input, 0, input.size)
        }
}
