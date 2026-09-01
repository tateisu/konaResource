package jp.juggler.konaResource.buildlogic

import org.gradle.api.Project
import java.io.File

/**
 * macOS ビルドが可能か自動判定する。
 * - macOS ホストなら常に可能
 * - それ以外は osxcross が導入済みなら可能
 */
fun macosBuildAvailable(): Boolean {
    val osName = System.getProperty("os.name").lowercase()
    if (osName.contains("mac") || osName.contains("darwin")) return true
    val osxcrossRoot = System.getenv("OSXCROSS_ROOT")
        ?: File(System.getProperty("user.home"), "osxcross").path
    return File(osxcrossRoot, "target").isDirectory
}

/**
 * macOS ビルドの有効/無効。
 * -Pmacos=true/false で明示的に上書きでき、未指定なら [macosBuildAvailable] で自動判定する。
 */
fun Project.macosEnabled(): Boolean {
    val override = findProperty("macos") as? String
    return override?.toBoolean() ?: macosBuildAvailable()
}

/**
 * PATH 上にクロスコンパイラが存在するか確認する。
 */
fun isCompilerAvailable(compiler: String): Boolean {
    val pathEntries = System.getenv("PATH")?.split(File.pathSeparator) ?: return false
    return pathEntries.any { dir -> File(dir, compiler).canExecute() }
}
