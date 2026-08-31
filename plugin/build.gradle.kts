plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotest)
    `java-gradle-plugin`
    `maven-publish`
}

group = "jp.juggler.konaResource"
version = rootProject.version

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(project(":common"))
    compileOnly(libs.kotlinGradlePluginLib)
    testImplementation(gradleTestKit())
    testImplementation(libs.kotestFrameworkEngine)
    testImplementation(libs.kotestAssertions)
    testImplementation(libs.kotestRunner)
}

tasks.test {
    useJUnitPlatform()
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
