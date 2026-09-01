import jp.juggler.konaResource.buildlogic.macosEnabled
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

// -Pmacos=true/false の上書きを考慮した macOS ビルドの有効/無効 (build-logic のユーティリティ)。
val enableMacos: Boolean = macosEnabled()

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
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    linuxX64()
    linuxArm64()
    if (enableMacos) {
        macosArm64()
    }
    mingwX64()

    fun KotlinNativeTarget.configureCinterops() {
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
                    definitionFile.set(cinteropDirectory.resolve("blake3.def"))
                    compilerOpts(
                        *nativeCCompilerOptions.toTypedArray(),
                        "-I${cinteropDirectory.absolutePath}",
                    )
                }
                create("sha256Intrinsics") {
                    definitionFile.set(cinteropDirectory.resolve("sha256_intrinsics.def"))
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

    linuxX64 { configureCinterops() }
    linuxArm64 { configureCinterops() }
    if (enableMacos) {
        macosArm64 { configureCinterops() }
    }
    mingwX64 { configureCinterops() }

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
}

tasks.named<ProcessResources>("jvmProcessResources") {
    dependsOn(":blake3Jni:buildBlake3Jni")
    from(rootProject.file("blake3Jni/build/native/linuxX64/libblake3_jni.so")) {
        into("jp/juggler/konaArchive/native/linux-x86_64")
    }
}
