package jp.juggler.konaArchive.util

import java.security.MessageDigest

class KonaSha256Jvm : KonaSha256 {
    private val digest = MessageDigest.getInstance("SHA-256")
    private var finished = false

    override fun update(ba: ByteArray, start: Int, end: Int) {
        check(!finished) { "KonaSha256 has already been finished" }
        require(start in 0..ba.size && end in start..ba.size) {
            "Invalid SHA-256 input range"
        }
        if (start < end) digest.update(ba, start, end - start)
    }

    override fun finish(): ByteArray {
        check(!finished) { "KonaSha256 has already been finished" }
        finished = true
        return digest.digest()
    }
}

internal actual val defaultKonaSha256: () -> KonaSha256 = { KonaSha256Jvm() }
