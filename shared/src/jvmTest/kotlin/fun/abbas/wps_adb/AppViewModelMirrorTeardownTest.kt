package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.MockAdbRepository
import `fun`.abbas.wps_adb.data.ScrcpyMirrorService
import `fun`.abbas.wps_adb.data.ScrcpyStartResult
import `fun`.abbas.wps_adb.model.DeviceAction
import `fun`.abbas.wps_adb.model.MirrorSessionState
import `fun`.abbas.wps_adb.model.ScrcpyConnectionOptions
import `fun`.abbas.wps_adb.model.SidePanelTab
import `fun`.abbas.wps_adb.model.TabListenKind
import `fun`.abbas.wps_adb.viewmodel.AppViewModel
import `fun`.abbas.wps_adb.viewmodel.SidePanelController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class AppViewModelMirrorTeardownTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var sessionManager: RecordingTabSessionManager
    private lateinit var scrcpyService: FakeScrcpyMirrorService

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
        sessionManager = RecordingTabSessionManager()
        scrcpyService = FakeScrcpyMirrorService()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onMirrorDevice_startsScrcpyWhenAvailable() = runTest(dispatcher) {
        val repository = MockAdbRepository(initialScanDelayMs = 0)
        val viewModel = AppViewModel(repository, sessionManager, scrcpyService)
        val collector = launch { viewModel.uiState.collect { } }
        val device = repository.devices.value.first()

        viewModel.onMirrorDevice(device)
        advanceUntilIdle()

        val tabId = SidePanelController.mirrorTabId(device.id)
        assertTrue(tabId in scrcpyService.startedTabIds)
        val mirrorTab = viewModel.uiState.value.sidePanel.tabs.filterIsInstance<SidePanelTab.Mirror>().single()
        assertEquals(MirrorSessionState.RUNNING, mirrorTab.sessionState)
        assertTrue(TabListenKind.SCRCPY_PROCESS in sessionManager.activeKinds(tabId))
        collector.cancel()
    }

    @Test
    fun closeSidePanelTab_stopsScrcpySession() = runTest(dispatcher) {
        val repository = MockAdbRepository(initialScanDelayMs = 0)
        val viewModel = AppViewModel(repository, sessionManager, scrcpyService)
        val collector = launch { viewModel.uiState.collect { } }
        val device = repository.devices.value.first()

        viewModel.onMirrorDevice(device)
        advanceUntilIdle()
        val tabId = SidePanelController.mirrorTabId(device.id)

        viewModel.closeSidePanelTab(tabId)
        advanceUntilIdle()

        assertTrue(tabId in scrcpyService.stoppedTabIds)
        assertTrue(viewModel.uiState.value.sidePanel.isVisible)
        assertTrue(viewModel.uiState.value.sidePanel.tabs.isEmpty())
        collector.cancel()
    }

    @Test
    fun disconnectDevice_stopsScrcpyForMirrorTab() = runTest(dispatcher) {
        val repository = MockAdbRepository(initialScanDelayMs = 0)
        val viewModel = AppViewModel(repository, sessionManager, scrcpyService)
        val collector = launch { viewModel.uiState.collect { } }
        val device = repository.devices.value.first()

        viewModel.onMirrorDevice(device)
        advanceUntilIdle()
        val tabId = SidePanelController.mirrorTabId(device.id)

        viewModel.onDeviceAction(device.id, DeviceAction.DISCONNECT)
        advanceUntilIdle()

        assertTrue(tabId in scrcpyService.stoppedTabIds)
        assertFalse(viewModel.uiState.value.sidePanel.tabs.any { it.device.id == device.id })
        collector.cancel()
    }

    @Test
    fun killAdb_stopsAllScrcpySessions() = runTest(dispatcher) {
        val repository = MockAdbRepository(initialScanDelayMs = 0)
        val viewModel = AppViewModel(repository, sessionManager, scrcpyService)
        val collector = launch { viewModel.uiState.collect { } }
        val device = repository.devices.value.first()

        viewModel.onMirrorDevice(device)
        advanceUntilIdle()

        viewModel.killAdb()
        advanceUntilIdle()

        assertEquals(1, scrcpyService.stopAllCount)
        assertFalse(viewModel.uiState.value.sidePanel.isVisible)
        collector.cancel()
    }

    @Test
    fun onMirrorDevice_marksUnavailableWhenScrcpyMissing() = runTest(dispatcher) {
        scrcpyService.available = false
        val repository = MockAdbRepository(initialScanDelayMs = 0)
        val viewModel = AppViewModel(repository, sessionManager, scrcpyService)
        val collector = launch { viewModel.uiState.collect { } }
        val device = repository.devices.value.first()

        viewModel.onMirrorDevice(device)
        advanceUntilIdle()

        val mirrorTab = viewModel.uiState.value.sidePanel.tabs.filterIsInstance<SidePanelTab.Mirror>().single()
        assertEquals(MirrorSessionState.UNAVAILABLE, mirrorTab.sessionState)
        assertTrue(scrcpyService.startedTabIds.isEmpty())
        collector.cancel()
    }
}

internal class FakeScrcpyMirrorService : ScrcpyMirrorService {
    var available = true
    val startedTabIds = mutableListOf<String>()
    val stoppedTabIds = mutableListOf<String>()
    var stopAllCount = 0
    private var exitListener: ((String, Int, Boolean) -> Unit)? = null
    private val running = mutableSetOf<String>()

    override fun isAvailable(): Boolean = available

    override fun start(
        tabId: String,
        serial: String,
        deviceName: String,
        options: ScrcpyConnectionOptions,
    ): ScrcpyStartResult {
        if (running.contains(tabId)) {
            return ScrcpyStartResult(true, "Already running")
        }
        startedTabIds += tabId
        running += tabId
        return ScrcpyStartResult(true, "started")
    }

    override fun stop(tabId: String) {
        if (running.remove(tabId)) {
            stoppedTabIds += tabId
        }
    }

    override fun stopAll() {
        stopAllCount++
        running.toList().forEach(::stop)
    }

    override fun isRunning(tabId: String): Boolean = tabId in running

    override fun setExitListener(listener: ((String, Int, Boolean) -> Unit)?) {
        exitListener = listener
    }
}
