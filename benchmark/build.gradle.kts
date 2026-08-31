import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxBenchmark)
}

tasks.configureEach {
    if (name == "jvmBenchmark" || name == "jvmSmokeBenchmark") {
        dependsOn(":blake3Jni:buildBlake3Jni")
        doFirst {
            if (this is JavaExec) {
                systemProperty(
                    "kona.blake3.jni.path",
                    rootProject.file("blake3Jni/build/native/libblake3_jni.so").absolutePath,
                )
            }
        }
    }
}

group = "jp.juggler.konaResource"
version = rootProject.version

val skipNativeTargets = providers.environmentVariable("SKIP_NATIVE_TARGETS").forUseAtConfigurationTime().orElse("false").get().toBoolean()

kotlin {
    jvm()
    if (!skipNativeTargets) {
        linuxX64()
        linuxArm64()
    }

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
        named("main") {
            warmups = 3
            iterations = 3
            iterationTime = 500
            iterationTimeUnit = "ms"
        }
        register("smoke") {
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
