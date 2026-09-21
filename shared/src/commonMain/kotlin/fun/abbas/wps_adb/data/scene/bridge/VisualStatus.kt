package `fun`.abbas.wps_adb.data.scene.bridge

/**
 * Visual status of a 3D object projected into the scene.
 * Decoupled from runtime device status and ADB specifics.
 */
enum class VisualStatus {
    ONLINE,
    OFFLINE,
    UNBOUND,
}
