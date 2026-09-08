package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.readKonaFiles
import jp.juggler.konaArchive.util.defaultKonaBlake3n256
import jp.juggler.konaArchive.util.defaultKonaSha256
import java.io.File

internal actual fun benchmarkItems(): List<BenchmarkItem> = buildList {
    add(KonaLz4DecompressBenchmark())
    add(KonaLz4CompressBenchmark())
    add(DigesterBenchmark("sha256", defaultKonaSha256))
    add(DigesterBenchmark("blake3", defaultKonaBlake3n256))
}

internal actual fun benchmarkSourceFiles(): List<ByteArray> = readKonaFiles(
    listOf(File("common/src"), File("../common/src"))
        .firstOrNull { it.isDirectory }?.path
        ?: error("Unable to find common/src"),
)
