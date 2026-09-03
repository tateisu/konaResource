plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
}

kotlin {
    sourceSets {
        named("main") {
            kotlin.srcDir("src/shared/kotlin")
        }
    }
}

group = "jp.juggler.konaResource"
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.kotlinGradlePluginLib)
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation(libs.kotestAssertions)
    testImplementation(libs.kotestFrameworkEngine)
    testImplementation(libs.kotestRunner)
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("buildLogic") {
            id = "jp.juggler.konaResource.buildlogic"
            implementationClass = "jp.juggler.konaResource.buildlogic.BuildLogicPlugin"
            displayName = "Kona Resource build logic"
            description = "Provides utility functions for build scripts"
        }
    }
}
