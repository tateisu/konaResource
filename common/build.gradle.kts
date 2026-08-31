import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
    `maven-publish`
}

group = "jp.juggler.konaResource"
version = rootProject.version

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

val blake3SourceFiles = listOf(
    "blake3.c",
    "blake3_dispatch.c",
    "blake3_portable.c",
    "blake3_sse2_x86-64_unix.S",
    "blake3_sse41_x86-64_unix.S",
    "blake3_avx2_x86-64_unix.S",
    "blake3_avx512_x86-64_unix.S",
).map { file("src/linuxX64Main/cinterop/$it") }

val blake3BuildDirectory = layout.buildDirectory.dir("blake3")
val blake3Archive = blake3BuildDirectory.map { it.file("libblake3.a") }
val buildBlake3 = tasks.register("buildBlake3") {
    inputs.files(
        blake3SourceFiles +
            file("src/linuxX64Main/cinterop/blake3.h") +
            file("src/linuxX64Main/cinterop/blake3_impl.h"),
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
                "-O3",
                "-fPIC",
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
                        compilerOpts("-I${file("src/linuxX64Main/cinterop").absolutePath}")
                    }
                    create("opensslSha256") {
                        definitionFile.set(file("src/linuxX64Main/cinterop/openssl_sha256.def"))
                        compilerOpts(
                            "-I${file("src/linuxX64Main/cinterop").absolutePath}",
                            "-I/usr/include",
                            "-I/usr/include/x86_64-linux-gnu",
                        )
                    }
                    create("blake3") {
                        definitionFile.set(file("src/linuxX64Main/cinterop/blake3.def"))
                        compilerOpts("-I${file("src/linuxX64Main/cinterop").absolutePath}")
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

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
