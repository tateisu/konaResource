import jp.juggler.konaResource.buildlogic.JniBuildTarget
import jp.juggler.konaResource.buildlogic.availableJniBuildTargets
import jp.juggler.konaResource.buildlogic.konaTargets
import jp.juggler.konaResource.buildlogic.runKonan
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("jp.juggler.konaResource.buildlogic")
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    `maven-publish`
}

group = "jp.juggler.konaResource"
version = rootProject.version

// warning, PIC options, optimization for this project.
val nativeCCompilerOptions = listOf("-W", "-Wall", "-O3", "-fPIC")
val cinteropDirectory = file("src/nativeMain/cinterop")

val commonJavadocJar = tasks.register<Jar>("javadocJar") {
    description = "javadoc Jarを生成する(common)"
    archiveBaseName.set("common")
    archiveClassifier.set("javadoc")
    from(rootProject.file("README.md"))
}

val jvmJavadocJar = tasks.register<Jar>("jvmJavadocJar") {
    description = "javadoc Jarを生成する(common-jvm)"
    archiveBaseName.set("common-jvm")
    archiveClassifier.set("javadoc")
    from(rootProject.file("README.md"))
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        when (name) {
            "kotlinMultiplatform" -> artifact(commonJavadocJar)
            "jvm" -> artifact(jvmJavadocJar)
        }
    }
}

kotlin {
    konaTargets()

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.okio)
            implementation(libs.kotlinxCoroutinesCore)
        }
        nativeMain.dependencies {
            implementation(libs.lz4Native)
        }
        jvmMain.dependencies {
            implementation(libs.lz4Java)
        }
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        val nativeTargetName = targetName
        val nativeTargetSuffix = targetName.replaceFirstChar { it.uppercase() }
        compilations.getByName("main") {
            cinterops {
                create("lz4") {
                    definitionFile.set(cinteropDirectory.resolve("lz4.def"))
                    compilerOpts(
                        *nativeCCompilerOptions.toTypedArray(),
                        "-I${cinteropDirectory.absolutePath}",
                    )
                }
                create("blake3") {
                    definitionFile.set(
                        layout.buildDirectory.file("generated/cinterop/$nativeTargetName/blake3.def"),
                    )
                    compilerOpts(
                        *nativeCCompilerOptions.toTypedArray(),
                        "-I${cinteropDirectory.absolutePath}",
                    )
                }
                create("sha256Intrinsics") {
                    val shaDefinition = layout.buildDirectory.file(
                        "generated/cinterop/$nativeTargetName/sha256_intrinsics.def",
                    )
                    val blakeDefinition = layout.buildDirectory.file(
                        "generated/cinterop/$nativeTargetName/blake3.def",
                    )
                    val nativeLibraryDirectory = layout.buildDirectory.dir("native/kona_common_native/$nativeTargetName")
                    val nativeLibrary = nativeLibraryDirectory.map { it.file("libkona_common_native.a") }
                    val shaObject = nativeLibraryDirectory.map { it.file("sha256.o") }
                    val shaWrapperObject = nativeLibraryDirectory.map { it.file("sha256_intrinsics.o") }
                    val blakeSources = when (nativeTargetName) {
                        "linuxX64", "macosX64" -> listOf(
                            "blake3.c", "blake3_dispatch.c", "blake3_portable.c",
                            "blake3_sse2_x86-64_unix.S", "blake3_sse41_x86-64_unix.S",
                            "blake3_avx2_x86-64_unix.S", "blake3_avx512_x86-64_unix.S",
                        )
                        "linuxArm64", "macosArm64" -> listOf(
                            "blake3.c", "blake3_dispatch.c", "blake3_portable.c", "blake3_neon.c",
                        )
                        "mingwX64" -> listOf("blake3.c", "blake3_dispatch.c", "blake3_portable.c")
                        else -> emptyList()
                    }.map { cinteropDirectory.resolve("blake3/$it") }
                    val blakeObjects = blakeSources.map { source ->
                        nativeLibraryDirectory.map { it.file("${source.name}.o") }
                    }
                    val fastShaSource = when (nativeTargetName) {
                        "linuxX64", "macosX64", "mingwX64" -> cinteropDirectory.resolve("SHA-Intrinsics/sha256-x86.c")
                        "linuxArm64", "macosArm64" -> cinteropDirectory.resolve("SHA-Intrinsics/sha256-arm.c")
                        else -> null
                    }
                    if (fastShaSource == null) {
                        definitionFile.set(cinteropDirectory.resolve("sha256_intrinsics.def"))
                    } else {
                        val konanTarget = when (nativeTargetName) {
                            "linuxX64" -> "linux_x64"
                            "linuxArm64" -> "linux_arm64"
                            "mingwX64" -> "mingw_x64"
                            "macosX64" -> "macos_x64"
                            "macosArm64" -> "macos_arm64"
                            else -> error("Unexpected native target: $nativeTargetName")
                        }
                        fun clang() = runKonan(
                            kotlinVersion = libs.versions.kotlin.get(),
                            mode = "clang",
                            tool = "clang",
                            target = konanTarget,
                        )
                        val compileSha = tasks.register<Exec>("compileSha256Intrinsics$nativeTargetName") {
                            inputs.file(fastShaSource)
                            outputs.file(shaObject)
                            doFirst { shaObject.get().asFile.parentFile.mkdirs() }
                            doFirst {
                                commandLine(
                                clang() + listOf(
                                    "-c", fastShaSource.absolutePath, "-O3", "-fPIC",
                                ) + if (nativeTargetName.endsWith("X64")) {
                                    listOf("-msse4.1", "-msha")
                                } else {
                                    listOf("-march=armv8-a+crc+crypto")
                                } + listOf(
                                    "-o", shaObject.get().asFile.absolutePath,
                                ),
                                )
                            }
                        }
                        val compileShaWrapper = tasks.register<Exec>("compileSha256IntrinsicsWrapper$nativeTargetName") {
                            inputs.file(cinteropDirectory.resolve("sha256_intrinsics.c"))
                            outputs.file(shaWrapperObject)
                            doFirst { shaWrapperObject.get().asFile.parentFile.mkdirs() }
                            val process = "sha256_process_${if (nativeTargetName.endsWith("X64")) "x86" else "arm"}"
                            doFirst {
                                commandLine(
                                clang() + listOf(
                                    "-c", cinteropDirectory.resolve("sha256_intrinsics.c").absolutePath,
                                    "-O3", "-fPIC", "--define-macro=KONA_SHA256_PROCESS=$process",
                                    "-I${cinteropDirectory.absolutePath}", "-o", shaWrapperObject.get().asFile.absolutePath,
                                ),
                                )
                            }
                        }
                        val compileBlake = blakeSources.map { source ->
                            tasks.register<Exec>("compileBlake3${source.name.replace('.', '_')}$nativeTargetName") {
                                inputs.file(source)
                                outputs.file(nativeLibraryDirectory.map { it.file("${source.name}.o") })
                                doFirst { nativeLibraryDirectory.get().asFile.mkdirs() }
                                doFirst {
                                    commandLine(
                                    clang() + listOf(
                                        "-c", source.absolutePath, "-O3", "-fPIC",
                                    ) + when {
                                        nativeTargetName == "linuxArm64" || nativeTargetName == "macosArm64" ->
                                            listOf("--define-macro=BLAKE3_USE_NEON=1")
                                        nativeTargetName == "mingwX64" -> listOf(
                                            "--define-macro=BLAKE3_NO_AVX512",
                                            "--define-macro=BLAKE3_NO_AVX2",
                                            "--define-macro=BLAKE3_NO_SSE41",
                                            "--define-macro=BLAKE3_NO_SSE2",
                                        )
                                        else -> emptyList()
                                    } + listOf(
                                        "-I${cinteropDirectory.absolutePath}",
                                        "-I${cinteropDirectory.resolve("blake3").absolutePath}",
                                        "-o", nativeLibraryDirectory.get().asFile.resolve("${source.name}.o").absolutePath,
                                    ),
                                    )
                                }
                            }
                        }
                        val archiveNative = tasks.register<Exec>("archiveKonaCommonNative$nativeTargetName") {
                            dependsOn(compileSha, compileShaWrapper)
                            dependsOn(compileBlake)
                            inputs.files(shaObject, shaWrapperObject, blakeObjects)
                            outputs.file(nativeLibrary)
                            doFirst {
                                nativeLibrary.get().asFile.parentFile.mkdirs()
                                commandLine(
                                    runKonan(
                                        kotlinVersion = libs.versions.kotlin.get(),
                                        mode = "llvm",
                                        tool = "llvm-ar",
                                    ) + listOf(
                                        "rcs", nativeLibrary.get().asFile.absolutePath,
                                        // Add other Kona native objects to this archive as they are introduced.
                                        shaObject.get().asFile.absolutePath,
                                        shaWrapperObject.get().asFile.absolutePath,
                                    ) + blakeObjects.map { it.get().asFile.absolutePath },
                                )
                            }
                        }
                        val generateShaDefinition = tasks.register("generateSha256IntrinsicsDefinition$nativeTargetName") {
                            dependsOn(archiveNative)
                            outputs.file(shaDefinition)
                            doLast {
                                shaDefinition.get().asFile.apply {
                                    parentFile.mkdirs()
                                    writeText(
                                            "headers = sha256_intrinsics.h\n" +
                                            "staticLibraries = libkona_common_native.a\n" +
                                            "libraryPaths = ${nativeLibraryDirectory.get().asFile.absolutePath}\n" +
                                            "package = jp.juggler.konaResource.sha256intrinsics\n",
                                    )
                                }
                                blakeDefinition.get().asFile.apply {
                                    parentFile.mkdirs()
                                    writeText(
                                        "headers = blake3_kona.h\n" +
                                            "staticLibraries = libkona_common_native.a\n" +
                                            "libraryPaths = ${nativeLibraryDirectory.get().asFile.absolutePath}\n" +
                                            "package = jp.juggler.konaResource.blake3\n",
                                    )
                                }
                            }
                        }
                        definitionFile.set(shaDefinition)
                        tasks.named("cinteropSha256Intrinsics$nativeTargetSuffix") {
                            dependsOn(generateShaDefinition)
                        }
                        tasks.named("cinteropBlake3$nativeTargetSuffix") {
                            dependsOn(generateShaDefinition)
                        }
                    }
                    compilerOpts(
                        *nativeCCompilerOptions.toTypedArray(),
                        "-I${cinteropDirectory.absolutePath}",
                    )
                }
                create("konaSystem") {
                    definitionFile.set(cinteropDirectory.resolve("kona_system.def"))
                    compilerOpts(
                        *nativeCCompilerOptions.toTypedArray(),
                        "-I${cinteropDirectory.absolutePath}",
                    )
                }
            }
        }
    }
}

tasks.named<ProcessResources>("jvmProcessResources") {
    val availableTargets = project.availableJniBuildTargets(libs.versions.kotlin.get())
    listOf(
        JniBuildTarget.LinuxX64 to "linux-x86_64",
        JniBuildTarget.LinuxArm64 to "linux-aarch64",
        JniBuildTarget.MingwX64 to "windows-x86_64",
    ).forEach { (target, resourceDirectory) ->
        if (target in availableTargets) {
            dependsOn(":commonJni:buildCommonJni${target.buildName.replaceFirstChar { it.uppercase() }}")
        } else {
            dependsOn(":commonJni:collectJniFromWorkflowResult")
        }
        from(rootProject.file("commonJni/build/native/${target.buildName}/${target.libraryName}")) {
            into("jp/juggler/konaArchive/native/$resourceDirectory")
        }
    }

    val macosTargets = listOf(JniBuildTarget.MacosArm64, JniBuildTarget.MacosX64)
    val macosLibrary = when {
        macosTargets.all { it in availableTargets } -> {
            dependsOn(":commonJni:buildCommonJniMacosUniversal2")
            rootProject.file("commonJni/build/native/macosUniversal2/libkona_common_jni.dylib")
        }

        else -> {
            val hostMacosTarget = macosTargets.firstOrNull { it in availableTargets }
            if (hostMacosTarget == null) {
                dependsOn(":commonJni:collectJniFromWorkflowResult")
                rootProject.file("commonJni/build/native/macosUniversal2/libkona_common_jni.dylib")
            } else {
                dependsOn(":commonJni:buildCommonJni${hostMacosTarget.buildName.replaceFirstChar { it.uppercase() }}")
                rootProject.file(
                    "commonJni/build/native/${hostMacosTarget.buildName}/${hostMacosTarget.libraryName}",
                )
            }
        }
    }
    from(macosLibrary) {
        into("jp/juggler/konaArchive/native/macos-universal")
    }
}
