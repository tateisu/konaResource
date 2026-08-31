import org.gradle.api.GradleException
import java.io.IOException

plugins {
    base
}

// -Pmacos=true を指定したときのみ macos ターゲットを含める。
// macOS ターゲットのJNIビルドは macOS ホストでしか処理できないため。
val enableMacos: Boolean = (findProperty("macos") as? String)?.toBoolean() == true

val sourceDirectory = file("src/main/c")
val blake3SourceDirectory = sourceDirectory.resolve("blake3")
val jniSource = sourceDirectory.resolve("blake3_jni.c")
val nativeBuildDirectory = layout.buildDirectory.dir("native")
val javaHome = System.getProperty("java.home")
val jniIncludeDir = file("$javaHome/include")

val commonSources = listOf(
    blake3SourceDirectory.resolve("blake3.c"),
    blake3SourceDirectory.resolve("blake3_dispatch.c"),
    blake3SourceDirectory.resolve("blake3_portable.c"),
    jniSource,
)

val x86AsmSources = listOf(
    "blake3_sse2_x86-64_unix.S",
    "blake3_sse41_x86-64_unix.S",
    "blake3_avx2_x86-64_unix.S",
    "blake3_avx512_x86-64_unix.S",
).map { blake3SourceDirectory.resolve(it) }

val neonSource = blake3SourceDirectory.resolve("blake3_neon.c")

data class JniBuild(
    val name: String,
    val compiler: String,
    val libraryName: String,
    val jniPlatformInclude: String,
    val sources: List<File>,
    val cflags: List<String>,
    val linkFlags: List<String>,
)

val jniBuilds = mutableListOf<JniBuild>()

jniBuilds += JniBuild(
    name = "linuxX64",
    compiler = "cc",
    libraryName = "libblake3_jni.so",
    jniPlatformInclude = "linux",
    sources = commonSources + x86AsmSources,
    cflags = listOf("-Wall", "-Wextra", "-O3", "-fPIC", "-mavx", "-mavx2", "-mavx512f", "-mavx512vl"),
    linkFlags = listOf("-shared"),
)

jniBuilds += JniBuild(
    name = "linuxArm64",
    compiler = "aarch64-linux-gnu-gcc",
    libraryName = "libblake3_jni.so",
    jniPlatformInclude = "linux",
    sources = commonSources + neonSource,
    cflags = listOf("-Wall", "-Wextra", "-O3", "-fPIC", "-DBLAKE3_USE_NEON=1"),
    linkFlags = listOf("-shared"),
)

// Windows は x86-64 用の .S アセンブリを用意していないため portable 実装にする。
jniBuilds += JniBuild(
    name = "windowsX64",
    compiler = "x86_64-w64-mingw32-gcc",
    libraryName = "blake3_jni.dll",
    jniPlatformInclude = "win32",
    sources = commonSources,
    cflags = listOf(
        "-Wall", "-Wextra", "-O3", "-D_JNI_IMPLEMENTATION_",
        "-DBLAKE3_NO_AVX512", "-DBLAKE3_NO_AVX2", "-DBLAKE3_NO_SSE41", "-DBLAKE3_NO_SSE2",
    ),
    linkFlags = listOf("-shared", "-static-libgcc"),
)

jniBuilds += JniBuild(
    name = "windowsArm64",
    compiler = "aarch64-w64-mingw32-gcc",
    libraryName = "blake3_jni.dll",
    jniPlatformInclude = "win32",
    sources = commonSources,
    cflags = listOf(
        "-Wall", "-Wextra", "-O3", "-D_JNI_IMPLEMENTATION_", "-DBLAKE3_USE_NEON=0",
    ),
    linkFlags = listOf("-shared", "-static-libgcc"),
)

fun checkCompiler(compiler: String) {
    try {
        ProcessBuilder(compiler, "--version").inheritIO().start().waitFor()
    } catch (e: IOException) {
        throw GradleException(
            "Cross compiler '$compiler' for target not found. Install it or exclude the target.",
            e,
        )
    }
}

fun runCommand(vararg command: String) {
    check(ProcessBuilder(*command).inheritIO().start().waitFor() == 0) {
        "Command failed: ${command.joinToString(" ")}"
    }
}

fun registerJniBuild(build: JniBuild) {
    val taskName = "buildBlake3Jni${build.name.replaceFirstChar { it.uppercase() }}"
    // ターゲットごとのサブディレクトリに出力して、DLL名の衝突(linux x64/arm64 の .so 等)を避ける。
    val library = nativeBuildDirectory.map { it.dir(build.name).file(build.libraryName) }
    val includeDirs = listOf(
        "-I$sourceDirectory",
        "-I$blake3SourceDirectory",
        "-I$jniIncludeDir",
        "-I${file("$jniIncludeDir/${build.jniPlatformInclude}")}",
    )
    tasks.register(taskName) {
        group = "build"
        description = "Builds the BLAKE3 JNI shared library for ${build.name}"
        inputs.files(build.sources, jniIncludeDir)
        outputs.file(library)
        doLast {
            checkCompiler(build.compiler)
            check(file("$jniIncludeDir/jni.h").isFile) {
                "jni.h was not found under $jniIncludeDir"
            }
            val outputDirectory = nativeBuildDirectory.get().asFile
            outputDirectory.mkdirs()
            val objects = build.sources.map { source ->
                val objectFile = outputDirectory.resolve("objects/${build.name}/${source.name}.o")
                objectFile.parentFile.mkdirs()
                objectFile
            }
            build.sources.zip(objects).forEach { (source, objectFile) ->
                runCommand(
                    build.compiler,
                    *build.cflags.toTypedArray(),
                    "-c", source.absolutePath,
                    *includeDirs.toTypedArray(),
                    "-o", objectFile.absolutePath,
                )
            }
            runCommand(
                build.compiler,
                *build.linkFlags.toTypedArray(),
                "-o", library.get().asFile.absolutePath,
                *objects.map { it.absolutePath }.toTypedArray(),
            )
        }
    }
}

jniBuilds.forEach { registerJniBuild(it) }

// macOS universal2 (x86_64 + arm64)。macOS ホストで -Pmacos=true を指定したときのみ。
if (enableMacos) {
    val macosUniversal = nativeBuildDirectory.map { it.dir("macosUniversal2").file("libblake3_jni.dylib") }
    val includeDirs = listOf(
        "-I$sourceDirectory",
        "-I$blake3SourceDirectory",
        "-I$jniIncludeDir",
        "-I${file("$jniIncludeDir/darwin")}",
    )
    tasks.register("buildBlake3JniMacosUniversal2") {
        group = "build"
        description = "Builds the BLAKE3 JNI shared library for macOS universal2 (x86_64 + arm64)"
        inputs.files(commonSources + x86AsmSources + neonSource, jniIncludeDir)
        outputs.file(macosUniversal)
        doLast {
            checkCompiler("cc")
            check(file("$jniIncludeDir/jni.h").isFile) {
                "jni.h was not found under $jniIncludeDir"
            }
            val outputDirectory = nativeBuildDirectory.get().asFile
            outputDirectory.mkdirs()
            val x86Directory = outputDirectory.resolve("macos-x86_64")
            val armDirectory = outputDirectory.resolve("macos-arm64")
            x86Directory.mkdirs()
            armDirectory.mkdirs()

            fun compile(arch: String, directory: File, sources: List<File>, cflags: List<String>) {
                val objects = sources.map { source ->
                    val objectFile = directory.resolve("${source.name}.o")
                    runCommand(
                        "cc",
                        *cflags.toTypedArray(),
                        "-arch", arch,
                        "-c", source.absolutePath,
                        *includeDirs.toTypedArray(),
                        "-o", objectFile.absolutePath,
                    )
                    objectFile
                }
                val dylib = directory.resolve("libblake3_jni.dylib")
                runCommand(
                    "cc",
                    "-shared",
                    "-arch", arch,
                    "-o", dylib.absolutePath,
                    *objects.map { it.absolutePath }.toTypedArray(),
                )
            }

            compile(
                arch = "x86_64",
                directory = x86Directory,
                sources = commonSources + x86AsmSources,
                cflags = listOf("-Wall", "-Wextra", "-O3", "-fPIC", "-mavx", "-mavx2", "-mavx512f", "-mavx512vl"),
            )
            compile(
                arch = "arm64",
                directory = armDirectory,
                sources = commonSources + neonSource,
                cflags = listOf("-Wall", "-Wextra", "-O3", "-fPIC", "-DBLAKE3_USE_NEON=1"),
            )
            runCommand(
                "lipo",
                "-create",
                x86Directory.resolve("libblake3_jni.dylib").absolutePath,
                armDirectory.resolve("libblake3_jni.dylib").absolutePath,
                "-output", macosUniversal.get().asFile.absolutePath,
            )
        }
    }
}

// デフォルトのビルドタスク(common モジュールの jvm 側が参照する)。ホストの Linux x64 をビルドする。
val buildBlake3Jni = tasks.register("buildBlake3Jni") {
    group = "build"
    description = "Builds the BLAKE3 JNI shared library for the default (Linux x64) target"
    dependsOn("buildBlake3JniLinuxX64")
}

tasks.assemble {
    dependsOn(buildBlake3Jni)
}

tasks.check {
    dependsOn(buildBlake3Jni)
}
