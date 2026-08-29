package jp.juggler.konaResource

import jp.juggler.konaArchive.KonaArchive
import jp.juggler.konaArchive.decodeKonaArchiveOrClose
import jp.juggler.konaArchive.util.ByteArrayRandomAccess

/**
 * ByteArrayからKonaArchiveを開く
 * - ByteArrayにアクセスし続けるので後から内容を変更すると動作が壊れる
 */
fun ByteArray.openKonaArchive(): KonaArchive =
    ByteArrayRandomAccess(this).decodeKonaArchiveOrClose()
