package jp.juggler.konaArchive.util

import java.security.MessageDigest

class KonaSha256Jvm : KonaDigest() {
    override fun digest(
        updater: (updateDigest: (ba: ByteArray, start: Int, end: Int) -> Unit) -> Unit,
    ): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        updater { ba, start, end ->
            require(start in 0..ba.size && end in start..ba.size) {
                "Invalid SHA-256 input range"
            }
            digest.update(ba, start, end - start)
        }
        return digest.digest()
    }
}
