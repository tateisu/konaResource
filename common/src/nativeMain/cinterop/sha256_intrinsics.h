#ifndef KONA_SHA256_INTRINSICS_H
#define KONA_SHA256_INTRINSICS_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Process complete SHA-256 blocks and update the chaining state. */
void kona_sha256_process(int32_t state[8], const void *data, uint32_t length);

#ifdef __cplusplus
}
#endif

#endif
