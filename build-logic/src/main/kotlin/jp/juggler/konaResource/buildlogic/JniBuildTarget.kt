package jp.juggler.konaResource.buildlogic

import org.gradle.api.Project
import java.io.File

/**
 * JNIのビルドターゲット
 */

enum class JniBuildTarget(
    val konanTargetName: String,
    val libraryName: String,
    val jniPlatformInclude: String,
) {
    LinuxX64("linux_x64", libraryName = "libkona_common_jni.so", jniPlatformInclude = "linux"),
    LinuxArm64("linux_arm64", libraryName = "libkona_common_jni.so", jniPlatformInclude = "linux"),
    MingwX64("mingw_x64", libraryName = "kona_common_jni.dll", jniPlatformInclude = "win32"),
    MacosX64(
        konanTargetName = "macos_x64",
        libraryName = "libkona_common_jni.dylib",
        jniPlatformInclude = "darwin",
    ),
    MacosArm64(
        konanTargetName = "macos_arm64",
        libraryName = "libkona_common_jni.dylib",
        jniPlatformInclude = "darwin",
    ),

    ;

    val buildName: String = name.replaceFirstChar { it.lowercase() }

    /**
     * このホストでJNIをビルドできるなら真
     */
    internal fun isAvailable(project: Project, kotlinVersion: String): Boolean =
        konanTargetName in project.availableKotlinNativeTargets(kotlinVersion) &&
            File(javaHome(project.rootProject.projectDir), "include/jni.h").isFile

    /**
     * ルートプロジェクトに配置したターゲット固有の JDK のパスを返す。
     * 対象JDKがなく、ターゲットがホストと同じアーキテクチャならホストJDKを使う。
     */
    fun javaHome(rootProjectDirectory: File): String {
        val targetJavaHome = File(rootProjectDirectory, "jdk/$name")
        if (File(targetJavaHome, "include/jni.h").isFile) return targetJavaHome.absolutePath
        if (isHostTarget()) return System.getProperty("java.home")
        return targetJavaHome.absolutePath
    }

    /**
     * enum要素が現在のホストと同一アーキなら真
     */
    private fun isHostTarget(): Boolean {
        val host = getKonaBuildHost()
        return when (this) {
            LinuxX64 -> host == KonaBuildHost.LinuxX64
            LinuxArm64 -> false
            MingwX64 -> host == KonaBuildHost.WindowsX64
            MacosX64 -> host == KonaBuildHost.MacosX64
            MacosArm64 -> host == KonaBuildHost.MacosArm64
        }
    }
}

private var cacheAvailableList: List<JniBuildTarget>? = null

/**
 * 現在のビルド環境でビルドできる JniBuildTarget のリストを返す
 * commonJni や test から使われるはず
 */
@Suppress("unused")
fun Project.availableJniBuildTargets(kotlinVersion: String): List<JniBuildTarget> =
    cacheAvailableList ?: run {
        JniBuildTarget.entries.filter {
            it.isAvailable(this, kotlinVersion)
        }
    }.also { cacheAvailableList = it }

/**
 * Returns a host/target-specific JNI build property.
 *
 * The property name is `${host}_${target}_${suffix}`, for example
 * `LinuxX64_MingwX64_compileOpt`.
 */
fun Project.jniBuildProperty(target: JniBuildTarget, suffix: String): String? {
    val propertyName = "${getKonaBuildHost().name}_${target.name}_$suffix"
    return providers.gradleProperty(propertyName).orNull
        ?: findProperty(propertyName)?.toString()
}

/**
 * Returns comma-separated host/target-specific options, or [default] when not overridden.
 */
fun Project.jniBuildOptions(
    target: JniBuildTarget,
    suffix: String,
    default: List<String>,
): List<String> = jniBuildProperty(target, suffix)
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?: default
