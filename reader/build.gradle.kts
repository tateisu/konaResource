import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
}

kotlin {
    jvm()
    linuxX64()
    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
        }
        jvmTest.dependencies {
            implementation(libs.kotestFrameworkEngine)
            implementation(libs.kotestRunner)
            implementation(libs.kotestAssertions)
            implementation(libs.kotlinxCoroutinesCore)
        }
        linuxX64Test.dependencies {
            implementation(libs.kotestFrameworkEngine)
            implementation(libs.kotestAssertions)
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
