pluginManagement {
    repositories {
        maven { url = uri("$rootDir/localMaven") }
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
        maven { url = uri("$rootDir/localMaven") }
        mavenCentral()
    }
}

rootProject.name = "konaResource"

include(
    ":commonJni",
    ":common",
    ":utils",
    ":test",
    ":test-ksp",
    ":plugin",
    ":sample1",
    ":empty",
    ":cli",
    ":benchmark",
)

// sample2はlocalMavenに公開したpluginを使うため、localMavenがある場合だけ設定対象にする。
// publish処理中はmarker生成前に設定されるため、さらにsample2を外す。
val localMavenAvailable = file("$rootDir/localMaven").isDirectory
val isLocalPublishing = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':') in setOf("publishPluginLocal", "publishLocalMaven")
}
val isKotlinNativeBootstrap = gradle.startParameter.projectProperties.containsKey("bootstrapKotlinNative")
when {
    !localMavenAvailable || isKotlinNativeBootstrap -> println("[konaResource] Skipping :sample2 during Kotlin/Native bootstrap.")
    isLocalPublishing -> println("[konaResource] Skipping :sample2 while publishing the local plugin.")
    else -> {
        include(":sample2")
    }
}
