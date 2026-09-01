package jp.juggler.konaResource.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Copies standalone Kotlin/Native binaries to the root project. */
abstract class DeployKonaNativeBinariesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val binaryFiles: ConfigurableFileCollection

    @get:Input
    abstract val deploySpecs: ListProperty<DeployBinarySpec>

    @get:Optional
    @get:InputFile
    abstract val jarFile: RegularFileProperty

    @get:OutputFiles
    abstract val deployedFiles: ConfigurableFileCollection

    @get:Internal
    abstract val destinationDirectory: DirectoryProperty

    @TaskAction
    fun deploy() {
        val destination = destinationDirectory.get().asFile
        if (jarFile.isPresent) {
            copyFile(jarFile.get().asFile, File(destination, "konaBenchmark.jar"))
        }
        for (spec in deploySpecs.get()) {
            val source = File(spec.fileName)
            val extension = source.extension
                .takeIf { it.isNotEmpty() && it != "kexe" }
                ?.let { ".$it" }
                ?: ""
            val target = File(destination, "konaBenchmark-${spec.displayName}$extension")
            copyFile(source, target)
            target.setExecutable(true, false)
        }
    }

    private fun copyFile(source: File, target: File) {
        target.parentFile.mkdirs()
        Files.copy(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.COPY_ATTRIBUTES,
        )
    }
}
