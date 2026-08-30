pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    // `sample1`が使う plugin モジュールの切り替え
    //  Gradle に `-Psample1Artifact=0.1.3` のように指定すると挙動が変わる
    when (val version = gradle.startParameter.projectProperties["sample1Artifact"]?.trim()) {
        // 兄弟モジュールを使う
        null -> includeBuild("plugin-build")
        // 公開アーティファクトの指定バージョンを参照する
        else -> resolutionStrategy {
            eachPlugin {
                if (requested.id.id == "jp.juggler.konaResource") {
                    useModule("jp.juggler.konaResource:plugin:${version.trim()}")
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
        publishingType = "AUTOMATIC"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "konaResource"
include(":common", ":plugin", ":cli", ":sample1")
