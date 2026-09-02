package jp.juggler.konaResource.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
