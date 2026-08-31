#include "sha256_intrinsics.h"

#include <stdatomic.h>

#include <cpuid.h>

void sha256_process(uint32_t state[8], const uint8_t data[], uint32_t length);
void sha256_process_x86(uint32_t state[8], const uint8_t data[], uint32_t length);

static atomic_int sha_ni_supported = ATOMIC_VAR_INIT(-1);

static int detect_sha_ni(void) {
    unsigned int max_leaf = __get_cpuid_max(0, 0);
    if (max_leaf < 7) return 0;

    unsigned int eax;
    unsigned int ebx;
    unsigned int ecx;
    unsigned int edx;
    if (!__get_cpuid(1, &eax, &ebx, &ecx, &edx)) return 0;
    if ((ecx & bit_SSSE3) == 0 || (ecx & bit_SSE4_1) == 0) return 0;

    __cpuid_count(7, 0, eax, ebx, ecx, edx);
    return (ebx & bit_SHA) != 0;
}

static int has_sha_ni(void) {
    int value = atomic_load_explicit(&sha_ni_supported, memory_order_acquire);
    if (value < 0) {
        int detected = detect_sha_ni();
        int expected = -1;
        atomic_compare_exchange_strong_explicit(
            &sha_ni_supported,
            &expected,
            detected,
            memory_order_release,
            memory_order_relaxed);
        value = atomic_load_explicit(&sha_ni_supported, memory_order_acquire);
    }
    return value;
}

void kona_sha256_process(int32_t state[8], const void *data, uint32_t length) {
    uint32_t *state_words = (uint32_t *)state;
    const uint8_t *input = (const uint8_t *)data;
    if (has_sha_ni()) {
        sha256_process_x86(state_words, input, length);
    } else {
        sha256_process(state_words, input, length);
    }
}
