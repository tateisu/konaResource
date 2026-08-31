plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxBenchmark)
}

group = "jp.juggler.konaResource"
version = rootProject.version

kotlin {
    jvm()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(libs.kotlinxBenchmarkRuntime)
        }
    }
}

benchmark {
    targets {
        register("jvm")
        register("linuxX64")
    }
}
