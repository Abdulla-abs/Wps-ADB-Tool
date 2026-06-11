package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.JvmAdbKeyManager
import kotlin.test.Test

class JvmAdbKeyManagerTest {
    @Test
    fun hasKeyPair_returnsBooleanWithoutThrowing() {
        // Uses real ~/.android in environment; only assert the call is safe.
        JvmAdbKeyManager.hasKeyPair()
    }

    @Test
    fun ensureKeyPair_withInvalidAdbPath_doesNotCrash() {
        JvmAdbKeyManager.ensureKeyPair("__missing_adb_binary__")
    }
}
