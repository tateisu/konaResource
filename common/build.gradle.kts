import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
    `maven-publish`
}

group = "jp.juggler.konaResource"
version = rootProject.version

// warning, PIC options, optimization for this project.
val nativeCCompilerOptions = listOf("-W", "-Wall", "-O3", "-fPIC")
val cinteropDirectory = file("src/linuxX64Main/cinterop")

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

val blake3SourceDirectory = cinteropDirectory.resolve("blake3")
val blake3SourceFiles = listOf(
    "blake3.c",
    "blake3_dispatch.c",
    "blake3_portable.c",
    "blake3_sse2_x86-64_unix.S",
    "blake3_sse41_x86-64_unix.S",
    "blake3_avx2_x86-64_unix.S",
    "blake3_avx512_x86-64_unix.S",
).map { blake3SourceDirectory.resolve(it) }

val blake3BuildDirectory = layout.buildDirectory.dir("blake3")
val blake3Archive = blake3BuildDirectory.map { it.file("libblake3.a") }
val buildBlake3 = tasks.register("buildBlake3") {
    inputs.files(
        blake3SourceFiles +
            blake3SourceDirectory.resolve("blake3.h") +
            blake3SourceDirectory.resolve("blake3_impl.h"),
    )
    outputs.file(blake3Archive)
    doLast {
        fun run(vararg command: String) {
            check(ProcessBuilder(*command).inheritIO().start().waitFor() == 0) {
                "Command failed: ${command.joinToString(" ")}"
            }
        }

        val outputDirectory = blake3BuildDirectory.get().asFile
        outputDirectory.mkdirs()
        val objects = blake3SourceFiles.map { source ->
            outputDirectory.resolve("${source.name}.o")
        }
        blake3SourceFiles.zip(objects).forEach { (source, objectFile) ->
            run(
                "cc",
                *nativeCCompilerOptions.toTypedArray(),
                "-c",
                source.absolutePath,
                "-I${source.parentFile.absolutePath}",
                "-o",
                objectFile.absolutePath,
            )
        }
        run(
            "ar",
                "rcs",
                blake3Archive.get().asFile.absolutePath,
                *objects.map { it.absolutePath }.toTypedArray(),
        )
    }
}

val sha256IntrinsicsSourceFiles = listOf(
    "SHA-Intrinsics/sha256.c",
    "SHA-Intrinsics/sha256-x86.c",
    "sha256_intrinsics.c",
).map { cinteropDirectory.resolve(it) }
val sha256IntrinsicsBuildDirectory = layout.buildDirectory.dir("sha256Intrinsics")
val sha256IntrinsicsArchive = sha256IntrinsicsBuildDirectory.map { it.file("libsha256intrinsics.a") }
val buildSha256Intrinsics = tasks.register("buildSha256Intrinsics") {
    inputs.files(sha256IntrinsicsSourceFiles, file("src/linuxX64Main/cinterop/sha256_intrinsics.h"))
    outputs.file(sha256IntrinsicsArchive)
    doLast {
        fun run(vararg command: String) {
            check(ProcessBuilder(*command).inheritIO().start().waitFor() == 0) {
                "Command failed: ${command.joinToString(" ")}"
            }
        }

        val outputDirectory = sha256IntrinsicsBuildDirectory.get().asFile
        outputDirectory.mkdirs()
        val objects = sha256IntrinsicsSourceFiles.map { source ->
            outputDirectory.resolve("${source.name}.o")
        }
        sha256IntrinsicsSourceFiles.zip(objects).forEach { (source, objectFile) ->
            val sourceOptions = if (source.name == "sha256-x86.c") {
                nativeCCompilerOptions + listOf("-msse4.1", "-msha")
            } else {
                nativeCCompilerOptions
            }
            run(
                "cc",
                *sourceOptions.toTypedArray(),
                "-c",
                source.absolutePath,
                "-I${cinteropDirectory.absolutePath}",
                "-o",
                objectFile.absolutePath,
            )
        }
        run(
            "ar",
            "rcs",
            sha256IntrinsicsArchive.get().asFile.absolutePath,
            *objects.map { it.absolutePath }.toTypedArray(),
        )
    }
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
    jvm()
    linuxX64()
    sourceSets {
        commonMain.dependencies {
            implementation(libs.okio)
        }
        linuxX64Main.dependencies {
            implementation(libs.lz4Native)
        }
        linuxX64 {
            compilations.getByName("main") {
                cinterops {
                    create("lz4") {
                        definitionFile.set(file("src/linuxX64Main/cinterop/lz4.def"))
                        compilerOpts(
                            *nativeCCompilerOptions.toTypedArray(),
                            "-I${file("src/linuxX64Main/cinterop").absolutePath}",
                        )
                    }
                    create("blake3") {
                        definitionFile.set(file("src/linuxX64Main/cinterop/blake3.def"))
                        compilerOpts(
                            *nativeCCompilerOptions.toTypedArray(),
                            "-I${file("src/linuxX64Main/cinterop").absolutePath}",
                        )
                    }
                    create("sha256Intrinsics") {
                        definitionFile.set(file("src/linuxX64Main/cinterop/sha256_intrinsics.def"))
                        compilerOpts(
                            *nativeCCompilerOptions.toTypedArray(),
                            "-I${file("src/linuxX64Main/cinterop").absolutePath}",
                        )
                    }
                }
            }
        }
        jvmMain.dependencies {
            implementation(libs.commonsCodec)
            implementation(libs.lz4Java)
        }
        commonTest.dependencies {
            implementation(libs.kotestFrameworkEngine)
            implementation(libs.kotestAssertions)
        }
        jvmTest.dependencies {
            implementation(libs.kotestFrameworkEngine)
            implementation(libs.kotestRunner)
            implementation(libs.kotestAssertions)
        }
        linuxX64Test.dependencies {
            implementation(libs.kotestFrameworkEngine)
            implementation(libs.kotestAssertions)
        }
    }
}

tasks.named("cinteropBlake3LinuxX64") {
    dependsOn(buildBlake3)
}

tasks.named("cinteropSha256IntrinsicsLinuxX64") {
    dependsOn(buildSha256Intrinsics)
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
