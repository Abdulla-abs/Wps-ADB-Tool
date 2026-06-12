package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.MockAdbRepository
import `fun`.abbas.wps_adb.data.NoOpDeviceShellService
import `fun`.abbas.wps_adb.viewmodel.LogTrayMode
import `fun`.abbas.wps_adb.model.DeviceShellSessionState
import `fun`.abbas.wps_adb.model.DeviceWallRoute
import `fun`.abbas.wps_adb.viewmodel.AppViewModel
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelDeviceShellTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onTerminalDevice sets shell route and session`() = runTest(dispatcher) {
        val repo = MockAdbRepository(initialScanDelayMs = 0)
        val vm = AppViewModel(repo, deviceShellService = NoOpDeviceShellService())
        val stateCollector = launch { vm.uiState.collect { } }
        advanceUntilIdle()
        val device = repo.devices.value.first()
        vm.onTerminalDevice(device)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.deviceWallRoute is DeviceWallRoute.Shell)
        assertNotNull(vm.uiState.value.shellSession)
        assertEquals(device.id, vm.uiState.value.shellSession?.deviceId)
        stateCollector.cancel()
    }

    @Test
    fun `closeDeviceShell returns to grid route`() = runTest(dispatcher) {
        val repo = MockAdbRepository(initialScanDelayMs = 0)
        val shellService = FakeDeviceShellService()
        val vm = AppViewModel(repo, deviceShellService = shellService)
        val stateCollector = launch { vm.uiState.collect { } }
        advanceUntilIdle()
        val device = repo.devices.value.first()
        vm.onTerminalDevice(device)
        advanceUntilIdle()
        vm.closeDeviceShell()
        advanceUntilIdle()
        assertEquals(DeviceWallRoute.Grid, vm.uiState.value.deviceWallRoute)
        assertEquals(1, shellService.stopCount)
        stateCollector.cancel()
    }

    @Test
    fun `openShellDeviceLogcat opens tray in logcat mode`() = runTest(dispatcher) {
        val repo = MockAdbRepository(initialScanDelayMs = 0)
        val vm = AppViewModel(repo, deviceShellService = FakeDeviceShellService())
        val stateCollector = launch { vm.uiState.collect { } }
        advanceUntilIdle()
        val device = repo.devices.value.first()
        vm.onTerminalDevice(device)
        advanceUntilIdle()
        vm.openShellDeviceLogcat()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLogTrayOpen)
        assertEquals(LogTrayMode.LOGCAT, vm.uiState.value.logTrayMode)
        assertEquals(device.id, vm.uiState.value.logcatDeviceFilter)
        stateCollector.cancel()
    }
}
