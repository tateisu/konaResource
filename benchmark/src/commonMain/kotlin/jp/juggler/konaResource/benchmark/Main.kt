package jp.juggler.konaResource.benchmark

import jp.juggler.util.ArgParserConfig
import jp.juggler.util.buildCommandSpec
import jp.juggler.util.parse
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

private class BenchmarkOptions(
    var warmupIterations: Int = 1,
    var warmupDuration: Duration = 0.5.minutes,
    var measurementIterations: Int = 3,
    var measurementDuration: Duration = 0.5.minutes,
)

private val benchmarkSpec = buildCommandSpec(
    desc = "Run benchmarks",
    creator = { BenchmarkOptions() },
) {
    stringOption(
        desc = "Number of warmup iterations.",
        fullName = "wi",
        valueName = "count",
    ) { warmupIterations = it.parsePositiveInt("warmupIterations") }
    stringOption(
        desc = "Duration of warmup per iteration.",
        fullName = "wd",
        shortName = 'w',
        valueName = "duration",
    ) { warmupDuration = it.parseDuration("warmupDuration") }

    stringOption(
        desc = "Number of measurement iterations.",
        fullName = "mi",
        valueName = "count",
    ) { measurementIterations = it.parsePositiveInt("iterations") }
    stringOption(
        desc = "Duration of measurement per iteration.",
        fullName = "md",
        valueName = "duration",
    ) { measurementDuration = it.parseDuration("measurementDuration") }
}

private fun benchmarkOptionsConfig(args: Array<out String>) = ArgParserConfig(
    program = "konaBenchmark",
    args = args,
    topSpec = benchmarkSpec,
    subcommands = emptyMap(),
)

fun main(args: Array<String>) {
    val result = benchmarkOptionsConfig(args).parse()
    val error = result.error
    when {
        error != null -> {
            println(result.formatUsage(error.message))
            return
        }

        result.help -> {
            println(result.formatUsage())
            return
        }
    }

    val options = result.top as BenchmarkOptions
    benchmarkItems().forEach { benchmark ->
        benchmark.setup()
        runBenchmark(
            name = benchmark.name,
            operation = benchmark::run,
            bytesPerOperation = benchmark::bytes,
            options = options,
        )
    }
}

private class IterationResult(
    val countPerSecond: Double,
    val checksum: Int,
    val bytesPerSecond: Double,
)

private fun runBenchmark(
    name: String,
    operation: () -> Int,
    bytesPerOperation: () -> Long,
    options: BenchmarkOptions,
) {
    fun runIteration(
        expectDuration: Duration,
    ): IterationResult {
        val start = TimeSource.Monotonic.markNow()
        var count = 0L
        var checksum = 0
        val bytes = bytesPerOperation()
        var processedBytes = 0L
        var elapsed = Duration.ZERO
        while (start.elapsedNow().also { elapsed = it } < expectDuration) {
            checksum = operation()
            count++
            processedBytes += bytes
        }
        return IterationResult(
            countPerSecond = countPerSecond(count = count, elapsed = elapsed),
            checksum = checksum,
            bytesPerSecond = bytesPerSecond(
                bytes = processedBytes,
                elapsed = elapsed,
            ),
        )
    }

    fun showResults(name: String, iterations: List<IterationResult>) {
        val cpsList = iterations.map { it.countPerSecond }
        val cpsAvg = cpsList.average()
        val bpsList = iterations.map { it.bytesPerSecond }
        val bpsAvg = bpsList.average()
        for (it in iterations) {
            val list = buildList {
                if (bpsAvg > 0.0) {
                    add("${it.bytesPerSecond.formatBytes()}/s")
                    add(formatDeviation(it.bytesPerSecond, bpsAvg))
                } else {
                    add("${(it.countPerSecond).formatDouble(3)} op/s")
                    add(formatDeviation(it.countPerSecond, cpsAvg))
                }
                add("checksum=${it.checksum}")
            }
            println("$name: ${list.joinToString(", ")}")
        }
    }

    // warmup
    showResults(
        name = "warmup-$name",
        iterations = (0 until options.warmupIterations).map {
            runIteration(options.warmupDuration)
        },
    )
    // measurement
    showResults(
        name = "measurement-$name",
        iterations = (0 until options.measurementIterations).map {
            runIteration(options.measurementDuration)
        },
    )
}

private fun String.parseDuration(name: String): Duration {
    val duration = Duration.parseOrNull(this)
        ?: error("$name :parse error. [$this]")
    return duration
        .takeIf { it >= 1.milliseconds }
        ?: error("$name :must >= 1ms.")
}

private fun String.parsePositiveInt(name: String): Int = toIntOrNull()
    ?.takeIf { it > 0 }
    ?: error("$name :must be a positive integer. [$this]")

private fun countPerSecond(
    count: Long,
    elapsed: Duration,
): Double {
    val elapsedNanos = elapsed.inWholeNanoseconds.coerceAtLeast(1L)
    return count.toDouble() * 1_000_000_000.0 / elapsedNanos
}

private fun bytesPerSecond(
    bytes: Long,
    elapsed: Duration,
): Double {
    val elapsedNanos = elapsed.inWholeNanoseconds.coerceAtLeast(1L)
    return bytes.toDouble() * 1_000_000_000.0 / elapsedNanos
}

private fun Double.formatDouble(n: Int = 1): String = when {
    !isFinite() || this < -Long.MAX_VALUE || this > Long.MAX_VALUE || n > 18 ->
        toString()

    else -> {
        val sign = if (this < 0.0) "-" else ""
        // toLong()は0方向への丸め
        val intPart = abs(this).toLong()
        val fraction = when {
            n <= 0 -> ""
            else -> {
                var scale = 1L
                repeat(n) { scale *= 10L }
                abs(this).mod(1.0).times(scale)
                    .toLong().toString().padStart(n, '0')
            }
        }
        "$sign$intPart.$fraction"
    }
}

private fun Double.formatBytes(): String {
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = this
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return "${value.formatDouble(3)}${units[unitIndex]}"
}

private fun formatDeviation(value: Double, average: Double): String {
    if (!value.isFinite() || !average.isFinite() || average == 0.0) return "n/a"
    val deviation = (value - average) * 100.0 / average
    val sign = when {
        deviation > 0.0 -> "+"
        deviation < 0.0 -> "-"
        else -> "±"
    }
    return "$sign${abs(deviation).formatDouble(1)}%"
}
