package `fun`.abbas.wps_adb.model

object PortRangeValidator {
    fun normalizedRange(minPort: Int, maxPort: Int): IntRange {
        val min = minOf(minPort, maxPort)
        val max = maxOf(minPort, maxPort)
        return min..max
    }

    fun isInRange(port: Int, minPort: Int, maxPort: Int): Boolean =
        port in normalizedRange(minPort, maxPort)

    fun isInRange(port: Int, settings: AppSettings): Boolean =
        isInRange(port, settings.minPort, settings.maxPort)
}
