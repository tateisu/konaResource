plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("jp.juggler.konaResource") version "0.1.2"
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
            if (providers.gradleProperty("useLocalArtifacts").map(String::toBoolean).getOrElse(true)) {
                implementation(project(":common"))
            } else {
                implementation("jp.juggler.konaResource:common:0.1.2")
            }
        }
    }
}

tasks.register("run") {
    description = "Runs the debug Kotlin/Native executable."
    dependsOn("runDebugExecutableLinuxX64")
}
