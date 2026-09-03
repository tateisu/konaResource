package jp.juggler.konaResource.plugin

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.GradleRunner
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class KonaResourcePluginFunctionalTest : FreeSpec() {
    private fun writeFixture(projectDir: Path) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("jp.juggler.konaResource")
            }

            konaResource {
                modules.add("sample" to "src/sample")
            }
            """.trimIndent() + "\n",
        )
        val input = projectDir.resolve("src/sample")
        input.createDirectories()
        input.resolve("hello.txt").writeText("hello from functional test\n")
        input.resolve("data.bin").writeBytes(byteArrayOf(1, 2, 3, 4))
    }

    init {
        "skipEmbedIf receives the Kotlin/Native target name" {
            val extension = KonaResourceExtension()
            extension.skipEmbedIf { targetName -> targetName == "macosArm64" }

            extension.shouldSkipEmbed("macosArm64") shouldBe true
            extension.shouldSkipEmbed("macosX64") shouldBe false
        }

        "does not register a target-less resource generation task" {
            val projectDir = Files.createTempDirectory("kona-resource-plugin-test")
            try {
                writeFixture(projectDir)
                val result = GradleRunner.create()
                    .withProjectDir(projectDir.toFile())
                    .withPluginClasspath()
                    .withArguments("tasks", "--all", "--stacktrace")
                    .forwardOutput()
                    .build()

                result.output.contains("BUILD SUCCESSFUL") shouldBe true
                result.output.contains("generateKonaResource") shouldBe false
                result.output.contains("konaResourceObjects") shouldBe false
            } finally {
                projectDir.toFile().deleteRecursively()
            }
        }
    }
}
