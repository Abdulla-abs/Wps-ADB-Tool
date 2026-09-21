package `fun`.abbas.wps_adb.data.scene.bridge

import `fun`.abbas.wps_adb.model.scene.BindingStatus
import `fun`.abbas.wps_adb.model.scene.ResolvedBinding
import `fun`.abbas.wps_adb.model.scene.ResolvedSceneState

/**
 * Projects runtime domain state ([ResolvedSceneState]) into bridge visual representations.
 * Strictly separates visual styling from persistent ADB domains.
 */
interface SceneVisualProjector {
    fun project(state: ResolvedSceneState, selectedObjectId: String? = null): SceneVisualSnapshot
    fun projectBinding(binding: ResolvedBinding): DeviceVisualDescriptor
}

class DefaultSceneVisualProjector : SceneVisualProjector {

    override fun project(state: ResolvedSceneState, selectedObjectId: String?): SceneVisualSnapshot {
        val descriptors = state.bindings.map { projectBinding(it) }
        return SceneVisualSnapshot(
            sceneId = state.scene.id,
            devices = descriptors,
            selectedObjectId = selectedObjectId,
        )
    }

    override fun projectBinding(binding: ResolvedBinding): DeviceVisualDescriptor {
        val objectId = binding.binding.objectId
        val deviceIdentity = binding.identity?.value ?: binding.binding.deviceIdentity.value
        val device = binding.device

        val status: VisualStatus
        val style: VisualStyle
        val displayName: String
        val connectionTypeStr: String

        when (binding.status) {
            BindingStatus.ONLINE -> {
                status = VisualStatus.ONLINE
                displayName = device?.name?.takeIf { it.isNotBlank() } ?: objectId
                val connType = device?.connectionType?.name ?: "USB"
                connectionTypeStr = connType
                style = VisualStyle(
                    statusColorHex = COLOR_ONLINE,
                    isEmissive = true,
                    emissiveIntensity = EMISSIVE_ONLINE,
                    badgeText = "ONLINE ($connType)",
                    isDimmed = false,
                )
            }
            BindingStatus.OFFLINE -> {
                if (binding.identity == null && device == null) {
                    status = VisualStatus.UNBOUND
                    displayName = objectId
                    connectionTypeStr = "NONE"
                    style = VisualStyle(
                        statusColorHex = COLOR_UNBOUND,
                        isEmissive = false,
                        emissiveIntensity = 0.0f,
                        badgeText = "UNBOUND",
                        isDimmed = false,
                    )
                } else {
                    status = VisualStatus.OFFLINE
                    displayName = device?.name?.takeIf { it.isNotBlank() } ?: objectId
                    connectionTypeStr = device?.connectionType?.name ?: "OFFLINE"
                    style = VisualStyle(
                        statusColorHex = COLOR_OFFLINE,
                        isEmissive = false,
                        emissiveIntensity = 0.0f,
                        badgeText = "OFFLINE",
                        isDimmed = true,
                    )
                }
            }
        }

        return DeviceVisualDescriptor(
            objectId = objectId,
            deviceIdentity = deviceIdentity,
            displayName = displayName,
            status = status,
            connectionType = connectionTypeStr,
            visual = style,
        )
    }

    companion object {
        const val COLOR_ONLINE = "#10B981"
        const val COLOR_OFFLINE = "#64748B"
        const val COLOR_UNBOUND = "#3B82F6"
        const val EMISSIVE_ONLINE = 0.8f
    }
}
