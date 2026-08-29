package jp.juggler.konaArchive

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import jp.juggler.konaArchive.util.defaultKonaSha256
import jp.juggler.konaArchive.util.defaultLz4Codec
import jp.juggler.konaArchive.util.hex
import jp.juggler.konaArchive.util.sha256
import okio.Buffer

class CommonCodecTest : FreeSpec({
    "SHA-256 matches the standard vector" {
        val input = "abc".encodeToByteArray()
        input.sha256().hex() shouldBe
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    }

    "SHA-256 update uses an exclusive end offset" {
        val digest = defaultKonaSha256()
        digest.update("xabcY".encodeToByteArray(), 1, 4)
        digest.finish().hex() shouldBe
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    }

    "LZ4 compresses and decompresses data" {
        val input = ByteArray(128 * 1024) { index -> (index * 31).toByte() }
        val compressed = defaultLz4Codec.compress(
            inputSize = input.size,
            input = { buffer ->
                buffer.write(input)
                input.size
            },
        )

        val compressedBytes = compressed.readByteArray()
        var offset = 0
        val restored = defaultLz4Codec.decompress(
            expectedSize = input.size,
            input = { buffer ->
                if (offset == compressedBytes.size) {
                    -1
                } else {
                    val size = compressedBytes.size - offset
                    buffer.write(compressedBytes, offset, size)
                    offset += size
                    size
                }
            },
        )

        offset shouldBe compressedBytes.size
        restored.readByteArray().toList() shouldBe input.toList()
    }
})
