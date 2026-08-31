import org.gradle.api.file.DuplicatesStrategy

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.shadow)
    application
}

dependencies {
    implementation(project(":common"))
    implementation(libs.kotlinxCli)
    implementation(libs.okio)
}

application {
    mainClass = "jp.juggler.konaArchive.cli.MainKt"
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

tasks.register<Copy>("deploy") {
    dependsOn(tasks.named("shadowJar"))
    from(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar"))
    into(rootProject.layout.projectDirectory)
    rename { "konaArchive.jar" }
    doLast {
        val launcher = rootProject.file("konaArchive")
        launcher.writeText(
            """#!/bin/sh
            exec java -jar "$(dirname "$0")/konaArchive.jar" "$@"
            """.trimIndent() + "\n",
        )
        launcher.setExecutable(true)
    }
}
