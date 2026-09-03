package jp.juggler.konaResource.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * commonJni/build.gradle.kts で使用
 */
abstract class ListAvailableJniBuildTargetsTask : DefaultTask() {
    @get:Input
    abstract val targetNames: ListProperty<String>

    @TaskAction
    fun list() {
        logger.lifecycle(targetNames.get().joinToString(" "))
    }
}
