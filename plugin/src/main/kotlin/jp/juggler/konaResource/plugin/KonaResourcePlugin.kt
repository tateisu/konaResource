package jp.juggler.konaResource.plugin

import jp.juggler.konaArchive.konaArchivePack
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import javax.inject.Inject

@Suppress("unused")
class KonaResourcePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("konaResource", KonaResourceExtension::class.java)
        val generate = project.tasks.register("generateKonaResource", GenerateKonaResourceTask::class.java)
        generate.configure { task ->
            task.outputDirectory.set(project.layout.buildDirectory.dir("generated/konaResource"))
            task.sourceExtension = extension
        }
        project.afterEvaluate {
            generate.configure { task ->
                task.resourceDirectories.from(
                    extension.modules.map { (_, rawDirectory) -> project.file(rawDirectory) },
                )
                task.moduleNames.set(extension.modules.map { it.first })
                task.lz4CompressionLevel.set(extension.lz4CompressionLevel)
                task.lz4BlockSizeID.set(extension.lz4BlockSizeID)
                task.lz4BlockMode.set(extension.lz4BlockMode)
                task.lz4ContentSizeFlag.set(extension.lz4ContentSizeFlag)
                task.lz4ContentChecksumFlag.set(extension.lz4ContentChecksumFlag)
                task.lz4blockChecksumFlag.set(extension.lz4blockChecksumFlag)
                task.lz4AutoFlush.set(extension.lz4AutoFlush)
                task.lz4FavorDecSpeed.set(extension.lz4FavorDecSpeed)
            }
        }
        project.tasks.register("konaResourceObjects").configure { task ->
            task.dependsOn(generate)
            task.doLast {
                project.logger.lifecycle(
                    "Kona Resource objects are in ${generate.get().outputDirectory.get().asFile}",
                )
            }
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

@CacheableTask
abstract class GenerateKonaResourceTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDirectories: ConfigurableFileCollection

    @get:Input
    abstract val moduleNames: ListProperty<String>

    @get:Input
    abstract val lz4CompressionLevel: Property<Int>

    @get:Input
    abstract val lz4BlockSizeID: Property<Int>

    @get:Input
    abstract val lz4BlockMode: Property<String>

    @get:Input
    abstract val lz4ContentSizeFlag: Property<Boolean>

    @get:Input
    abstract val lz4ContentChecksumFlag: Property<Boolean>

    @get:Input
    abstract val lz4blockChecksumFlag: Property<Boolean>

    @get:Input
    abstract val lz4AutoFlush: Property<Boolean>

    @get:Input
    abstract val lz4FavorDecSpeed: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    lateinit var sourceExtension: KonaResourceExtension

    @TaskAction
    @Suppress("LongMethod")
    fun generate() {
        val output = outputDirectory.get().asFile
        output.mkdirs()
        sourceExtension.modules.forEach { (name, rawDirectory) ->
            val inputDirectory = project.file(rawDirectory)
            val safeName = name.replace(Regex("[^A-Za-z0-9_]"), "_")
            val archiveFile = output.resolve("$safeName.bin")
            konaArchivePack(
                archivePath = archiveFile.toPath(),
                inputDirectory = inputDirectory.toPath(),
                previousArchivePath = archiveFile.takeIf { it.isFile }?.toPath(),
                options = sourceExtension.options(),
            )
            val assembly = output.resolve("$safeName.S")
            val symbol = "konaResource_$safeName"
            assembly.writeText(
                """
                    .section .rodata
                    .balign 8
                    .global ${symbol}_start
                    .type ${symbol}_start, @object
                    ${symbol}_start:
                    .incbin "${archiveFile.name}"
                    .global ${symbol}_end
                    .type ${symbol}_end, @object
                    ${symbol}_end:
                    .size ${symbol}_start, ${symbol}_end-${symbol}_start
                """.trimIndent() + "\n",
            )
            val objectFile = output.resolve("$safeName.o")
            execOperations.exec(
                Action { spec ->
                    spec.workingDir(output)
                    spec.commandLine(
                        "cc",
                        "-c",
                        "-x",
                        "assembler-with-cpp",
                        assembly.absolutePath,
                        "-o",
                        objectFile.absolutePath,
                    )
                },
            )
        }
    }
}
