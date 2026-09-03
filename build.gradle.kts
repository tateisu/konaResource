import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.detekt) apply false
}

group = "jp.juggler.konaResource"
version = "0.1.6"

val detektFormatting = libs.detektFormatting
val localMavenDirectory = layout.projectDirectory.dir("localMaven")
val publishLocal = gradle.startParameter.taskNames.any { taskName ->
    taskName.substringAfterLast(':') in setOf("publishPluginLocal", "publishLocalMaven")
}
val localVersion = "latest"

if (publishLocal) {
    gradle.afterProject {
        if (path == ":common" || path == ":plugin") version = localVersion
    }
}

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
        source = project.fileTree("src") {
            include("**/*.kt")
        }
    }

    plugins.withId("maven-publish") {
        val publishing = extensions.getByType<PublishingExtension>()
        publishing.repositories {
            maven {
                name = "localMaven"
                url = localMavenDirectory.asFile.toURI()
            }
        }
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

val publishLocalMaven = tasks.register("publishLocalMaven") {
    group = "publishing"
    description = "Publishes `plugin` and `common` into localMaven."
    dependsOn(
        // ---------------------------------
        // common (plugin用)
        // KMPのルートartifact + JVM部分
        // タスク名は自動生成の長いやつだ…

        // publish KMPのルートartifact
        ":common:publishKotlinMultiplatformPublicationToLocalMavenRepository",

        // publish local artifact JVM
        ":common:publishJvmPublicationToLocalMavenRepository",

        // ---------------------------------
        // plugin
        // pluigはKMPではなくJVMオンリーなので、artifactとmarkerをpushlishする
        // タスク名は自動生成の長いやつだ…

        // publish artifact
        ":plugin:publishPluginMavenPublicationToLocalMavenRepository",

        // public marker
        ":plugin:publishKonaResourceLocalPluginMarkerMavenPublicationToLocalMavenRepository",
    )
}

// Replace host-limited common Native publications with the publications
// collected from every Kotlin/Native build host before Nmcp stages them.
val workflowCommonMaven = layout.projectDirectory.dir("workflowResult/MacosArm64/maven")
val installWorkflowCommonNativePublications = tasks.register<Copy>("installWorkflowCommonNativePublications") {
    enabled = workflowCommonMaven.asFile.isDirectory
    dependsOn(":common:publishAllPublicationsToNmcpRepository")
    from(workflowCommonMaven)
    into(layout.projectDirectory.dir("common/build/nmcp/m2"))
}

tasks.named("publishAggregationToCentralPortal") {
    dependsOn(installWorkflowCommonNativePublications)
}
