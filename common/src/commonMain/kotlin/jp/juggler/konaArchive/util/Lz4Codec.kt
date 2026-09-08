package jp.juggler.konaArchive.util

import okio.Buffer
import kotlin.math.min

expect val defaultLz4Codec: Lz4Codec

@Suppress("MagicNumber")
data class Lz4Options(
    val compressionLevel: Int = 0,
    val blockSize: Int = 4 * SIZE_MIB,
    val blockLinked: Boolean = false,
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

abstract class Lz4Codec {
    companion object {
        const val MAX_CHUNK_SIZE = 4 * 1024 * 1024
    }

    /**
     * LZ4圧縮する。
     * @param inputSize 入力データの長さ(ヒント)
     * @param options LZ4圧縮オプション
     * @param input codecが入力データを要求したら呼ばれる。戻り値は追加したバイト数。<=0 は入力の終端を表す
     * @param output codecが出力フレームを用意したら呼ばれる。ByteArrayの指定範囲を必ず読み出す義務がある
     * @return outputに渡されたバイト数の合計
     */
    abstract fun compress(
        inputSize: Int,
        options: Lz4Options = Lz4Options(),
        input: (ByteArray, offset: Int, maxLength: Int) -> Int,
        output: (ByteArray, offset: Int, length: Int) -> Unit,
    ): Int

    /**
     * LZ4展開する。
     * @param expectedSize 入力データの長さ
     * @param input codecが入力データを要求したら呼ばれる。戻り値は追加したバイト数。<=0 は入力の終端を表す
     * @param output codecが出力フレームを用意したら呼ばれる。ByteArrayの指定範囲を必ず読み出す義務がある
     * @return outputに渡されたバイト数の合計
     */
    abstract fun decompress(
        expectedSize: Int,
        input: (ByteArray, offset: Int, maxLength: Int) -> Int,
        output: (ByteArray, offset: Int, length: Int) -> Unit,
    ): Int

    /**
     * LZ4圧縮する。
     * @param inputSize 入力データの長さ(ヒント)
     * @param options LZ4圧縮オプション
     * @param input codecが入力データを要求したら呼ばれる。戻り値は追加したバイト数。<=0 は入力の終端を表す
     * @param output codecが出力フレームを用意したら呼ばれる。Bufferから読んでもよい。読まないとBufferがどんどん長くなる
     * @return outputに渡されるのと同じBuffer。outputで適切に読み出していれば中身はカラになるだろう
     */
    @Suppress("LoopWithTooManyJumpStatements")
    fun compressBuffer(
        inputSize: Int,
        options: Lz4Options = Lz4Options(),
        input: (Buffer) -> Int,
        output: (Buffer) -> Unit = {},
    ): Buffer {
        val inBuffer = Buffer()
        val outBuffer = Buffer()
        var inputFinished = false
        compress(
            inputSize = inputSize,
            options = options,
            input = { buf, offset, maxLength ->
                while (!inputFinished && inBuffer.size < maxLength.toLong()) {
                    val r = input(inBuffer)
                    if (r <= 0) {
                        inputFinished = true
                        break
                    }
                }
                var nRead = 0
                while (!inBuffer.exhausted()) {
                    val step = min(inBuffer.size.toInt(), maxLength - nRead)
                    if (step <= 0) break
                    val delta = inBuffer.read(buf, offset + nRead, step)
                    if (delta <= 0) break
                    nRead += delta
                }
                nRead
            },
            output = { buf, offset, length ->
                outBuffer.write(buf, offset, length)
                output(outBuffer)
            },
        )
        return outBuffer
    }

    /**
     * LZ4展開する。
     * @param expectedSize 入力データの長さ
     * @param input codecが入力データを要求したら呼ばれる。戻り値は追加したバイト数。<=0 は入力の終端を表す
     * @param output codecが出力フレームを用意したら呼ばれる。Bufferから読んでもよい。読まないとBufferがどんどん長くなる
     * @return outputに渡されるのと同じBuffer。outputで適切に読み出していれば中身はカラになるだろう
     */
    fun decompressBuffer(
        expectedSize: Int,
        // codecが入力バイト列を要求したら呼ばれる
        input: (Buffer) -> Int,
        output: (Buffer) -> Unit = {},
    ): Buffer {
        val inBuffer = Buffer()
        val outBuffer = Buffer()
        var inputFinished = false
        decompress(
            expectedSize = expectedSize,
            input = { buf, offset, maxLength ->
                while (!inputFinished && inBuffer.size < maxLength.toLong()) {
                    val r = input(inBuffer)
                    if (r <= 0) {
                        inputFinished = true
                        break
                    }
                }
                var nRead = 0
                while (!inBuffer.exhausted()) {
                    val step = min(inBuffer.size.toInt(), maxLength - nRead)
                    if (step <= 0) break
                    val delta = inBuffer.read(buf, offset + nRead, step)
                    if (delta <= 0) break
                    nRead += delta
                }
                nRead
            },
            output = { buf, offset, length ->
                outBuffer.write(buf, offset, length)
                output(outBuffer)
            },
        )
        return outBuffer
    }

    fun compressByteArray(
        options: Lz4Options = Lz4Options(),
        src: ByteArray,
        start: Int = 0,
        end: Int = src.size,
    ): Buffer {
        val length = end - start
        var nRead = 0
        return compressBuffer(
            inputSize = length,
            options = options,
            input = {
                val step = (length - nRead).coerceAtMost(MAX_CHUNK_SIZE)
                when {
                    step <= 0 -> -1
                    else -> {
                        it.write(src, start + nRead, step)
                        nRead += step
                        step
                    }
                }
            },
        )
    }
}
