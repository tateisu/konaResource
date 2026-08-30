# konaResource

`konaResource` provides embedded resources for Linux/x64 Kotlin/Native executables.
- `plugin` is a Gradle plugin that updates embedded resources.
- `common` contains the KonaArchive format and the library for reading embedded resources.
- `sample1` is a sample project that uses `plugin` and `common`.
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
- Example: `sample1/src/linuxX64Main/kotlin/jp/juggler/konaResource/sample1/Main.kt`

```kotlin
val root = embedKonaArchive("sample").root
val string = (root.getPath(path) as? KonaArchiveFile)?.string()
```

## Using common
- The plugin must be applied.
- Use it from Linux/x64 Kotlin/Native code.

## Using the CLI
```shell
# List the contents of an archive
./gradlew :cli:run --args='list archive.bin'

# Convert a directory to an archive
./gradlew :cli:run --args='pack archive.bin input-directory'

# Reuse identical content from a previous archive
./gradlew :cli:run --args='pack archive.bin input-directory --previous archive.bin'

# Extract an archive
./gradlew :cli:run --args='extract archive.bin output-directory'
```

## Build

```shell
# Build
./gradlew build

# Run tests
./gradlew check

# Build and run the sample1 debug executable
./gradlew sample1:runDebugExecutableLinuxX64

# Build the sample1 release executable
./gradlew sample1:linkReleaseExecutableLinuxX64
```

### Switching the Artifact Used by sample1
- By default, `sample1` uses sibling modules in this project.
- Specify `-Psample1Artifact=0.1.3` for Gradle to use an artifact published to Maven Central.

Example:
```
./gradlew -Psample1Artifact=0.1.3 clean sample1:runDebugExecutableLinuxX64
```
