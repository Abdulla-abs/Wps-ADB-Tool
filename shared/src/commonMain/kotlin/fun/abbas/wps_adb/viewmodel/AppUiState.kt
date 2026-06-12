package `fun`.abbas.wps_adb.viewmodel

import `fun`.abbas.wps_adb.model.ApkInstallToast
import `fun`.abbas.wps_adb.model.DeviceShellSession
import `fun`.abbas.wps_adb.model.DeviceWallRoute
import `fun`.abbas.wps_adb.model.EasyActionKind
import `fun`.abbas.wps_adb.model.FilterTab
import `fun`.abbas.wps_adb.model.NavTab
import `fun`.abbas.wps_adb.model.PairingMethod
import `fun`.abbas.wps_adb.model.ShellTransitionKind
import `fun`.abbas.wps_adb.model.SidePanelState
import `fun`.abbas.wps_adb.model.SortParam

enum class LogTrayMode { EVENTS, LOGCAT }

object LogTrayHeightLimits {
    const val DEFAULT = 280f
    const val MIN = 120f
    const val MAX = 600f
}

data class AppUiState(
    val activeTab: NavTab = NavTab.WALL,
    val filterTab: FilterTab = FilterTab.ALL,
    val searchQuery: String = "",
    val sortParam: SortParam = SortParam.NAME,
    val isLogTrayOpen: Boolean = false,
    val logTrayHeightDp: Float = LogTrayHeightLimits.DEFAULT,
    val logTrayMode: LogTrayMode = LogTrayMode.EVENTS,
    val logcatDeviceFilter: String? = null,
    val isPairingDialogOpen: Boolean = false,
    val pairingMethod: PairingMethod = PairingMethod.LEGACY_TCP,
    val sidePanel: SidePanelState = SidePanelState(),
    val isAdbActive: Boolean = true,
    val isRestartingAdb: Boolean = false,
    val isScanningDevices: Boolean = false,
    val apkInstallToast: ApkInstallToast? = null,
    val deviceWallRoute: DeviceWallRoute = DeviceWallRoute.Grid,
    val shellSession: DeviceShellSession? = null,
    val shellTransitionKind: ShellTransitionKind = ShellTransitionKind.SHARED_ELEMENT,
    val pendingDestructiveAction: EasyActionKind? = null,
    val pendingPackageAction: EasyActionKind? = null,
)
