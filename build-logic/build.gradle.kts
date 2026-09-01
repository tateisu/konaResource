plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
}

group = "jp.juggler.konaResource"
version = rootProject.version

repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir("../plugin/src/main/kotlin")
        }
    }
}

dependencies {
    implementation("jp.juggler.konaResource:common-local")
    compileOnly(libs.kotlinGradlePluginLib)
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        create("konaResourceLocal") {
            id = "jp.juggler.konaResource.local"
            implementationClass = "jp.juggler.konaResource.plugin.KonaResourcePlugin"
            displayName = "konaResource"
            description = "Embeds resource archives in Linux/x64 Kotlin/Native executables"
        }
        create("buildLogic") {
            id = "jp.juggler.konaResource.buildlogic"
            implementationClass = "jp.juggler.konaResource.buildlogic.BuildLogicPlugin"
            displayName = "Kona Resource build logic"
            description = "Provides utility functions for build scripts"
        }
    }
}
