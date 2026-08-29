#include <openssl/evp.h>
#include <stddef.h>

static inline void* kona_sha256_new(void) {
    EVP_MD_CTX* context = EVP_MD_CTX_new();
    if (context == NULL) return NULL;
    if (EVP_DigestInit_ex(context, EVP_sha256(), NULL) != 1) {
        EVP_MD_CTX_free(context);
        return NULL;
    }
    return context;
}

static inline int kona_sha256_update(void* context, const void* data, size_t length) {
    return EVP_DigestUpdate((EVP_MD_CTX*)context, data, length);
}

static inline int kona_sha256_final(void* context, void* digest) {
    unsigned int digestLength = 0;
    if (EVP_DigestFinal_ex((EVP_MD_CTX*)context, (unsigned char*)digest, &digestLength) != 1) return 0;
    return digestLength == 32;
}

static inline void kona_sha256_free(void* context) {
    EVP_MD_CTX_free((EVP_MD_CTX*)context);
}
