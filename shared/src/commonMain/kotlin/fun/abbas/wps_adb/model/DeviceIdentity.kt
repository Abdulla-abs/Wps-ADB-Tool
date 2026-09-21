package `fun`.abbas.wps_adb.model

import `fun`.abbas.wps_adb.model.scene.DeviceIdentityRef

enum class DeviceIdentitySource {
    RO_SERIALNO,
    RO_BOOT_SERIALNO,
    EMULATOR_FALLBACK,
    TRANSPORT_FALLBACK,
}

data class DeviceIdentity(
    val value: String,
    val source: DeviceIdentitySource = DeviceIdentitySource.TRANSPORT_FALLBACK,
    val rawHardwareSerial: String? = null,
) {
    fun toIdentityRef(): DeviceIdentityRef = DeviceIdentityRef(value)

    companion object {
        fun fromTransport(serial: String): DeviceIdentity = DeviceIdentity(
            value = serial,
            source = DeviceIdentitySource.TRANSPORT_FALLBACK,
            rawHardwareSerial = null,
        )

        fun fromEmulator(serial: String, avdName: String? = null): DeviceIdentity = DeviceIdentity(
            value = avdName?.takeIf { it.isNotBlank() } ?: serial,
            source = DeviceIdentitySource.EMULATOR_FALLBACK,
            rawHardwareSerial = null,
        )
    }
}
