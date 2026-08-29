package jp.juggler.konaResource

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import jp.juggler.konaArchive.KonaArchiveFile
import jp.juggler.konaArchive.KonaWriterDirectory
import jp.juggler.konaArchive.encodeKonaArchive
import jp.juggler.konaArchive.toKonaWriterEntry
import jp.juggler.konaArchive.util.FileRandomAccess
import platform.posix.getpid
import platform.posix.mkdir
import platform.posix.rmdir
import platform.posix.unlink

class OpenKonaArchiveLinuxTest : FreeSpec({
    "opens an archive from a file path" {
        val root = "/tmp/kona-reader-linux-${getpid()}"
        val inputDirectory = "$root/input"
        val inputFile = "$inputDirectory/hello.txt"
        val archivePath = "$root/archive.bin"
        mkdir(root, 448U)
        mkdir(inputDirectory, 448U)
        try {
            val source = FileRandomAccess(inputFile, isReadOnly = false)
            try {
                source.writeByteArray("hello".encodeToByteArray())
                source.truncate()
            } finally {
                source.close()
            }

            val writerRoot = inputDirectory.toKonaWriterEntry() as KonaWriterDirectory
            FileRandomAccess(archivePath, isReadOnly = false)
                .encodeKonaArchive(writerRoot)

            openKonaArchive(archivePath).use { archive ->
                (archive.root["hello.txt"] as? KonaArchiveFile)
                    ?.string() shouldBe "hello"
            }
        } finally {
            unlink(inputFile)
            rmdir(inputDirectory)
            unlink(archivePath)
            rmdir(root)
        }
    }
})
