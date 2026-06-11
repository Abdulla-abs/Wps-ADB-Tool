package `fun`.abbas.wps_adb.viewmodel

import `fun`.abbas.wps_adb.model.ApkInstallResult
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.ScrcpyConnectionOptions
import `fun`.abbas.wps_adb.model.SidePanelState
import `fun`.abbas.wps_adb.model.SidePanelTab

object SidePanelController {
    const val MAX_TABS = 8

    fun mirrorTabId(deviceId: String): String = "mirror_$deviceId"

    fun appLogTabId(deviceId: String, apkFileName: String): String =
        "applog_${deviceId}_${apkFileName.hashCode()}"

    fun openAppLogTab(state: SidePanelState, device: Device, result: ApkInstallResult): OpenTabResult {
        val existing = state.tabs
            .filterIsInstance<SidePanelTab.AppLog>()
            .find { it.device.id == device.id && it.apkFileName == result.apkFileName }
        if (existing != null) {
            return OpenTabResult(
                state = state.copy(activeTabId = existing.id),
                evictedTabIds = emptyList(),
            )
        }
        val tab = SidePanelTab.AppLog(
            id = appLogTabId(device.id, result.apkFileName),
            title = "${device.name} · ${result.apkFileName}",
            device = device,
            apkPath = result.apkPath,
            apkFileName = result.apkFileName,
            packageName = result.metadata?.packageName,
            launchActivity = result.metadata?.launchActivity,
            appLabel = result.metadata?.appLabel,
        )
        return insertTab(state, tab)
    }

    fun openMirrorTab(
        state: SidePanelState,
        device: Device,
        connectionOptions: ScrcpyConnectionOptions,
    ): OpenTabResult {
        val existing = state.tabs
            .filterIsInstance<SidePanelTab.Mirror>()
            .find { it.device.id == device.id }
        if (existing != null) {
            return OpenTabResult(
                state = state.copy(activeTabId = existing.id),
                evictedTabIds = emptyList(),
            )
        }
        val tab = SidePanelTab.Mirror(
            id = mirrorTabId(device.id),
            title = device.name,
            device = device,
            connectionOptions = connectionOptions,
        )
        return insertTab(state, tab)
    }

    fun closeTab(state: SidePanelState, tabId: String): SidePanelState {
        val remaining = state.tabs.filter { it.id != tabId }
        val activeTabId = when {
            state.activeTabId != tabId -> state.activeTabId?.takeIf { id -> remaining.any { it.id == id } }
            else -> remaining.lastOrNull()?.id
        }
        return state.copy(tabs = remaining, activeTabId = activeTabId)
    }

    fun closeTabsForDevice(state: SidePanelState, deviceId: String): Pair<SidePanelState, List<String>> {
        val removedIds = state.tabs.filter { it.device.id == deviceId }.map { it.id }
        if (removedIds.isEmpty()) {
            return state to emptyList()
        }
        val remaining = state.tabs.filter { it.device.id != deviceId }
        val activeTabId = state.activeTabId?.takeIf { id -> remaining.any { it.id == id } }
            ?: remaining.lastOrNull()?.id
        return state.copy(tabs = remaining, activeTabId = activeTabId) to removedIds
    }

    fun clearAll(state: SidePanelState): Pair<SidePanelState, List<String>> {
        val removedIds = state.tabs.map { it.id }
        return SidePanelState() to removedIds
    }

    private fun insertTab(state: SidePanelState, tab: SidePanelTab): OpenTabResult {
        var tabs = state.tabs + tab
        val evicted = mutableListOf<String>()
        while (tabs.size > MAX_TABS) {
            val candidate = tabs.firstOrNull { it.id != tab.id && it.id != state.activeTabId }
                ?: tabs.first { it.id != tab.id }
            evicted += candidate.id
            tabs = tabs.filter { it.id != candidate.id }
        }
        return OpenTabResult(
            state = state.copy(tabs = tabs, activeTabId = tab.id),
            evictedTabIds = evicted,
        )
    }
}

data class OpenTabResult(
    val state: SidePanelState,
    val evictedTabIds: List<String>,
)
