#include <stddef.h>

typedef void* LZ4F_compressionContext_t;
typedef void* LZ4F_decompressionContext_t;

typedef struct {
    int blockSizeID;
    int blockMode;
    int contentChecksumFlag;
    int frameType;
    unsigned long long contentSize;
    unsigned dictID;
    int blockChecksumFlag;
} LZ4F_preferences_frameInfo_t;

typedef struct {
    LZ4F_preferences_frameInfo_t frameInfo;
    int compressionLevel;
    unsigned autoFlush;
    unsigned favorDecSpeed;
    unsigned reserved[3];
} LZ4F_preferences_t;

static inline void kona_lz4_init_preferences(
    LZ4F_preferences_t* preferences,
    int blockSizeID,
    int blockMode,
    int contentChecksumFlag,
    unsigned long long contentSize,
    int blockChecksumFlag,
    int compressionLevel,
    unsigned autoFlush,
    unsigned favorDecSpeed
) {
    *preferences = (LZ4F_preferences_t){0};
    preferences->frameInfo.blockSizeID = blockSizeID;
    preferences->frameInfo.blockMode = blockMode;
    preferences->frameInfo.contentChecksumFlag = contentChecksumFlag;
    preferences->frameInfo.frameType = 0;
    preferences->frameInfo.contentSize = contentSize;
    preferences->frameInfo.blockChecksumFlag = blockChecksumFlag;
    preferences->compressionLevel = compressionLevel;
    preferences->autoFlush = autoFlush;
    preferences->favorDecSpeed = favorDecSpeed;
}

size_t LZ4F_createCompressionContext(LZ4F_compressionContext_t* cctxPtr, unsigned version);
size_t LZ4F_freeCompressionContext(LZ4F_compressionContext_t cctx);
size_t LZ4F_compressBegin(LZ4F_compressionContext_t cctx, void* dst, size_t dstCapacity, const LZ4F_preferences_t* prefsPtr);
size_t LZ4F_compressBound(size_t srcSize, const LZ4F_preferences_t* prefsPtr);
size_t LZ4F_compressUpdate(LZ4F_compressionContext_t cctx, void* dst, size_t dstCapacity, const void* src, size_t srcSize, const void* optionsPtr);
size_t LZ4F_compressEnd(LZ4F_compressionContext_t cctx, void* dst, size_t dstCapacity, const void* optionsPtr);
unsigned LZ4F_isError(size_t code);
const char* LZ4F_getErrorName(size_t code);
size_t LZ4F_createDecompressionContext(LZ4F_decompressionContext_t* dctxPtr, unsigned version);
size_t LZ4F_freeDecompressionContext(LZ4F_decompressionContext_t dctx);
size_t LZ4F_decompress(LZ4F_decompressionContext_t dctx, void* dst, size_t* dstSizePtr, const void* src, size_t* srcSizePtr, const void* optionsPtr);
