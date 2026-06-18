package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.AdbRepository
import `fun`.abbas.wps_adb.data.MockAdbRepository
import `fun`.abbas.wps_adb.model.ApkInstallResult
import `fun`.abbas.wps_adb.model.ApkMetadata
import `fun`.abbas.wps_adb.model.DeviceStatus
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
class AppViewModelApkReinstallTest {
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
    fun installConflict_showsReinstallPromptInsteadOfFailureToast() = runTest(dispatcher) {
        val repository = ConflictInstallRepository()
        val viewModel = AppViewModel(repository)
        val stateCollector = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        val device = repository.devices.value.first { it.status == DeviceStatus.ONLINE }

        viewModel.installApkOnDevice(device.id, "C:\\temp\\demo.apk")
        advanceUntilIdle()

        val prompt = viewModel.uiState.value.apkReinstallPrompt
        assertNotNull(prompt)
        assertEquals(device.id, prompt.deviceId)
        assertEquals("com.mock.demo", prompt.packageName)
        assertNull(viewModel.uiState.value.apkInstallToast)
        stateCollector.cancel()
    }

    @Test
    fun confirmReinstall_uninstallsThenInstalls() = runTest(dispatcher) {
        val repository = ConflictInstallRepository()
        val viewModel = AppViewModel(repository)
        val stateCollector = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        val device = repository.devices.value.first { it.status == DeviceStatus.ONLINE }

        viewModel.installApkOnDevice(device.id, "C:\\temp\\demo.apk")
        advanceUntilIdle()
        viewModel.confirmApkReinstall()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.apkReinstallPrompt)
        assertEquals(1, repository.uninstallCount)
        assertEquals(2, repository.installCount)
        assertTrue(viewModel.uiState.value.apkInstallToast?.success == true)
        stateCollector.cancel()
    }
}

private class ConflictInstallRepository(
    private val base: MockAdbRepository = MockAdbRepository(initialScanDelayMs = 0),
) : AdbRepository by base {
    var installCount = 0
    var uninstallCount = 0
    private var conflictPending = true

    override suspend fun installApkOnDevice(deviceId: String, apkPath: String): ApkInstallResult {
        installCount++
        val metadata = ApkMetadata(
            packageName = "com.mock.demo",
            appLabel = "Demo",
            versionName = "1.0",
        )
        if (conflictPending) {
            return ApkInstallResult(
                success = false,
                message = "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match]",
                apkPath = apkPath,
                apkFileName = "demo.apk",
                metadata = metadata,
            )
        }
        return ApkInstallResult(
            success = true,
            message = "Success",
            apkPath = apkPath,
            apkFileName = "demo.apk",
            metadata = metadata,
        )
    }

    override suspend fun isPackageInstalled(deviceId: String, packageName: String): Boolean =
        packageName == "com.mock.demo"

    override suspend fun uninstallApp(deviceId: String, packageName: String): Result<Unit> {
        uninstallCount++
        conflictPending = false
        return Result.success(Unit)
    }
}
