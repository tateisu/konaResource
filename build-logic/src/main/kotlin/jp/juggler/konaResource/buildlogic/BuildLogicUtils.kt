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
