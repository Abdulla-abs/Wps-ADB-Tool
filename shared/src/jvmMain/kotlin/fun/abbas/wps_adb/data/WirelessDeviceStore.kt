package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.ScreenFormFactor
import java.io.File

class WirelessDeviceStore(
    private val storeFile: File = defaultStoreFile(),
) {
    fun load(): List<SavedWirelessDevice> {
        if (!storeFile.exists()) return emptyList()
        return storeFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull(::parseLine)
            .distinctBy { it.endpoint }
    }

    fun addOrUpdate(device: SavedWirelessDevice) {
        val updated = load()
            .filterNot { it.endpoint == device.endpoint } + device
        writeAll(updated)
    }

    fun remove(endpoint: String) {
        val current = load()
        val updated = current.filterNot { it.endpoint == endpoint }
        if (updated.size == current.size) return
        writeAll(updated)
    }

    fun updateFromDevice(device: Device) {
        if (':' !in device.serial) return
        val host = device.serial.substringBeforeLast(':')
        val port = device.serial.substringAfterLast(':').toIntOrNull() ?: return
        addOrUpdate(
            SavedWirelessDevice(
                host = host,
                port = port,
                name = device.name,
                formFactor = device.formFactor,
                screenWidthPx = device.screenWidthPx,
                screenHeightPx = device.screenHeightPx,
            ),
        )
    }

    private fun writeAll(devices: List<SavedWirelessDevice>) {
        storeFile.parentFile?.mkdirs()
        storeFile.writeText(
            devices.joinToString("\n") { saved ->
                listOf(
                    saved.host,
                    saved.port.toString(),
                    saved.name.replace('\t', ' '),
                    saved.formFactor.name,
                    saved.screenWidthPx.toString(),
                    saved.screenHeightPx.toString(),
                ).joinToString("\t")
            },
        )
    }

    private fun parseLine(line: String): SavedWirelessDevice? {
        val parts = line.split('\t')
        if (parts.size < 2) return null
        val host = parts[0].trim()
        val port = parts[1].trim().toIntOrNull() ?: return null
        if (host.isBlank()) return null
        val name = parts.getOrNull(2)?.trim().orEmpty()
        val formFactor = parts.getOrNull(3)
            ?.trim()
            ?.let { runCatching { ScreenFormFactor.valueOf(it) }.getOrNull() }
            ?: ScreenFormFactor.PHONE
        val screenWidthPx = parts.getOrNull(4)?.trim()?.toIntOrNull() ?: 0
        val screenHeightPx = parts.getOrNull(5)?.trim()?.toIntOrNull() ?: 0
        return SavedWirelessDevice(
            host = host,
            port = port,
            name = name,
            formFactor = formFactor,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
    }

    companion object {
        fun defaultStoreFile(): File {
            val dir = File(System.getProperty("user.home"), ".wps-adb-tool")
            dir.mkdirs()
            return File(dir, "wireless-devices.txt")
        }
    }
}
