#ifndef BLAKE3_KONA_IMPL_H
#define BLAKE3_KONA_IMPL_H

// x86 では .S アセンブリ(SIMD)を提供しないため portable 実装にする。
// ARM64 では blake3_neon.c を提供しないため portable 実装にする。
// (blake3_impl.h は x86 で IS_X86、ARM64 で BLAKE3_USE_NEON=1 を自動定義する)
#define BLAKE3_NO_AVX512
#define BLAKE3_NO_AVX2
#define BLAKE3_NO_SSE41
#define BLAKE3_NO_SSE2
#ifndef BLAKE3_USE_NEON
#define BLAKE3_USE_NEON 0
#endif

#include "blake3_kona.h"
#include "blake3/blake3.c"
#include "blake3/blake3_dispatch.c"
#include "blake3/blake3_portable.c"

#endif
