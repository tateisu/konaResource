import jp.juggler.konaResource.buildlogic.konaTargets
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("jp.juggler.konaResource.buildlogic")
}

group = "jp.juggler.konaResource"
version = rootProject.version

kotlin {
    konaTargets()
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
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
