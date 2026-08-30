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
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.Pinned
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
import okio.Buffer

private const val LZ4F_VERSION = 100u
private const val CHUNK_SIZE = 64 * 1024

internal actual val defaultLz4Codec: Lz4Codec = Lz4CodecLinux

@OptIn(ExperimentalForeignApi::class)
@Suppress("MagicNumber")
private object Lz4CodecLinux : Lz4Codec {
    private class CompressEnv(
        val contextPtr: LZ4F_compressionContext_tVar,
        val preferencesPtr: CPointer<LZ4F_preferences_t>,
    ) {
        val outputBuffer = Buffer()
        val inputBuffer = Buffer()
        var dstArray = ByteArray(4096)
        fun <T> ensureDstArray(size: Int, block: (Pinned<ByteArray>, ULong) -> T): T {
            if (dstArray.size < size) dstArray = ByteArray(size)
            return dstArray.usePinned { block(it, dstArray.size.toULong()) }
        }

        fun compressBegin() {
            val context = requireNotNull(contextPtr.value)
            val written = ensureDstArray(64) { array, arraySize ->
                LZ4F_compressBegin(
                    cctx = context,
                    dst = array.addressOf(0),
                    dstCapacity = arraySize,
                    prefsPtr = preferencesPtr,
                )
            }
            checkLz4(written, "write frame header")
            outputBuffer.write(dstArray, 0, written.toInt())
        }

        /**
         * @return consumed input bytes
         */
        fun compressUpdate(): Int {
            val chunkSize = minOf(inputBuffer.size, CHUNK_SIZE.toLong()).toInt()
            val bound = LZ4F_compressBound(chunkSize.toULong(), preferencesPtr)
            checkLz4(bound, "calculate compressed size")
            val context = requireNotNull(contextPtr.value)
            val written = ensureDstArray(bound.toInt()) { dstArray, dstArraySize ->
                inputBuffer.readByteArray(chunkSize.toLong()).usePinned { inputPinned ->
                    LZ4F_compressUpdate(
                        cctx = context,
                        dst = dstArray.addressOf(0),
                        dstCapacity = dstArraySize,
                        src = inputPinned.addressOf(0),
                        srcSize = chunkSize.toULong(),
                        optionsPtr = null,
                    )
                }
            }
            checkLz4(written, "compress input")
            outputBuffer.write(dstArray, 0, written.toInt())
            return chunkSize
        }

        fun compressEnd() {
            val bound = LZ4F_compressBound(0uL, preferencesPtr)
            checkLz4(bound, "calculate final compressed size")
            val context = requireNotNull(contextPtr.value)

            val written = ensureDstArray(bound.toInt()) { dstArray, dstArraySize ->
                LZ4F_compressEnd(
                    cctx = context,
                    dst = dstArray.addressOf(0),
                    dstCapacity = dstArraySize,
                    optionsPtr = null,
                )
            }
            checkLz4(written, "finish frame")
            outputBuffer.write(dstArray, 0, written.toInt())
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun checkLz4(code: ULong, operation: String) {
        require(LZ4F_isError(code) == 0u) { "LZ4 failed to $operation: ${LZ4F_getErrorName(code)?.toKString()}" }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun compress(
        inputSize: Int,
        options: Lz4Options,
        input: (Buffer) -> Int,
        output: (Buffer) -> Unit,
    ): Buffer = memScoped {
        with(
            CompressEnv(
                contextPtr = alloc<LZ4F_compressionContext_tVar>(),
                preferencesPtr = alloc<LZ4F_preferences_t>().ptr,
            ),
        ) {
            try {
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
                checkLz4(
                    LZ4F_createCompressionContext(
                        contextPtr.ptr,
                        LZ4F_VERSION,
                    ),
                    "create compression context",
                )
                compressBegin()
                var consumedInput = 0
                var inputFinished = false
                while (!inputFinished && consumedInput < inputSize) {
                    val before = inputBuffer.size
                    val read = input(inputBuffer)
                    val added = (inputBuffer.size - before).toInt()
                    when {
                        read == -1 -> {
                            require(added == 0) { "LZ4 input callback returned data after EOF" }
                            inputFinished = true
                        }

                        read == 0 -> {
                            require(added == 0) { "LZ4 input callback size mismatch" }
                            inputFinished = true
                        }

                        else -> {
                            require(read == added) { "LZ4 input callback size mismatch" }
                            require(!inputBuffer.exhausted()) {
                                "LZ4 input callback returned no data before the expected input size was consumed"
                            }
                        }
                    }
                    while (!inputBuffer.exhausted()) {
                        consumedInput += compressUpdate()
                        require(consumedInput <= inputSize) { "LZ4 input callback returned too much data" }
                        if (outputBuffer.size >= 4096L) {
                            output(outputBuffer)
                        }
                    }
                }
                require(!options.contentSizeFlag || consumedInput == inputSize) {
                    "LZ4 input size mismatch: expected $inputSize, got $consumedInput"
                }
                compressEnd()
                if (!outputBuffer.exhausted()) {
                    output(outputBuffer)
                }
                outputBuffer
            } finally {
                LZ4F_freeCompressionContext(contextPtr.value)
            }
        }
    }

    @Suppress("LongMethod")
    override fun decompress(
        expectedSize: Int,
        input: (Buffer) -> Int,
        output: (Buffer) -> Unit,
    ): Buffer = memScoped {
        require(expectedSize >= 0) { "expectedSize must not be negative" }
        val context = alloc<LZ4F_decompressionContext_tVar>()
        checkLz4(
            LZ4F_createDecompressionContext(
                context.ptr,
                LZ4F_VERSION,
            ),
            "create decompression context",
        )
        try {
            val inputBuffer = Buffer()
            var pending = ByteArray(0)
            var pendingOffset = 0
            var producedOutput = 0
            var finished = false
            var inputFinished = false
            val outputBuffer = Buffer()

            while (!finished) {
                if (pendingOffset == pending.size) {
                    pending = ByteArray(0)
                    pendingOffset = 0
                    while (inputBuffer.exhausted() && !inputFinished) {
                        val before = inputBuffer.size
                        val read = input(inputBuffer)
                        val added = (inputBuffer.size - before).toInt()
                        require(read == added || read == -1) { "LZ4 input callback size mismatch" }
                        if (read == -1) {
                            require(inputBuffer.exhausted()) { "LZ4 input callback returned data after EOF" }
                            inputFinished = true
                        } else {
                            require(read > 0L) { "LZ4 input callback returned no data" }
                        }
                    }
                    if (!inputBuffer.exhausted()) {
                        val readSize = minOf(inputBuffer.size, CHUNK_SIZE.toLong())
                        pending = inputBuffer.readByteArray(readSize)
                    }
                }
                require(pendingOffset < pending.size) { "LZ4 input ended before the frame completed" }

                require(producedOutput <= expectedSize) { "LZ4 output exceeds expected size" }
                val destinationCapacity = maxOf(1, minOf(CHUNK_SIZE, expectedSize - producedOutput))
                val destination = ByteArray(destinationCapacity)
                val sourceSize = allocArray<ULongVar>(1)
                val destinationSize = allocArray<ULongVar>(1)
                sourceSize.pointed.value = (pending.size - pendingOffset).toULong()
                destinationSize.pointed.value = destinationCapacity.toULong()
                var result = 1uL
                destination.usePinned { destinationPinned ->
                    pending.usePinned { pendingPinned ->
                        result = LZ4F_decompress(
                            context.value,
                            destinationPinned.addressOf(0),
                            destinationSize,
                            pendingPinned.addressOf(pendingOffset),
                            sourceSize,
                            null,
                        )
                    }
                }
                checkLz4(result, "decompress frame")
                pendingOffset += sourceSize.pointed.value.toInt()
                val produced = destinationSize.pointed.value.toInt()
                require(producedOutput + produced <= expectedSize) { "LZ4 output exceeds expected size" }
                if (produced > 0) {
                    outputBuffer.write(destination, 0, produced)
                    output(outputBuffer)
                    producedOutput += produced
                }
                finished = result == 0uL
                if (!finished) {
                    require(sourceSize.pointed.value != 0uL || produced != 0) {
                        "LZ4 decompressor made no progress"
                    }
                }
            }
            require(producedOutput == expectedSize) { "LZ4 size mismatch" }
            require(pendingOffset == pending.size && inputBuffer.exhausted()) { "Trailing bytes after LZ4 frame" }
            outputBuffer
        } finally {
            LZ4F_freeDecompressionContext(context.value)
        }
    }
}
