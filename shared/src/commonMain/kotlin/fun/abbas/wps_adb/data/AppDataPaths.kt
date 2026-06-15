package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.AppSettings
import java.io.File

class AppDataPaths private constructor(
    private val root: File,
) {
    fun cacheRoot(): String = root.absolutePath
    fun decompileRoot(): String = File(root, "decompile").absolutePath
    fun decompileWorkspacesRoot(): String = decompileRoot()
    fun recentProjectsFile(): String = File(decompileRoot(), "recent.json").absolutePath
    fun debugKeystoreFile(): String = File(decompileRoot(), "debug.keystore").absolutePath

    fun ensureDirectoriesExist() {
        File(decompileRoot()).mkdirs()
    }

    companion object {
        fun defaultCacheRoot(): File {
            val home = System.getProperty("user.home")
            return File(home, ".wps-adb-tool/cache")
        }

        /** Legacy decompile dir (underscore) — read-only fallback */
        fun legacyDecompileRoot(): File =
            File(System.getProperty("user.home"), ".wps_adb_tool/decompile")

        fun fromSettings(settings: AppSettings): AppDataPaths {
            val root = if (settings.dataCacheDir.isBlank()) {
                defaultCacheRoot()
            } else {
                File(settings.dataCacheDir)
            }
            return AppDataPaths(root.canonicalFile)
        }
    }
}
