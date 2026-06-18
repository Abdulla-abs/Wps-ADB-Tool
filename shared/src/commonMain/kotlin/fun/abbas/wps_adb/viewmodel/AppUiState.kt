package `fun`.abbas.wps_adb.viewmodel

import `fun`.abbas.wps_adb.model.ApkInstallToast
import `fun`.abbas.wps_adb.model.ApkReinstallPrompt
import `fun`.abbas.wps_adb.model.EasyActionToast
import `fun`.abbas.wps_adb.model.DeviceShellSession
import `fun`.abbas.wps_adb.model.DeviceWallRoute
import `fun`.abbas.wps_adb.model.EasyActionKind
import `fun`.abbas.wps_adb.model.FilterTab
import `fun`.abbas.wps_adb.model.NavTab
import `fun`.abbas.wps_adb.model.PairingMethod
import `fun`.abbas.wps_adb.model.SettingsSaveToast
import `fun`.abbas.wps_adb.model.ShellTransitionKind
import `fun`.abbas.wps_adb.model.SidePanelState
import `fun`.abbas.wps_adb.model.SortParam
import `fun`.abbas.wps_adb.model.DecompileWorkspace
import `fun`.abbas.wps_adb.model.FileNode
import `fun`.abbas.wps_adb.model.EditorTab
import `fun`.abbas.wps_adb.model.DexSearchHit
import `fun`.abbas.wps_adb.model.RecentDecompileProject
import `fun`.abbas.wps_adb.model.StringConstantItem

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
    val deviceCustomOrder: List<String> = emptyList(),
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
    val apkReinstallPrompt: ApkReinstallPrompt? = null,
    val easyActionToast: EasyActionToast? = null,
    val settingsSaveToast: SettingsSaveToast? = null,
    val deviceWallRoute: DeviceWallRoute = DeviceWallRoute.Grid,
    val shellSession: DeviceShellSession? = null,
    val shellTransitionKind: ShellTransitionKind = ShellTransitionKind.SHARED_ELEMENT,
    val pendingDestructiveAction: EasyActionKind? = null,
    val pendingPackageAction: EasyActionKind? = null,
    val pendingEasyActionPackageName: String? = null,
    
    // Decompile state fields
    val decompileWorkspace: DecompileWorkspace? = null,
    val fileTreeRoot: FileNode.Folder? = null,
    val openTabs: List<EditorTab> = emptyList(),
    val activeTabId: String? = null,
    val decompileProgress: Float? = null,
    val currentTaskName: String = "",
    val showDexDialogForFile: FileNode.File? = null,

    val activeDexEditorProject: String? = null,
    val dexBrowseTree: FileNode.Folder? = null,
    val dexSearchQuery: String = "",
    val dexSearchResults: List<DexSearchHit> = emptyList(),
    val dexConstantsList: List<StringConstantItem> = emptyList(),

    val showDexMultiSelectDialog: Boolean = false,
    val dexMultiSelectCandidates: List<FileNode.File> = emptyList(),
    val dexMultiSelectDefaultPath: String? = null,
    val dexEditorSourceDexFiles: List<FileNode.File> = emptyList(),

    val recentDecompileProjects: List<RecentDecompileProject> = emptyList(),
    val showDecompileProjectManager: Boolean = false,
)
