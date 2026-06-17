package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.AppDataPaths
import `fun`.abbas.wps_adb.model.AppSettings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppDataPathsTest {
    @Test
    fun defaultCacheRoot_usesWpsAdbToolCacheUnderHome() {
        val paths = AppDataPaths.fromSettings(AppSettings())
        val root = File(paths.cacheRoot())
        assertTrue(root.path.replace('\\', '/').endsWith(".wps-adb-tool/cache"))
    }

    @Test
    fun customCacheRoot_overridesDefault() {
        val custom = File(System.getProperty("java.io.tmpdir"), "wps-cache-test").absolutePath
        val paths = AppDataPaths.fromSettings(AppSettings(dataCacheDir = custom))
        assertEquals(File(custom).canonicalFile.path, File(paths.cacheRoot()).path)
        assertEquals(
            File(custom, "decompile/recent.json").path,
            paths.recentProjectsFile(),
        )
    }
}
