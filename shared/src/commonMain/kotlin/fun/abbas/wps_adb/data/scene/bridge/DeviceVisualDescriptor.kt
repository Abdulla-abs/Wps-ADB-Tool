package `fun`.abbas.wps_adb.data.scene.bridge

/**
 * Visual descriptor for a device-bound 3D object in the renderer.
 * Purely represents visual identity and status; excludes ADB transport identifiers (like serial).
 */
data class DeviceVisualDescriptor(
    val objectId: String,
    val deviceIdentity: String?,
    val displayName: String,
    val status: VisualStatus,
    val connectionType: String,
    val visual: VisualStyle,
)
