package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.AppSettings
import java.io.File

class AppDataPaths private constructor(
    private val cacheRoot: File,
    private val appRoot: File = defaultAppRoot(),
) {
    fun appRoot(): String = appRoot.absolutePath
    fun cacheRoot(): String = cacheRoot.absolutePath
    fun decompileRoot(): String = File(cacheRoot, "decompile").absolutePath
    fun decompileWorkspacesRoot(): String = decompileRoot()
    fun recentProjectsFile(): String = File(decompileRoot(), "recent.json").absolutePath
    fun debugKeystoreFile(): String = File(decompileRoot(), "debug.keystore").absolutePath
    fun jcefBundleDir(): File = File(cacheRoot, "jcef-bundle")
    fun scenesRoot(): File = File(appRoot, "scenes")

    fun ensureDirectoriesExist() {
        File(decompileRoot()).mkdirs()
        scenesRoot().mkdirs()
    }

    companion object {
        fun defaultAppRoot(): File = File(System.getProperty("user.home"), ".wps-adb-tool")

        fun defaultCacheRoot(): File {
            return File(defaultAppRoot(), "cache")
        }

        fun defaultJcefBundleDir(): File = File(defaultCacheRoot(), "jcef-bundle")
        fun defaultScenesRoot(): File = File(defaultAppRoot(), "scenes")

        /** Legacy decompile dir (underscore) — read-only fallback */
        fun legacyDecompileRoot(): File =
            File(System.getProperty("user.home"), ".wps_adb_tool/decompile")

        fun fromSettings(
            settings: AppSettings,
            appRoot: File = defaultAppRoot(),
        ): AppDataPaths {
            val cacheRoot = if (settings.dataCacheDir.isBlank()) {
                defaultCacheRoot()
            } else {
                File(settings.dataCacheDir)
            }
            return AppDataPaths(cacheRoot.canonicalFile, appRoot.canonicalFile)
        }
    }
}
