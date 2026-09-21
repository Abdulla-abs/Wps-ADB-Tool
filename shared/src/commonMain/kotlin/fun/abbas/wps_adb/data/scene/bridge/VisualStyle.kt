package `fun`.abbas.wps_adb.data.scene.bridge

/**
 * Visual styling properties for rendering an object in the 3D scene.
 * Decoupled from transient UI interaction states like selection.
 */
data class VisualStyle(
    val statusColorHex: String,
    val isEmissive: Boolean,
    val emissiveIntensity: Float,
    val badgeText: String,
    val isDimmed: Boolean,
)
