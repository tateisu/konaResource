package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.util.KonaDigest
import jp.juggler.konaArchive.util.defaultKonaSha256

open class KonaSha256Benchmark {
    private lateinit var files: List<ByteArray>

    fun setup() {
        files = benchmarkSourceFiles()
        check(files.isNotEmpty()) { "Unable to find common/src source files" }
    }

    open fun defaultImplementation(): Int = digestAll { defaultSha256() }

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

internal expect fun benchmarkSourceFiles(): List<ByteArray>

internal fun defaultSha256(): KonaDigest = defaultKonaSha256
