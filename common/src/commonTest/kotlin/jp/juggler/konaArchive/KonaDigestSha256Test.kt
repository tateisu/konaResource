package jp.juggler.konaArchive

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import jp.juggler.konaArchive.util.KonaSha256Kotlin
import jp.juggler.konaArchive.util.defaultKonaSha256
import jp.juggler.konaArchive.util.hex

class KonaDigestSha256Test : FreeSpec() {
    init {
        "SHA-256 matches the standard vector" {
            val input = "abc".encodeToByteArray()
            defaultKonaSha256.digest(input).hex() shouldBe
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        }

        "SHA-256 update uses an exclusive end offset" {
            val input = "xabcY".encodeToByteArray()
            defaultKonaSha256.digest { updateDigest ->
                updateDigest(input, 1, 4)
            }.hex() shouldBe
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        }

        "Pure Kotlin SHA-256 supports streaming updates" {
            val input = ByteArray(257) { it.toByte() }
            KonaSha256Kotlin().digest { updateDigest ->
                updateDigest(input, 0, 1)
                updateDigest(input, 1, 128)
                updateDigest(input, 128, input.size)
            }.hex() shouldBe defaultKonaSha256.digest(input).hex()
        }
        "Pure Kotlin SHA-256 hash result is same to defaultKonaSha256" {
            val files = konaArchiveTestUtils.sourceFiles("common/src")
            files.isNotEmpty() shouldBe true

            files.forEach { file ->
                val defaultDigest = defaultKonaSha256.digest(file.bytes)
                val kotlinDigest = KonaSha256Kotlin().digest(file.bytes)
                check(kotlinDigest.contentEquals(defaultDigest)) {
                    "SHA-256 mismatch: ${file.path}, " +
                        "default=${defaultDigest.hex()}, kotlin=${kotlinDigest.hex()}"
                }
            }
        }
    }
}
