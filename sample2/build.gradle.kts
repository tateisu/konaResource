import jp.juggler.konaResource.buildlogic.konaTargets
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("jp.juggler.konaResource.buildlogic")
    alias(libs.plugins.kotlinMultiplatform)
    // Use the locally published plugin implementation
    id("jp.juggler.konaResource.local") version "latest"
}

konaResource {
    modules.add("res" to "src/res")
    modules.add("res2" to "src/res2")
}

kotlin {
    konaTargets()
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main").defaultSourceSet.kotlin.srcDir("src/linuxX64Main/kotlin")
        compilations.getByName("main").defaultSourceSet.dependencies {
            implementation(project(":common"))
        }
        binaries {
            executable {
                entryPoint = "jp.juggler.konaResource.sample.main"
            }
        }
    }
}

/**
 * Gradleを実行しているホストのアーキテクチャを返す
 * - sample2 の runDebugExecutable{...} タスクにマッチする名前
 */
val hostArch: String by lazy {
    when {
        System.getProperty("os.name").lowercase().contains("linux") ->
            when (val arch = System.getProperty("os.arch").lowercase()) {
                in setOf("amd64", "x86_64", "x64") -> "LinuxX64"
                in setOf("aarch64", "arm64") -> "LinuxArm64"
                else -> error("host is Linux, but os.arch is unexpected. [$arch]")
            }

        System.getProperty("os.name").lowercase().contains("windows") &&
            System.getProperty("os.arch").lowercase() in setOf("amd64", "x86_64", "x64") -> "MingwX64"

        System.getProperty("os.name").lowercase().contains("mac") &&
            System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64") -> "MacosArm64"

        else -> error("Unsupported host platform. os.name=${System.getProperty("os.name")}, os.arch=${System.getProperty("os.arch")}")
    }
}


tasks.register("runDebug") {
    group = "run"
    description = "Runs sample2 for the host architecture."
    dependsOn("runDebugExecutable${hostArch}")
}
