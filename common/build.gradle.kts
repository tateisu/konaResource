plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
    `maven-publish`
}

group = "dev.kona.resource"
version = "0.1.0"

kotlin {
    jvm()
    linuxX64()
    sourceSets {
        commonMain.dependencies {
            implementation(libs.okio)
            implementation(libs.kotlinxCoroutinesCore)
        }
        linuxX64Main.dependencies {
            implementation(libs.lz4Native)
        }
        linuxX64 {
            compilations.getByName("main") {
                cinterops {
                    create("lz4") {
                        definitionFile.set(file("src/linuxX64Main/cinterop/lz4.def"))
                        compilerOpts("-I${file("src/linuxX64Main/cinterop").absolutePath}")
                    }
                    create("opensslSha256") {
                        definitionFile.set(file("src/linuxX64Main/cinterop/openssl_sha256.def"))
                        compilerOpts(
                            "-I${file("src/linuxX64Main/cinterop").absolutePath}",
                            "-I/usr/include",
                            "-I/usr/include/x86_64-linux-gnu",
                        )
                    }
                }
            }
        }
        jvmMain.dependencies {
            implementation(libs.lz4Java)
        }
        commonTest.dependencies {
            implementation(libs.kotestFrameworkEngine)
            implementation(libs.kotestAssertions)
        }
        jvmTest.dependencies {
            implementation(libs.kotestFrameworkEngine)
            implementation(libs.kotestRunner)
            implementation(libs.kotestAssertions)
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
