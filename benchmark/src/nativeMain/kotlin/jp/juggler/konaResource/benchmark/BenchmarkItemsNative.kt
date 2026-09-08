package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.readKonaFiles
import jp.juggler.konaArchive.util.defaultKonaBlake3n256
import jp.juggler.konaArchive.util.defaultKonaSha256

internal actual fun benchmarkItems(): List<BenchmarkItem> = buildList {
    add(KonaLz4DecompressBenchmark())
    add(KonaLz4CompressBenchmark())
    add(KonaLz4FrameBenchmark())
    add(DigesterBenchmark("sha256", defaultKonaSha256))
    add(DigesterBenchmark("blake3", defaultKonaBlake3n256))
}

internal actual fun benchmarkSourceFiles(): List<ByteArray> {
    val candidates = listOf("common/src", "../common/src")
    for (candidate in candidates) {
        runCatching { return readKonaFiles(candidate) }
    }
    error("Unable to find common/src")
}
