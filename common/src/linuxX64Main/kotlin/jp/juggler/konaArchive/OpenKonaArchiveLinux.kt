package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.FileRandomAccess

/**
 * pathを指定してKonaArchiveを開く
 * 使い終わったらcloseすること
 */
fun openKonaArchive(path: String): KonaArchive =
    FileRandomAccess(path, isReadOnly = true)
        .decodeKonaArchiveOrClose()
