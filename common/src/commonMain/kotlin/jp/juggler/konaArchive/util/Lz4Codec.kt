package jp.juggler.konaArchive.util

import okio.Buffer

internal expect val defaultLz4Codec: Lz4Codec

@Suppress("MagicNumber")
data class Lz4Options(
    val compressionLevel: Int = 0,
    val blockSize: Int = 4 * SIZE_MIB,
    val blockLinked: Boolean = true,
    val contentSizeFlag: Boolean = true,
    val contentChecksumFlag: Boolean = true,
    val blockChecksumFlag: Boolean = true,
    val autoFlush: Boolean = false,
    val favorDecSpeed: Boolean = false,
) {
    val blockSizeId: Int
        get() = when {
            blockSize >= 4 * SIZE_MIB -> 7
            blockSize >= SIZE_MIB -> 6
            blockSize >= 256 * SIZE_KIB -> 5
            else -> 4
        }
}

interface Lz4Codec {
    /**
     * LZ4圧縮する。
     * @param inputSize 入力データの長さ(ヒント)
     * @param options LZ4圧縮オプション
     * @param input codecが入力データを要求したら呼ばれる。戻り値は追加したバイト数。-1 は入力の終端を表す
     * @param output codecが出力フレームを用意したら呼ばれる。Bufferから読んでもよい。読まないとBufferがどんどん長くなる
     * @return outputに渡されるのと同じBuffer。outputで適切に読み出していれば中身はカラになるだろう
     */
    fun compress(
        inputSize: Int,
        options: Lz4Options = Lz4Options(),
        input: (Buffer) -> Int,
        output: (Buffer) -> Unit = {},
    ): Buffer

    /**
     * LZ4展開する。
     * @param expectedSize 入力データの長さ
     * @param input codecが入力データを要求したら呼ばれる。戻り値は追加したバイト数。-1 は入力の終端を表す
     * @param output codecが出力フレームを用意したら呼ばれる。Bufferから読んでもよい。読まないとBufferがどんどん長くなる
     * @return outputに渡されるのと同じBuffer。outputで適切に読み出していれば中身はカラになるだろう
     */
    fun decompress(
        expectedSize: Int,
        // codecが入力バイト列を要求したら呼ばれる
        // 戻り値は追加したバイト数。-1 は入力の終端を表す
        input: (Buffer) -> Int,
        // codecが出力フレームを用意したら呼ばれる
        output: (Buffer) -> Unit = {},
    ): Buffer
}
