import jp.juggler.konaResource.buildlogic.konaTargets
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("jp.juggler.konaResource.buildlogic")
}

kotlin {
    konaTargets()
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.executable {
            entryPoint = "main"
        }
    }
}
