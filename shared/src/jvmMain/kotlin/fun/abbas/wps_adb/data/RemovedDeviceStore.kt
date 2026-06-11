package `fun`.abbas.wps_adb.data

import java.io.File

class RemovedDeviceStore(
    private val storeFile: File = defaultStoreFile(),
) {
    fun load(): Set<String> {
        if (!storeFile.exists()) return emptySet()
        return storeFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
    }

    fun add(serial: String) {
        if (serial.isBlank()) return
        val updated = load() + serial
        writeAll(updated)
    }

    fun remove(serial: String) {
        if (serial.isBlank()) return
        val current = load()
        val updated = current - serial
        if (updated.size == current.size) return
        writeAll(updated)
    }

    fun contains(serial: String): Boolean = serial in load()

    private fun writeAll(serials: Set<String>) {
        storeFile.parentFile?.mkdirs()
        storeFile.writeText(serials.sorted().joinToString("\n"))
    }

    companion object {
        fun defaultStoreFile(): File {
            val dir = File(System.getProperty("user.home"), ".wps-adb-tool")
            dir.mkdirs()
            return File(dir, "removed-devices.txt")
        }
    }
}
