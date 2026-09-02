package jp.juggler.konaResource.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.gradle.testfixtures.ProjectBuilder

class KonaBuildHostTest {
    @Test
    fun identifiesSupportedHosts() {
        assertEquals(KonaBuildHost.LinuxX64, KonaBuildHost.from("Linux", "x86_64"))
        assertEquals(KonaBuildHost.MacosArm64, KonaBuildHost.from("Mac OS X", "aarch64"))
        assertEquals(KonaBuildHost.MacosX64, KonaBuildHost.from("Darwin", "amd64"))
        assertEquals(KonaBuildHost.WindowsX64, KonaBuildHost.from("Windows 11", "x64"))
    }

    @Test
    fun rejectsUnsupportedHosts() {
        assertNull(KonaBuildHost.from("Linux", "aarch64"))
        assertNull(KonaBuildHost.from("Windows", "arm64"))
        assertNull(KonaBuildHost.from("FreeBSD", "x86_64"))
    }

    @Test
    fun readsHostTargetJniOverrides() {
        val project = ProjectBuilder.builder().build()
        val host = getKonaBuildHost()
        project.extensions.extraProperties.set("${host.name}_WindowsArm64_compiler", "custom-compiler")
        project.extensions.extraProperties.set("${host.name}_WindowsArm64_compileOpt", "-O2, -DTEST")
        project.extensions.extraProperties.set("${host.name}_WindowsArm64_linkOpt", "-shared")

        assertEquals("custom-compiler", project.jniBuildProperty(JniBuildTarget.WindowsArm64, "compiler"))
        assertEquals(
            listOf("-O2", "-DTEST"),
            project.jniBuildOptions(JniBuildTarget.WindowsArm64, "compileOpt", listOf("-O3")),
        )
        assertEquals(
            listOf("-shared"),
            project.jniBuildOptions(JniBuildTarget.WindowsArm64, "linkOpt", listOf("-shared", "-static-libgcc")),
        )
    }
}
