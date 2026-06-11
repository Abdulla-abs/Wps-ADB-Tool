package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.ProcessIdResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProcessIdResolverTest {
    @Test
    fun parseSinglePid_acceptsOnePlausiblePid() {
        assertEquals(17339, ProcessIdResolver.parseSinglePid("17339"))
    }

    @Test
    fun parseSinglePid_rejectsInitPid() {
        assertNull(ProcessIdResolver.parseSinglePid("1"))
    }

    @Test
    fun parseSinglePid_rejectsBrokenPidofOutput() {
        assertNull(
            ProcessIdResolver.parseSinglePid(
                "1 2 3 5 7 8 9 10 11 12 14 15 16 18 19 20 22 23 17339",
            ),
        )
    }

    @Test
    fun parseFromPs_readsLegacyAndroidPsLine() {
        val output = "u0_a121   17339 1805  1342356 360868 SyS_epoll_ 0000000000 S com.wanpishiky.android"

        assertEquals(17339, ProcessIdResolver.parseFromPs(output, "com.wanpishiky.android"))
    }

    @Test
    fun parseFromPs_ignoresUnrelatedProcesses() {
        val output = """
            root      1     0     1512   676   SyS_epoll_ 0000000000 S /init
            u0_a121   17339 1805  1342356 360868 SyS_epoll_ 0000000000 S com.wanpishiky.android
        """.trimIndent()

        assertEquals(17339, ProcessIdResolver.parseFromPs(output, "com.wanpishiky.android"))
    }
}
