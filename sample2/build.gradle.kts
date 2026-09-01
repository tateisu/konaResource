plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // Use the locally published plugin implementation
    id("jp.juggler.konaResource.local") version "latest"
}

konaResource {
    modules.add("res" to "src/res")
    modules.add("res2" to "src/res2")
}

kotlin {
    linuxX64 {
        binaries {
            executable {
                entryPoint = "jp.juggler.konaResource.sample.main"
            }
        }
    }
    linuxArm64 {
        binaries {
            executable {
                entryPoint = "jp.juggler.konaResource.sample.main"
            }
        }
    }
    sourceSets {
        linuxX64Main.dependencies {
            implementation(project(":common"))
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

        else -> error("unexpected arch. ")
    }
}


tasks.register("runDebug") {
    group = "run"
    description = "Runs sample2 for the host architecture."
    dependsOn("runDebugExecutable${hostArch}")
}
