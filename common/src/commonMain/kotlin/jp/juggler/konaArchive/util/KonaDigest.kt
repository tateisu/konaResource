package jp.juggler.konaArchive.util

import okio.ByteString.Companion.toByteString

abstract class KonaDigest {
    /**
     * ダイジェストを計算する
     *
     * 使用例
     * val digest = konaDigest.digest{ updateDigest->
     *    updateDigest(...)
     *    updateDigest(...)
     *    updateDigest(...)
     * }
     *
     * Note: 実装者はtry-finallyでリソース開放すること
     *
     * @param updater updateDigestラムダを0回以上呼び出すラムダ式
     * @return ダイジェストのハッシュ値
     */
    abstract fun digest(
        updater: (
            updateDigest: (ba: ByteArray, start: Int, end: Int) -> Unit,
        ) -> Unit,
    ): ByteArray

    /**
     * KonaRandomAccess の指定範囲を読んでダイジェストを計算する
     */
    fun rangeDigest(
        access: KonaRandomAccess,
        start: Long = 0L,
        end: Long = access.size,
    ): ByteArray = digest { updateDigest ->
        access.readRange(start, end) { ba, len ->
            updateDigest(ba, 0, len)
        }
    }

    /**
     * KonaRandomAccess の指定範囲のダイジェストが一致するか確認
     */
    fun checkDigest(
        name: String,
        expect: ByteArray, // 32byte
        access: KonaRandomAccess,
        start: Long,
        end: Long,
    ) {
        val actual = rangeDigest(access, start, end)
        if (!actual.contentEquals(expect)) error("digest mismatch: $name")
    }

    fun digest(ba: ByteArray, start: Int = 0, end: Int = ba.size) =
        digest { it(ba, start, end) }
}

expect val defaultKonaSha256: KonaDigest
expect val defaultKonaBlake3n256: KonaDigest

fun ByteArray.hex(): String = toByteString().hex()
