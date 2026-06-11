package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.model.ScrcpyCommandBuilder
import `fun`.abbas.wps_adb.model.ScrcpyConnectionOptions
import `fun`.abbas.wps_adb.model.ScrcpyMaxFps
import `fun`.abbas.wps_adb.model.ScrcpyMaxSize
import `fun`.abbas.wps_adb.model.ScrcpyVideoBitRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScrcpyCommandBuilderTest {

    @Test
    fun defaultOptions_disableAudioOnly() {
        val args = ScrcpyCommandBuilder.connectionArgs(ScrcpyConnectionOptions())
        assertEquals(listOf("--no-audio"), args)
    }

    @Test
    fun enableAudio_omitsNoAudioFlag() {
        val args = ScrcpyCommandBuilder.connectionArgs(
            ScrcpyConnectionOptions(enableAudio = true),
        )
        assertFalse(args.contains("--no-audio"))
    }

    @Test
    fun videoQualityFlags_areIncluded() {
        val args = ScrcpyCommandBuilder.connectionArgs(
            ScrcpyConnectionOptions(
                enableAudio = true,
                maxSize = ScrcpyMaxSize.P1080,
                videoBitRate = ScrcpyVideoBitRate.MEDIUM,
                maxFps = ScrcpyMaxFps.FPS30,
            ),
        )
        assertTrue("--max-size=1080" in args)
        assertTrue("--video-bit-rate=4M" in args)
        assertTrue("--max-fps=30" in args)
    }

    @Test
    fun behaviorFlags_areIncluded() {
        val args = ScrcpyCommandBuilder.connectionArgs(
            ScrcpyConnectionOptions(
                enableAudio = false,
                stayAwake = true,
                turnScreenOff = true,
                showTouches = true,
                alwaysOnTop = true,
                viewOnly = true,
            ),
        )
        assertTrue("--stay-awake" in args)
        assertTrue("--turn-screen-off" in args)
        assertTrue("--show-touches" in args)
        assertTrue("--always-on-top" in args)
        assertTrue("--no-control" in args)
    }
}
