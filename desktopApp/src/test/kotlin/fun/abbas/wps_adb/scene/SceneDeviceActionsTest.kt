package `fun`.abbas.wps_adb.scene

import `fun`.abbas.wps_adb.data.MockAdbRepository
import `fun`.abbas.wps_adb.model.DeviceWallRoute
import `fun`.abbas.wps_adb.scene.ui.DefaultSceneDeviceActions
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SceneDeviceActionsTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun actions_delegateToViewModelProperly() = runTest(dispatcher) {
        val repository = MockAdbRepository(initialScanDelayMs = 0)
        val vm = AppViewModel(
            repository = repository,
        )
        val stateCollector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val device = repository.devices.value.first()
        var pickedApkPath: String? = null

        val actions = DefaultSceneDeviceActions(
            viewModel = vm,
            scope = backgroundScope,
            apkPicker = { pickedApkPath },
        )

        // 1. Terminal action
        actions.terminal(device)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.deviceWallRoute is DeviceWallRoute.Shell)
        assertNotNull(vm.uiState.value.shellSession)
        assertEquals(device.id, vm.uiState.value.shellSession?.deviceId)

        // 2. Logcat action
        actions.logcat(device.id)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLogTrayOpen)
        assertEquals(LogTrayMode.LOGCAT, vm.uiState.value.logTrayMode)
        assertEquals(device.id, vm.uiState.value.logcatDeviceFilter)

        // 3. Reconnect action
        actions.reconnect(device.id)
        advanceUntilIdle()

        // 4. Install APK action
        pickedApkPath = "C:/test/sample.apk"
        actions.installApk(device.id)
        advanceUntilIdle()

        stateCollector.cancel()
    }
}
