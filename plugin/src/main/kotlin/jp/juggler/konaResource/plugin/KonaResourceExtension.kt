package jp.juggler.konaResource.plugin

import jp.juggler.konaArchive.util.Lz4Options

open class KonaResourceExtension {
    var lz4CompressionLevel: Int = 0
    var lz4BlockSizeID: Int = 4 * 1024 * 1024
    var lz4BlockMode: String = "LZ4F_blockLinked"
    var lz4ContentSizeFlag: Boolean = true
    var lz4ContentChecksumFlag: Boolean = true
    var lz4blockChecksumFlag: Boolean = true
    var lz4AutoFlush: Boolean = false
    var lz4FavorDecSpeed: Boolean = false
    val modules: MutableList<Pair<String, Any>> = ArrayList()

    internal fun options(): Lz4Options = Lz4Options(
        compressionLevel = lz4CompressionLevel,
        blockSize = lz4BlockSizeID,
        blockLinked = lz4BlockMode != "LZ4F_blockIndependent",
        contentSizeFlag = lz4ContentSizeFlag,
        contentChecksumFlag = lz4ContentChecksumFlag,
        blockChecksumFlag = lz4blockChecksumFlag,
        autoFlush = lz4AutoFlush,
        favorDecSpeed = lz4FavorDecSpeed,
    )
}
