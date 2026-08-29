pluginManagement {
    includeBuild("plugin-build")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "konaResource"
include(":common", ":plugin", ":reader", ":cli", ":sample1")
