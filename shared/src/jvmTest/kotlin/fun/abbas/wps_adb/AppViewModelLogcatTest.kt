package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.MockAdbRepository
import `fun`.abbas.wps_adb.viewmodel.AppViewModel
import `fun`.abbas.wps_adb.viewmodel.LogTrayMode
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelLogcatTest {
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
    fun `onTerminalDevice opens tray logcat mode and sets device filter`() = runTest(dispatcher) {
        val repo = MockAdbRepository(initialScanDelayMs = 0)
        val vm = AppViewModel(repo)
        val stateCollector = launch { vm.uiState.collect { } }
        advanceUntilIdle()
        val device = repo.devices.value.first()
        vm.onTerminalDevice(device)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLogTrayOpen)
        assertEquals(LogTrayMode.LOGCAT, vm.uiState.value.logTrayMode)
        assertEquals(device.id, vm.uiState.value.logcatDeviceFilter)
        stateCollector.cancel()
    }
}
