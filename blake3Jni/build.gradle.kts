import jp.juggler.konaResource.buildlogic.Blake3JniBuildTask
import jp.juggler.konaResource.buildlogic.Blake3JniBuildUnit
import jp.juggler.konaResource.buildlogic.macosEnabled

plugins {
    base
    id("jp.juggler.konaResource.buildlogic")
}

// -Pmacos=true/false の上書きを考慮した macOS ビルドの有効/無効 (build-logic のユーティリティ)。
val enableMacos: Boolean = macosEnabled()

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

fun registerJniBuild(build: JniBuild) {
    val taskName = "buildBlake3Jni${build.name.replaceFirstChar { it.uppercase() }}"
    // ターゲットごとのサブディレクトリに出力して、DLL名の衝突(linux x64/arm64 の .so 等)を避ける。
    val library = nativeBuildDirectory.map { it.dir(build.name).file(build.libraryName) }
    tasks.register(taskName, Blake3JniBuildTask::class.java) {
        group = "build"
        description = "Builds the BLAKE3 JNI shared library for ${build.name}"
        compiler.set(build.compiler)
        linkFlags.set(build.linkFlags)
        buildUnits.set(listOf(Blake3JniBuildUnit(arch = "", sources = build.sources, cflags = build.cflags)))
        includeDirs.setFrom(
            sourceDirectory,
            blake3SourceDirectory,
            jniIncludeDir,
            file("$jniIncludeDir/${build.jniPlatformInclude}"),
        )
        jniHeader.set(file("$jniIncludeDir/jni.h"))
        outputLibrary.set(library)
    }
}

jniBuilds.forEach { registerJniBuild(it) }

// macOS universal2 (x86_64 + arm64)。macOS ビルドが可能なときのみ。
if (enableMacos) {
    val macosUniversal = nativeBuildDirectory.map { it.dir("macosUniversal2").file("libblake3_jni.dylib") }
    tasks.register("buildBlake3JniMacosUniversal2", Blake3JniBuildTask::class.java) {
        group = "build"
        description = "Builds the BLAKE3 JNI shared library for macOS universal2 (x86_64 + arm64)"
        compiler.set("cc")
        linkFlags.set(listOf("-shared"))
        buildUnits.set(
            listOf(
                Blake3JniBuildUnit(
                    arch = "x86_64",
                    sources = commonSources + x86AsmSources,
                    cflags = listOf("-Wall", "-Wextra", "-O3", "-fPIC", "-mavx", "-mavx2", "-mavx512f", "-mavx512vl"),
                ),
                Blake3JniBuildUnit(
                    arch = "arm64",
                    sources = commonSources + neonSource,
                    cflags = listOf("-Wall", "-Wextra", "-O3", "-fPIC", "-DBLAKE3_USE_NEON=1"),
                ),
            ),
        )
        includeDirs.setFrom(
            sourceDirectory,
            blake3SourceDirectory,
            jniIncludeDir,
            file("$jniIncludeDir/darwin"),
        )
        jniHeader.set(file("$jniIncludeDir/jni.h"))
        outputLibrary.set(macosUniversal)
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
