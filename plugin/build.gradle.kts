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
        create("konaResourceLocal") {
            id = "jp.juggler.konaResource.local"
            implementationClass = "jp.juggler.konaResource.plugin.KonaResourcePlugin"
            displayName = "Kona Resource (local)"
            description = "Local sibling-project variant of the Kona Resource plugin"
        }
    }
}

// The local marker is only for localMaven and must not be sent to Maven Central.
tasks.matching {
    it.name == "publishKonaResourceLocalPluginMarkerMavenPublicationToNmcpRepository"
}.configureEach {
    enabled = false
}
