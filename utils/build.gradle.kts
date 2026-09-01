import jp.juggler.konaResource.buildlogic.konaTargets
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

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
    targets.withType<KotlinNativeTarget>().configureEach {
        if (name != "mingwX64") {
            compilations.getByName("main").defaultSourceSet.kotlin.srcDir("src/posixMain/kotlin")
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(libs.kotlinxAtomicfu)
            implementation(libs.kotlinxCoroutinesCore)
            implementation(libs.kotlinxDatetime)
            implementation(libs.okio)
        }
    }
}
