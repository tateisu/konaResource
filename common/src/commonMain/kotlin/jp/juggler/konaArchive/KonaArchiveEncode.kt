package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.KonaRandomAccess
import jp.juggler.konaArchive.util.Lz4Options
import jp.juggler.konaArchive.util.defaultKonaBlake3n256
import jp.juggler.konaArchive.util.defaultLz4Codec

private fun mergePath(
    parentPath: String,
    name: String,
): String = when (parentPath) {
    "" -> name
    else -> "$parentPath/$name"
}

private fun KonaWriterDirectory.scan(
    path: String,
    block: (String, KonaWriterEntry) -> Unit,
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

private class WriteEnv(
    val root: KonaWriterDirectory,
    val accessAndDigester: KonaArchive.AccessAndDigester,
    val options: Lz4Options,
    val previousDigests: Map<String, KonaArchiveFile>,
) {
    val tmpArray: ByteArray = ByteArray(4096)
    val digestToMetaIndex = HashMap<String, Int>()
    val pathToMetaIndex = HashMap<String, Int>()
    val contentMetas = mutableListOf<KonaArchiveFile>()
    val hashDupCheck = mutableMapOf<String, String>()
    val access = accessAndDigester.access
    val digester = accessAndDigester.digester

    fun addMeta(
        path: String,
        key: String,
        meta: KonaArchiveFile,
    ) {
        hashDupCheck[key]?.let { oldPath ->
            error("duplicate hash. ['$oldPath', '$path']")
        }
        hashDupCheck[key] = path
        val metaIndex = contentMetas.size
        pathToMetaIndex[path] = metaIndex
        digestToMetaIndex[key] = metaIndex
        contentMetas.add(meta)
    }

    @Suppress("LongMethod")
    private fun writeContent(
        path: String,
        entry: KonaWriterEntry,
    ) {
        if (entry !is KonaWriterFile) return
        var uncompressedSize = 0
        val uncompressedDigest = entry.open().use { fileAccess ->
            uncompressedSize = fileAccess.size
                .takeIf { it <= Int.MAX_VALUE }
                ?.toInt() ?: error("too large file. $path")
            digester.rangeDigest(fileAccess)
        }
        val metaKey = KonaArchiveFile.metaKey(
            version = accessAndDigester.version,
            size = uncompressedSize,
            digest = uncompressedDigest,
        )
        // 同一ファイル中で既出？
        digestToMetaIndex[metaKey]?.let {
            pathToMetaIndex[path] = it
            return
        }
        val compressedStart = access.pos

        // 前回のアーカイブに同一コンテンツがある？
        previousDigests[metaKey]?.let { prevContent ->
            // old のcompressedDataをストリームに書く
            prevContent.openCompressed().use { src ->
                while (true) {
                    val nRead = src.readByteArray(tmpArray, 0, tmpArray.size)
                    if (nRead <= 0) break
                    access.writeByteArray(tmpArray, 0, nRead)
                }
            }
            addMeta(
                path = path,
                key = metaKey,
                meta = KonaArchiveFile(
                    // not used
                    accessAndDigester = accessAndDigester,
                    // not used
                    name = "",
                    compressedStart = compressedStart.toInt(),
                    compressedSize = prevContent.compressedSize,
                    uncompressedSize = uncompressedSize,
                    compressedDigest = prevContent.compressedDigest,
                    uncompressedDigest = uncompressedDigest,
                ),
            )
            return
        }

        // 圧縮して書き出す
        entry.open().use { src ->
            defaultLz4Codec.compress(
                inputSize = uncompressedSize,
                options = options,
                // codecが入力バイト列を要求したら呼ばれる。
                // 戻り値は追加したバイト数。-1 は入力の終端を表す。
                input = {
                    val i = src.readByteArray(tmpArray, 0, tmpArray.size)
                    if (i > 0) {
                        it.write(tmpArray, 0, i)
                        i
                    } else {
                        -1
                    }
                },
                output = { access.writeByteArray(it.readByteArray()) },
            )
        }
        val end = access.pos
        val compressedSize = (end - compressedStart)
            .takeIf { it <= Int.MAX_VALUE }
            ?.toInt() ?: error("too large compressed file. $path")
        val compressedDigest = digester.rangeDigest(
            access = access,
            start = compressedStart,
            end = end,
        )
        access.seek(end)
        addMeta(
            path = path,
            key = metaKey,
            meta = KonaArchiveFile(
                // not used
                accessAndDigester = accessAndDigester,
                // not used
                name = "",
                compressedStart = compressedStart.toInt(),
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                compressedDigest = compressedDigest,
                uncompressedDigest = uncompressedDigest,
            ),
        )
    }

    @Suppress("LongMethod")
    fun write() = with(access) {
        // -------------------------
        // write magic at start of access
        seek(0L)
        writeInt32(KONA_ARCHIVE_MAGIC)

        // -------------------------
        // write compressed contents
        val compressedDataStart = pos
        root.scan("", ::writeContent)

        // -------------------------
        // write contentMetas
        val contentMetaStart = pos
        val metaStarts = contentMetas.map {
            val metaStart = pos
            writeInt32(it.compressedStart)
            writeByteArray(it.compressedDigest)
            writeInt32(it.compressedSize)
            writeByteArray(it.uncompressedDigest)
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

        // --------------------------------------
        // write header
        val headerStart = pos

        val contentMetaDigest = digester.rangeDigest(this, contentMetaStart, namesStart)
        val namesDigest = digester.rangeDigest(this, namesStart, dirItemsStart)
        val dirItemsDigest = digester.rangeDigest(this, dirItemsStart, headerStart)
        seek(headerStart)
        writeInt32(compressedDataStart.toInt())
        writeInt32(contentMetaStart.toInt())
        writeByteArray(contentMetaDigest)
        writeInt32(namesStart.toInt())
        writeByteArray(namesDigest)
        writeInt32(dirItemsStart.toInt())
        writeInt32(dirItemsCount)
        writeByteArray(dirItemsDigest)
        val rootDirInfo = pathToDirInfo[""]!!
        writeInt32(rootDirInfo.first)
        writeInt32(rootDirInfo.second)
        val headerEnd = pos

        val headerDigest = digester.rangeDigest(this, headerStart, headerEnd)
        seek(headerEnd)
        writeByteArray(headerDigest)
        writeInt32(KONA_ARCHIVE_VERSION)
        if (pos > Int.MAX_VALUE) error("too large archive. $pos")
        truncate()
    }
}

/**
 * レシーバの先頭にシークして KonaArchive のバイナリを出力して truncate,close する
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun KonaRandomAccess.encodeKonaArchive(
    root: KonaWriterDirectory,
    options: Lz4Options = Lz4Options(),
    previous: KonaArchive? = null,
) {
    try {
        WriteEnv(
            root = root,
            options = options,
            accessAndDigester = KonaArchive.AccessAndDigester(
                access = this,
                version = KONA_ARCHIVE_VERSION,
                // VERSION 2+ uses BLAKE-3-256
                digester = defaultKonaBlake3n256,
            ),
            previousDigests = buildMap {
                try {
                    previous?.takeIf {
                        it.accessAndDigester.version == KONA_ARCHIVE_VERSION
                    }?.contentMetas { put(it.metaKey(), it) }
                } catch (ex: Throwable) {
                    // 問題のあるアーカイブに遭遇してもクラッシュはしないが、そのデータは使わない
                    ex.printStackTrace()
                    clear()
                }
            },
        ).write()
    } finally {
        close()
    }
}
