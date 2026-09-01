import jp.juggler.konaResource.buildlogic.macosEnabled
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("jp.juggler.konaResource.buildlogic")
}

group = "jp.juggler.konaResource"
version = rootProject.version

// -Pmacos=true/false の上書きを考慮した macOS ビルドの有効/無効 (build-logic のユーティリティ)。
val enableMacos: Boolean = macosEnabled()

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

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinxAtomicfu)
            implementation(libs.kotlinxBenchmarkRuntime)
            implementation(libs.kotlinxCoroutinesCore)
            implementation(libs.kotlinxDatetime)
            implementation(libs.okio)
        }
    }
}
