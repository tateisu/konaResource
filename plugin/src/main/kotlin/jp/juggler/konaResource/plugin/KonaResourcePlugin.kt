package jp.juggler.konaResource.plugin

import jp.juggler.konaArchive.konaArchivePack
import jp.juggler.konaArchive.util.Lz4Options
import jp.juggler.konaResource.buildlogic.runKonan
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
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import javax.inject.Inject

@Suppress("unused")
class KonaResourcePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("konaResource", KonaResourceExtension::class.java)
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            project.afterEvaluate {
                val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
                kotlin.targets.withType(KotlinNativeTarget::class.java)
                    .configureEach { target -> target.updateBuild(extension) }
                project.tasks.register("konaResourceObjects", DefaultTask::class.java) { aggregate ->
                    aggregate.dependsOn(project.tasks.withType(GenerateKonaResourceTask::class.java))
                    aggregate.doLast {
                        project.tasks.withType(GenerateKonaResourceTask::class.java).forEach { task ->
                            aggregate.logger.lifecycle(
                                "Kona Resource objects for ${task.name} are in " +
                                    task.outputDirectory.get().asFile,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun KotlinNativeTarget.updateBuild(extension: KonaResourceExtension) {
    val target = this
    val targetName = target.name.replaceFirstChar { it.uppercase() }
    val skipEmbed = extension.shouldSkipEmbed(target.name)
    val targetGenerate = project.tasks.register(
        "generateKonaResource$targetName",
        GenerateKonaResourceTask::class.java,
    )
    targetGenerate.configure { task ->
        task.outputDirectory.set(
            project.layout.buildDirectory.dir("generated/konaResource/${target.name}"),
        )
        task.skipEmbed.set(skipEmbed)
        task.onlyIf { !skipEmbed }
        task.compilerArgs.convention(emptyList())
        task.setFromExtension(extension)
        val runKonanCommand = runKonan(
            kotlinVersion = project.getKotlinPluginVersion(),
            mode = "clang",
            tool = "clang",
            target = target.konanTarget.name,
        )
        task.compiler.set(runKonanCommand.first())
        task.compilerArgs.set(runKonanCommand.drop(1))
    }
    if (skipEmbed) return
    target.binaries.all { binary ->
        val linkerOptions: Array<String> = extension.modules.map { module ->
            val safeName = module.first.replace(Regex("[^A-Za-z0-9_]"), "_")
            targetGenerate.get().outputDirectory.get().file("$safeName.o").asFile.absolutePath
        }.toTypedArray()
        binary.linkerOpts(*linkerOptions)
        binary.linkTaskProvider.configure { it.dependsOn(targetGenerate) }
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
    abstract val skipEmbed: Property<Boolean>

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

    fun setFromExtension(extension: KonaResourceExtension) {
        resourceDirectories.from(
            extension.modules.map { (_, rawDirectory) -> project.file(rawDirectory) },
        )
        moduleNames.set(extension.modules.map { it.first })
        lz4CompressionLevel.set(extension.lz4CompressionLevel)
        lz4BlockSizeID.set(extension.lz4BlockSizeID)
        lz4BlockMode.set(extension.lz4BlockMode)
        lz4ContentSizeFlag.set(extension.lz4ContentSizeFlag)
        lz4ContentChecksumFlag.set(extension.lz4ContentChecksumFlag)
        lz4blockChecksumFlag.set(extension.lz4blockChecksumFlag)
        lz4AutoFlush.set(extension.lz4AutoFlush)
        lz4FavorDecSpeed.set(extension.lz4FavorDecSpeed)
    }

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
                    #if defined(__APPLE__)
                    #define KONA_RESOURCE_SYMBOL(name) _##name
                    .section __TEXT,__const
                    #else
                    #define KONA_RESOURCE_SYMBOL(name) name
                    .section .rodata
                    #endif
                    .balign 8
                    .globl KONA_RESOURCE_SYMBOL(${symbol}_start)
                    KONA_RESOURCE_SYMBOL(${symbol}_start):
                    .incbin "${archiveFile.name}"
                    .globl KONA_RESOURCE_SYMBOL(${symbol}_end)
                    KONA_RESOURCE_SYMBOL(${symbol}_end):
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
