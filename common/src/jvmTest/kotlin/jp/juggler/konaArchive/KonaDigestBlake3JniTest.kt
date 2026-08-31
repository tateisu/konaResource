package jp.juggler.konaArchive

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import jp.juggler.konaArchive.util.KonaBlake3n256Jni
import jp.juggler.konaArchive.util.hex

class KonaDigestBlake3JniTest : FreeSpec() {
    private val jni = KonaBlake3n256Jni()

    init {
        "matches the official empty input vector" {
            jni.digest(ByteArray(0)).hex() shouldBe
                "af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262"
        }

        "produces the same digest for split updates at block and chunk boundaries" {
            listOf(1, 63, 64, 65, 1023, 1024, 1025, 4097, 16 * 1024, 64 * 1024).forEach { size ->
                val input = ByteArray(size) { index -> (index * 31 + 7).toByte() }
                val expected = jni.digest(input).hex()
                jni.digest { updateDigest ->
                    var start = 0
                    while (start < input.size) {
                        val end = minOf(start + 17, input.size)
                        updateDigest(input, start, end)
                        start = end
                    }
                }.hex() shouldBe expected
            }
        }

        "supports split updates" {
            val input = ByteArray(4097) { index -> (index * 17 + 3).toByte() }
            jni.digest { updateDigest ->
                updateDigest(input, 0, 1)
                updateDigest(input, 1, 1024)
                updateDigest(input, 1024, input.size)
            }.hex() shouldBe jni.digest(input).hex()
        }
    }
}
