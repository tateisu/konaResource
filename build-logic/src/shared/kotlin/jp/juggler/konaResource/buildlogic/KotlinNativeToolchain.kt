package jp.juggler.konaResource.buildlogic

import org.gradle.api.Project
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val nativeToolchains by lazy {
    val dataDirectory = File(
        System.getenv("KONAN_DATA_DIR")
            ?: File(System.getProperty("user.home"), ".konan").absolutePath,
    )
    if (!dataDirectory.isDirectory) {
        error("nativeToolchains is not directory. $dataDirectory")
    }
    dataDirectory.walkTopDown()
        .filter { it.isFile && it.name in setOf("run_konan", "run_konan.bat") }
        .map { it.parentFile.parentFile }
        .filter { it.isDirectory }
        .distinct()
        .toList()
        .takeIf { it.isNotEmpty() }
        ?: error("can't find 'run_konan' in $dataDirectory")
}

private class NativeToolchain(private val home: File) {
    val runKonan: File by lazy { findExecutable("run_konan") }

    val kotlincNative: File by lazy { findExecutable("kotlinc-native") }

    private fun findExecutable(name: String): File = sequenceOf(
        File(home, "bin/$name"),
        File(home, "bin/$name.bat"),
    ).firstOrNull(File::isFile)
        ?: error("Kotlin/Native executable '$name' was not found under $home")
}

private val nativeToolchainCache = ConcurrentHashMap<String, Lazy<NativeToolchain?>>()
private val nativeTargetCache = ConcurrentHashMap<String, Set<String>>()

private fun nativeToolchain(kotlinVersion: String): NativeToolchain? =
    nativeToolchainCache
        .computeIfAbsent(kotlinVersion) {
            lazy {
                nativeToolchains
                    .firstOrNull { it.name.endsWith("-$kotlinVersion") }
                    ?.let(::NativeToolchain)
            }
        }
        .value

/**
 * Builds a command line for Kotlin/Native's [run_konan] wrapper.
 */
fun runKonan(
    kotlinVersion: String,
    mode: String,
    tool: String,
    target: String? = null,
): List<String> {
    val command = nativeToolchain(kotlinVersion)?.runKonan
        ?: error("run_konan for Kotlin $kotlinVersion was not found under KONAN_DATA_DIR")
    return buildList {
        if (command.extension == "bat") {
            add("cmd.exe")
            add("/c")
        }
        add(command.absolutePath)
        add(mode)
        add(tool)
        target?.let(::add)
    }
}

/**
 * Returns the Kotlin/Native targets supported by the distribution for [kotlinVersion].
 */
internal fun Project.availableKotlinNativeTargets(kotlinVersion: String): Set<String> =
    nativeTargetCache[kotlinVersion] ?: run {
        val kotlincNative = nativeToolchain(kotlinVersion)?.kotlincNative ?: return emptySet()
        val output = providers.exec {
            it.commandLine(kotlincNative.absolutePath, "-list-targets")
        }.standardOutput.asText.get()
        output.lineSequence()
            .map { it.substringBefore(' ').trim() }
            .filter { it.isNotEmpty() }
            .toSet()
            .also { nativeTargetCache[kotlinVersion] = it }
    }
