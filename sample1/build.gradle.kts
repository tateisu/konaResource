plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("jp.juggler.konaResource")
}

konaResource {
    modules.add("sample" to "src/sample")
    modules.add("sampleB" to "src/sampleB")
}

kotlin {
    linuxX64 {
        binaries {
            executable {
                entryPoint = "jp.juggler.konaResource.sample1.main"
            }
        }
    }
    sourceSets {
        linuxX64Main.dependencies {
            implementation(project(":reader"))
        }
    }
}

tasks.register("run") {
    description = "Runs the debug Kotlin/Native executable."
    dependsOn("runDebugExecutableLinuxX64")
}
