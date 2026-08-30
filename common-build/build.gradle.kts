plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "jp.juggler.konaResource"
version = "0.1.2"

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
