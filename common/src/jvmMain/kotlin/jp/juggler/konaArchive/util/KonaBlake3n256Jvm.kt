package jp.juggler.konaArchive.util

import org.apache.commons.codec.digest.Blake3

actual val defaultKonaBlake3n256: KonaDigest = KonaBlake3n256Jvm()

class KonaBlake3n256Jvm : KonaDigest() {
    private companion object {
        const val DIGEST_SIZE = 32
    }

    override fun digest(
        updater: (updateDigest: (ba: ByteArray, start: Int, end: Int) -> Unit) -> Unit,
    ): ByteArray {
        val digest = Blake3.initHash()
        updater { ba, start, end ->
            require(start in 0..ba.size && end in start..ba.size) {
                "Invalid BLAKE3 input range"
            }
            digest.update(ba, start, end - start)
        }
        return digest.doFinalize(DIGEST_SIZE)
    }
}
