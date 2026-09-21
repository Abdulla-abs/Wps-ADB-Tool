package `fun`.abbas.wps_adb.model.scene

import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceIdentity

data class ResolvedBinding(
    val binding: SceneBinding,
    val identity: DeviceIdentity?,
    val device: Device?,
    val status: BindingStatus,
)
