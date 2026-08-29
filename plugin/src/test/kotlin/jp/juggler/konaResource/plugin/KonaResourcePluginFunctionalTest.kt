package jp.juggler.konaResource.plugin

import org.gradle.testkit.runner.GradleRunner
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue

class KonaResourcePluginFunctionalTest {
    @Test
    fun generatesArchiveAndElfObject() {
        val projectDir = Files.createTempDirectory("kona-resource-plugin-test")
        try {
            writeFixture(projectDir)
            val result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("generateKonaResource", "--stacktrace")
                .forwardOutput()
                .build()

            assertTrue(result.output.contains("BUILD SUCCESSFUL"))
            assertTrue(Files.isRegularFile(projectDir.resolve("build/generated/konaResource/sample.bin")))
            assertTrue(Files.isRegularFile(projectDir.resolve("build/generated/konaResource/sample.S")))
            assertTrue(Files.isRegularFile(projectDir.resolve("build/generated/konaResource/sample.o")))
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }

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
            """.trimIndent() + "\n"
        )
        val input = projectDir.resolve("src/sample")
        input.createDirectories()
        input.resolve("hello.txt").writeText("hello from functional test\n")
        input.resolve("data.bin").writeBytes(byteArrayOf(1, 2, 3, 4))
    }
}
