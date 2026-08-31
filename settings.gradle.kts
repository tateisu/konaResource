pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    includeBuild("plugin-build")
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
    ":common",
    ":plugin",
    ":sample1",
    ":sample2",
    ":empty",
    ":cli",
    ":benchmark",
)
