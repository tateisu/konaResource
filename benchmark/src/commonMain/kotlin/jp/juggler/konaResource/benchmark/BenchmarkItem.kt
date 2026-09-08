package jp.juggler.konaResource.benchmark

internal interface BenchmarkItem {
    val name: String
    fun setup()
    fun run(): Int
    fun bytes(): Long
}

internal expect fun benchmarkItems(): List<BenchmarkItem>
internal expect fun benchmarkSourceFiles(): List<ByteArray>
