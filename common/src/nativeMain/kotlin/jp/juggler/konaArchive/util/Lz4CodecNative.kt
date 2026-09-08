package jp.juggler.konaArchive.util

import jp.juggler.konaResource.lz4.cinterop.LZ4F_compressBegin
import jp.juggler.konaResource.lz4.cinterop.LZ4F_compressBound
import jp.juggler.konaResource.lz4.cinterop.LZ4F_compressEnd
import jp.juggler.konaResource.lz4.cinterop.LZ4F_compressUpdate
import jp.juggler.konaResource.lz4.cinterop.LZ4F_compressionContext_tVar
import jp.juggler.konaResource.lz4.cinterop.LZ4F_createCompressionContext
import jp.juggler.konaResource.lz4.cinterop.LZ4F_createDecompressionContext
import jp.juggler.konaResource.lz4.cinterop.LZ4F_decompress
import jp.juggler.konaResource.lz4.cinterop.LZ4F_decompressionContext_tVar
import jp.juggler.konaResource.lz4.cinterop.LZ4F_freeCompressionContext
import jp.juggler.konaResource.lz4.cinterop.LZ4F_freeDecompressionContext
import jp.juggler.konaResource.lz4.cinterop.LZ4F_getErrorName
import jp.juggler.konaResource.lz4.cinterop.LZ4F_isError
import jp.juggler.konaResource.lz4.cinterop.LZ4F_preferences_t
import jp.juggler.konaResource.lz4.cinterop.kona_lz4_init_preferences
import jp.juggler.konaResource.system.cinterop.kona_memmove
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

@OptIn(ExperimentalForeignApi::class)
@Suppress("MagicNumber")
internal object Lz4CodecNative : Lz4Codec() {
    private const val LZ4F_VERSION = 100u

    private val poolCompLock = reentrantLock()
    private val poolCompSrc = ArrayList<ByteArray>()
    private val poolCompDst = ArrayList<ByteArray>()

    private val poolDecompLock = reentrantLock()
    private val poolDecompSrc = ArrayList<ByteArray>()
    private val poolDecompDst = ArrayList<ByteArray>()

    private class DecodeCallbackContext(
        val srcArray: ByteArray,
        val dstArray: ByteArray,
        val userInput: (ByteArray, offset: Int, maxLength: Int) -> Int,
        val userOutput: (ByteArray, offset: Int, length: Int) -> Unit,
    )
    private val decodeCallbackContextMap = mutableMapOf<Int,DecodeCallbackContext>()
    private val decodeCallbackContextIdSeed = atomic(0)



    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun compress(
        inputSize: Int,
        options: Lz4Options,
        input: (ByteArray, offset: Int, maxLength: Int) -> Int,
        output: (ByteArray, offset: Int, length: Int) -> Unit,
    ): Int = memScoped {
        var result: ULong
        val contextPtr: LZ4F_compressionContext_tVar = alloc<LZ4F_compressionContext_tVar>()
        result = LZ4F_createCompressionContext(
            contextPtr.ptr,
            LZ4F_VERSION,
        )
        require(LZ4F_isError(result) == 0u) {
            "LZ4F_createCompressionContext failed. ${LZ4F_getErrorName(result)?.toKString()}"
        }
        try {
            val preferencesPtr: CPointer<LZ4F_preferences_t> = alloc<LZ4F_preferences_t>().ptr
            kona_lz4_init_preferences(
                preferences = preferencesPtr,
                blockSizeID = options.blockSizeId,
                blockMode = if (options.blockLinked) 0 else 1,
                contentChecksumFlag = if (options.contentChecksumFlag) 1 else 0,
                contentSize = if (options.contentSizeFlag) inputSize.toULong() else 0uL,
                blockChecksumFlag = if (options.blockChecksumFlag) 1 else 0,
                compressionLevel = options.compressionLevel,
                autoFlush = if (options.autoFlush) 1u else 0u,
                favorDecSpeed = if (options.favorDecSpeed) 1u else 0u,
            )

            var (srcArray, dstArray) = poolCompLock.withLock {
                Pair(
                    poolCompSrc.removeLastOrNull() ?: ByteArray(MAX_CHUNK_SIZE),
                    poolCompDst.removeLastOrNull() ?: ByteArray(MAX_CHUNK_SIZE),
                )
            }
            try {
                var consumedInput = 0
                var outputCount = 0

                // LZ4F_compressBegin
                result = dstArray.usePinned { dstPinned ->
                    LZ4F_compressBegin(
                        cctx = contextPtr.value,
                        dst = dstPinned.addressOf(0),
                        dstCapacity = dstArray.size.toULong(),
                        prefsPtr = preferencesPtr,
                    )
                }
                require(LZ4F_isError(result) == 0u) {
                    "LZ4F_compressBegin failed. ${LZ4F_getErrorName(result)?.toKString()}"
                }
                output(dstArray, 0, result.toInt())
                outputCount += result.toInt()

                srcArray.usePinned { srcPinned ->
                    // loop of LZ4F_compressUpdate
                    while (true) {
                        // 入力をsrcArrayにコピー
                        val step = input(srcArray, 0, srcArray.size)
                        if (step <= 0) break
                        val bound = LZ4F_compressBound(step.toULong(), preferencesPtr)
                        require(LZ4F_isError(bound) == 0u) {
                            "LZ4F_compressBound failed. ${LZ4F_getErrorName(bound)?.toKString()}"
                        }
                        if (dstArray.size < bound.toInt()) dstArray = ByteArray(bound.toInt())
                        result = dstArray.usePinned { dstPinned ->
                            LZ4F_compressUpdate(
                                cctx = contextPtr.value,
                                dst = dstPinned.addressOf(0),
                                dstCapacity = dstArray.size.toULong(),
                                src = srcPinned.addressOf(0),
                                srcSize = step.toULong(),
                                optionsPtr = null,
                            )
                            // エラー時: LZ4F_isError(result) != 0 となる特殊な size_t 値
                            // - エラー後の compression context は再利用せず、解放・再作成が必要
                            // 成功時: dst に書いたバイト数。0は入力は内部バッファされて出力がまだない状態
                            // - 成功時は srcの[0..srcSize) が消費された
                        }
                        require(LZ4F_isError(result) == 0u) {
                            "LZ4F_compressUpdate failed. ${LZ4F_getErrorName(result)?.toKString()}"
                        }
                        if (result > 0UL) {
                            output(dstArray, 0, result.toInt())
                            outputCount += result.toInt()
                        }
                        consumedInput += step
                    }
                }
                require(!options.contentSizeFlag || consumedInput == inputSize) {
                    "LZ4 input size mismatch: expected $inputSize, got $consumedInput"
                }
                // LZ4F_compressEnd
                val bound = LZ4F_compressBound(0uL, preferencesPtr)
                require(LZ4F_isError(bound) == 0u) {
                    "LZ4F_compressBound failed. ${LZ4F_getErrorName(bound)?.toKString()}"
                }
                if (dstArray.size < bound.toInt()) dstArray = ByteArray(bound.toInt())
                result = dstArray.usePinned { dstPinned ->
                    LZ4F_compressEnd(
                        cctx = contextPtr.value,
                        dst = dstPinned.addressOf(0),
                        dstCapacity = dstArray.size.toULong(),
                        optionsPtr = null,
                    )
                    // result =4: end mark のみ
                    // result =8: end mark + content checksum
                    // result >8: 未出力の最終 block も flush された場合
                }
                require(LZ4F_isError(result) == 0u) {
                    "LZ4F_compressEnd failed. ${LZ4F_getErrorName(result)?.toKString()}"
                }
                output(dstArray, 0, result.toInt())
                outputCount += result.toInt()
                outputCount
            } finally {
                poolCompLock.withLock {
                    poolCompSrc.add(srcArray)
                    poolCompDst.add(dstArray)
                }
            }
        } finally {
            LZ4F_freeCompressionContext(contextPtr.value)
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    override fun decompress(
        expectedSize: Int,
        input: (ByteArray, offset: Int, maxLength: Int) -> Int,
        output: (ByteArray, offset: Int, length: Int) -> Unit,
    ): Int {
        require(expectedSize >= 0) { "expectedSize must not be negative" }
        val(callbackContext,callbackContextId)  = poolDecompLock.withLock {
            val context = DecodeCallbackContext(
                srcArray =  poolDecompSrc.removeLastOrNull() ?: ByteArray(MAX_CHUNK_SIZE + 1024),
                dstArray =  poolDecompDst.removeLastOrNull() ?: ByteArray(MAX_CHUNK_SIZE * 2),
                userInput = input,
                userOutput = output,
            )
            val id = decodeCallbackContextIdSeed.incrementAndGet()
            decodeCallbackContextMap[id]=context
            context to id
        }
        return try {
            callbackContext.dstArray.usePinned { dstPinned ->
                callbackContext.srcArray.usePinned { srcPinned ->
                    callbackContext.usePinned { callbackContextPinned ->
                        // TODO: この内部をまるごとCコードに変更する
                        // Cコードからのコールバックは callbackContextPinned または callbackContextId を含むようにする
                        memScoped {
                            val srcArray = callbackContext.srcArray
                            val dstArray = callbackContext.dstArray
                            var result: ULong
                            val context = alloc<LZ4F_decompressionContext_tVar>()
                            result = LZ4F_createDecompressionContext(
                                context.ptr,
                                LZ4F_VERSION,
                            )
                            require(LZ4F_isError(result) == 0u) {
                                "LZ4F_createDecompressionContext failed. ${LZ4F_getErrorName(result)?.toKString()}"
                            }
                            try {
                                val sourceSize = allocArray<ULongVar>(1)
                                val destinationSize = allocArray<ULongVar>(1)
                                // input() が <=0 を返したら変化する
                                var inputFinished = false
                                // srcArrayの使用中バイト数
                                var srcUsed = 0
                                // 無限ループ対策
                                var emptyCount = 0
                                // デコード済みバイト数
                                var outLength = 0
                                // memmove したバイト数の合計
                                var memMoveTotal = 0
                                loop@ while (true) {
                                    while (!inputFinished && srcUsed < srcArray.size) {
                                        // srcArray+srcUsedの位置に追加で読む
                                        val nRead = input(
                                            srcArray,
                                            srcUsed,
                                            srcArray.size - srcUsed,
                                        )
                                        if (nRead <= 0) {
                                            inputFinished = true
                                            break
                                        }
                                        srcUsed += nRead
                                    }
                                    // 入力が足りない
                                    if (srcUsed <= 0) {
                                        error("unexpected end: remaining=${expectedSize - outLength}")
                                    }
                                    var srcOffset = 0
                                    do {
                                        sourceSize.pointed.value = (srcUsed - srcOffset).toULong()
                                        destinationSize.pointed.value = dstArray.size.toULong()
                                        result = LZ4F_decompress(
                                            context.value,
                                            dstPinned.addressOf(0),
                                            destinationSize,
                                            srcPinned.addressOf(srcOffset),
                                            sourceSize,
                                            null,
                                        )
                                        require(LZ4F_isError(result) == 0u) {
                                            "LZ4F_decompress failed. ${LZ4F_getErrorName(result)?.toKString()}"
                                        }
                                        // 入力を消費したバイト数
                                        val srcConsumed = sourceSize.pointed.value.toInt()
                                        // デコードしたバイト数
                                        val decoded = destinationSize.pointed.value.toInt()
                                        // デコード分があれば outputBuffer に追記して outputラムダでユーザに通知
                                        if (decoded > 0) {
                                            require(outLength + decoded <= expectedSize) {
                                                "LZ4 output exceeds expected size"
                                            }
                                            output(dstArray, 0, decoded)
                                            outLength += decoded
                                        }
                                        srcOffset += srcConsumed
                                        when {
                                            // result == 0なら 現在の LZ4 frame の展開完了
                                            // 終端マーカーなど読み終わった
                                            result == 0uL -> {
                                                require(outLength == expectedSize) {
                                                    "LZ4 size mismatch"
                                                }
                                                require(srcOffset == srcUsed) {
                                                    "Trailing bytes after LZ4 frame"
                                                }
                                                break@loop
                                            }
                                            // 無限ループ対策
                                            srcConsumed > 0 || decoded > 0 -> emptyCount = 0
                                            srcConsumed == 0 && decoded == 0 && ++emptyCount >= 3 ->
                                                error("LZ4 decompressor made no progress")
                                        }
                                        // result>0は次回要求バイト数のヒント
                                        // srcArrayにあるデータの残りがそれ以上あるならバッファ管理なしでで再度decodeする
                                    } while ((srcUsed - srcOffset).toULong() >= result)
                                    // 次回入力のためにバッファを詰める
                                    if (srcOffset > 0) {
                                        val srcRemain = srcUsed - srcOffset
                                        srcUsed = when {
                                            srcRemain < 0 -> error("LZ4 decompressor consumed too much input")
                                            srcRemain == 0 -> 0
                                            else -> {
                                                memMoveTotal += srcRemain
                                                kona_memmove(
                                                    srcPinned.addressOf(0),
                                                    srcPinned.addressOf(srcOffset),
                                                    srcRemain.toULong(),
                                                )
                                                srcRemain
                                            }
                                        }
                                    }
                                }
                                // println("memMoveTotal=$memMoveTotal")
                                outLength
                            } finally {
                                LZ4F_freeDecompressionContext(context.value)
                            }
                        }
                    }
                }
            }
        } finally {
            poolDecompLock.withLock {
                poolDecompSrc.add(callbackContext.srcArray)
                poolDecompDst.add(callbackContext.dstArray)
                decodeCallbackContextMap.remove(callbackContextId)
            }
        }
    }
}
