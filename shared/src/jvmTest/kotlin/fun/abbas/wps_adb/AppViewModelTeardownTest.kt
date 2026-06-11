package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.MockAdbRepository
import `fun`.abbas.wps_adb.data.MockData
import `fun`.abbas.wps_adb.data.TabSessionManager
import `fun`.abbas.wps_adb.model.AppLogMonitorState
import `fun`.abbas.wps_adb.model.DeviceAction
import `fun`.abbas.wps_adb.model.SidePanelTab
import `fun`.abbas.wps_adb.model.TabListenKind
import `fun`.abbas.wps_adb.viewmodel.AppViewModel
import `fun`.abbas.wps_adb.viewmodel.SidePanelController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTeardownTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var sessionManager: RecordingTabSessionManager

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
        sessionManager = RecordingTabSessionManager()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun closeSidePanelTab_whileMonitoring_teardownsSession() = runTest(dispatcher) {
        val repository = MockAdbRepository(initialScanDelayMs = 0)
        val viewModel = AppViewModel(repository, sessionManager)
        val stateCollector = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        val device = repository.devices.value.first()

        viewModel.installApkOnDevice(device.id, "C:\\temp\\demo.apk")
        advanceUntilIdle()

        val tabId = viewModel.uiState.value.sidePanel.tabs.single().id
        viewModel.toggleLogMonitorInTab(tabId)
        advanceUntilIdle()
        delay(100)
        advanceUntilIdle()

        val monitoringTab = viewModel.uiState.value.sidePanel.tabs
            .filterIsInstance<SidePanelTab.AppLog>()
            .single()
        assertEquals(AppLogMonitorState.MONITORING, monitoringTab.monitorState)

        viewModel.closeSidePanelTab(tabId)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.sidePanel.isVisible)
        assertTrue(tabId in sessionManager.stopAllTabIds)
        stateCollector.cancel()
    }

    @Test
    fun disconnectDevice_closesMatchingTabsAndStopsSessions() = runTest(dispatcher) {
        val repository = MockAdbRepository(initialScanDelayMs = 0)
        val viewModel = AppViewModel(repository, sessionManager)
        val stateCollector = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        val device = repository.devices.value.first()

        viewModel.onMirrorDevice(device)
        advanceUntilIdle()
        val tabId = SidePanelController.mirrorTabId(device.id)

        viewModel.onDeviceAction(device.id, DeviceAction.DISCONNECT)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.sidePanel.tabs.any { it.device.id == device.id })
        assertTrue(tabId in sessionManager.stopAllTabIds)
        stateCollector.cancel()
    }

    @Test
    fun killAdb_clearsSidePanelAndStopsAllSessions() = runTest(dispatcher) {
        val repository = MockAdbRepository(initialScanDelayMs = 0)
        val viewModel = AppViewModel(repository, sessionManager)
        val stateCollector = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        val device = repository.devices.value.first()

        viewModel.onMirrorDevice(device)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.sidePanel.isVisible)

        viewModel.killAdb()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.sidePanel.isVisible)
        assertEquals(1, sessionManager.stopAllGlobalCount)
        stateCollector.cancel()
    }

    @Test
    fun evictedTabDuringOpenMirror_teardownsEvictedSession() = runTest(dispatcher) {
        val repository = MockAdbRepository(initialScanDelayMs = 0)
        val viewModel = AppViewModel(repository, sessionManager)
        val devices = MockData.initialDevices
        val stateCollector = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        repeat(SidePanelController.MAX_TABS) { index ->
            val device = devices[index % devices.size].copy(
                id = "device_$index",
                name = "Device $index",
            )
            viewModel.onMirrorDevice(device)
            advanceUntilIdle()
        }

        val overflowDevice = devices.first().copy(id = "device_overflow", name = "Overflow")
        viewModel.onMirrorDevice(overflowDevice)
        advanceUntilIdle()

        assertEquals(SidePanelController.MAX_TABS, viewModel.uiState.value.sidePanel.tabs.size)
        assertEquals(SidePanelController.mirrorTabId("device_0"), sessionManager.stopAllTabIds.last())
        stateCollector.cancel()
    }
}

internal class RecordingTabSessionManager : TabSessionManager {
    val stopAllTabIds = mutableListOf<String>()
    var stopAllGlobalCount = 0
    private val sessions = mutableMapOf<String, MutableSet<TabListenKind>>()

    override fun start(tabId: String, kind: TabListenKind) {
        sessions.getOrPut(tabId) { mutableSetOf() }.add(kind)
    }

    override fun stop(tabId: String, kind: TabListenKind) {
        sessions[tabId]?.remove(kind)
    }

    override fun stopAll(tabId: String) {
        stopAllTabIds += tabId
        sessions.remove(tabId)
    }

    override fun stopAll() {
        stopAllGlobalCount++
        sessions.clear()
    }

    override fun activeKinds(tabId: String): Set<TabListenKind> =
        sessions[tabId]?.toSet().orEmpty()
}
