package `fun`.abbas.wps_adb.data.scene

import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.scene.BindingStatus
import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.ResolvedBinding
import `fun`.abbas.wps_adb.model.scene.ResolvedSceneState

class DefaultSceneBindingResolver : SceneBindingResolver {
    override fun resolve(
        scene: DeviceScene,
        devices: List<Device>,
    ): ResolvedSceneState {
        val devicesByIdentity = devices.associateBy { it.identity.value }

        val resolvedBindings = scene.bindings.map { binding ->
            val matchedDevice = devicesByIdentity[binding.deviceIdentity.value]
            val status = when {
                matchedDevice == null -> BindingStatus.OFFLINE
                matchedDevice.status == DeviceStatus.ONLINE -> BindingStatus.ONLINE
                else -> BindingStatus.OFFLINE
            }

            ResolvedBinding(
                binding = binding,
                identity = matchedDevice?.identity,
                device = matchedDevice,
                status = status,
            )
        }

        return ResolvedSceneState(
            scene = scene,
            bindings = resolvedBindings,
        )
    }
}
