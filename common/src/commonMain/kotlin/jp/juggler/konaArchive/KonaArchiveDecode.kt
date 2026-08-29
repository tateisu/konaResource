package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.KonaRandomAccess
import jp.juggler.konaArchive.util.checkSha256

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
    seek(headerStart)
    readInt32("header.compressedDataStart")
    val contentMetaStart = readInt32("header.contentMetaStart").toLong()
    val contentMetaSha256 = readBytes(32, "header.contentMetaSha256")
    val namesStart = readInt32("header.namesStart").toLong()
    val namesSha256 = readBytes(32, "header.namesSha256")
    val dirItemsStart = readInt32("header.dirItemsStart").toLong()
    val dirItemsCount = readInt32("header.dirItemsCount")
    val dirItemsSha256 = readBytes(32, "header.dirItemsSha256")
    val rootDirIndex = readInt32("header.rootDirIndex")
    val rootDirSize = readInt32("header.rootDirSize")
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
        end = dirItemsStart
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
            val entrySize = 3 * 4 + 2 * 32
            while (offset < namesStart) {
                callback(
                    readContentMeta(
                        name = "",
                        entryOffset = offset.toInt()
                    )
                )
                offset += entrySize
            }
        },
    )
}


internal fun KonaRandomAccess.readContentMeta(
    name: String,
    entryOffset: Int,
): KonaArchiveFile {
    seek(entryOffset.toLong())
    val compressedStart = readInt32("contentMeta.compressedStart")
    val compressedSha256 = readBytes(32, "contentMeta.compressedSha256")
    val compressedSize = readInt32("contentMeta.compressedSize")
    val uncompressedSha256 = readBytes(32, "contentMeta.unconmpressedSha256")
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
