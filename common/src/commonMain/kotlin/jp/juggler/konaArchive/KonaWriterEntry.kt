package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.KonaRandomAccess

/**
 * KonaArchiveのエンコーダが参照するファイルツリーの抽象化
 */
sealed class KonaWriterEntry : Comparable<KonaWriterEntry> {
    abstract val name: String

    override fun compareTo(other: KonaWriterEntry): Int =
        this.name.compareTo(other.name)
}

class KonaWriterFile(
    override val name: String,
    val open: () -> KonaRandomAccess,
) : KonaWriterEntry()

class KonaWriterDirectory(
    override val name: String,
    val list: () -> List<KonaWriterEntry>,
) : KonaWriterEntry()
