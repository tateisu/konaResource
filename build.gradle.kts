import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.PublishingExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotest) apply false
}

group = "dev.kona.resource"
version = "0.1.0"

subprojects {
    plugins.withId("maven-publish") {
        val githubPackagesUrl = providers.gradleProperty("gpr.url")
            .orElse("https://maven.pkg.github.com/tateisu/konaResource")
        val githubUsername = providers.gradleProperty("gpr.user")
            .orElse(providers.environmentVariable("GITHUB_ACTOR"))
        val githubToken = providers.gradleProperty("gpr.key")
            .orElse(providers.environmentVariable("GITHUB_TOKEN"))

        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri(githubPackagesUrl.get())
                    credentials(PasswordCredentials::class) {
                        username = githubUsername.getOrElse("")
                        password = githubToken.getOrElse("")
                    }
                }
            }
        }
    }
}
