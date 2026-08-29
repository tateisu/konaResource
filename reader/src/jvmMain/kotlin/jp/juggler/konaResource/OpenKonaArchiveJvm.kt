package jp.juggler.konaResource

import jp.juggler.konaArchive.KonaArchive
import jp.juggler.konaArchive.decodeKonaArchiveOrClose
import jp.juggler.konaArchive.util.FileRandomAccess
import java.io.File

/**
 * Fileを指定してKonaArchiveを開く。
 * 使い終わったらcloseすること
 */
fun File.openKonaArchive(): KonaArchive =
    FileRandomAccess(this, isReadOnly = true)
        .decodeKonaArchiveOrClose()
