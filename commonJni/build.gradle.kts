import jp.juggler.konaResource.buildlogic.CollectJniFromWorkflowResultTask
import jp.juggler.konaResource.buildlogic.CommonJniBuildTask
import jp.juggler.konaResource.buildlogic.CommonJniBuildUnit
import jp.juggler.konaResource.buildlogic.JniCollectionSpec
import jp.juggler.konaResource.buildlogic.JniBuildTarget
import jp.juggler.konaResource.buildlogic.WorkflowResultJarSpec
import jp.juggler.konaResource.buildlogic.availableJniBuildTargets
import jp.juggler.konaResource.buildlogic.jniBuildProperty
import jp.juggler.konaResource.buildlogic.jniBuildOptions

plugins {
    base
    id("jp.juggler.konaResource.buildlogic")
}

val sourceDirectory = file("src/main/c")
val blake3SourceDirectory = sourceDirectory.resolve("blake3")
val jniSource = sourceDirectory.resolve("blake3_jni.c")
val nativeBuildDirectory = layout.buildDirectory.dir("native")

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

fun JniBuildTarget.registerJniBuild(
    sources: List<File>,
    cflags: List<String>,
    linkFlags: List<String>,
): TaskProvider<*> {
    val taskName = "buildBlake3Jni${buildName.replaceFirstChar { it.uppercase() }}"
    // ターゲットごとのサブディレクトリに出力して、DLL名の衝突(linux x64/arm64 の .so 等)を避ける。
    val library = nativeBuildDirectory.map { it.dir(buildName).file(libraryName) }
    val jniIncludeDir = file(javaHome(rootProject.projectDir)).resolve("include")
    return tasks.register(taskName, CommonJniBuildTask::class.java) {
        group = "build"
        description = "Builds the BLAKE3 JNI shared library for $buildName"
        this.compiler.set(this@registerJniBuild.compilerForHost(project))
        this.linkFlags.set(project.jniBuildOptions(this@registerJniBuild, "linkOpt", linkFlags))
        buildUnits.set(
            listOf(
                CommonJniBuildUnit(
                    arch = arch,
                    sources = sources,
                    cflags = project.jniBuildOptions(this@registerJniBuild, "compileOpt", cflags),
                ),
            ),
        )
        includeDirs.setFrom(
            sourceDirectory,
            blake3SourceDirectory,
            jniIncludeDir,
            file("$jniIncludeDir/${jniPlatformInclude}"),
        )
        jniHeader.set(file("$jniIncludeDir/jni.h"))
        outputLibrary.set(library)
    }
}

fun JniBuildTarget.registerJniBuild() {
    val registeredTask = when (this) {
        JniBuildTarget.LinuxX64 -> registerJniBuild(
            sources = commonSources + x86AsmSources,
            cflags = listOf("-Wall", "-Wextra", "-O3", "-fPIC"),
            linkFlags = listOf("-shared"),
        )

        JniBuildTarget.LinuxArm64 -> registerJniBuild(
            sources = commonSources + neonSource,
            cflags = listOf("-Wall", "-Wextra", "-O3", "-fPIC", "-DBLAKE3_USE_NEON=1"),
            linkFlags = listOf("-shared"),
        )

        // Windows は x86-64 用の .S アセンブリを用意していないため portable 実装にする。
        JniBuildTarget.WindowsX64 -> registerJniBuild(
            sources = commonSources,
            cflags = listOf(
                "-Wall", "-Wextra", "-O3", "-D_JNI_IMPLEMENTATION_",
                "-DBLAKE3_NO_AVX512", "-DBLAKE3_NO_AVX2", "-DBLAKE3_NO_SSE41", "-DBLAKE3_NO_SSE2",
            ),
            linkFlags = listOf("-shared", "-static-libgcc"),
        )

        JniBuildTarget.WindowsArm64 -> registerJniBuild(
            sources = commonSources,
            cflags = listOf(
                "-Wall", "-Wextra", "-O3", "-D_JNI_IMPLEMENTATION_", "-DBLAKE3_USE_NEON=0",
            ),
            linkFlags = listOf("-shared", "-static-libgcc"),
        )

        JniBuildTarget.MacosX64 -> registerJniBuild(
            sources = commonSources + x86AsmSources,
            cflags = listOf("-Wall", "-Wextra", "-O3", "-fPIC"),
            linkFlags = listOf("-shared"),
        )

        JniBuildTarget.MacosArm64 -> registerJniBuild(
            sources = commonSources + neonSource,
            cflags = listOf("-Wall", "-Wextra", "-O3", "-fPIC", "-DBLAKE3_USE_NEON=1"),
            linkFlags = listOf("-shared"),
        )
    }
    tasks.assemble { dependsOn(registeredTask) }
}

val availableJniBuildTargets = project.availableJniBuildTargets()

val workflowResultJars = fileTree(rootProject.file("workflowResult")) {
    include("**/common.jar")
}.files
    // workflowResult/common.jar is the local host build, not a host-specific result.
    .filter { it.parentFile != rootProject.file("workflowResult") }
    .map { it to it.parentFile.name }
    .sortedWith(compareBy({ it.second }, { it.first.absolutePath }))

val jniCollectionSpecs = buildList {
    JniBuildTarget.entries
        .filter { it !in availableJniBuildTargets }
        .filter { it != JniBuildTarget.MacosX64 && it != JniBuildTarget.MacosArm64 }
        .forEach { target ->
            add(
                JniCollectionSpec(
                    resourcePath = "jp/juggler/konaArchive/native/${when (target) {
                        JniBuildTarget.LinuxX64 -> "linux-x86_64/libkona_common_jni.so"
                        JniBuildTarget.LinuxArm64 -> "linux-aarch64/libkona_common_jni.so"
                        JniBuildTarget.WindowsX64 -> "windows-x86_64/kona_common_jni.dll"
                        JniBuildTarget.WindowsArm64 -> "windows-aarch64/kona_common_jni.dll"
                        else -> error("Unexpected macOS target")
                    }}",
                    outputPath = nativeBuildDirectory.get().dir(target.buildName).file(target.libraryName).asFile.absolutePath,
                    targetName = target.name,
                ),
            )
        }
    if (JniBuildTarget.MacosX64 !in availableJniBuildTargets ||
        JniBuildTarget.MacosArm64 !in availableJniBuildTargets
    ) {
        add(
            JniCollectionSpec(
                resourcePath = "jp/juggler/konaArchive/native/macos-universal/libkona_common_jni.dylib",
                outputPath = nativeBuildDirectory.get().dir("macosUniversal2").file("libkona_common_jni.dylib").asFile.absolutePath,
                targetName = "MacosArm64",
            ),
        )
    }
}

val collectJniFromWorkflowResult = tasks.register<CollectJniFromWorkflowResultTask>("collectJniFromWorkflowResult") {
    group = "build"
    description = "Collects unavailable JNI libraries from common.jar files in workflowResult"
    sourceJars.from(workflowResultJars.map { it.first })
    sourceJarSpecs.set(workflowResultJars.map {
        WorkflowResultJarSpec(it.first.absolutePath, it.second)
    })
    collectionSpecs.set(jniCollectionSpecs)
    outputFiles.from(jniCollectionSpecs.map { it.outputPath })
}

tasks.assemble { dependsOn(collectJniFromWorkflowResult) }

// このホストでビルド可能なアーキを全部ビルド
availableJniBuildTargets.forEach{ it.registerJniBuild() }

// MacosX64 と MacosArm64 をビルドできるなら Universal2 もビルド
if (JniBuildTarget.MacosX64 in availableJniBuildTargets &&
    JniBuildTarget.MacosArm64 in availableJniBuildTargets
) {
    // registerMacosUniversal2Build
    val macosUniversal = nativeBuildDirectory.map { it.dir("macosUniversal2").file("libkona_common_jni.dylib") }
    val macosJniIncludeDir = file(JniBuildTarget.MacosX64.javaHome(rootProject.projectDir)).resolve("include")
    val registeredTask = tasks.register("buildBlake3JniMacosUniversal2", CommonJniBuildTask::class.java) {
        group = "build"
        description = "Builds the BLAKE3 JNI shared library for macOS universal2 (x86_64 + arm64)"
        compiler.set(
            project.jniBuildProperty(JniBuildTarget.MacosX64, "compiler")
                ?: project.jniBuildProperty(JniBuildTarget.MacosArm64, "compiler")
                ?: "cc",
        )
        linkFlags.set(project.jniBuildOptions(JniBuildTarget.MacosX64, "linkOpt", listOf("-shared")))
        buildUnits.set(
            listOf(
                CommonJniBuildUnit(
                    arch = "x86_64",
                    sources = commonSources + x86AsmSources,
                    cflags = project.jniBuildOptions(
                        JniBuildTarget.MacosX64,
                        "compileOpt",
                        listOf("-Wall", "-Wextra", "-O3", "-fPIC", "-mavx", "-mavx2", "-mavx512f", "-mavx512vl"),
                    ),
                ),
                CommonJniBuildUnit(
                    arch = "arm64",
                    sources = commonSources + neonSource,
                    cflags = project.jniBuildOptions(
                        JniBuildTarget.MacosArm64,
                        "compileOpt",
                        listOf("-Wall", "-Wextra", "-O3", "-fPIC", "-DBLAKE3_USE_NEON=1"),
                    ),
                ),
            ),
        )
        includeDirs.setFrom(
            sourceDirectory,
            blake3SourceDirectory,
            macosJniIncludeDir,
            macosJniIncludeDir.resolve("darwin"),
        )
        jniHeader.set(macosJniIncludeDir.resolve("jni.h"))
        outputLibrary.set(macosUniversal)
    }
    tasks.assemble { dependsOn(registeredTask) }
    tasks.check { dependsOn(registeredTask) }
}
