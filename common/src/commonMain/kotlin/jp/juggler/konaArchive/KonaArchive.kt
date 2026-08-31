package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.KonaDigest
import jp.juggler.konaArchive.util.KonaRandomAccess
import jp.juggler.konaArchive.util.defaultLz4Codec
import jp.juggler.konaArchive.util.hex
import okio.Buffer

/**
 * MAGICは KonaArchive ファイル先頭にlittle endianで書かれる
 */
internal const val KONA_ARCHIVE_MAGIC: Int = 0x0123CDEF

/**
 * エンコーダが出力する KonaArchive ファイルのスキーマバージョン。
 * - version 1: ダイジェストは SHA256
 * - version 2: ダイジェストは BLAKE-3-256
 */
internal const val KONA_ARCHIVE_VERSION: Int = 2
internal const val KONA_ARCHIVE_CONTENT_META_SIZE: Int = 3 * 4 + 2 * 32
internal const val KONA_ARCHIVE_DIR_ITEM_SIZE: Int = 12
internal const val DIR_FLAG = 0x80000000.toInt()
internal const val DIR_MASK = DIR_FLAG.inv()

class KonaArchive(
    val accessAndDigester: AccessAndDigester,
    @Suppress("unused")
    val root: KonaArchiveDir,
    // SHA256で識別されるコンテンツを列挙するラムダ
    val contentMetas: (callback: (KonaArchiveFile) -> Unit) -> Unit,
) : AutoCloseable {
    override fun close() {
        accessAndDigester.access.close()
    }

    class AccessAndDigester(
        val access: KonaRandomAccess,
        val version: Int,
        val digester: KonaDigest,
    )

    fun pathToFile(path: String): KonaArchiveFile? = root.pathToFile(path)
    fun pathToDir(path: String): KonaArchiveDir? = root.pathToDir(path)
    fun pathToEntry(path: String): KonaArchiveEntry? = root.pathToEntry(path)
}

/**
 * KonaArchiveのディレクトリ中の要素の抽象インタフェース
 */
sealed class KonaArchiveEntry {
    abstract val accessAndDigester: KonaArchive.AccessAndDigester
    abstract val name: String
    abstract val size: Int
}

/**
 * KonaArchiveのディレクトリ中のファイル
 */
class KonaArchiveFile(
    override val accessAndDigester: KonaArchive.AccessAndDigester,
    override val name: String,
    val compressedStart: Int,
    val compressedSize: Int,
    val uncompressedSize: Int,
    // verifySha256() で圧縮前後のデータを検証する
    val compressedDigest: ByteArray,
    val uncompressedDigest: ByteArray,
) : KonaArchiveEntry() {
    companion object {
        internal fun metaKey(
            version: Int,
            size: Int,
            digest: ByteArray,
        ) = "$version:$size:${digest.hex()}"
    }

    override val size = uncompressedSize
    fun isEmpty() = size <= 0
    fun isNotEmpty() = size > 0

    fun metaKey() = metaKey(
        version = accessAndDigester.version,
        size = uncompressedSize,
        digest = uncompressedDigest,
    )

    /**
     * コンテンツをデコードする。内容は順次 callbackに渡される。
     * SHA256 の検証は行わない。
     */
    fun content(
        callback: (Buffer) -> Unit = {},
    ): Buffer = accessAndDigester.access.subRange(
        compressedStart.toLong(),
        (compressedStart + compressedSize).toLong(),
    ).use { compressedRange ->
        compressedRange.seek(0L)
        defaultLz4Codec.decompress(
            expectedSize = uncompressedSize,
            output = callback,
            input = {
                val i = compressedRange.readToTmpArray()
                when {
                    i <= 0 -> -1
                    else -> {
                        it.write(
                            compressedRange.tmpArray,
                            0,
                            i,
                        )
                        i
                    }
                }
            },
        )
    }

    /**
     * 圧縮データと展開データのSHA256を検証する。
     * content() と異なり、検証のために圧縮データの読み込みと展開を行う。
     */
    fun verifyDigest() {
        val compressedDigest = openCompressed().use {
            accessAndDigester.digester.rangeDigest(it)
        }
        require(compressedDigest.contentEquals(this@KonaArchiveFile.compressedDigest)) {
            "compressed content digest mismatch: $name"
        }
        val uncompressedDigest = accessAndDigester.digester.digest { updateDigest ->
            content { buffer ->
                val bytes = buffer.readByteArray()
                updateDigest(bytes, 0, bytes.size)
            }
        }
        require(uncompressedDigest.contentEquals(this@KonaArchiveFile.uncompressedDigest)) {
            "uncompressed content digest mismatch: $name"
        }
    }

    /**
     * 圧縮されたデータのバイト列を含むKonaRandomAccessを返す
     * 使い終わったらcloseすること
     */
    fun openCompressed(): KonaRandomAccess = accessAndDigester.access.subRange(
        start = compressedStart.toLong(),
        end = (compressedStart + compressedSize).toLong(),
    )

    /**
     * 解凍データをBufferとして返す
     * - 巨大データは扱えない
     */
    fun buffer(): Buffer {
        val buffer = Buffer()
        content { buffer.write(it.readByteArray()) }
        return buffer
    }

    /**
     * 解凍データをByteArrayとして返す
     * - 巨大データは扱えない
     */
    fun bytes(): ByteArray = buffer().readByteArray()

    /**
     * 解凍データを文字列として返す
     * - 巨大データは扱えない
     */
    fun string(): String = buffer().readUtf8()
}

/**
 * KonaArchiveのディレクトリ
 */
@Suppress("TooManyFunctions")
class KonaArchiveDir(
    override val accessAndDigester: KonaArchive.AccessAndDigester,
    override val name: String,
    private val access: KonaRandomAccess,
    private val dirItemsStart: Long,
    private val dirItemsRange: IntRange,
    private val dirIndex: Int,
    private val dirCount: Int,
) : KonaArchiveEntry(), List<KonaArchiveEntry> {
    override val size = dirCount
    override fun isEmpty() = size <= 0

    // get(i) に指定可能な範囲
    private val _indices = 0 until dirCount

    init {
        require(dirIndex >= 0) { "dirIndex must not be negative: $dirIndex" }
        require(dirCount >= 0) { "dirCount must not be negative: $dirCount" }
        if (dirCount == 0) {
            require(dirIndex == 0) { "empty directory must have dirIndex=0: $dirIndex" }
        } else {
            check(dirIndex in dirItemsRange) {
                "dirIndex=$dirIndex must in $dirItemsRange"
            }
            check(dirIndex.toLong() + dirCount <= dirItemsRange.last.toLong() + 1L) {
                "directory range [$dirIndex, ${dirIndex + dirCount}) must in $dirItemsRange"
            }
        }
    }

    /**
     * names からnameを読む
     */
    private fun readNameString(start: Int): String = with(access) {
        seek(start.toLong())
        val length = readInt32("length")
        val bytes = readBytes(length, "name")
        bytes.decodeToString()
    }

    private fun readEntry(i: Int): KonaArchiveEntry = with(access) {
        // dirItems配列全体に対するインデクス
        val index = dirIndex + i
        if (index !in dirItemsRange) error("readEntry: index $index must in $dirItemsRange")
        seek(dirItemsStart + KONA_ARCHIVE_DIR_ITEM_SIZE.toLong() * index)
        val i0 = readInt32("entry-i0")
        val i1 = readInt32("entry-i1")
        val i2 = readInt32("entry-i2")
        val name = readNameString(i0)
        when {
            i1.and(DIR_FLAG) != 0 -> KonaArchiveDir(
                accessAndDigester = accessAndDigester,
                name = name,
                access = access.subRange(),
                dirItemsStart = dirItemsStart,
                dirItemsRange = dirItemsRange,
                dirIndex = i1.and(DIR_MASK),
                dirCount = i2,
            )

            else -> {
                val entryOffset = i1.and(DIR_MASK)
                readContentMeta(
                    accessAndDigester = accessAndDigester,
                    name = name,
                    entryOffset = entryOffset,
                )
            }
        }
    }

    /**
     * ディレクトリ中のi番目の要素の名前だけを取得する
     */
    private fun readEntryName(i: Int): String = with(access) {
        // dirItems配列全体に対するインデクス
        val index = dirIndex + i
        if (index !in dirItemsRange) error("readEntry: index $index must in $dirItemsRange")
        seek(dirItemsStart + KONA_ARCHIVE_DIR_ITEM_SIZE.toLong() * index)
        val i0 = readInt32("entry-i0")
        readNameString(i0)
    }

    override operator fun get(index: Int): KonaArchiveEntry = when {
        index in _indices -> readEntry(index)
        else -> throw IndexOutOfBoundsException("index $index must in $_indices")
    }

    fun name(i: Int): String? = if (i in _indices) readEntryName(i) else null

    private fun indexOfName(name: String): Int? {
        var low = 0
        var high = dirCount - 1
        while (low <= high) {
            val middle = low + (high - low) / 2
            val middleName = readEntryName(middle)
            when {
                middleName < name -> low = middle + 1
                middleName > name -> high = middle - 1
                else -> return middle
            }
        }
        return null
    }

    operator fun get(name: String): KonaArchiveEntry? =
        indexOfName(name)?.let { readEntry(it) }

    fun names() = buildList {
        for (i in _indices) {
            add(readEntryName(i))
        }
    }

    override fun contains(element: KonaArchiveEntry): Boolean =
        indexOfName(element.name) != null

    override fun containsAll(elements: Collection<KonaArchiveEntry>): Boolean =
        elements.all(::contains)

    override fun indexOf(element: KonaArchiveEntry): Int =
        indexOfName(element.name) ?: -1

    override fun iterator(): Iterator<KonaArchiveEntry> =
        listIterator()

    override fun lastIndexOf(element: KonaArchiveEntry): Int =
        indexOf(element)

    override fun listIterator(): ListIterator<KonaArchiveEntry> =
        listIterator(0)

    override fun listIterator(index: Int): ListIterator<KonaArchiveEntry> {
        if (index !in 0..size) {
            throw IndexOutOfBoundsException("index $index must be in 0..$size")
        }
        return object : ListIterator<KonaArchiveEntry> {
            private var cursor = index

            override fun previousIndex(): Int = cursor - 1
            override fun nextIndex(): Int = cursor

            override fun hasPrevious(): Boolean = cursor > 0
            override fun hasNext(): Boolean = cursor < size

            override fun previous(): KonaArchiveEntry =
                if (hasPrevious()) get(--cursor) else throw NoSuchElementException()

            override fun next(): KonaArchiveEntry =
                if (hasNext()) get(cursor++) else throw NoSuchElementException()
        }
    }

    override fun subList(
        fromIndex: Int,
        toIndex: Int,
    ): List<KonaArchiveEntry> {
        if (fromIndex !in 0..size || toIndex !in 0..size) {
            throw IndexOutOfBoundsException("range [$fromIndex, $toIndex) must be in 0..$size")
        }
        require(fromIndex <= toIndex) {
            "fromIndex ($fromIndex) must not be greater than toIndex ($toIndex)"
        }
        return (fromIndex until toIndex).map(::get)
    }

    /**
     * 相対 path の各要素を順に辿ってアーカイブエントリを取得する。
     * 先頭の空要素は `/` とみなして無視する。
     */
    @Suppress("ReturnCount")
    operator fun get(
        pathSegments: List<String>,
    ): KonaArchiveEntry? {
        val first = pathSegments.indexOfFirst { it.isNotEmpty() }
        if (first < 0) return this

        var current: KonaArchiveEntry? = this
        for (i in first until pathSegments.size) {
            val segment = pathSegments[i]
            current = if (segment.isEmpty()) null else (current as? KonaArchiveDir)?.get(segment)
            if (current == null) break
        }
        return current
    }

    /**
     * 引数の path 文字列を pathSegmentsに分解してアーカイブエントリを取得する。
     * 先頭の `/` の連続は無視する。
     */
    fun pathToEntry(
        path: String,
    ): KonaArchiveEntry? = get(
        buildList {
            var start = 0
            while (start <= path.length) {
                val separator = path.indexOf('/', start)
                val end = if (separator < 0) path.length else separator
                add(path.substring(start, end))
                if (separator < 0) break
                start = separator + 1
            }
        },
    )

    // ショートハンド
    fun pathToFile(path: String) = pathToEntry(path) as? KonaArchiveFile
    fun pathToDir(path: String) = pathToEntry(path) as? KonaArchiveDir
    fun file(i: Int) = get(i) as? KonaArchiveFile
    fun dir(i: Int) = get(i) as? KonaArchiveDir
    fun file(name: String) = get(name) as? KonaArchiveFile
    fun dir(name: String) = get(name) as? KonaArchiveDir
}
