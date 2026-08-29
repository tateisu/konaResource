plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "dev.kona.resource"
version = "0.1.1"

kotlin {
    jvm()
    sourceSets {
        commonMain {
            kotlin.srcDir("../common/src/commonMain/kotlin")
            dependencies {
                implementation(libs.okio)
                implementation(libs.kotlinxCoroutinesCore)
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
