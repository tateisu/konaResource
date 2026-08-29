# konaResource

`konaResource` embeds LZ4-compressed resource archives in Linux/x64 Kotlin/Native executables.

The repository contains:

- `common`: portable archive encoder/decoder and SHA-256/LZ4 implementations.
- `plugin`: Gradle plugin that creates an archive and an ELF object containing it.
- `reader`: Linux/x64 reader that locates the exported ELF symbols with `dlsym`.
- `cli`: JVM command-line tool for `pack`, `list`, and `extract`.
- `sample1`: Kotlin/Native usage example.

The plugin extension accepts multiple `modules.add("name" to "directory")` entries. The generated object uses `konaResource_<name>_start` and `_end` symbols and is automatically added to Linux/x64 Kotlin/Native link tasks when the plugin is applied to the same project. The plugin is published by the `plugin` subproject and can be consumed with the generated plugin marker.

Run the CLI with `./gradlew :cli:run --args='list archive.bin'`.
