package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.ByteArrayRandomAccess
import jp.juggler.konaArchive.util.KonaRandomAccess
import jp.juggler.konaArchive.util.defaultKonaBlake3n256
import jp.juggler.konaArchive.util.defaultKonaSha256

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
    when (version) {
        in 1..2 -> Unit
        else -> error("version not in [1..2]")
    }
    val digester = when (version) {
        1 -> defaultKonaSha256
        2 -> defaultKonaBlake3n256
        else -> error("version not in [1..2]")
    }
    val headerEnd = size - 4 - 32
    seek(headerEnd)
    val headerDigest = readBytes(32, "headerSha256")
    val headerSize = 7 * 4 + 3 * 32
    val headerStart = headerEnd - headerSize
    require(headerStart >= 4L) { "header starts before the magic" }
    seek(headerStart)
    val compressedDataStart = readInt32("header.compressedDataStart").toLong()
    val contentMetaStart = readInt32("header.contentMetaStart").toLong()
    val contentMetaSha256 = readBytes(32, "header.contentMetaSha256")
    val namesStart = readInt32("header.namesStart").toLong()
    val namesDigest = readBytes(32, "header.namesSha256")
    val dirItemsStart = readInt32("header.dirItemsStart").toLong()
    val dirItemsCount = readInt32("header.dirItemsCount")
    val dirItemsDigest = readBytes(32, "header.dirItemsSha256")
    val rootDirIndex = readInt32("header.rootDirIndex")
    val rootDirSize = readInt32("header.rootDirSize")
    require(compressedDataStart in 4L..contentMetaStart) {
        "invalid compressedData range: $compressedDataStart..$contentMetaStart"
    }
    require(
        namesStart in contentMetaStart..dirItemsStart &&
            dirItemsStart <= headerStart,
    ) {
        "archive sections are out of order"
    }
    require(
        (namesStart - contentMetaStart) % KONA_ARCHIVE_CONTENT_META_SIZE == 0L,
    ) {
        "contentMeta section has an incomplete entry"
    }
    require(dirItemsCount >= 0) {
        "dirItemsCount must not negative. [$dirItemsCount]"
    }
    require(
        headerStart - dirItemsStart ==
            dirItemsCount.toLong() * KONA_ARCHIVE_DIR_ITEM_SIZE,
    ) {
        "dirItems section size does not match dirItemsCount"
    }
    require(rootDirIndex >= 0 && rootDirSize >= 0) {
        "root directory range must not be negative"
    }
    when (rootDirSize) {
        0 -> require(rootDirIndex == 0) {
            "empty root directory must have rootDirIndex=0"
        }

        else -> require(
            rootDirIndex.toLong() + rootDirSize <= dirItemsCount,
        ) {
            "root directory range not in dirItemsCount"
        }
    }

    digester.checkDigest(
        name = "contentMeta",
        expect = contentMetaSha256,
        access = this,
        start = contentMetaStart,
        end = namesStart,
    )
    digester.checkDigest(
        name = "names",
        expect = namesDigest,
        access = this,
        start = namesStart,
        end = dirItemsStart,
    )
    digester.checkDigest(
        name = "dirItems",
        expect = dirItemsDigest,
        access = this,
        start = dirItemsStart,
        end = headerStart,
    )
    digester.checkDigest(
        name = "header",
        expect = headerDigest,
        access = this,
        start = headerStart,
        end = headerEnd,
    )
    val accessAndDigester = KonaArchive.AccessAndDigester(
        access = this,
        version = version,
        digester = digester,
    )
    return KonaArchive(
        accessAndDigester = accessAndDigester,
        root = KonaArchiveDir(
            accessAndDigester = accessAndDigester,
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
                        accessAndDigester = accessAndDigester,
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
    accessAndDigester: KonaArchive.AccessAndDigester,
    name: String,
    entryOffset: Int,
): KonaArchiveFile {
    seek(entryOffset.toLong())
    val compressedStart = readInt32("contentMeta.compressedStart")
    val compressedDigest = readBytes(32, "contentMeta.compressedDigest")
    val compressedSize = readInt32("contentMeta.compressedSize")
    val uncompressedDigest = readBytes(32, "contentMeta.uncompressedDigest")
    val uncompressedSize = readInt32("contentMeta.uncompressedSize")
    return KonaArchiveFile(
        accessAndDigester = accessAndDigester,
        name = name,
        compressedStart = compressedStart,
        compressedDigest = compressedDigest,
        compressedSize = compressedSize,
        uncompressedDigest = uncompressedDigest,
        uncompressedSize = uncompressedSize,
    )
}
