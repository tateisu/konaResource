plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
    `maven-publish`
}

group = "jp.juggler.konaResource"
version = "0.1.1"

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(project(":common"))
    implementation(libs.kotlinxCoroutinesCore)
    compileOnly(libs.kotlinGradlePluginLib)
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnit()
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
