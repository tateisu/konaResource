package jp.juggler.konaResource.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

data class WorkflowResultJarSpec(
    val path: String,
    val hostName: String,
) : java.io.Serializable

data class JniCollectionSpec(
    val resourcePath: String,
    val outputPath: String,
    val targetName: String,
) : java.io.Serializable

abstract class CollectJniFromWorkflowResultTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceJars: ConfigurableFileCollection

    @get:Input
    abstract val sourceJarSpecs: ListProperty<WorkflowResultJarSpec>

    @get:Input
    abstract val collectionSpecs: ListProperty<JniCollectionSpec>

    @get:OutputFiles
    abstract val outputFiles: ConfigurableFileCollection

    @TaskAction
    fun collect() {
        val candidates = sourceJarSpecs.get().map { WorkflowResultJar(File(it.path), it.hostName) }
        collectionSpecs.get().forEach { spec ->
            val sortedCandidates = candidates.sortedWith(
                compareBy<WorkflowResultJar> {
                    levenshteinDistance(it.hostName, spec.targetName)
                }.thenBy { it.hostName }.thenBy { it.file.absolutePath },
            )
            var collected = false
            for (candidate in sortedCandidates) {
                try {
                    ZipFile(candidate.file).use { zip ->
                        val entry = zip.getEntry(spec.resourcePath) ?: return@use
                        if (entry.isDirectory) return@use
                        val outputFile = File(spec.outputPath)
                        outputFile.parentFile.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            Files.copy(
                                input,
                                outputFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING,
                            )
                        }
                        logger.warn("${spec.targetName} read from $candidate")
                        collected = true
                    }
                } catch (exception: IOException) {
                    logger.warn("Could not read optional workflow result ${candidate.file}", exception)
                }
                if (collected) break
            }
            if (!collected) {
                File(spec.outputPath).delete()
                logger.lifecycle("Optional JNI resource was not found: ${spec.resourcePath}")
            }
        }
    }

    private data class WorkflowResultJar(
        val file: File,
        val hostName: String,
    )

    private fun levenshteinDistance(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        for (leftIndex in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            for (rightIndex in right.indices) {
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (left[leftIndex] == right[rightIndex]) 0 else 1,
                )
            }
            previous = current
        }
        return previous[right.length]
    }
}
