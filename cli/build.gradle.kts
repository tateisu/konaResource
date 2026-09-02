plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.shadow)
    application
}

dependencies {
    implementation(project(":utils"))
    implementation(project(":common"))
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
    description = "copy cli fatJar to rootProject/bin"
    dependsOn(tasks.named("shadowJar"))
    from(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar"))
    into(rootProject.layout.projectDirectory.dir("bin"))
    rename { "konaArchive.jar" }
}
