import jp.juggler.konaResource.buildlogic.availableKonaBuildTarget
import jp.juggler.konaResource.buildlogic.konaTargets
import jp.juggler.konaResource.buildlogic.getKonaBuildHost
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("jp.juggler.konaResource.buildlogic")
    alias(libs.plugins.kotlinMultiplatform)
    // Use the locally published plugin implementation
    id("jp.juggler.konaResource.local") version "latest"
}

konaResource {
    modules.add("res" to "src/res")
    modules.add("res2" to "src/res2")
}

kotlin {
    konaTargets()
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main").defaultSourceSet.kotlin.srcDir("src/linuxX64Main/kotlin")
        compilations.getByName("main").defaultSourceSet.dependencies {
            implementation(project(":common"))
        }
        binaries {
            executable {
                entryPoint = "jp.juggler.konaResource.sample.main"
            }
        }
    }
}

/**
 * Gradleを実行しているホストのアーキテクチャを返す
 * - sample2 の runDebugExecutable{...} タスクにマッチする名前
 */
val hostRunTask: String by lazy {
    val host = getKonaBuildHost()
    val target = availableKonaBuildTarget().firstOrNull {
        it.targetName.equals(host.targetName, ignoreCase = true)
    } ?: error(
        "Kotlin/Native target for host is unavailable. " +
            "host=${host.targetName}, available=${availableKonaBuildTarget().joinToString { it.targetName }}",
    )
    "runDebugExecutable${target.targetName.replaceFirstChar { it.uppercase() }}"
}


tasks.register("runDebug") {
    group = "run"
    description = "Runs sample2 for the host architecture."
    dependsOn(hostRunTask)
}
