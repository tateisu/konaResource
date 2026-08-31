package jp.juggler.konaArchive.util

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class KonaSha256IntrinsicsTest : FreeSpec() {
    init {
        "SHA-256 matches the standard vector" {
            KonaSha256Intrinsics().digest("abc".encodeToByteArray()).hex() shouldBe
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        }

        "SHA-256 supports streaming updates" {
            val input = "abc".encodeToByteArray()
            KonaSha256Intrinsics().digest { updateDigest ->
                updateDigest(input, 0, 1)
                updateDigest(input, 1, 2)
                updateDigest(input, 2, input.size)
            }.hex() shouldBe
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        }

        "SHA-256 supports empty input" {
            KonaSha256Intrinsics().digest(ByteArray(0)).hex() shouldBe
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        }
    }
}
