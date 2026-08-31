pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "konaResource-plugin-included"
includeBuild("../common-build") {
    dependencySubstitution {
        // Keep the included JVM helper isolated from the published Native common module.
        substitute(module("jp.juggler.konaResource:common-local")).using(project(":"))
    }
}
