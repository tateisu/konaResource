import jp.juggler.konaResource.buildlogic.JniBuildTarget
import jp.juggler.konaResource.buildlogic.availableJniBuildTargets
import jp.juggler.konaResource.buildlogic.konaTargets
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
}

tasks.named<ProcessResources>("jvmProcessResources") {
    val availableTargets = project.availableJniBuildTargets()
    availableTargets.forEach { target ->
        when (target) {
            JniBuildTarget.LinuxX64 -> {
                dependsOn(":commonJni:buildBlake3JniLinuxX64")
                from(rootProject.file("commonJni/build/native/linuxX64/libblake3_jni.so")) {
                    into("jp/juggler/konaArchive/native/linux-x86_64")
                }
            }

            JniBuildTarget.LinuxArm64 -> {
                dependsOn(":commonJni:buildBlake3JniLinuxArm64")
                from(rootProject.file("commonJni/build/native/linuxArm64/libblake3_jni.so")) {
                    into("jp/juggler/konaArchive/native/linux-aarch64")
                }
            }

            JniBuildTarget.WindowsX64 -> {
                dependsOn(":commonJni:buildBlake3JniWindowsX64")
                from(rootProject.file("commonJni/build/native/windowsX64/blake3_jni.dll")) {
                    into("jp/juggler/konaArchive/native/windows-x86_64")
                }
            }

            JniBuildTarget.WindowsArm64 -> {
                dependsOn(":commonJni:buildBlake3JniWindowsArm64")
                from(rootProject.file("commonJni/build/native/windowsArm64/blake3_jni.dll")) {
                    into("jp/juggler/konaArchive/native/windows-aarch64")
                }
            }

            JniBuildTarget.MacosX64,
            JniBuildTarget.MacosArm64,
            -> Unit
        }
    }

    if (JniBuildTarget.MacosX64 in availableTargets &&
        JniBuildTarget.MacosArm64 in availableTargets
    ) {
        dependsOn(":commonJni:buildBlake3JniMacosUniversal2")
        from(rootProject.file("commonJni/build/native/macosUniversal2/libblake3_jni.dylib")) {
            into("jp/juggler/konaArchive/native/macos-universal")
        }
    }
}
