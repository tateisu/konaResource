package jp.juggler.konaArchive.util

import jp.juggler.konaResource.openssl.kona_sha256_final
import jp.juggler.konaResource.openssl.kona_sha256_free
import jp.juggler.konaResource.openssl.kona_sha256_new
import jp.juggler.konaResource.openssl.kona_sha256_update
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

@OptIn(ExperimentalForeignApi::class)
class KonaSha256Linux : KonaDigest() {
    override fun digest(
        updater: (updateDigest: (ba: ByteArray, start: Int, end: Int) -> Unit) -> Unit,
    ): ByteArray {
        val context = kona_sha256_new()
            ?: error("Unable to create OpenSSL SHA-256 context")
        try {
            updater { ba, start, end ->
                require(start in 0..ba.size && end in start..ba.size) {
                    "Invalid SHA-256 input range"
                }
                val length = end - start
                if (length <= 0) return@updater
                ba.usePinned { pinned ->
                    check(kona_sha256_update(context, pinned.addressOf(start), length.toULong()) == 1) {
                        "OpenSSL SHA-256 update failed"
                    }
                }
            }
            val result = ByteArray(32)
            result.usePinned { pinned ->
                check(kona_sha256_final(context, pinned.addressOf(0)) == 1) {
                    "OpenSSL SHA-256 final failed"
                }
            }
            return result
        } finally {
            kona_sha256_free(context)
        }
    }
}

actual val defaultKonaSha256: KonaDigest = KonaSha256Linux()
