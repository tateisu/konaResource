package jp.juggler.konaResource

import jp.juggler.konaArchive.KonaArchive
import jp.juggler.konaArchive.decodeKonaArchive
import jp.juggler.konaArchive.decodeKonaArchiveOrClose
import jp.juggler.konaArchive.util.FileRandomAccess
import jp.juggler.konaArchive.util.embedRandomAccess

/**
 * pathを指定してKonaArchiveを開く。
 * 使い終わったらcloseすること
 */
fun openKonaArchive(path: String): KonaArchive =
    FileRandomAccess(path, isReadOnly = true)
        .decodeKonaArchiveOrClose()

/**
 * pluginでkexeに埋め込んだ名前を指定してKonaArchiveを開く
 */
fun embedKonaArchive(name: String) =
    embedRandomAccess(name).decodeKonaArchive()
