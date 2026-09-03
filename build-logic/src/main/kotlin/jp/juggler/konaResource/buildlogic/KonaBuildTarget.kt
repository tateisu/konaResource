package jp.juggler.konaResource.buildlogic

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

/**
 * Kotlin/Nativeのビルドターゲット
 */
enum class KonaBuildTarget(
    val targetName: String,
    val konanTargetName: String,
) {
    LinuxX64("linuxX64", "linux_x64"),
    LinuxArm64("linuxArm64", "linux_arm64"),
    MingwX64("mingwX64", "mingw_x64"),
    MacosArm64("macosArm64", "macos_arm64"),

    // Note:MacOS X64 は Kotlin Native 2.3.20以降でdeprecated
    ;

    internal fun isAvailable(project: Project, kotlinVersion: String): Boolean =
        konanTargetName in project.availableKotlinNativeTargets(kotlinVersion)
}

private var cacheAvailableTargets :List<KonaBuildTarget>? = null


/**
 * 現在のビルド環境で利用可能なKotlin/Nativeターゲットを返す。
 */
@Suppress("unused")
fun Project.availableKonaBuildTarget(): List<KonaBuildTarget> =
    cacheAvailableTargets?:run{
        KonaBuildTarget.entries.filter { it.isAvailable(this, getKotlinPluginVersion()) }
    }.also{ cacheAvailableTargets = it}

/**
 * 利用可能なKotlin/NativeターゲットをKotlin Multiplatformへ登録する。
 */
fun KotlinMultiplatformExtension.konaTargets() {
    val targets = project.availableKonaBuildTarget().ifEmpty {
        if (project.providers.gradleProperty("bootstrapKotlinNative").isPresent) {
            KonaBuildTarget.entries
        } else {
            emptyList()
        }
    }
    targets.forEach { target ->
        when (target) {
            KonaBuildTarget.LinuxX64 -> linuxX64()
            KonaBuildTarget.LinuxArm64 -> linuxArm64()
            KonaBuildTarget.MingwX64 -> mingwX64()
            KonaBuildTarget.MacosArm64 -> macosArm64()
        }
    }
}
