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
        substitute(module("jp.juggler.konaResource:common")).using(project(":"))
    }
}
