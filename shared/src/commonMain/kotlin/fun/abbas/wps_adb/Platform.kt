package `fun`.abbas.wps_adb

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform