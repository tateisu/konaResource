plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "jp.juggler.konaResource"
version = rootProject.version

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.11")
}
