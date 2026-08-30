plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
}

group = "jp.juggler.konaResource"
version = "0.1.2"

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
    implementation("jp.juggler.konaResource:common:0.1.2")
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
