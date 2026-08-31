# konaResource

`konaResource` provides embedded resources for Linux/x64 Kotlin/Native executables.

- `plugin` is a Gradle plugin that updates embedded resources.
- `common` contains the KonaArchive format and the library for reading embedded resources.
- `sample1` is a sample project that uses published artifacts.
- `sample2` is a sample project that uses sibling modules.
- `cli` is a CLI tool for the KonaArchive format.

## Build Configuration

```
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // Add the konaResource plugin
    id("jp.juggler.konaResource") version "..."
}
kotlin {
    sourceSets {
        linuxX64Main.dependencies {
            // Add the konaResource common module
            implementation("jp.juggler.konaResource:common:...")
        }
    }
}
// Specify compression settings and resource directories
konaResource{
    // LZ4 compression parameters. All parameters have default values and are optional.
    // LZ4F compression level. 0 is the default fast compression, positive values use LZ4HC, and negative values use fast acceleration.
    lz4CompressionLevel = 0
    lz4BlockSizeID = 1MB
    lz4BlockMode = "LZ4F_blockLinked"
    lz4ContentSizeFlag = true
    lz4ContentChecksumFlag	= true
    lz4blockChecksumFlag = true
    lz4AutoFlush = false
    lz4FavorDecSpeed = true

    // Used for the .o file name and symbol name
    val name1 = "resources"
    // Input directory for the resource archive
    val inDir1 = "src/resources"
    modules.add( name1 to inDir1 )

    // Multiple name and input-directory pairs can be registered
    modules.add( "resourcesB" to "src/resourcesB" )
}
```

## Accessing Embedded Resources

- Example: `sample1/src/linuxX64Main/kotlin/jp/juggler/konaResource/sample/Main.kt`

```kotlin
val root = embedKonaArchive("sample").root
val bytes = root.pathToFile(path)?.bytes()
val string = root.pathToFile(path)?.string()
val buffer = root.pathToFile(path)?.buffer()
for (entry in root.pathToDir(path)!!) {
    println("name=${entry.name}")
}
```

## Using common

- The plugin must be applied.
- Use it from Linux/x64 Kotlin/Native code.

## Using the CLI

```shell
# Deploy the CLI fat JAR and launcher
./gradlew cli:deploy

# Convert a directory to an archive
./konaArchive pack sample1Res.kona sample1/src/res

# List the contents of an archive
./konaArchive list sample1Res.kona

# Extract an archive
./konaArchive extract sample1Res.kona /tmp/sample1Res
```

## Build

```shell
# Build
./gradlew build

# tests
./gradlew check

# Run sample1

# Build and run the sample1 that uses published artifacts
./gradlew sample1:runDebugExecutableLinuxX64
./gradlew sample1:runReleaseExecutableLinuxX64

# Build and run the sample2 that uses sibling modules
./gradlew sample2:runDebugExecutableLinuxX64
./gradlew sample2:runReleaseExecutableLinuxX64

# Run benchmarks

# Execute all benchmarks
./gradlew :benchmark:benchmark

# Executes benchmark for 'linuxX64'
./gradlew :benchmark:linuxX64Benchmark

# Execute benchmark for 'jvm'
./gradlew :benchmark:jvmBenchmark
```
