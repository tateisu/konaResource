
this software depends on some OSS library.

----

# Runtime Dependency

## Linux x64
The release executable built from `sample2` has these ELF `DT_NEEDED` entries.
- Note: sample1は公開アーティファクトを参照してしまうのでsample2で計測

```
$ ./gradlew -q build && readelf -d sample2/build/bin/linuxX64/releaseExecutable/sample2.kexe |grep NEEDED |sort
 0x0000000000000001 (NEEDED)             Shared library: [ld-linux-x86-64.so.2]
 0x0000000000000001 (NEEDED)             Shared library: [libcrypt.so.1]
 0x0000000000000001 (NEEDED)             Shared library: [libc.so.6]
 0x0000000000000001 (NEEDED)             Shared library: [libdl.so.2]
 0x0000000000000001 (NEEDED)             Shared library: [libgcc_s.so.1]
 0x0000000000000001 (NEEDED)             Shared library: [libm.so.6]
 0x0000000000000001 (NEEDED)             Shared library: [libpthread.so.0]
 0x0000000000000001 (NEEDED)             Shared library: [libresolv.so.2]
 0x0000000000000001 (NEEDED)             Shared library: [librt.so.1]
 0x0000000000000001 (NEEDED)             Shared library: [libutil.so.1]
```

An empty Kotlin/Native project with only `fun main()=Unit` has these dependencies:
```
$ ./gradlew -q build && readelf -d empty/build/bin/linuxX64/releaseExecutable/empty.kexe |grep NEEDED |sort
 0x0000000000000001 (NEEDED)             Shared library: [ld-linux-x86-64.so.2]
 0x0000000000000001 (NEEDED)             Shared library: [libc.so.6]
 0x0000000000000001 (NEEDED)             Shared library: [libdl.so.2]
 0x0000000000000001 (NEEDED)             Shared library: [libgcc_s.so.1]
 0x0000000000000001 (NEEDED)             Shared library: [libm.so.6]
 0x0000000000000001 (NEEDED)             Shared library: [libpthread.so.0]
```

differences are:
```
 0x0000000000000001 (NEEDED)             Shared library: [libcrypt.so.1]
 0x0000000000000001 (NEEDED)             Shared library: [libresolv.so.2]
 0x0000000000000001 (NEEDED)             Shared library: [librt.so.1]
 0x0000000000000001 (NEEDED)             Shared library: [libutil.so.1]
```

`libcrypt.so.1` is not OpenSSL's `libcrypto`.
This project does not call the `crypt` API, but it is added by Kotlin/Native's **Linux POSIX platform library**, which specifies `-lcrypt` as a default linker option. Kotlin/Native の platform.posix の都合で `libcrypt.so.1` は不要な場合でもリンクされてしまう問題が報告されています。 https://youtrack.jetbrains.com/projects/KT/issues/KT-55643?utm_source=chatgpt.com

`libresolv.so.2`, `librt.so.1`, and `libutil.so.1` are also added by Kotlin/Native's Linux POSIX platform library's default linker options. They are not linked by the empty Kotlin/Native project above.

----

# OSS information

## Runtime libraries (native)

### BLAKE3-team/BLAKE3
- Partially incorporated source code for BLAKE3-256 digest.
- https://github.com/BLAKE3-team/BLAKE3
- CC0 1.0; alternatively Apache License 2.0 or Apache License 2.0 with LLVM exceptions.
- No explicit copyright notice was found in the imported C source. BLAKE3 was designed by Jack O'Connor, Samuel Neves, Jean-Philippe Aumasson, and Zooko.
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

### Apache Commons Codec
- https://github.com/apache/commons-codec
- https://commons.apache.org/proper/commons-codec/
- Apache License 2.0
- Copyright 2002-2026 The Apache Software Foundation.

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
  - This repository is archived and read-only; development continues in the community-maintained fork https://github.com/yawkat/lz4-java.

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
