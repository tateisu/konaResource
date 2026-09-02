package jp.juggler.konaResource.buildlogic

import org.gradle.api.Project
import java.io.File

/**
 * JNIのビルドターゲット
 */

enum class JniBuildTarget(
    val compiler: String,
    val arch: String = "",
    val libraryName: String,
    val jniPlatformInclude: String,
    private val isMacos: Boolean = false,
) {
    LinuxX64("cc", libraryName = "libblake3_jni.so", jniPlatformInclude = "linux"),
    LinuxArm64("aarch64-linux-gnu-gcc", libraryName = "libblake3_jni.so", jniPlatformInclude = "linux"),
    WindowsX64("x86_64-w64-mingw32-gcc", libraryName = "blake3_jni.dll", jniPlatformInclude = "win32"),
    WindowsArm64("aarch64-w64-mingw32-gcc", libraryName = "blake3_jni.dll", jniPlatformInclude = "win32"),
    MacosX64(
        compiler = "cc",
        arch = "x86_64",
        libraryName = "libblake3_jni.dylib",
        jniPlatformInclude = "darwin",
        isMacos = true,
    ),
    MacosArm64(
        compiler = "cc",
        arch = "arm64",
        libraryName = "libblake3_jni.dylib",
        jniPlatformInclude = "darwin",
        isMacos = true,
    ),

    ;

    val buildName: String = name.replaceFirstChar { it.lowercase() }

    /**
     * PATH 上にクロスコンパイラが存在するか確認する。
     */
    private fun isCompilerAvailable(compiler: String): Boolean {
        val pathEntries = System.getenv("PATH")?.split(File.pathSeparator) ?: return false
        val compilerNames = when {
            getKonaBuildHost().isWindows -> listOf(compiler, "$compiler.exe")
            else -> listOf(compiler)
        }
        return pathEntries.any { dir -> compilerNames.any { File(dir, it).canExecute() } }
    }

    /**
     * Windows x64ホストでは、ホストのMinGW compilerでWindows x64向けJNIをビルドする。
     * Linuxなどからのクロスビルドでは、ターゲット用compilerを使う。
     */
    fun compilerForHost(): String = when {
        getKonaBuildHost() == KonaBuildHost.WindowsX64 && this == WindowsX64 -> "gcc"
        else -> compiler
    }

    /**
     * このホストでJNIをビルドできるなら真
     */
    internal fun isAvailable(rootProjectDirectory: File): Boolean =
        (!isMacos || macosBuildAvailable()) &&
            isCompilerAvailable(compilerForHost()) &&
            File(javaHome(rootProjectDirectory), "include/jni.h").isFile

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
            WindowsX64 -> host == KonaBuildHost.WindowsX64
            WindowsArm64 -> false
            MacosX64 -> host == KonaBuildHost.MacosX64
            MacosArm64 -> host == KonaBuildHost.MacosArm64
        }
    }
}

private var cacheAvailableList: List<JniBuildTarget>? = null

/**
 * 現在のビルド環境でビルドできる JniBuildTarget のリストを返す
 * commonJni や test から使われるはず
 * - macosEnabled は自動判定する。availableJniBuildTargets に 引数を追加するな
 */
@Suppress("unused")
fun Project.availableJniBuildTargets(): List<JniBuildTarget> =
    cacheAvailableList ?: run {
        JniBuildTarget.entries.filter {
            it.isAvailable(rootProject.projectDir)
        }
    }.also { cacheAvailableList = it }
