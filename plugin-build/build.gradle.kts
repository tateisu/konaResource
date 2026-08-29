plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
    `maven-publish`
}

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
    implementation("dev.kona.resource:common:0.1.1")
    implementation(libs.kotlinxCoroutinesCore)
    compileOnly(libs.kotlinGradlePluginLib)
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        create("konaResource") {
            id = "jp.juggler.konaResource"
            implementationClass = "jp.juggler.konaResource.plugin.KonaResourcePlugin"
            displayName = "Kona Resource"
            description = "Embeds resource archives in Linux/x64 Kotlin/Native executables"
        }
    }
}
