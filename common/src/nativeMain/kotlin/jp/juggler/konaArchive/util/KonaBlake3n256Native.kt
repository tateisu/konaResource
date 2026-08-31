package jp.juggler.konaArchive.util

import jp.juggler.konaResource.blake3.kona_blake3_finalize
import jp.juggler.konaResource.blake3.kona_blake3_free
import jp.juggler.konaResource.blake3.kona_blake3_new
import jp.juggler.konaResource.blake3.kona_blake3_update
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

@OptIn(ExperimentalForeignApi::class)
class KonaBlake3n256Native : KonaDigest() {
    override fun digest(
        updater: (updateDigest: (ba: ByteArray, start: Int, end: Int) -> Unit) -> Unit,
    ): ByteArray {
        val context = kona_blake3_new()
            ?: error("Unable to create BLAKE3 context")
        try {
            updater { ba, start, end ->
                require(start in 0..ba.size && end in start..ba.size) {
                    "Invalid BLAKE3 input range"
                }
                val length = end - start
                if (length <= 0) return@updater
                ba.usePinned { pinned ->
                    kona_blake3_update(context, pinned.addressOf(start), length.toULong())
                }
            }
            val result = ByteArray(BLAKE3_OUT_LEN)
            result.usePinned { pinned ->
                kona_blake3_finalize(context, pinned.addressOf(0), result.size.toULong())
            }
            return result
        } finally {
            kona_blake3_free(context)
        }
    }

    private companion object {
        const val BLAKE3_OUT_LEN = 32
    }
}
