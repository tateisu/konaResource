plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // Use the plugin implementation from build-logic, not the published plugin.
    id("jp.juggler.konaResource.local") version "0.1.5"
}

konaResource {
    modules.add("res" to "src/res")
    modules.add("res2" to "src/res2")
}

kotlin {
    linuxX64 {
        binaries {
            executable {
                entryPoint = "jp.juggler.konaResource.sample.main"
            }
        }
    }
    linuxArm64 {
        binaries {
            executable {
                entryPoint = "jp.juggler.konaResource.sample.main"
            }
        }
    }
    sourceSets {
        linuxX64Main.dependencies {
            implementation(project(":common"))
        }
    }
}
