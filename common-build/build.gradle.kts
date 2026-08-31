import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "jp.juggler.konaResource"
version = "0.1.2"

val blake3JniDirectory = file("../blake3Jni")
val blake3JniSourceDirectory = blake3JniDirectory.resolve("src/main/c")
val blake3JniBuildScript = blake3JniDirectory.resolve("build-native.sh")
val blake3JniLibrary = layout.buildDirectory.file("blake3Jni/libblake3_jni.so")
val buildBlake3Jni = tasks.register("buildBlake3Jni") {
    inputs.files(
        blake3JniBuildScript,
        blake3JniSourceDirectory.resolve("blake3_jni.c"),
        fileTree(blake3JniSourceDirectory.resolve("blake3")),
    )
    outputs.file(blake3JniLibrary)
    doLast {
        val command = arrayOf(
            "bash",
            blake3JniBuildScript.absolutePath,
            blake3JniLibrary.get().asFile.absolutePath,
            System.getProperty("java.home"),
        )
        check(ProcessBuilder(*command).inheritIO().start().waitFor() == 0) {
            "Command failed: ${command.joinToString(" ")}"
        }
    }
}

kotlin {
    jvm()
    sourceSets {
        commonMain {
            kotlin.srcDir("../common/src/commonMain/kotlin")
            dependencies {
                implementation(libs.okio)
            }
        }
        jvmMain {
            kotlin.srcDir("../common/src/jvmMain/kotlin")
            dependencies {
                implementation(libs.lz4Java)
            }
        }
    }
}

tasks.named<ProcessResources>("jvmProcessResources") {
    dependsOn(buildBlake3Jni)
    from(blake3JniLibrary) {
        into("jp/juggler/konaArchive/native/linux-x86_64")
    }
}
