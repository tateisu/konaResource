plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    linuxX64 {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }
}
