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
    configurations {
        register("sha256Smoke") {
            include("KonaSha256ImplementationsBenchmarkLinux")
            // warmup iteration の回数
            // 1以上でないとiterationが実行されない
            warmups = 1
            // measurement 時間 ≈ iterations(回数) × (iterationTime*iterationTimeUnit)(時間)
            iterations = 1
            iterationTime = 500
            iterationTimeUnit = "ms"
        }
    }
}
