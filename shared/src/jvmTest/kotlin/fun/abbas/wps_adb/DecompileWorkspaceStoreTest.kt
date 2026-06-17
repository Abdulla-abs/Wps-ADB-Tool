package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.DecompileWorkspaceStore
import `fun`.abbas.wps_adb.model.RecentDecompileProject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DecompileWorkspaceStoreTest {
    @Test
    fun saveRecent_writesToCustomRecentFile() {
        val tempDir = tempDir("ws-store")
        val recentFile = File(tempDir, "decompile/recent.json").absolutePath
        val project = RecentDecompileProject(
            apkPath = "C:/test/app.apk",
            workspacePath = tempDir.absolutePath + "/workspace",
            packageName = "com.test.app",
            apkFileName = "app.apk",
            lastOpenedAtMillis = 1234567890L,
        )
        DecompileWorkspaceStore.saveRecent(recentFile, project)
        assertEquals(1, DecompileWorkspaceStore.loadRecent(recentFile).size)
        assertEquals(project.packageName, DecompileWorkspaceStore.loadRecent(recentFile).first().packageName)
    }

    private fun tempDir(prefix: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "${prefix}_${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }
}
