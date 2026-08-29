pluginManagement {
    if (gradle.startParameter.projectProperties["useLocalArtifacts"]?.toBoolean() == true) {
        includeBuild("plugin-build")
    } else {
        repositories {
            maven {
                url = uri("https://jitpack.io")
                content {
                    includeGroup("com.github.tateisu.konaResource")
                }
            }
            gradlePluginPortal()
            mavenCentral()
        }
        resolutionStrategy {
            eachPlugin {
                if (requested.id.id == "jp.juggler.konaResource") {
                    useModule("com.github.tateisu.konaResource:plugin:${requested.version}")
                }
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        if (gradle.startParameter.projectProperties["useLocalArtifacts"]?.toBoolean() != true) {
            maven {
                url = uri("https://jitpack.io")
                content {
                    includeGroup("com.github.tateisu.konaResource")
                }
            }
        }
        mavenCentral()
    }
}

rootProject.name = "konaResource"
include(":common", ":plugin", ":cli", ":sample1")
