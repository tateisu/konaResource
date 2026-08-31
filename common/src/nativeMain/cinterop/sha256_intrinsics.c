#include "sha256_intrinsics.h"

#include <stdint.h>

void sha256_process(uint32_t state[8], const uint8_t data[], uint32_t length);

void kona_sha256_process(int32_t state[8], const void *data, uint32_t length) {
    uint32_t *state_words = (uint32_t *)state;
    const uint8_t *input = (const uint8_t *)data;
    sha256_process(state_words, input, length);
}
