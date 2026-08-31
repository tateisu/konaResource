plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // =======================================
    // --- konaResource plugin ---
    id("jp.juggler.konaResource") version "0.1.4"
    // =======================================
}

// =======================================
// --- konaResource{} block ---
konaResource {
    // --- add pairs of resouce symbol name and resource input directory
    modules.add("res" to "src/res")
    modules.add("res2" to "src/res2")
}
// =======================================

kotlin {
    linuxX64 {
        binaries {
            executable {
                entryPoint = "jp.juggler.konaResource.sample.main"
            }
        }
    }
    sourceSets {
        linuxX64Main.dependencies {
            // =======================================
            // add konaResource:common library
            // =======================================
            implementation("jp.juggler.konaResource:common:0.1.4")
        }
    }
}
