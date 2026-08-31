package jp.juggler.konaResource

import jp.juggler.konaArchive.decodeKonaArchive
import jp.juggler.konaArchive.util.EmbedRandomAccess
import jp.juggler.konaResource.system.cinterop.kona_dlsym
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * pluginでkexeに埋め込んだ名前を指定してKonaArchiveを開く
 */
fun embedKonaArchive(name: String) =
    embedRandomAccess(name).decodeKonaArchive()

/**
 * Kitlin/Native アプリに埋め込まれたデータのアドレス範囲を調べてEmbedRandomAccessを返す
 */
@OptIn(ExperimentalForeignApi::class)
fun embedRandomAccess(name: String): EmbedRandomAccess {
    val symbol = name.replace(Regex("[^A-Za-z0-9_]"), "_")
    val start = kona_dlsym("konaResource_${symbol}_start")
        ?: error("Kona Resource symbol not found: $name")
    val end = kona_dlsym("konaResource_${symbol}_end")
        ?: error("Kona Resource end symbol not found: $name")
    return EmbedRandomAccess(start.rawValue.toLong() until end.rawValue.toLong())
}
