package `fun`.abbas.wps_adb.model

enum class ScrcpyMaxSize(val pixels: Int?) {
    ORIGINAL(null),
    P720(720),
    P1080(1080),
    P1440(1440),
}

enum class ScrcpyVideoBitRate(val rate: String?) {
    DEFAULT(null),
    LOW("2M"),
    MEDIUM("4M"),
    HIGH("8M"),
    ULTRA("16M"),
}

enum class ScrcpyMaxFps(val fps: Int?) {
    DEFAULT(null),
    FPS15(15),
    FPS30(30),
    FPS60(60),
}

data class ScrcpyConnectionOptions(
    val enableAudio: Boolean = false,
    val maxSize: ScrcpyMaxSize = ScrcpyMaxSize.ORIGINAL,
    val videoBitRate: ScrcpyVideoBitRate = ScrcpyVideoBitRate.DEFAULT,
    val maxFps: ScrcpyMaxFps = ScrcpyMaxFps.DEFAULT,
    val stayAwake: Boolean = false,
    val turnScreenOff: Boolean = false,
    val showTouches: Boolean = false,
    val alwaysOnTop: Boolean = false,
    val viewOnly: Boolean = false,
)

object ScrcpyCommandBuilder {
    fun connectionArgs(options: ScrcpyConnectionOptions): List<String> = buildList {
        if (!options.enableAudio) add("--no-audio")
        options.maxSize.pixels?.let { add("--max-size=$it") }
        options.videoBitRate.rate?.let { add("--video-bit-rate=$it") }
        options.maxFps.fps?.let { add("--max-fps=$it") }
        if (options.stayAwake) add("--stay-awake")
        if (options.turnScreenOff) add("--turn-screen-off")
        if (options.showTouches) add("--show-touches")
        if (options.alwaysOnTop) add("--always-on-top")
        if (options.viewOnly) add("--no-control")
    }
}
