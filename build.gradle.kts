import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.detekt) apply false
}

group = "jp.juggler.konaResource"
version = "0.1.3"

val detektFormatting = libs.detektFormatting

subprojects {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    dependencies {
        add("detektPlugins", detektFormatting)
    }
    extensions.configure<DetektExtension> {
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
    }
    tasks.withType<Detekt>().configureEach {
        setSource(project.fileTree("src") {
            include("**/*.kt")
        })
    }

    plugins.withId("maven-publish") {
        val publishing = extensions.getByType<PublishingExtension>()
        publishing.publications.withType<MavenPublication>().configureEach {
            pom {
                name.set("Kona Resource")
                description.set("Embeds resource archives in Linux/x64 Kotlin/Native executables")
                url.set("https://github.com/tateisu/konaResource")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("tateisu")
                        name.set("tateisu")
                        url.set("https://github.com/tateisu")
                    }
                }
                scm {
                    url.set("https://github.com/tateisu/konaResource")
                    connection.set("scm:git:git://github.com/tateisu/konaResource.git")
                    developerConnection.set("scm:git:ssh://git@github.com/tateisu/konaResource.git")
                }
            }
        }

        pluginManager.apply("signing")
        extensions.configure<SigningExtension> {
            val signingKey = providers.gradleProperty("signingKey")
            val signingPassword = providers.gradleProperty("signingPassword")
            if (signingKey.isPresent) {
                useInMemoryPgpKeys(signingKey.orNull, signingPassword.orNull)
                sign(publishing.publications)
            }
        }
    }
}
