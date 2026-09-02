package jp.juggler.konaResource.buildlogic

import java.util.*

/**
 * Kotlin/Native compiler build hosts supported by official.
 * - missing Linux ARM64 or Windows ARM64
 */
enum class KonaBuildHost(
    val targetName: String,
    private val osNames: Set<String>,
    private val architectures: Set<String>,
) {
    MacosArm64(
        targetName = "MacosArm64",
        osNames = setOf("mac", "darwin"),
        architectures = setOf("aarch64", "arm64"),
    ),
    MacosX64(
        targetName = "MacosX64",
        osNames = setOf("mac", "darwin"),
        architectures = setOf("amd64", "x86_64", "x64"),
    ),
    LinuxX64(
        targetName = "LinuxX64",
        osNames = setOf("linux"),
        architectures = setOf("amd64", "x86_64", "x64"),
    ),
    WindowsX64(
        targetName = "WindowsX64",
        osNames = setOf("windows"),
        architectures = setOf("amd64", "x86_64", "x64"),
    ),

    ;

    val isMacos: Boolean
        get() = this == MacosArm64 || this == MacosX64

    val isWindows: Boolean
        get() = this == WindowsX64

    private fun matches(osName: String, architecture: String): Boolean =
        osNames.any(osName::contains) && architecture in architectures

    companion object {
        internal fun from(osName: String, architecture: String): KonaBuildHost? {
            val normalizedOsName = osName.lowercase(Locale.ROOT)
            val normalizedArchitecture = architecture.lowercase(Locale.ROOT)
            return entries.firstOrNull { it.matches(normalizedOsName, normalizedArchitecture) }
        }
    }
}

/**
 * Returns the current Kotlin/Native build host.
 *
 * Kotlin/Native does not support Linux ARM64 or Windows ARM64 as compiler hosts,
 * so an unknown host is rejected during Gradle configuration.
 */
fun getKonaBuildHost(): KonaBuildHost {
    val osName = System.getProperty("os.name").orEmpty()
    val architecture = System.getProperty("os.arch").orEmpty()
    return KonaBuildHost.from(osName, architecture)
        ?: error("Unsupported Kotlin/Native build host. os.name=[$osName], os.arch=[$architecture]")
}
