package jp.juggler.konaArchive

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import jp.juggler.konaArchive.util.defaultKonaBlake3n256
import jp.juggler.konaArchive.util.hex

class KonaDigestBlake3Test : FreeSpec() {
    init {
        "default BLAKE3 implementation matches the official empty input vector" {
            defaultKonaBlake3n256.digest(ByteArray(0)).hex() shouldBe
                "af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262"
        }

        "default BLAKE3 implementation supports streaming updates" {
            val input = ByteArray(1024) { (it % 251).toByte() }
            defaultKonaBlake3n256.digest { updateDigest ->
                updateDigest(input, 0, 1)
                updateDigest(input, 1, 512)
                updateDigest(input, 512, input.size)
            }.hex() shouldBe
                "42214739f095a406f3fc83deb889744ac00df831c10daa55189b5d121c855af7"
        }
    }
}
