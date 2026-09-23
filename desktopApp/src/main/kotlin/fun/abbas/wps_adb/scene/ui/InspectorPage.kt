package `fun`.abbas.wps_adb.scene.ui

/**
 * Temporary local UI navigation state for the 3D Scene Inspector.
 * Keeps all scene/asset operations confined to the right-hand panel
 * without triggering Compose floating popups occluded by JCEF.
 */
sealed interface InspectorPage {
    data object Overview : InspectorPage
    data object ImportScene : InspectorPage
    data object ManageScenes : InspectorPage
    data object ImportAsset : InspectorPage
}
