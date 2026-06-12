package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.MockData
import `fun`.abbas.wps_adb.model.ApkInstallResult
import `fun`.abbas.wps_adb.model.ApkMetadata
import `fun`.abbas.wps_adb.model.ScrcpyConnectionOptions
import `fun`.abbas.wps_adb.model.SidePanelDrawerState
import `fun`.abbas.wps_adb.model.SidePanelState
import `fun`.abbas.wps_adb.model.SidePanelTab
import `fun`.abbas.wps_adb.viewmodel.SidePanelController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SidePanelControllerTest {

    private val pixel = MockData.initialDevices[0]
    private val galaxy = MockData.initialDevices[1]
    private val defaultConnection = ScrcpyConnectionOptions()

    @Test
    fun openMirrorTab_expandsDrawer() {
        val result = SidePanelController.openMirrorTab(SidePanelState(), pixel, defaultConnection)

        assertEquals(SidePanelDrawerState.Expanded, result.state.drawerState)
    }

    @Test
    fun openMirrorTab_focusesExistingTabForSameDevice() {
        val first = SidePanelController.openMirrorTab(SidePanelState(), pixel, defaultConnection)
        val second = SidePanelController.openMirrorTab(first.state, pixel, defaultConnection)

        assertEquals(1, second.state.tabs.size)
        assertEquals(SidePanelController.mirrorTabId(pixel.id), second.state.activeTabId)
        assertTrue(second.evictedTabIds.isEmpty())
    }

    @Test
    fun openMirrorTab_createsSeparateTabsForDifferentDevices() {
        val first = SidePanelController.openMirrorTab(SidePanelState(), pixel, defaultConnection)
        val second = SidePanelController.openMirrorTab(first.state, galaxy, defaultConnection)

        assertEquals(2, second.state.tabs.size)
        assertEquals(SidePanelController.mirrorTabId(galaxy.id), second.state.activeTabId)
    }

    @Test
    fun closeTab_removesTabAndSelectsRemainingActiveTab() {
        val opened = SidePanelController.openMirrorTab(SidePanelState(), pixel, defaultConnection)
        val withTwo = SidePanelController.openMirrorTab(opened.state, galaxy, defaultConnection)
        val closed = SidePanelController.closeTab(withTwo.state, withTwo.state.activeTabId!!)

        assertEquals(1, closed.tabs.size)
        assertEquals(SidePanelController.mirrorTabId(pixel.id), closed.activeTabId)
    }

    @Test
    fun closeTabsForDevice_removesOnlyMatchingDeviceTabs() {
        val opened = SidePanelController.openMirrorTab(SidePanelState(), pixel, defaultConnection)
        val withTwo = SidePanelController.openMirrorTab(opened.state, galaxy, defaultConnection)
        val (remaining, removed) = SidePanelController.closeTabsForDevice(withTwo.state, pixel.id)

        assertEquals(1, remaining.tabs.size)
        assertEquals(listOf(SidePanelController.mirrorTabId(pixel.id)), removed)
        assertTrue(remaining.tabs.single() is SidePanelTab.Mirror)
        assertEquals(galaxy.id, remaining.tabs.single().device.id)
    }

    @Test
    fun openDebugTab_createsAwaitingApkTabAndFocusesExisting() {
        val first = SidePanelController.openDebugTab(SidePanelState(), pixel)
        val second = SidePanelController.openDebugTab(first.state, pixel)

        assertEquals(1, second.state.tabs.size)
        val tab = second.state.tabs.single() as SidePanelTab.AppLog
        assertEquals(SidePanelController.debugTabId(pixel.id), tab.id)
        assertTrue(tab.awaitingApk)
        assertEquals(first.state.activeTabId, second.state.activeTabId)
    }

    @Test
    fun openAppLogTab_updatesAwaitingDebugTabWithInstallResult() {
        val debug = SidePanelController.openDebugTab(SidePanelState(), pixel)
        val result = ApkInstallResult(
            success = true,
            message = "Success",
            apkPath = "/tmp/demo.apk",
            apkFileName = "demo.apk",
            metadata = ApkMetadata("com.mock.demo"),
        )
        val updated = SidePanelController.openAppLogTab(debug.state, pixel, result)

        assertEquals(1, updated.state.tabs.size)
        val tab = updated.state.tabs.single() as SidePanelTab.AppLog
        assertEquals(SidePanelController.debugTabId(pixel.id), tab.id)
        assertEquals(false, tab.awaitingApk)
        assertEquals("demo.apk", tab.apkFileName)
        assertEquals("com.mock.demo", tab.packageName)
    }

    @Test
    fun openAppLogTab_focusesExistingTabForSameDeviceAndApk() {
        val result = ApkInstallResult(
            success = true,
            message = "Success",
            apkPath = "/tmp/demo.apk",
            apkFileName = "demo.apk",
            metadata = ApkMetadata("com.mock.demo"),
        )
        val first = SidePanelController.openAppLogTab(SidePanelState(), pixel, result)
        val second = SidePanelController.openAppLogTab(first.state, pixel, result)

        assertEquals(1, second.state.tabs.size)
        assertTrue(second.state.tabs.single() is SidePanelTab.AppLog)
        assertEquals(first.state.activeTabId, second.state.activeTabId)
    }

    @Test
    fun openMirrorTab_evictsOldestTabWhenExceedingMax() {
        var state = SidePanelState()
        val devices = MockData.initialDevices
        val evictedAll = mutableListOf<String>()

        repeat(SidePanelController.MAX_TABS + 1) { index ->
            val device = devices[index % devices.size].copy(id = "device_$index", name = "Device $index")
            val result = SidePanelController.openMirrorTab(state, device, defaultConnection)
            state = result.state
            evictedAll += result.evictedTabIds
        }

        assertEquals(SidePanelController.MAX_TABS, state.tabs.size)
        assertTrue(evictedAll.isNotEmpty())
    }
}
