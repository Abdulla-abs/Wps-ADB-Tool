package `fun`.abbas.wps_adb.data

expect fun createScrcpyMirrorService(
    scrcpyPathProvider: () -> String,
    adbPathProvider: () -> String,
): ScrcpyMirrorService
