package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.DecompileProjectNames
import kotlin.test.Test
import kotlin.test.assertEquals

class DecompileProjectNamesTest {
    @Test
    fun displayName_prefersAppLabel() {
        assertEquals(
            "WPS 游戏中心",
            DecompileProjectNames.displayName(
                appLabel = "WPS 游戏中心",
                packageName = "com.wpsky.android.feature.gamelaunch",
                apkFileName = "game.apk",
            ),
        )
    }

    @Test
    fun displayName_fallsBackToValidPackageName() {
        assertEquals(
            "com.example.demo",
            DecompileProjectNames.displayName(
                appLabel = null,
                packageName = "com.example.demo",
                apkFileName = "demo.apk",
            ),
        )
    }

    @Test
    fun displayName_fallsBackToApkFileNameWhenPackageLooksWrong() {
        assertEquals(
            "game",
            DecompileProjectNames.displayName(
                appLabel = null,
                packageName = "com. wpsky . android. feature. billing",
                apkFileName = "game.apk",
            ),
        )
    }
}
