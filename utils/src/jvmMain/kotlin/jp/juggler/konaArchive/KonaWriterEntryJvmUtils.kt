package jp.juggler.konaArchive

import java.io.File

actual fun String.toKonaWriterEntry(): KonaWriterEntry =
    File(this).toKonaWriterEntry()
        ?: error("Unable to find path: $this")
