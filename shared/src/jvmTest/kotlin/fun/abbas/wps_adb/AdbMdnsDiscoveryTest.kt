package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.AdbMdnsDiscovery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdbMdnsDiscoveryTest {

    @Test
    fun formatEndpoint_formatsHostPort() {
        assertEquals(
            "192.168.1.10:37845",
            AdbMdnsDiscovery.formatEndpoint("192.168.1.10", 37845),
        )
    }

    @Test
    fun matchesServiceName_requiresExactMatch() {
        assertTrue(AdbMdnsDiscovery.matchesInstanceName("studio-abc123", "studio-abc123"))
        assertEquals(
            false,
            AdbMdnsDiscovery.matchesInstanceName("studio-abc123", "studio-other"),
        )
    }
}
