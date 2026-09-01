package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.readKonaFiles
import jp.juggler.konaArchive.util.defaultLz4Codec
import java.io.File

internal actual fun benchmarkSourceFiles(): List<ByteArray> =
    readKonaFiles(sourceRoot().path)

internal actual fun standaloneBenchmarks(): List<StandaloneBenchmark> = buildList {
    val sha256 = KonaSha256Benchmark()
    add(StandaloneBenchmark("sha256", sha256::setup, sha256::defaultImplementation))

    val blake3 = KonaBlake3Benchmark()
    add(StandaloneBenchmark("blake3", blake3::setup, blake3::blake3l256))

    lz4BenchmarkCodecs().forEach { implementation ->
        val lz4 = KonaLz4Benchmark(implementation.codec)
        add(StandaloneBenchmark("lz4-${implementation.name}-compress", lz4::setup, lz4::compress))
        add(StandaloneBenchmark("lz4-${implementation.name}-decompress", lz4::setup, lz4::decompress))
    }
}

private fun lz4BenchmarkCodecs(): List<Lz4BenchmarkCodec> = listOf(
    Lz4BenchmarkCodec("java", defaultLz4Codec),
)

private fun sourceRoot(): File =
    listOf(File("common/src"), File("../common/src"))
        .firstOrNull { it.isDirectory }
        ?: error("Unable to find common/src")
