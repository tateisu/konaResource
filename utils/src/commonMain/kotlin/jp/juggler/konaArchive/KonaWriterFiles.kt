package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.KonaRandomAccess

/** Creates a writer tree from a platform-native filesystem path. */
expect fun String.toKonaWriterEntry(): KonaWriterEntry

/** Reads all regular files below [root] in stable path order. */
fun readKonaFiles(root: String): List<ByteArray> {
    val result = mutableListOf<Pair<String, ByteArray>>()

    fun visit(path: String, entry: KonaWriterEntry) {
        when (entry) {
            is KonaWriterDirectory -> entry.list().forEach { child ->
                visit(if (path.isEmpty()) child.name else "$path/${child.name}", child)
            }

            is KonaWriterFile -> {
                val access: KonaRandomAccess = entry.open()
                try {
                    require(access.size <= Int.MAX_VALUE) { "File is too large: $path" }
                    result += path to access.readBytes(access.size.toInt(), path)
                } finally {
                    access.close()
                }
            }
        }
    }

    visit("", root.toKonaWriterEntry())
    return result.sortedBy { it.first }.map { it.second }
}
