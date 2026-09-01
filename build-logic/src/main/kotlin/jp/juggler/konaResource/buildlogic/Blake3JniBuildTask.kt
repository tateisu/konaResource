package jp.juggler.konaResource.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * blake3Jni の共有ライブラリをビルドするタスク。
 * [Blake3JniBuildUnit] が1つなら単一アーキテクチャ向けに直接リンクし、
 * 複数なら各アーキテクチャの中間ライブラリを lipo で統合する(macOS universal2)。
 *
 * config cache 対応のため、スクリプト定義クラスやクロージャは捕捉しない。
 */
data class Blake3JniBuildUnit(
    val arch: String,
    val sources: List<File>,
    val cflags: List<String>,
) : java.io.Serializable

abstract class Blake3JniBuildTask : DefaultTask() {
    @get:Input
    abstract val compiler: Property<String>

    @get:Input
    abstract val linkFlags: ListProperty<String>

    @get:Input
    abstract val buildUnits: ListProperty<Blake3JniBuildUnit>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val includeDirs: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val jniHeader: RegularFileProperty

    @get:OutputFile
    abstract val outputLibrary: RegularFileProperty

    @TaskAction
    fun build() {
        val compilerName = compiler.get()
        val units = buildUnits.get()
        val output = outputLibrary.get().asFile
        output.parentFile.mkdirs()
        if (!jniHeader.get().asFile.isFile) {
            throw GradleException("jni.h was not found under ${jniHeader.get().asFile.parentFile}")
        }
        val workDirectory = File(output.parentFile, "blake3_jni-work").apply { mkdirs() }
        val includeArgs = includeDirs.files.map { "-I${it.absolutePath}" }.toList()
        val intermediateLibraries = units.mapIndexed { index, unit ->
            val unitDirectory = File(workDirectory, "unit$index").apply { mkdirs() }
            val archArgs = if (unit.arch.isEmpty()) emptyList() else listOf("-arch", unit.arch)
            val objects = unit.sources.map { source ->
                val objectFile = File(unitDirectory, "${source.name}.o")
                exec(
                    compilerName,
                    archArgs + unit.cflags +
                        listOf("-c", source.absolutePath) +
                        includeArgs +
                        listOf("-o", objectFile.absolutePath),
                )
                objectFile
            }
            val intermediateLibrary = File(unitDirectory, "lib.dylib")
            exec(
                compilerName,
                archArgs + linkFlags.get() +
                    listOf("-o", intermediateLibrary.absolutePath) +
                    objects.map { it.absolutePath },
            )
            intermediateLibrary
        }
        if (intermediateLibraries.size == 1) {
            intermediateLibraries[0].copyTo(output, overwrite = true)
        } else {
            exec(
                "lipo",
                listOf("-create") +
                    intermediateLibraries.map { it.absolutePath } +
                    listOf("-output", output.absolutePath),
            )
        }
    }

    private fun exec(program: String, args: List<String>) {
        try {
            val exitCode = ProcessBuilder(listOf(program) + args).inheritIO().start().waitFor()
            check(exitCode == 0) { "Command failed: ${(listOf(program) + args).joinToString(" ")}" }
        } catch (e: IOException) {
            throw GradleException("C compiler '$program' not found on PATH.", e)
        }
    }
}

/**
 * ルートプロジェクトへコピーするネイティブバイナリの表示名とファイル名。
 */
data class DeployBinarySpec(
    val displayName: String,
    val fileName: String,
) : java.io.Serializable

/**
 * konaCommonTest の FatJar とネイティブ実行可能バイナリをルートプロジェクトへコピーする。
 * config cache 対応のため、Project やタスクへの参照は実行時に使わない。
 */
abstract class DeployKonaCommonTestTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fatJarFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val binaryFiles: ConfigurableFileCollection

    @get:Input
    abstract val deploySpecs: ListProperty<DeployBinarySpec>

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @TaskAction
    fun deploy() {
        val destination = destinationDirectory.get().asFile
        copyPreservingPermissions(fatJarFile.get().asFile, File(destination, "konaCommonTest.jar"))
        for (spec in deploySpecs.get()) {
            val source = File(spec.fileName)
            val extension = source.extension.takeIf { it.isNotEmpty() && it != "kexe" }?.let { ".$it" } ?: ""
            copyPreservingPermissions(source, File(destination, "konaCommonTest-${spec.displayName}$extension"))
        }
    }

    // POSIX 属性(実行ビット含む)を保持したままコピーする。File.copyTo では実行ビットが失われる。
    private fun copyPreservingPermissions(source: File, target: File) {
        target.parentFile.mkdirs()
        Files.copy(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.COPY_ATTRIBUTES,
        )
    }
}
