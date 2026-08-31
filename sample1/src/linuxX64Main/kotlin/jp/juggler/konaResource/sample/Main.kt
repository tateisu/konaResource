package jp.juggler.konaResource.sample

import jp.juggler.konaArchive.KonaArchiveDir
import jp.juggler.konaArchive.KonaArchiveFile
import jp.juggler.konaResource.embedKonaArchive

@Suppress("LongMethod")
fun readSample() {
    val symbolName = "res"
    val root = embedKonaArchive(symbolName).root
    println("[$symbolName] root.size=${root.size}")
    // directory access by index
    for (i in root.indices) {
        val entry = root[i]

        println("entry[$i].name=${entry.name}")
        when (entry) {
            is KonaArchiveFile -> {
                println("entry[$i] is file. size=${entry.uncompressedSize}")
                var nRead = 0L
                entry.content {
                    nRead += it.size
                    it.clear()
                }
                println("read $nRead bytes.")
                // or just use shorthands: entry.bytes(), string(), buffer()
            }

            is KonaArchiveDir -> {
                println("entry[$i] is directory. size=${entry.size}")
            }
        }
    }
    // directory access by name
    val names = root.names().toList()
    for (i in names) {
        val entry = root[i]

        println("entry[$i].name=${entry?.name}")
        when (entry) {
            null -> {
                println("entry[$i] is null.")
            }

            is KonaArchiveFile -> {
                println("entry[$i] is file. size=${entry.uncompressedSize}")
                var nRead = 0L
                entry.content {
                    nRead += it.size
                    it.clear()
                }
                println("read $nRead bytes.")
            }

            is KonaArchiveDir -> {
                println("entry[$i] is directory. size=${entry.size}")
            }
        }
    }
    // directory access by path segments
    run {
        val path = "dir1/dir1a/foo.txt"
        val entry = root.pathToFile(path)
        println("pathToFile($path)?.name=${entry?.name}")
        when (entry) {
            null -> println("pathToFile($path) is null.")
            else -> println("pathToFile($path) contents=${entry.string()}")
        }
    }
    run {
        val path = "dir1/dir1a"
        val entry = root.pathToDir(path)
        println("pathToDir($path)?.name=${entry?.name}")
        when (entry) {
            null -> println("pathToDir($path) is null.")
            else -> println("pathToDir($path) size=${entry.size}")
        }
    }
}

fun readSampleB() {
    val symbolName = "res2"
    val root = embedKonaArchive(symbolName).root
    println("[$symbolName] root.size=${root.size}")
    println("[$symbolName] bar.txt ${(root["bar.txt"] as? KonaArchiveFile)?.string()}")
}

fun main() {
    readSample()
    readSampleB()
}
