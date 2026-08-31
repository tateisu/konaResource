#ifndef KONA_SYSTEM_H
#define KONA_SYSTEM_H

#include <dlfcn.h>
#include <stddef.h>
#include <string.h>

static inline char* kona_dlsym(const char* name) {
    return (char*)dlsym(NULL, name);
}

static inline void kona_memcpy(void* destination, const void* source, size_t length) {
    memcpy(destination, source, length);
}

#endif
