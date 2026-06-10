package `fun`.abbas.wps_adb.model

fun Device.displayAspectRatio(): Float {
    if (screenWidthPx > 0 && screenHeightPx > 0) {
        return screenWidthPx.toFloat() / screenHeightPx.toFloat()
    }
    return when (formFactor) {
        ScreenFormFactor.TV -> 16f / 9f
        ScreenFormFactor.TABLET -> 4f / 3f
        ScreenFormFactor.PHONE -> 9f / 16f
        ScreenFormFactor.UNKNOWN -> 9f / 16f
    }
}

fun Device.isLandscapeScreen(): Boolean = displayAspectRatio() >= 1f

object DeviceScreenMetrics {
    fun parseFormFactor(characteristics: String): ScreenFormFactor {
        val tokens = characteristics.lowercase().split(',').map { it.trim() }
        return when {
            tokens.any { it == "tv" || it.endsWith("tv") || it.contains("television") } -> ScreenFormFactor.TV
            tokens.any { it == "tablet" || it.contains("tablet") } -> ScreenFormFactor.TABLET
            tokens.any { it == "phone" || it.contains("phone") } -> ScreenFormFactor.PHONE
            else -> ScreenFormFactor.UNKNOWN
        }
    }

    fun parseWindowSize(output: String): Pair<Int, Int>? {
        val physical = Regex("""Physical size:\s*(\d+)x(\d+)""", RegexOption.IGNORE_CASE)
            .find(output)
        if (physical != null) {
            return physical.groupValues[1].toIntOrNull()?.let { width ->
                physical.groupValues[2].toIntOrNull()?.let { height -> width to height }
            }
        }
        val generic = Regex("""(\d+)x(\d+)""").find(output.trim())
        return generic?.let {
            val width = it.groupValues[1].toIntOrNull() ?: return null
            val height = it.groupValues[2].toIntOrNull() ?: return null
            width to height
        }
    }

    fun inferFormFactor(
        characteristics: String,
        screenWidthPx: Int,
        screenHeightPx: Int,
    ): ScreenFormFactor {
        val fromCharacteristics = parseFormFactor(characteristics)
        if (fromCharacteristics != ScreenFormFactor.UNKNOWN) return fromCharacteristics
        if (screenWidthPx <= 0 || screenHeightPx <= 0) return ScreenFormFactor.UNKNOWN

        val shortSide = minOf(screenWidthPx, screenHeightPx)
        val longSide = maxOf(screenWidthPx, screenHeightPx)
        val aspect = screenWidthPx.toFloat() / screenHeightPx.toFloat()
        val elongation = longSide.toFloat() / shortSide.toFloat()

        return when {
            aspect >= 1.6f && shortSide >= 720 -> ScreenFormFactor.TV
            elongation > 1.9f -> ScreenFormFactor.PHONE
            elongation <= 1.45f && shortSide >= 600 -> ScreenFormFactor.TABLET
            shortSide >= 600 -> ScreenFormFactor.TABLET
            else -> ScreenFormFactor.PHONE
        }
    }
}
