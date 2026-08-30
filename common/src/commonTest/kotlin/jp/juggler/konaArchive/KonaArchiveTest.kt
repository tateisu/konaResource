package jp.juggler.konaArchive

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import jp.juggler.konaArchive.util.sha256

class KonaArchiveTest : FreeSpec() {
    private fun archivePaths(archive: KonaArchive): List<String> {
        val paths = mutableListOf<String>()
        fun visit(directory: KonaArchiveDir, prefix: String) {
            for (entry in directory) {
                val path = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
                when (entry) {
                    is KonaArchiveDir -> visit(entry, path)
                    is KonaArchiveFile -> paths += path
                }
            }
        }
        visit(archive.root, "")
        return paths
    }

    private fun archiveContents(archive: KonaArchive): Map<String, ByteArray> {
        val contents = mutableMapOf<String, ByteArray>()
        fun visit(directory: KonaArchiveDir, prefix: String) {
            for (entry in directory) {
                val path = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
                when (entry) {
                    is KonaArchiveDir -> visit(entry, path)
                    is KonaArchiveFile -> {
                        contents[path] = entry.content().readByteArray()
                    }
                }
            }
        }
        visit(archive.root, "")
        return contents
    }

    private fun archiveSha256(
        archive: KonaArchive,
        @Suppress("SameParameterValue")
        expectedPath: String,
    ): ByteArray {
        var result: ByteArray? = null
        fun visit(directory: KonaArchiveDir, prefix: String) {
            for (entry in directory) {
                val path = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
                when (entry) {
                    is KonaArchiveDir -> visit(entry, path)
                    is KonaArchiveFile -> if (path == expectedPath) result = entry.uncompressedSha256
                }
            }
        }
        visit(archive.root, "")
        return result ?: error("Missing archive entry: $expectedPath")
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
                    archivePaths(archive) shouldBe
                        listOf("empty", "html/index.html", "html/z.html", "same/a.bin", "same/b.bin")
                    (archive.root[listOf("html", "index.html")] as KonaArchiveFile).name shouldBe "index.html"
                    (archive.root[listOf("", "same", "a.bin")] as KonaArchiveFile).name shouldBe "a.bin"
                    (archive.root.getPath("/html/index.html") as KonaArchiveFile).name shouldBe "index.html"
                    (archive.root.getPath("///same/a.bin") as KonaArchiveFile).name shouldBe "a.bin"
                    archive.root.getPath("html//index.html") shouldBe null
                    archive.root[listOf("missing")] shouldBe null
                    val first = archive.root[0]
                    archive.root.contains(first) shouldBe true
                    archive.root.containsAll(listOf(first, archive.root[1])) shouldBe true
                    archive.root.indexOf(first) shouldBe 0
                    archive.root.lastIndexOf(first) shouldBe 0
                    archive.root.iterator().next().name shouldBe "empty"
                    archive.root.listIterator(1).previous().name shouldBe "empty"
                    archive.root.subList(0, 1).single().name shouldBe "empty"
                    val contents = archiveContents(archive)
                    contents["html/index.html"]!!.toList() shouldBe "hello hello hello".encodeToByteArray().toList()
                    contents["same/b.bin"]!!.toList() shouldBe byteArrayOf(1, 2, 3).toList()
                    contents["empty"]!!.toList() shouldBe emptyList()
                    (archive.root.getPath("html/index.html") as KonaArchiveFile).verifySha256()
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
                    val firstContent = archiveContents(firstContentArchive)["a"]
                    val secondContent = archiveContents(second)["b"]
                    secondContent!!.toList() shouldBe firstContent!!.toList()
                    secondContent.sha256().toList() shouldBe archiveSha256(second, "b").toList()
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
