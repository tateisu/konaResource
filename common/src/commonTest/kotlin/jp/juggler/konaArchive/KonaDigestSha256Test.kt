package jp.juggler.konaArchive

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
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
    }
}
