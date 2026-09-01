package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.readKonaFiles
import jp.juggler.konaArchive.util.defaultLz4Codec

internal actual fun benchmarkSourceFiles(): List<ByteArray> {
    val candidates = listOf("common/src", "../common/src")
    for (candidate in candidates) {
        runCatching { return readKonaFiles(candidate) }
    }
    error("Unable to find common/src")
}

internal actual fun standaloneBenchmarks(): List<StandaloneBenchmark> = buildList {
    val sha256 = KonaSha256Benchmark()
    add(StandaloneBenchmark("sha256", sha256::setup, sha256::defaultImplementation))

    val blake3 = KonaBlake3Benchmark()
    add(StandaloneBenchmark("blake3", blake3::setup, blake3::blake3l256))

    val lz4 = KonaLz4Benchmark(defaultLz4Codec)
    add(StandaloneBenchmark("lz4-native-compress", lz4::setup, lz4::compress))
    add(StandaloneBenchmark("lz4-native-decompress", lz4::setup, lz4::decompress))
}
