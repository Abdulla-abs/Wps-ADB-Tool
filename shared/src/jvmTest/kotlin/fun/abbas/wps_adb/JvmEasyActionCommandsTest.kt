package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.JvmEasyActionCommands
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmEasyActionCommandsTest {
    @Test
    fun clearAppCacheCommand_usesCmdPackageClearAppCache() {
        val command = JvmEasyActionCommands.clearAppCache("com.example.app")
        assertEquals(
            listOf("shell", "cmd", "package", "clear-app-cache", "--user", "0", "com.example.app"),
            command,
        )
    }
}
