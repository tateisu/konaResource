package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.util.KonaDigest
import jp.juggler.konaArchive.util.defaultKonaBlake3n256
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

@State(Scope.Benchmark)
open class KonaBlake3Benchmark {
    private lateinit var files: List<ByteArray>

    @Setup
    fun setup() {
        files = benchmarkSourceFiles()
        check(files.isNotEmpty()) { "Unable to find common/src source files" }
    }

    @Benchmark
    open fun blake3l256(): Int {
        var result = 1
        files.forEach { input ->
            val digest = digest(input)
            result = 31 * result + digest[0].toInt()
        }
        return result
    }

    private fun digest(input: ByteArray): ByteArray =
        defaultBlake3().digest { updateDigest ->
            updateDigest(input, 0, input.size)
        }
}

internal fun defaultBlake3(): KonaDigest = defaultKonaBlake3n256
