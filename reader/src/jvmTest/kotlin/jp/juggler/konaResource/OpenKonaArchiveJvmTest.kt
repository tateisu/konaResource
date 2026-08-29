package jp.juggler.konaResource

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import jp.juggler.konaArchive.*
import jp.juggler.konaArchive.util.FileRandomAccess
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes

class OpenKonaArchiveJvmTest : FreeSpec({
    "decodes from file path and byte array" {
        val inputDirectory = createTempDirectory("kona-reader-input")
        val sourceFile = inputDirectory.resolve("hello.txt")
        sourceFile.writeBytes("hello".encodeToByteArray())
        val archivePath = createTempFile("kona-reader-archive", ".bin")
        try {
            val writerRoot = inputDirectory.toFile().toKonaWriterEntry() as KonaWriterDirectory
            FileRandomAccess(archivePath.toFile()).encodeKonaArchive(writerRoot)
            val archiveBytes = archivePath.toFile().readBytes()

            archivePath.toFile().openKonaArchive().use { archive ->
                (archive.root["hello.txt"] as? KonaArchiveFile)
                    ?.string() shouldBe "hello"
            }
            archiveBytes.openKonaArchive().use { archive ->
                (archive.root["hello.txt"] as? KonaArchiveFile)
                    ?.string() shouldBe "hello"
            }
            sourceFile.deleteIfExists()
        } finally {
            archivePath.deleteIfExists()
            inputDirectory.toFile().deleteRecursively()
        }
    }
})
