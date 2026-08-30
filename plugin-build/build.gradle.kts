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
    implementation("jp.juggler.konaResource:common:${rootProject.version}")
    compileOnly(libs.kotlinGradlePluginLib)
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        create("konaResource") {
            id = "jp.juggler.konaResource"
            implementationClass = "jp.juggler.konaResource.plugin.KonaResourcePlugin"
            displayName = "konaResource"
            description = "Embeds resource archives in Linux/x64 Kotlin/Native executables"
        }
    }
}
