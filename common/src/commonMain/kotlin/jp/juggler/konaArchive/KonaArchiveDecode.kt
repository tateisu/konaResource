package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.ByteArrayRandomAccess
import jp.juggler.konaArchive.util.KonaRandomAccess
import jp.juggler.konaArchive.util.checkSha256

/**
 * ByteArrayからKonaArchiveを開く
 * - ByteArrayにアクセスし続けるので後から内容を変更すると動作が壊れる
 */
fun ByteArray.openKonaArchive(): KonaArchive =
    ByteArrayRandomAccess(this).decodeKonaArchiveOrClose()

/**
 * KonaRandomAccessからKonaArchiveを開く
 * 失敗したらKonaRandomAccessをcloseする
 */
@Suppress("TooGenericExceptionCaught")
fun KonaRandomAccess.decodeKonaArchiveOrClose(): KonaArchive = try {
    decodeKonaArchive()
} catch (ex: Throwable) {
    runCatching { close() }
    throw ex
}

/**
 * ランダムアクセス可能なストリームから KonaArchive をデコードして、
 * アーカイブ中のルートフォルダの KonaArchiveDir を返す
 */
@Suppress("MagicNumber", "LongMethod")
fun KonaRandomAccess.decodeKonaArchive(): KonaArchive {
    seek(0)
    val magic = readInt32("magic")
    if (magic != KONA_ARCHIVE_MAGIC) error("magic not match. 0x${magic.toString(16)}")
    seek(size - 4)
    val version = readInt32("version")
    if (version != KONA_ARCHIVE_VERSION) error("version not match. $version")
    val headerEnd = size - 4 - 32
    seek(headerEnd)
    val headerSha256 = readBytes(32, "headerSha256")
    val headerSize = 7 * 4 + 3 * 32
    val headerStart = headerEnd - headerSize
    require(headerStart >= 4L) { "header starts before the magic" }
    seek(headerStart)
    val compressedDataStart = readInt32("header.compressedDataStart").toLong()
    val contentMetaStart = readInt32("header.contentMetaStart").toLong()
    val contentMetaSha256 = readBytes(32, "header.contentMetaSha256")
    val namesStart = readInt32("header.namesStart").toLong()
    val namesSha256 = readBytes(32, "header.namesSha256")
    val dirItemsStart = readInt32("header.dirItemsStart").toLong()
    val dirItemsCount = readInt32("header.dirItemsCount")
    val dirItemsSha256 = readBytes(32, "header.dirItemsSha256")
    val rootDirIndex = readInt32("header.rootDirIndex")
    val rootDirSize = readInt32("header.rootDirSize")
    require(compressedDataStart in 4L..contentMetaStart) {
        "invalid compressedData range: $compressedDataStart..$contentMetaStart"
    }
    require(contentMetaStart <= namesStart && namesStart <= dirItemsStart && dirItemsStart <= headerStart) {
        "archive sections are out of order"
    }
    require((namesStart - contentMetaStart) % KONA_ARCHIVE_CONTENT_META_SIZE == 0L) {
        "contentMeta section has an incomplete entry"
    }
    require(dirItemsCount >= 0) { "dirItemsCount must not be negative: $dirItemsCount" }
    require(headerStart - dirItemsStart == dirItemsCount.toLong() * KONA_ARCHIVE_DIR_ITEM_SIZE) {
        "dirItems section size does not match dirItemsCount"
    }
    require(rootDirIndex >= 0 && rootDirSize >= 0) {
        "root directory range must not be negative"
    }
    if (rootDirSize == 0) {
        require(rootDirIndex == 0) { "empty root directory must have rootDirIndex=0" }
    } else {
        require(rootDirIndex.toLong() + rootDirSize <= dirItemsCount) {
            "root directory range exceeds dirItems"
        }
    }
    checkSha256(
        name = "contentMeta",
        expect = contentMetaSha256,
        start = contentMetaStart,
        end = namesStart,
    )
    checkSha256(
        name = "names",
        expect = namesSha256,
        start = namesStart,
        end = dirItemsStart,
    )
    checkSha256(
        name = "dirItems",
        expect = dirItemsSha256,
        start = dirItemsStart,
        end = headerStart,
    )
    checkSha256(
        name = "header",
        expect = headerSha256,
        start = headerStart,
        end = headerEnd,
    )
    return KonaArchive(
        access = this,
        root = KonaArchiveDir(
            name = "",
            access = this,
            dirItemsStart = dirItemsStart,
            dirItemsRange = 0 until dirItemsCount,
            dirIndex = rootDirIndex,
            dirCount = rootDirSize,
        ),
        contentMetas = { callback ->
            var offset = contentMetaStart
            val entrySize = KONA_ARCHIVE_CONTENT_META_SIZE
            while (offset < namesStart) {
                callback(
                    readContentMeta(
                        name = "",
                        entryOffset = offset.toInt(),
                    ),
                )
                offset += entrySize
            }
        },
    )
}

@Suppress("MagicNumber")
internal fun KonaRandomAccess.readContentMeta(
    name: String,
    entryOffset: Int,
): KonaArchiveFile {
    seek(entryOffset.toLong())
    val compressedStart = readInt32("contentMeta.compressedStart")
    val compressedSha256 = readBytes(32, "contentMeta.compressedSha256")
    val compressedSize = readInt32("contentMeta.compressedSize")
    val uncompressedSha256 = readBytes(32, "contentMeta.uncompressedSha256")
    val uncompressedSize = readInt32("contentMeta.uncompressedSize")
    return KonaArchiveFile(
        name = name,
        access = this,
        compressedStart = compressedStart,
        compressedSha256 = compressedSha256,
        compressedSize = compressedSize,
        uncompressedSha256 = uncompressedSha256,
        uncompressedSize = uncompressedSize,
    )
}
