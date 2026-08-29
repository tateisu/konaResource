package jp.juggler.konaArchive.util

import okio.ByteString.Companion.toByteString

interface KonaSha256 {
    fun update(ba: ByteArray, start: Int=0,end: Int=ba.size)
    fun finish(): ByteArray
}

internal expect val defaultKonaSha256: () -> KonaSha256

/**
 * KonaRandomAccess の指定範囲のSHA256ダイジェストを計算する
 */
internal fun KonaRandomAccess.rangeSha256(
    start: Long = 0L,
    end: Long = size,
): ByteArray {
    val digester = defaultKonaSha256()
    readRange(start, end) { ba, len ->
        digester.update(ba, 0, len)
    }
    return digester.finish()
}

internal fun KonaRandomAccess.checkSha256(
    name: String,
    expect: ByteArray, // 32byte
    start: Long,
    end: Long,
) {
    val actual = rangeSha256(start, end)
    if (!actual.contentEquals(expect)) error("digest mismatch: $name")
}

internal fun ByteArray.sha256(): ByteArray =
    with(defaultKonaSha256()){
        update(this@sha256)
        finish()
    }

internal fun ByteArray.hex(): String = toByteString().hex()
