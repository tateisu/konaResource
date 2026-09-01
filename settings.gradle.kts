pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    includeBuild("build-logic")
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
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

rootProject.name = "konaResource"

include(
    ":blake3Jni",
    ":common",
    ":utils",
    ":test",
    ":test-ksp",
    ":plugin",
    // 公開済みプラグイン(0.1.4)が config cache 非互換のため一時的に除外。
    // プラグインを再公開したら再び有効化する。
    // ":sample1",
    ":sample2",
    ":empty",
    ":cli",
    ":benchmark",
)
