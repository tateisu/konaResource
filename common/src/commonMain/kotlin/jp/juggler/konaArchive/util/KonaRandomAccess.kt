package jp.juggler.konaArchive.util

import okio.Buffer
import kotlin.math.min

/**
 * ランダムアクセスファイルの抽象化
 * - 使い終わったらcloseすること
 * - 開いた直後のpositionやsizeは全く保証されない。利用側が適切にseek,truncateすること
 */
abstract class KonaRandomAccess : AutoCloseable {
    /**
     * リードオンリーなら真
     */
    abstract val isReadOnly: Boolean

    /**
     * このストリームの長さ
     */
    abstract val size: Long

    /**
     * 現在注目中の位置
     * 有効な値域は [0..size)
     */
    open var pos: Long = 0L
        protected set

    // set current read/write position
    abstract fun seek(offset: Long)

    // delete range [tell..length]
    abstract fun truncate()

    // write to current position
    abstract fun writeByteArray(b: ByteArray, start: Int = 0, end: Int = b.size)

    /**
     * pos位置から最大(end-start)バイトを読み、ByteArrayの指定範囲にセットする
     * @return 読めたバイト数。0かもしれないが、-1ではない
     */
    abstract fun readByteArray(b: ByteArray, start: Int = 0, end: Int = b.size): Int

    /**
     * このストリームの部分範囲を示すストリームを返す
     * - 親と子はposを共有しない
     * - 子は常にリードオンリー
     */
    abstract fun subRange(start: Long = 0, end: Long = size): KonaRandomAccess

    // -----------------------------------------------
    // 以下はデフォルト実装あり

    // このクラスはマルチスレッド動作を考慮してない
    // 考慮するならmutexが必要
    val tmpArray = ByteArray(4096)

    fun readToTmpArray(length: Int? = null) :Int = readByteArray(
        tmpArray,
        0,
        when{
            length==null -> tmpArray.size
            else -> min(length,tmpArray.size)
        }
    )

    /**
     * pos位置に1バイト書き込む
     */
    open fun writeByte(b: Int) {
        tmpArray[0] = b.toByte()
        writeByteArray(tmpArray, 0, 1)
    }

    /**
     * pos位置から1バイト読む。posが範囲外ならnull
     */
    open fun readByte(): Int? {
        val nRead = readByteArray(tmpArray, 0, 1)
        return if (nRead <= 0) null else tmpArray[0].toInt().and(0xff)
    }

    /**
     * pos位置にInt32を書き込む
     * little endian
     */
    open fun writeInt32(i: Int) {
        tmpArray[0] = i.toByte()
        tmpArray[1] = (i ushr 8).toByte()
        tmpArray[2] = (i ushr 16).toByte()
        tmpArray[3] = (i ushr 24).toByte()
        writeByteArray(tmpArray, 0, 4)
    }

    /**
     * pos位置からInt32を読む
     * 範囲内ではないなら missing name 例外を投げる
     * little endian
     */
    open fun readInt32(name: String): Int {
        val nRead = readByteArray(tmpArray, 0, 4)
        if (nRead < 4L) error("missing (i32)$name")
        return tmpArray[0].toInt().and(0xff)
            .or(tmpArray[1].toInt().and(0xff).shl(8))
            .or(tmpArray[2].toInt().and(0xff).shl(16))
            .or(tmpArray[3].toInt().and(0xff).shl(24))
    }

    /**
     * pos位置に Buffer の先頭lengthバイトを書き込む
     */
    open fun writeBuffer(b: Buffer, length: Long = b.size) {
        if (length <= 0) return
        var nWrite = 0
        while (true) {
            val remaining = length - nWrite
            if (remaining <= 0L) break
            val step = min(remaining, tmpArray.size.toLong()).toInt()
            val result = b.read(tmpArray, 0, step)
            if (result <= 0) error("read buffer empty. remain=$remaining")
            writeByteArray(tmpArray, 0, step)
            nWrite += result
        }
    }

    /**
     * pos位置から[0..maxLength]バイトを読み込み、Bufferの末尾に追加する
     * @return 読めたバイト数。常に0以上
     */
    open fun readBuffer(b: Buffer, maxLength: Long): Long {
        if (maxLength <= 0L) return 0
        var nRead = 0L
        while (true) {
            val remaining = maxLength - nRead
            if (remaining <= 0L) break
            val step = min(remaining, tmpArray.size.toLong()).toInt()
            val result = readByteArray(tmpArray, 0, step)
            if (result <= 0) break
            b.write(tmpArray, 0, result)
            nRead += result
        }
        return nRead
    }

    /**
     * pos位置からバイト列を読み、結果をByteArrayとして返す
     */
    fun readBytes(length: Int, name: String): ByteArray {
        val ba = ByteArray(length)
        var nRead = 0
        while (nRead < length) {
            val i = readByteArray(ba, nRead, length)
            if (i <= 0) error("missing (bytes[$length])$name")
            nRead += i
        }
        return ba
    }

    /**
     * 指定範囲を適当な粒度で読み、blockを繰り返し呼ぶ
     * - blockの引数に渡されるByteArrayは毎回同じなので、
     *   呼ばれた側で過去の内容を読みたいなら copyOfRange すること
     */
    fun readRange(
        start: Long,
        end: Long,
        block: (ByteArray, Int) -> Unit
    ) {
        seek(start)
        val length = end - start
        var nRead = 0L
        while (true) {
            val remaining = length - nRead
            if (remaining <= 0L) break
            val step = min(remaining, tmpArray.size.toLong()).toInt()
            val result = readByteArray(tmpArray, 0, step)
            if (result <= 0) break
            nRead += result
            block(tmpArray, result)
        }
    }
}
