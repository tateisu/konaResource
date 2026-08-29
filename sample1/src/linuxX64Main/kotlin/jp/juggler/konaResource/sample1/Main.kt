package jp.juggler.konaResource.sample1

import jp.juggler.konaArchive.KonaArchiveDir
import jp.juggler.konaArchive.KonaArchiveFile
import jp.juggler.konaResource.embedKonaArchive
import kotlinx.coroutines.runBlocking

fun readSample() {
    val root = embedKonaArchive("sample").root
    println("sample root.size=${root.size}")
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
    // directory access by segmented path
    val path = "dir1/dir1a/foo.txt"
    val entry = root.getPath(path)
    println("getPath($path).name=${entry?.name}")
    when (entry) {
        null -> println("getPath($path) is null.")
        is KonaArchiveFile -> println("getPath($path) contents=${entry.string()}")
        is KonaArchiveDir -> println("getPath($path) is directory. size=${entry.size}")
    }
}

fun readSampleB() {
    val root = embedKonaArchive("sampleB").root
    println("sampleB root.size=${root.size}")
    println("sampleB bar.txt ${(root["bar.txt"] as? KonaArchiveFile)?.string()}")
}

fun main() {
    runBlocking {
        readSample()
        readSampleB()
    }
}
