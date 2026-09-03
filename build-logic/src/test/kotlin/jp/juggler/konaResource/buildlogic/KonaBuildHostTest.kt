package jp.juggler.konaResource.buildlogic

import io.kotest.core.spec.style.FreeSpec
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KonaBuildHostTest : FreeSpec() {
    init {
        "identifiesSupportedHosts" {
            assertEquals(KonaBuildHost.LinuxX64, KonaBuildHost.from("Linux", "x86_64"))
            assertEquals(KonaBuildHost.MacosArm64, KonaBuildHost.from("Mac OS X", "aarch64"))
            assertEquals(KonaBuildHost.MacosX64, KonaBuildHost.from("Darwin", "amd64"))
            assertEquals(KonaBuildHost.MingwX64, KonaBuildHost.from("Windows 11", "x64"))
        }

        "rejectsUnsupportedHosts" {
            assertNull(KonaBuildHost.from("Linux", "aarch64"))
            assertNull(KonaBuildHost.from("Windows", "arm64"))
            assertNull(KonaBuildHost.from("FreeBSD", "x86_64"))
        }

        "readsHostTargetJniOverrides" {
            val project = ProjectBuilder.builder().build()
            val host = getKonaBuildHost()
            project.extensions.extraProperties.set("${host.name}_MingwX64_compileOpt", "-O2, -DTEST")
            project.extensions.extraProperties.set("${host.name}_MingwX64_linkOpt", "-shared")

            assertEquals(
                listOf("-O2", "-DTEST"),
                project.jniBuildOptions(JniBuildTarget.MingwX64, "compileOpt", listOf("-O3")),
            )
            assertEquals(
                listOf("-shared"),
                project.jniBuildOptions(JniBuildTarget.MingwX64, "linkOpt", listOf("-shared")),
            )
        }
    }
}
