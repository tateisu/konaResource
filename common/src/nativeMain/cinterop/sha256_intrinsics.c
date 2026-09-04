#include "sha256_intrinsics.h"

#include <stdint.h>

#ifndef KONA_SHA256_PROCESS
#define KONA_SHA256_PROCESS sha256_process
#endif

void KONA_SHA256_PROCESS(uint32_t state[8], const uint8_t data[], uint32_t length);

void kona_sha256_process(int32_t state[8], const void *data, uint32_t length) {
    uint32_t *state_words = (uint32_t *)state;
    const uint8_t *input = (const uint8_t *)data;
    KONA_SHA256_PROCESS(state_words, input, length);
}
