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
    fun customCacheRoot_overridesCachePaths_butKeepsScenesUnderAppRoot() {
        val custom = File(System.getProperty("java.io.tmpdir"), "wps-cache-test").absolutePath
        val paths = AppDataPaths.fromSettings(AppSettings(dataCacheDir = custom))
        assertEquals(File(custom).canonicalFile.path, File(paths.cacheRoot()).path)
        assertEquals(
            File(custom, "decompile/recent.json").path,
            paths.recentProjectsFile(),
        )
        assertEquals(
            File(custom, "jcef-bundle").path,
            paths.jcefBundleDir().path,
        )
        // scenesRoot must remain under persistent app root, not dataCacheDir
        assertEquals(
            AppDataPaths.defaultScenesRoot().path,
            paths.scenesRoot().path,
        )
    }

    @Test
    fun customAppRoot_overridesScenesRoot() {
        val customApp = File(System.getProperty("java.io.tmpdir"), "wps-app-test")
        val paths = AppDataPaths.fromSettings(AppSettings(), appRoot = customApp)
        assertEquals(
            File(customApp.canonicalFile, "scenes").path,
            paths.scenesRoot().path,
        )
    }

    @Test
    fun defaultDirectories_pointUnderExpectedRoots() {
        val bundle = AppDataPaths.defaultJcefBundleDir()
        val scenes = AppDataPaths.defaultScenesRoot()
        assertTrue(bundle.path.replace('\\', '/').endsWith(".wps-adb-tool/cache/jcef-bundle"))
        assertTrue(scenes.path.replace('\\', '/').endsWith(".wps-adb-tool/scenes"))
    }
}
