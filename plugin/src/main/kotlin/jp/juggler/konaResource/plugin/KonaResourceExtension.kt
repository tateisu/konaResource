package jp.juggler.konaResource.plugin

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
}
