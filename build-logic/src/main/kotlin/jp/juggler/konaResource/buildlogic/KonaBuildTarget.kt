package jp.juggler.konaResource.buildlogic

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Kotlin/Nativeのビルドターゲット
 */
enum class KonaBuildTarget(
    val targetName: String,
    private val isMacos: Boolean = false,
) {
    LinuxX64("linuxX64"),
    LinuxArm64("linuxArm64"),
    MingwX64("mingwX64"),
    MacosArm64("macosArm64", isMacos = true),

    // Note:MacOS X64 は Kotlin Native ではdeprecated
    ;

    internal fun isAvailable(): Boolean = !isMacos || macosBuildAvailable()
}

private var cacheAvailableTargets :List<KonaBuildTarget>? = null


/**
 * 現在のビルド環境で利用可能なKotlin/Nativeターゲットを返す。
 */
@Suppress("unused")
fun availableKonaBuildTarget(): List<KonaBuildTarget> =
    cacheAvailableTargets?:run{
        KonaBuildTarget.entries.filter { it.isAvailable() }
    }.also{ cacheAvailableTargets = it}

/**
 * 利用可能なKotlin/NativeターゲットをKotlin Multiplatformへ登録する。
 */
fun KotlinMultiplatformExtension.konaTargets() {
    availableKonaBuildTarget().forEach { target ->
        when (target) {
            KonaBuildTarget.LinuxX64 -> linuxX64()
            KonaBuildTarget.LinuxArm64 -> linuxArm64()
            KonaBuildTarget.MingwX64 -> mingwX64()
            KonaBuildTarget.MacosArm64 -> macosArm64()
        }
    }
}
