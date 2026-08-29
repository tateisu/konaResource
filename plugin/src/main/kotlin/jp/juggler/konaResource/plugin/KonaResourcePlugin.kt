package jp.juggler.konaResource.plugin

import jp.juggler.konaArchive.*
import jp.juggler.konaArchive.util.FileRandomAccess
import kotlinx.coroutines.runBlocking
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

@Suppress("unused")
class KonaResourcePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("konaResource", KonaResourceExtension::class.java)
        val generate = project.tasks.register("generateKonaResource", GenerateKonaResourceTask::class.java)
        generate.configure { task ->
            task.outputDirectory.set(project.layout.buildDirectory.dir("generated/konaResource"))
            task.sourceExtension = extension
            task.outputs.upToDateWhen { false }
        }
        project.tasks.register("konaResourceObjects").configure { task ->
            task.dependsOn(generate)
            task.doLast { project.logger.lifecycle("Kona Resource objects are in ${generate.get().outputDirectory.get().asFile}") }
        }
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            project.afterEvaluate {
                project.extensions.getByType(KotlinMultiplatformExtension::class.java).targets
                    .withType(KotlinNativeTarget::class.java).configureEach { target ->
                        target.binaries.all { binary ->
                            val linkerOptions: Array<String> = extension.modules.map { module ->
                                val safeName = module.first.replace(Regex("[^A-Za-z0-9_]"), "_")
                                generate.get().outputDirectory.get().file("$safeName.o").asFile.absolutePath
                            }.toTypedArray()
                            binary.linkerOpts(*linkerOptions)
                            binary.linkTaskProvider.configure { it.dependsOn(generate) }
                        }
                    }
            }
        }
    }
}

@DisableCachingByDefault(because = "Resource inputs are configured through the extension and assembled by an external tool.")
abstract class GenerateKonaResourceTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    lateinit var sourceExtension: KonaResourceExtension

    @TaskAction
    fun generate() {
        val output = outputDirectory.get().asFile
        output.mkdirs()
        runBlocking {
            sourceExtension.modules.forEach { (name, rawDirectory) ->
                val inputDirectory = project.file(rawDirectory)
                require(inputDirectory.isDirectory) { "Kona Resource directory does not exist: $inputDirectory" }
                val writerRoot = inputDirectory.toKonaWriterEntry()
                require(writerRoot is KonaWriterDirectory) { "Kona Resource input is not a directory: $inputDirectory" }
                val safeName = name.replace(Regex("[^A-Za-z0-9_]"), "_")
                val archiveFile = output.resolve("$safeName.bin")
                val temporaryArchive = Files.createTempFile(
                    output.toPath(),
                    ".${archiveFile.name}.",
                    ".tmp",
                ).toFile()
                try {
                    openPreviousArchive(archiveFile).use { previous ->
                        FileRandomAccess(temporaryArchive).use { access ->
                            access.encodeKonaArchive(
                                root = writerRoot,
                                options = sourceExtension.options(),
                                previous = previous,
                            )
                        }
                    }
                    replaceArchive(temporaryArchive, archiveFile)
                } finally {
                    temporaryArchive.delete()
                }
                val assembly = output.resolve("$safeName.S")
                val symbol = "konaResource_${safeName}"
                assembly.writeText(
                    """
                    .section .rodata
                    .balign 8
                    .global ${symbol}_start
                    .type ${symbol}_start, @object
                    ${symbol}_start:
                    .incbin "${archiveFile.absolutePath.replace("\\", "/")}"
                    .global ${symbol}_end
                    .type ${symbol}_end, @object
                    ${symbol}_end:
                    .size ${symbol}_start, ${symbol}_end-${symbol}_start
                """.trimIndent() + "\n"
                )
                val objectFile = output.resolve("$safeName.o")
                execOperations.exec(Action { spec ->
                    spec.commandLine(
                        "cc", "-c", "-x", "assembler-with-cpp",
                        assembly.absolutePath, "-o", objectFile.absolutePath,
                    )
                })
            }
        }
    }

    private fun replaceArchive(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun openPreviousArchive(archiveFile: File): KonaArchive? =
        runCatching {
            FileRandomAccess(archiveFile, isReadOnly = true)
                .decodeKonaArchiveOrClose()
        }.getOrNull()
}
