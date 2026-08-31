package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.util.KonaDigest
import jp.juggler.konaArchive.util.KonaSha256Jvm
import java.io.File

internal actual fun defaultSha256(): KonaDigest = KonaSha256Jvm()

internal actual fun benchmarkSourceFiles(): List<ByteArray> =
    sourceRoot().walkTopDown()
        .filter { it.isFile }
        .map { it.readBytes() }
        .toList()

private fun sourceRoot(): File =
    listOf(File("common/src"), File("../common/src"))
        .firstOrNull { it.isDirectory }
        ?: error("Unable to find common/src")
