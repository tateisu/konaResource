package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.util.Lz4Codec

internal class StandaloneBenchmark(
    val name: String,
    private val setupAction: () -> Unit,
    private val operation: () -> Int,
) {
    fun setup() = setupAction()

    fun run(): Int = operation()
}

internal class Lz4BenchmarkCodec(
    val name: String,
    val codec: Lz4Codec,
)

internal expect fun standaloneBenchmarks(): List<StandaloneBenchmark>
