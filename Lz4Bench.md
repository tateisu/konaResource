
Lz4Codec の JVM実装とNative実装の速度差。

```
# Kotlin/JVM実装
 ./gradlew benchmark:jvmRun --args="--name lz4-decompress"
decompress: 131727 => 612363 bytes
warmup-lz4-decompress: 431.005MiB/s, ±0.0%, checksum=612363
measurement-lz4-decompress: 435.751MiB/s, +0.5%, checksum=612363
measurement-lz4-decompress: 434.553MiB/s, +0.2%, checksum=612363
measurement-lz4-decompress: 430.420MiB/s, -0.7%, checksum=612363
```

```
# Kotlin/Native実装
$ ./gradlew benchmark:runRelease -Pargs="--name lz4-decompress"
decompress: 131727 => 612363 bytes
warmup-lz4-decompress: 390.026MiB/s, ±0.0%, checksum=612363
measurement-lz4-decompress: 388.592MiB/s, -0.2%, checksum=612363
measurement-lz4-decompress: 389.823MiB/s, +0.0%, checksum=612363
measurement-lz4-decompress: 390.326MiB/s, +0.1%, checksum=612363
```

12%ほどJVMの方が速い。

この差がどこから来るのか？
- Kotlin/Nativeは LZ4_decompress_safe_usingDict を呼び出している
- lz4-javaは内部で JNI → LZ4_decompress_safe を呼び出している
あたりの差異がある。

12%を詰めるためにlz4-javaがやってるあれこれを真似る余地はあるが、
優先度はあまり高くない


関連ファイル
common/src/commonMain/kotlin/jp/juggler/konaArchive/util/Lz4Codec.kt
common/src/nativeMain/kotlin/jp/juggler/konaArchive/util/Lz4CodecNative.kt
common/src/jvmMain/kotlin/jp/juggler/konaArchive/util/Lz4CodecJvm.kt
benchmark/src/commonMain/kotlin/jp/juggler/konaResource/benchmark/KonaLz4Benchmark.kt
benchmark/src/nativeMain/kotlin/jp/juggler/konaResource/benchmark/KonaLz4FrameBenchmark.kt

