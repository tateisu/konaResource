package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.util.KonaDigest
import jp.juggler.konaArchive.util.KonaSha256Intrinsics
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

@State(Scope.Benchmark)
open class KonaSha256ImplementationsBenchmarkNative {
    private lateinit var files: List<ByteArray>

    @Setup
    fun setup() {
        files = benchmarkSourceFiles()
        check(files.isNotEmpty()) { "Unable to find common/src source files" }
    }

    @Benchmark
    open fun shaIntrinsics(): Int = digestAll { KonaSha256Intrinsics() }

    private fun digestAll(factory: () -> KonaDigest): Int {
        var result = 1
        files.forEach { input ->
            val digest = digest(input, factory())
            result = 31 * result + digest[0].toInt()
        }
        return result
    }

    private fun digest(input: ByteArray, digest: KonaDigest): ByteArray =
        digest.digest { updateDigest ->
            updateDigest(input, 0, input.size)
        }
}
