pluginManagement {
    if (gradle.startParameter.projectProperties["useLocalArtifacts"]?.toBoolean() != false) {
        includeBuild("plugin-build")
    } else {
        repositories {
            gradlePluginPortal()
            mavenCentral()
        }
        resolutionStrategy {
            eachPlugin {
                if (requested.id.id == "jp.juggler.konaResource") {
                    useModule("jp.juggler.konaResource:plugin:${requested.version}")
                }
            }
        }
    }
}

plugins {
    id("com.gradleup.nmcp.settings") version "1.6.1"
}

nmcpSettings {
    centralPortal {
        username = providers.gradleProperty("centralPortalUsername").getOrElse("")
        password = providers.gradleProperty("centralPortalPassword").getOrElse("")
        publishingType = "USER_MANAGED"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "konaResource"
include(":common", ":plugin", ":cli", ":sample1")
