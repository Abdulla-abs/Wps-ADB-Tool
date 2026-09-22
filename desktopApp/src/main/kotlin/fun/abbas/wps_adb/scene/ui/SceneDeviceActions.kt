package `fun`.abbas.wps_adb.scene.ui

import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.platform.pickApkFile
import `fun`.abbas.wps_adb.viewmodel.AppViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Adapter interface defining device actions executable from the 3D Scene Inspector.
 * Decouples the inspector UI from [AppViewModel].
 */
interface SceneDeviceActions {
    fun mirror(device: Device)
    fun terminal(device: Device)
    fun logcat(deviceId: String)
    fun reconnect(deviceId: String)
    fun installApk(deviceId: String)
}

class DefaultSceneDeviceActions(
    private val viewModel: AppViewModel,
    private val scope: CoroutineScope,
    private val apkPicker: suspend () -> String? = { pickApkFile() },
) : SceneDeviceActions {

    override fun mirror(device: Device) {
        viewModel.onMirrorDevice(device)
    }

    override fun terminal(device: Device) {
        viewModel.onTerminalDevice(device)
    }

    override fun logcat(deviceId: String) {
        viewModel.openDeviceLogcat(deviceId)
    }

    override fun reconnect(deviceId: String) {
        viewModel.reconnectDevice(deviceId)
    }

    override fun installApk(deviceId: String) {
        scope.launch {
            val apkPath = apkPicker()
            if (apkPath != null) {
                viewModel.installApkOnDevice(deviceId, apkPath)
            }
        }
    }
}
