#include "blake3/blake3.h"
#include <stdlib.h>

static inline blake3_hasher* kona_blake3_new(void) {
    blake3_hasher* context = (blake3_hasher*)malloc(sizeof(blake3_hasher));
    if (context != NULL) blake3_hasher_init(context);
    return context;
}

static inline void kona_blake3_update(blake3_hasher* context,
                                      const void* input, size_t input_len) {
    blake3_hasher_update(context, input, input_len);
}

static inline void kona_blake3_finalize(blake3_hasher* context,
                                        void* output, size_t output_len) {
    blake3_hasher_finalize(context, (uint8_t*)output, output_len);
}

static inline void kona_blake3_free(blake3_hasher* context) {
    free(context);
}
