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
    val availableTargets = project.availableJniBuildTargets(libs.versions.kotlin.get())
    listOf(
        JniBuildTarget.LinuxX64 to "linux-x86_64",
        JniBuildTarget.LinuxArm64 to "linux-aarch64",
        JniBuildTarget.MingwX64 to "windows-x86_64",
    ).forEach { (target, resourceDirectory) ->
        if (target in availableTargets) {
            dependsOn(":commonJni:buildBlake3Jni${target.buildName.replaceFirstChar { it.uppercase() }}")
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
            dependsOn(":commonJni:buildBlake3JniMacosUniversal2")
            rootProject.file("commonJni/build/native/macosUniversal2/libkona_common_jni.dylib")
        }

        else -> {
            val hostMacosTarget = macosTargets.firstOrNull { it in availableTargets }
            if (hostMacosTarget == null) {
                dependsOn(":commonJni:collectJniFromWorkflowResult")
                rootProject.file("commonJni/build/native/macosUniversal2/libkona_common_jni.dylib")
            } else {
                dependsOn(":commonJni:buildBlake3Jni${hostMacosTarget.buildName.replaceFirstChar { it.uppercase() }}")
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
