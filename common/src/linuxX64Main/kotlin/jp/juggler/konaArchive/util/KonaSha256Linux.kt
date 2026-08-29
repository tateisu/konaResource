package jp.juggler.konaArchive.util

import jp.juggler.konaResource.openssl.kona_sha256_final
import jp.juggler.konaResource.openssl.kona_sha256_free
import jp.juggler.konaResource.openssl.kona_sha256_new
import jp.juggler.konaResource.openssl.kona_sha256_update
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

@OptIn(ExperimentalForeignApi::class)
class KonaSha256Linux : KonaSha256 {
    private var context = kona_sha256_new()
        ?: error("Unable to create OpenSSL SHA-256 context")
    private var finished = false

    override fun update(ba: ByteArray, start: Int, end: Int) {
        check(!finished) { "KonaSha256 has already been finished" }
        require(start in 0..ba.size && end in start..ba.size) {
            "Invalid SHA-256 input range"
        }
        val length = end - start
        if (length <= 0) return
        val activeContext = context
        ba.usePinned { pinned ->
            check(
                kona_sha256_update(
                    activeContext,
                    pinned.addressOf(start),
                    length.toULong()
                ) == 1
            ) { "OpenSSL SHA-256 update failed" }
        }
    }

    override fun finish(): ByteArray {
        check(!finished) { "KonaSha256 has already been finished" }
        finished = true
        val result = ByteArray(32)
        val activeContext = context
        try {
            result.usePinned { pinned ->
                check(kona_sha256_final(activeContext, pinned.addressOf(0)) == 1) {
                    "OpenSSL SHA-256 final failed"
                }
            }
            return result
        } finally {
            kona_sha256_free(activeContext)
        }
    }
}

internal actual val defaultKonaSha256: () -> KonaSha256 = { KonaSha256Linux() }
