package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.DeviceIdentity
import `fun`.abbas.wps_adb.model.DeviceIdentitySource

object JvmDeviceIdentityResolver {
    private fun isValidSerial(value: String): Boolean =
        value.isNotBlank() &&
            !value.equals("unknown", ignoreCase = true) &&
            !value.equals("null", ignoreCase = true) &&
            !value.contains("error:", ignoreCase = true)

    fun resolveIdentity(
        runner: JvmAdbRunner,
        serial: String,
        isEmulator: Boolean,
        deviceName: String? = null,
    ): DeviceIdentity {
        // Priority 1: ro.serialno
        val serialnoResult = runner.run(listOf("shell", "getprop", "ro.serialno"), serial = serial)
        val serialno = serialnoResult.output.trim()
        if (serialnoResult.success && isValidSerial(serialno)) {
            return DeviceIdentity(
                value = serialno,
                source = DeviceIdentitySource.RO_SERIALNO,
                rawHardwareSerial = serialno,
            )
        }

        // Priority 2: ro.boot.serialno
        val bootSerialResult = runner.run(listOf("shell", "getprop", "ro.boot.serialno"), serial = serial)
        val bootSerial = bootSerialResult.output.trim()
        if (bootSerialResult.success && isValidSerial(bootSerial)) {
            return DeviceIdentity(
                value = bootSerial,
                source = DeviceIdentitySource.RO_BOOT_SERIALNO,
                rawHardwareSerial = bootSerial,
            )
        }

        // Priority 3: emulator identity fallback
        if (isEmulator) {
            val avdNameResult = runner.run(listOf("emu", "avd", "name"), serial = serial)
            val avdName = avdNameResult.output.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            if (avdNameResult.success && !avdName.isNullOrBlank() && isValidSerial(avdName) && !avdName.contains("KO", ignoreCase = true)) {
                return DeviceIdentity(
                    value = avdName,
                    source = DeviceIdentitySource.EMULATOR_FALLBACK,
                    rawHardwareSerial = null,
                )
            }
            val qemuAvdResult = runner.run(listOf("shell", "getprop", "ro.boot.qemu.avd_name"), serial = serial)
            val qemuAvd = qemuAvdResult.output.trim()
            if (qemuAvdResult.success && isValidSerial(qemuAvd)) {
                return DeviceIdentity(
                    value = qemuAvd,
                    source = DeviceIdentitySource.EMULATOR_FALLBACK,
                    rawHardwareSerial = null,
                )
            }
            return DeviceIdentity(
                value = serial,
                source = DeviceIdentitySource.EMULATOR_FALLBACK,
                rawHardwareSerial = null,
            )
        }

        // Priority 4: transport fallback
        val fallback = deviceName?.takeIf { it.isNotBlank() } ?: serial
        return DeviceIdentity(
            value = fallback,
            source = DeviceIdentitySource.TRANSPORT_FALLBACK,
            rawHardwareSerial = null,
        )
    }
}
