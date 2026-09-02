package jp.juggler.konaResource.plugin

import jp.juggler.konaArchive.konaArchivePack
import jp.juggler.konaArchive.util.Lz4Options
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.util.Locale
import javax.inject.Inject

private fun compilerForTarget(targetName: String): String = when (targetName.lowercase(Locale.ROOT)) {
    "linuxarm64" -> "clang"
    "mingwx64" -> if (System.getProperty("os.name").lowercase(Locale.ROOT).contains("windows")) {
        "cc"
    } else {
        "x86_64-w64-mingw32-gcc"
    }

    else -> "cc"
}

@Suppress("unused")
class KonaResourcePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("konaResource", KonaResourceExtension::class.java)
        val generate = project.tasks.register("generateKonaResource", GenerateKonaResourceTask::class.java)
        generate.configure { task ->
            task.outputDirectory.set(project.layout.buildDirectory.dir("generated/konaResource"))
            task.compiler.convention("cc")
            task.compilerArgs.convention(emptyList())
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
                task.logger.lifecycle(
                    "Kona Resource objects are in ${generate.get().outputDirectory.get().asFile}",
                )
            }
        }
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            project.afterEvaluate {
                fun configureGenerateTask(
                    task: GenerateKonaResourceTask,
                    outputDirectory: Provider<Directory>,
                ) {
                    task.outputDirectory.set(outputDirectory)
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
                    task.compilerArgs.convention(emptyList())
                }

                val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
                kotlin.targets.withType(KotlinNativeTarget::class.java).configureEach { target ->
                    val targetName = target.name.replaceFirstChar { it.uppercase() }
                    val targetGenerate = project.tasks.register(
                        "generateKonaResource$targetName",
                        GenerateKonaResourceTask::class.java,
                    )
                    targetGenerate.configure { task ->
                        configureGenerateTask(
                            task,
                            project.layout.buildDirectory.dir("generated/konaResource/${target.name}"),
                        )
                        task.compiler.set(compilerForTarget(target.name))
                        if (target.name.equals("linuxArm64", ignoreCase = true)) {
                            task.compilerArgs.set(listOf("--target=aarch64-linux-gnu"))
                        }
                    }
                    target.binaries.all { binary ->
                        val linkerOptions: Array<String> = extension.modules.map { module ->
                            val safeName = module.first.replace(Regex("[^A-Za-z0-9_]"), "_")
                            targetGenerate.get().outputDirectory.get().file("$safeName.o").asFile.absolutePath
                        }.toTypedArray()
                        binary.linkerOpts(*linkerOptions)
                        binary.linkTaskProvider.configure { it.dependsOn(targetGenerate) }
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

    @get:Input
    abstract val compiler: Property<String>

    @get:Input
    abstract val compilerArgs: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    @Suppress("LongMethod")
    fun generate() {
        val output = outputDirectory.get().asFile
        output.mkdirs()
        val options = Lz4Options(
            compressionLevel = lz4CompressionLevel.get(),
            blockSize = lz4BlockSizeID.get(),
            blockLinked = lz4BlockMode.get() != "LZ4F_blockIndependent",
            contentSizeFlag = lz4ContentSizeFlag.get(),
            contentChecksumFlag = lz4ContentChecksumFlag.get(),
            blockChecksumFlag = lz4blockChecksumFlag.get(),
            autoFlush = lz4AutoFlush.get(),
            favorDecSpeed = lz4FavorDecSpeed.get(),
        )
        val names = moduleNames.get()
        val directories = resourceDirectories.files.toList()
        names.zip(directories).forEach { (name, inputDirectory) ->
            val safeName = name.replace(Regex("[^A-Za-z0-9_]"), "_")
            val archiveFile = output.resolve("$safeName.bin")
            konaArchivePack(
                archivePath = archiveFile.toPath(),
                inputDirectory = inputDirectory.toPath(),
                previousArchivePath = archiveFile.takeIf { it.isFile }?.toPath(),
                options = options,
            )
            val assembly = output.resolve("$safeName.S")
            val symbol = "konaResource_$safeName"
            assembly.writeText(
                """
                    .section .rodata
                    .balign 8
                    .global ${symbol}_start
                    ${symbol}_start:
                    .incbin "${archiveFile.name}"
                    .global ${symbol}_end
                    ${symbol}_end:
                    #if defined(_WIN32)
                    .section .drectve
                    .ascii " -export:${symbol}_start -export:${symbol}_end"
                    #endif
                """.trimIndent() + "\n",
            )
            val objectFile = output.resolve("$safeName.o")
            execOperations.exec(
                Action { spec ->
                    spec.workingDir(output)
                    spec.commandLine(
                        compiler.get(),
                        *compilerArgs.get().toTypedArray(),
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
