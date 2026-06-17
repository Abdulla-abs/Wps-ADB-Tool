package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.MockAdbRepository
import `fun`.abbas.wps_adb.data.NoOpDeviceShellService
import `fun`.abbas.wps_adb.viewmodel.LogTrayMode
import `fun`.abbas.wps_adb.model.DeviceShellSessionState
import `fun`.abbas.wps_adb.model.DeviceWallRoute
import `fun`.abbas.wps_adb.model.EasyActionKind
import `fun`.abbas.wps_adb.model.EasyActionToastKind
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
import kotlin.test.assertNull
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

    @Test
    fun `clear app data requires package then destructive confirmation`() = runTest(dispatcher) {
        val repo = MockAdbRepository(initialScanDelayMs = 0)
        val vm = AppViewModel(repo, deviceShellService = FakeDeviceShellService())
        val stateCollector = launch { vm.uiState.collect { } }
        advanceUntilIdle()
        vm.onTerminalDevice(repo.devices.value.first())
        advanceUntilIdle()

        vm.onEasyAction(EasyActionKind.CLEAR_APP_DATA)
        advanceUntilIdle()
        assertEquals(EasyActionKind.CLEAR_APP_DATA, vm.uiState.value.pendingPackageAction)
        assertNull(vm.uiState.value.pendingDestructiveAction)

        vm.confirmPackageEasyAction("com.example.app")
        advanceUntilIdle()
        assertNull(vm.uiState.value.pendingPackageAction)
        assertEquals(EasyActionKind.CLEAR_APP_DATA, vm.uiState.value.pendingDestructiveAction)
        assertEquals("com.example.app", vm.uiState.value.pendingEasyActionPackageName)

        vm.confirmDestructiveEasyAction()
        advanceUntilIdle()
        assertNull(vm.uiState.value.pendingDestructiveAction)
        assertNull(vm.uiState.value.pendingEasyActionPackageName)
        stateCollector.cancel()
    }

    @Test
    fun `take screenshot shows success toast`() = runTest(dispatcher) {
        val repo = MockAdbRepository(initialScanDelayMs = 0)
        val vm = AppViewModel(repo, deviceShellService = FakeDeviceShellService())
        val stateCollector = launch { vm.uiState.collect { } }
        advanceUntilIdle()
        val device = repo.devices.value.first()
        vm.onTerminalDevice(device)
        advanceUntilIdle()

        vm.onEasyAction(EasyActionKind.TAKE_SCREENSHOT)
        advanceUntilIdle()

        val toast = vm.uiState.value.easyActionToast
        assertNotNull(toast)
        assertEquals(EasyActionToastKind.SCREENSHOT_SAVED, toast.kind)
        assertEquals(device.name, toast.deviceName)
        assertTrue(toast.success)
        stateCollector.cancel()
    }
}
