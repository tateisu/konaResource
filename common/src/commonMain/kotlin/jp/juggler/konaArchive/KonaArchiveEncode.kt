package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.KonaRandomAccess
import jp.juggler.konaArchive.util.Lz4Options
import jp.juggler.konaArchive.util.defaultLz4Codec
import jp.juggler.konaArchive.util.hex
import jp.juggler.konaArchive.util.rangeSha256

private fun mergePath(
    parentPath: String,
    name: String
): String = when (parentPath) {
    "" -> name
    else -> "$parentPath/$name"
}

private fun KonaWriterDirectory.scan(
    path: String,
    block: (String, KonaWriterEntry) -> Unit
) {
    val dupCheck = mutableSetOf<String>()
    for (entry in list().sorted()) {
        val childPath = mergePath(path, entry.name)
        if (entry.name.isEmpty()) error("empty entry name is not allowed.")
        if (dupCheck.contains(entry.name)) error("duplicated entry name. $childPath")
        dupCheck.add(entry.name)
        when (entry) {
            is KonaWriterDirectory -> entry.scan(childPath, block)
            is KonaWriterFile -> block(childPath, entry)
        }
    }
    block(path, this)
}

/**
 * レシーバの先頭にシークして KonaArchive のバイナリを出力して truncate,close する
 */
fun KonaRandomAccess.encodeKonaArchive(
    root: KonaWriterDirectory,
    options: Lz4Options = Lz4Options(),
    previous: KonaArchive? = null,
) {
    val tmpArray = ByteArray(4096)
    seek(0L)
    writeInt32(KONA_ARCHIVE_MAGIC)
    // -------------------------
    // write compressed contents
    val previousDigests = previous?.let { p ->
        buildMap {
            p.contentMetas { entry ->
                put(entry.uncompressedSha256.hex(), entry)
            }
        }
    }
    val compressedDataStart = pos
    val contentMetas = mutableListOf<KonaArchiveFile>()
    val digestToMetaIndex = HashMap<String, Int>()
    val pathToMetaIndex = HashMap<String, Int>()
    root.scan("") { path, entry ->
        if (entry !is KonaWriterFile) return@scan
        var uncompressedSize = 0L
        val uncompressedSha256 = entry.open().use {
            uncompressedSize = it.size
            it.rangeSha256()
        }
        val digestHex = uncompressedSha256.hex()
        // 同一ファイル中で既出？
        val existing = digestToMetaIndex[digestHex]
        if (existing != null) {
            pathToMetaIndex[path] = existing
            return@scan
        }
        val compressedStart = pos
        val metaIndex = contentMetas.size
        // 前回のアーカイブに同一コンテンツがある？
        val old = previousDigests?.get(uncompressedSha256.hex())
        if (old != null) {
            // old のcompressedDataをストリームに書く
            old.openCompressed().use { src ->
                while (true) {
                    val nRead = src.readByteArray(tmpArray, 0, tmpArray.size)
                    if (nRead <= 0) break
                    writeByteArray(tmpArray, 0, nRead)
                }
            }
            pathToMetaIndex[path] = metaIndex
            digestToMetaIndex[digestHex] = metaIndex
            contentMetas.add(
                KonaArchiveFile(
                    name = "", // not used
                    access = this,
                    compressedStart = compressedStart.toInt(),
                    compressedSize = old.compressedSize,
                    uncompressedSize = uncompressedSize.toInt(),
                    compressedSha256 = old.compressedSha256,
                    uncompressedSha256 = uncompressedSha256,
                )
            )
            return@scan
        }
        // 圧縮して書き出す
        entry.open().use { src ->
            defaultLz4Codec.compress(
                inputSize = uncompressedSize.toInt(),
                options = options,
                // codecが入力バイト列を要求したら呼ばれる。
                // 戻り値は追加したバイト数。-1 は入力の終端を表す。
                input = {
                    val i = src.readByteArray(tmpArray, 0, tmpArray.size)
                    if (i > 0) it.write(tmpArray, 0, i)
                    i
                },
                output = {
                    writeByteArray(it.readByteArray())
                }
            )
        }
        val end = pos
        val compressedSize = (end - compressedStart)
        val compressedSha256 = rangeSha256(start = compressedStart, end = pos)
        seek(end)
        pathToMetaIndex[path] = metaIndex
        digestToMetaIndex[digestHex] = metaIndex
        contentMetas.add(
            KonaArchiveFile(
                name = "", // not used
                access = this,
                compressedStart = compressedStart.toInt(),
                compressedSize = compressedSize.toInt(),
                uncompressedSize = uncompressedSize.toInt(),
                compressedSha256 = compressedSha256,
                uncompressedSha256 = uncompressedSha256,
            )
        )
    }

    // -------------------------
    // write contentMetas
    val contentMetaStart = pos
    val metaStarts = contentMetas.map {
        val metaStart = pos
        writeInt32(it.compressedStart)
        writeByteArray(it.compressedSha256)
        writeInt32(it.compressedSize)
        writeByteArray(it.uncompressedSha256)
        writeInt32(it.uncompressedSize)
        metaStart
    }
    // -------------------------
    // write names
    val namesStart = pos
    val nameMap = mutableMapOf<String, Long>()
    root.scan("") { _, entry ->
        val name = entry.name
        if (nameMap.containsKey(name)) return@scan
        nameMap[name] = pos
        val nameBytes = name.encodeToByteArray()
        writeInt32(nameBytes.size)
        writeByteArray(nameBytes)
    }
    // -------------------------
    // write dirItems
    val dirItemsStart = pos
    val pathToDirInfo = HashMap<String, Pair<Int, Int>>()
    var dirItemsCount = 0
    root.scan("") { path, entry ->
        if (entry !is KonaWriterDirectory) return@scan
        val list = entry.list().sorted()
        pathToDirInfo[path] = if (list.isEmpty()) {
            0 to 0
        } else {
            val startIndex = dirItemsCount
            for (it in list) {
                val childPath = mergePath(path, it.name)
                writeInt32(nameMap[it.name]!!.toInt())
                when (it) {
                    is KonaWriterFile -> {
                        val metaIndex = pathToMetaIndex[childPath]!!
                        writeInt32(metaStarts[metaIndex].toInt())
                        writeInt32(0)
                    }

                    is KonaWriterDirectory -> {
                        val info = pathToDirInfo.remove(childPath)!!
                        writeInt32(info.first or DIR_FLAG)
                        writeInt32(info.second)
                    }
                }
                ++dirItemsCount
            }
            startIndex to list.size
        }
    }
    //--------------------------------------
    // write header
    val headerStart = pos

    val contentMetaSha256 = rangeSha256(contentMetaStart, namesStart)
    val namesSha256 = rangeSha256(namesStart, dirItemsStart)
    val dirItemsSha256 = rangeSha256(dirItemsStart, headerStart)
    seek(headerStart)
    writeInt32(compressedDataStart.toInt())
    writeInt32(contentMetaStart.toInt())
    writeByteArray(contentMetaSha256)
    writeInt32(namesStart.toInt())
    writeByteArray(namesSha256)
    writeInt32(dirItemsStart.toInt())
    writeInt32(dirItemsCount)
    writeByteArray(dirItemsSha256)
    val rootDirInfo = pathToDirInfo[""]!!
    writeInt32(rootDirInfo.first)
    writeInt32(rootDirInfo.second)
    val headerEnd = pos

    val headerSha256 = rangeSha256(headerStart, headerEnd)
    seek(headerEnd)
    writeByteArray(headerSha256)
    writeInt32(KONA_ARCHIVE_VERSION)
    truncate()
    close()
}
