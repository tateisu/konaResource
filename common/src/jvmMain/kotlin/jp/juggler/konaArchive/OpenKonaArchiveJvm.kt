package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.FileRandomAccess
import java.io.File

/**
 * Fileを指定してKonaArchiveを開く。
 * 使い終わったらcloseすること
 */
fun File.openKonaArchive(): KonaArchive =
    FileRandomAccess(this, isReadOnly = true)
        .decodeKonaArchiveOrClose()
