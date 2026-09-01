package jp.juggler.konaResource.benchmark

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private const val WARMUP_COUNT = 3
private val MEASUREMENT_TIME = 500.milliseconds

fun main() {
    standaloneBenchmarks().forEach { benchmark ->
        benchmark.setup()
        runBenchmark(benchmark.name, benchmark::run)
    }
}

private fun runBenchmark(name: String, operation: () -> Int) {
    repeat(WARMUP_COUNT) { operation() }

    val start = TimeSource.Monotonic.markNow()
    var count = 0
    var checksum = 0
    do {
        checksum = operation()
        ++count
    } while (start.elapsedNow() < MEASUREMENT_TIME)

    val elapsedNanos = start.elapsedNow().inWholeNanoseconds.coerceAtLeast(1L)
    val operationsPerSecond = count.toDouble() * 1_000_000_000.0 / elapsedNanos
    println("$name: $operationsPerSecond ops/s (iterations=$count, checksum=$checksum)")
}
