package jp.juggler.konaResource

import jp.juggler.konaArchive.decodeKonaArchive
import jp.juggler.konaArchive.util.EmbedRandomAccess
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import platform.posix.dlsym

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
    val start = dlsym(null, "konaResource_${symbol}_start")?.reinterpret<ByteVar>()
        ?: error("Kona Resource symbol not found: $name")
    val end = dlsym(null, "konaResource_${symbol}_end")?.reinterpret<ByteVar>()
        ?: error("Kona Resource end symbol not found: $name")
    return EmbedRandomAccess(start.rawValue.toLong() until end.rawValue.toLong())
}
