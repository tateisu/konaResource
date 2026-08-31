package jp.juggler.konaArchive

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import jp.juggler.konaArchive.util.hex

class KonaArchiveTest : FreeSpec() {
    private fun archivePaths(archive: KonaArchive) = buildSet {
        fun visit(directory: KonaArchiveDir, path: String) {
            for (entry in directory) {
                val childPath = when {
                    path.isEmpty() -> entry.name
                    else -> "$path/${entry.name}"
                }
                when (entry) {
                    is KonaArchiveFile -> add(childPath)
                    // フォルダ自体は列挙しないが子要素は探索する
                    is KonaArchiveDir -> visit(entry, childPath)
                }
            }
        }
        visit(archive.root, "")
    }

    init {
        val utils = konaArchiveTestUtils

        "roundTripAndSortedTraversal" {
            val root = utils.tempDirectory("input")
            val archivePath = utils.tempArchive("archive")
            try {
                utils.writeFile(utils.resolve(root, "html/z.html"), "z".encodeToByteArray())
                utils.writeFile(utils.resolve(root, "html/index.html"), "hello hello hello".encodeToByteArray())
                utils.writeFile(utils.resolve(root, "same/a.bin"), byteArrayOf(1, 2, 3))
                utils.writeFile(utils.resolve(root, "same/b.bin"), byteArrayOf(1, 2, 3))
                utils.writeFile(utils.resolve(root, "empty"), ByteArray(0))

                utils.encodeDirectory(root, archivePath)
                utils.decodeArchive(archivePath).use { archive ->
                    archivePaths(archive) shouldBe setOf(
                        "empty",
                        "html/index.html",
                        "html/z.html",
                        "same/a.bin",
                        "same/b.bin",
                    )
                    // path segment access
                    (archive.root[listOf("html", "index.html")] as? KonaArchiveFile)?.name shouldBe "index.html"
                    (archive.root[listOf("", "same", "a.bin")] as? KonaArchiveFile)?.name shouldBe "a.bin"
                    archive.root[listOf("missing")] shouldBe null
                    // pathTo** API
                    archive.pathToFile("/html/index.html")?.name shouldBe "index.html"
                    archive.pathToFile("///same/a.bin")?.name shouldBe "a.bin"
                    archive.pathToFile("html//index.html") shouldBe null
                    archive.pathToFile("html/index.html")?.bytes()?.hex() shouldBe
                        "hello hello hello".encodeToByteArray().hex()
                    archive.pathToFile("same/b.bin")?.bytes()?.hex() shouldBe
                        byteArrayOf(1, 2, 3).hex()
                    archive.pathToFile("empty")?.bytes()?.hex() shouldBe ""
                    archive.pathToFile("html/index.html")?.verifyDigest()
                    // implements List<*>
                    val first = archive.root[0]
                    archive.root.contains(first) shouldBe true
                    archive.root.containsAll(listOf(first, archive.root[1])) shouldBe true
                    archive.root.indexOf(first) shouldBe 0
                    archive.root.lastIndexOf(first) shouldBe 0
                    archive.root.iterator().next().name shouldBe "empty"
                    archive.root.listIterator(1).previous().name shouldBe "empty"
                    archive.root.subList(0, 1).single().name shouldBe "empty"
                }
            } finally {
                utils.deleteTree(root)
                utils.deleteFile(archivePath)
            }
        }

        "incrementalArchiveKeepsLogicalContent" {
            val firstRoot = utils.tempDirectory("first")
            val secondRoot = utils.tempDirectory("second")
            val firstArchivePath = utils.tempArchive("first")
            val secondArchivePath = utils.tempArchive("second")
            try {
                val content = ByteArray(1000) { 42 }
                utils.writeFile(utils.resolve(firstRoot, "a"), content)
                utils.writeFile(utils.resolve(secondRoot, "b"), content)

                utils.encodeDirectory(firstRoot, firstArchivePath)
                val first = utils.decodeArchive(firstArchivePath)
                try {
                    utils.encodeDirectory(secondRoot, secondArchivePath, first)
                } finally {
                    first.close()
                }

                val firstContentArchive = utils.decodeArchive(firstArchivePath)
                val second = utils.decodeArchive(secondArchivePath)
                try {
                    second.pathToFile("b")?.bytes()?.hex() shouldBe firstContentArchive.pathToFile("a")?.bytes()?.hex()
                    second.pathToFile("b")?.uncompressedDigest?.hex() shouldBe
                        second.accessAndDigester.digester.digest(
                            second.pathToFile("b")!!.bytes(),
                        ).hex()
                } finally {
                    firstContentArchive.close()
                    second.close()
                }
            } finally {
                utils.deleteTree(firstRoot)
                utils.deleteTree(secondRoot)
                utils.deleteFile(firstArchivePath)
                utils.deleteFile(secondArchivePath)
            }
        }

        "emptyDirectoriesRoundTrip" {
            val root = utils.tempDirectory("empty-directory")
            val archivePath = utils.tempArchive("empty-directory")
            try {
                utils.encodeDirectory(root, archivePath)
                utils.decodeArchive(archivePath).use { archive ->
                    archive.root.size shouldBe 0
                }

                val emptyDirectory = utils.resolve(root, "empty")
                utils.writeFile(utils.resolve(emptyDirectory, ".keep"), ByteArray(0))
                utils.deleteFile(utils.resolve(emptyDirectory, ".keep"))

                utils.encodeDirectory(root, archivePath)
                utils.decodeArchive(archivePath).use { archive ->
                    (archive.root["empty"] as KonaArchiveDir).size shouldBe 0
                }
            } finally {
                utils.deleteTree(root)
                utils.deleteFile(archivePath)
            }
        }
    }
}
