package jp.juggler.konaArchive

import jp.juggler.konaArchive.util.FileRandomAccess
import java.io.File

fun File.toKonaWriterEntry(): KonaWriterEntry? {
    require(exists()) { "File does not exist: $this" }
    val source = this
    return when {
        isDirectory -> KonaWriterDirectory(name) {
            listFiles()?.mapNotNull { it.toKonaWriterEntry() }
                ?: error("Unable to list directory: $source")
        }

        isFile -> KonaWriterFile(name) {
            FileRandomAccess(source, isReadOnly = true)
        }

        else -> null
    }
}
