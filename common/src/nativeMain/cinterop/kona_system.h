#ifndef KONA_SYSTEM_H
#define KONA_SYSTEM_H

#include <stddef.h>
#include <string.h>

#if defined(_WIN32)
#include <windows.h>
#else
#include <dlfcn.h>
#endif

static inline char* kona_dlsym(const char* name) {
#if defined(_WIN32)
    return (char*)(void*)GetProcAddress(GetModuleHandleA(NULL), name);
#else
    return (char*)dlsym(NULL, name);
#endif
}

static inline void kona_memcpy(void* destination, const void* source, size_t length) {
    memcpy(destination, source, length);
}

#endif
