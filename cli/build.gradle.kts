plugins {
    alias(libs.plugins.kotlinJvm)
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
