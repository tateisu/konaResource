plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("jp.juggler.konaResource") version "0.1.3"
    // Note: このサンプルでは `-Psample1Artifact=0.1.3` のような指定を反映するため
    // settings.gradle.kts で特殊なことをしています
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
            // `sample1`が使う plugin モジュールの切り替え
            //  Gradle に `-Psample1Artifact=0.1.3` のように指定すると挙動が変わる
            val publishedArtifact = providers.gradleProperty("sample1Artifact")
            when {
                // 公開アーティファクトを使う
                publishedArtifact.isPresent -> {
                    implementation("jp.juggler.konaResource:common:${publishedArtifact.get().trim()}")
                }

                // 兄弟モジュールを使う
                else -> implementation(project(":common"))
            }
        }
    }
}

tasks.register("run") {
    description = "Runs the debug Kotlin/Native executable."
    dependsOn("runDebugExecutableLinuxX64")
}
