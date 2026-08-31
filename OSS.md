This software depends on several OSS libraries.

----

# Runtime Dependency

## Linux x64

An empty Kotlin/Native project with only `fun main() = Unit` has these dependencies:

```
$ ./gradlew -q build && readelf -d empty/build/bin/linuxX64/releaseExecutable/empty.kexe | grep NEEDED | sort
 0x0000000000000001 (NEEDED)             Shared library: [ld-linux-x86-64.so.2]
 0x0000000000000001 (NEEDED)             Shared library: [libc.so.6]
 0x0000000000000001 (NEEDED)             Shared library: [libdl.so.2]
 0x0000000000000001 (NEEDED)             Shared library: [libgcc_s.so.1]
 0x0000000000000001 (NEEDED)             Shared library: [libm.so.6]
 0x0000000000000001 (NEEDED)             Shared library: [libpthread.so.0]
```

The release executable built from `sample2` has these ELF `DT_NEEDED` entries. Notice:
`sample1` references a published artifact, so `sample2` is used.

```
$ ./gradlew -q build && readelf -d sample2/build/bin/linuxX64/releaseExecutable/sample2.kexe | grep NEEDED | sort
 0x0000000000000001 (NEEDED)             Shared library: [ld-linux-x86-64.so.2]
 0x0000000000000001 (NEEDED)             Shared library: [libc.so.6]
 0x0000000000000001 (NEEDED)             Shared library: [libdl.so.2]
 0x0000000000000001 (NEEDED)             Shared library: [libgcc_s.so.1]
 0x0000000000000001 (NEEDED)             Shared library: [libm.so.6]
 0x0000000000000001 (NEEDED)             Shared library: [libpthread.so.0]
```

The `sample2` and empty-project dependency lists are identical.

The `common` module does not require additional ELF shared-library dependencies
beyond those of an empty Kotlin/Native `linuxX64` executable.

The `common` module's Linux main source uses C interop wrappers for `dlsym` and
`memcpy` to avoid the Kotlin/Native `platform.posix` library.

----

# OSS information

## Runtime libraries (native or JNI)

### BLAKE3-team/BLAKE3

- Partially incorporated source code for BLAKE3-256 digest.
- Used by the Linux x64 native and JVM JNI implementations.
- https://github.com/BLAKE3-team/BLAKE3
- CC0 1.0; alternatively Apache License 2.0 or Apache License 2.0 with LLVM exceptions.
- No explicit copyright notice was found in the imported C source. BLAKE3 was designed by Jack O'Connor, Samuel Neves,
  Jean-Philippe Aumasson, and Zooko.
- https://github.com/BLAKE3-team/BLAKE3/blob/master/LICENSE_CC0
- https://github.com/BLAKE3-team/BLAKE3/blob/master/LICENSE_A2

### lz4/lz4

- https://github.com/lz4/lz4
- BSD 2-Clause License for the library code in `lib`
- Copyright (c) 2011-2020, Yann Collet.
- https://github.com/Cyan4973

### noloader/SHA-Intrinsics

- Partially incorporated source code for SHA-256 compression and generic fallback.
- https://github.com/noloader/SHA-Intrinsics
- Written and placed in public domain by Jeffrey Walton.
- The x86 source is based on code from Intel and Sean Gulley for the miTLS project.

## Runtime libraries (JVM or KMP)

### kotlinx-cli

- https://github.com/Kotlin/kotlinx-cli
- Apache License 2.0
- Copyright is not specified; the LICENSE file retains the Apache License template placeholder.
    - https://github.com/Kotlin/kotlinx-cli/issues/108

### Okio

- https://github.com/lysine-dev/okio
- Apache License 2.0
- Copyright 2013 Square, Inc. (by README.md)

### yawkat/lz4-java

- https://github.com/yawkat/lz4-java
- Apache License 2.0
- Copyright 2020 Adrien Grand and the lz4-java contributors.
    - This repository is archived and read-only; development continues in the community-maintained
      fork https://github.com/yawkat/lz4-java.

## Build and test libraries

### Detekt

- https://github.com/detekt/detekt
- Apache License 2.0
- Copyright 2016-2017 Artur Bosch & Contributors

### google/ksp

- https://github.com/google/ksp
- Apache License 2.0
- Copyright 2020 Google LLC; Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.

### GradleUp/shadow

- https://github.com/GradleUp/shadow
- Apache License 2.0
- Copyright is not specified; the LICENSE file retains the Apache License template placeholder.
    - https://github.com/GradleUp/shadow/issues/2264

### Kotest

- https://github.com/kotest/kotest
- https://kotest.io
- Apache License 2.0
- Copyright `[2016] [sksamuel]`

### Kotlin

- https://github.com/JetBrains/kotlin
- https://kotlinlang.org
- Apache License 2.0
- Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
    - (by license/COPYRIGHT.txt)

### kotlinx-benchmark

- https://github.com/Kotlin/kotlinx-benchmark
- Apache License 2.0
- Copyright is not specified; the LICENSE file retains the Apache License template placeholder.
- https://github.com/Kotlin/kotlinx-benchmark/issues/397
