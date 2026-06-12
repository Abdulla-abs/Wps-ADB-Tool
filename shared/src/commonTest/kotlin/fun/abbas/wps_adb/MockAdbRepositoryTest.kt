package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.MockAdbRepository
import `fun`.abbas.wps_adb.model.DeviceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockAdbRepositoryTest {
    private suspend fun MockAdbRepository.awaitDevicesLoaded() {
        devices.first { it.isNotEmpty() }
    }

    @Test
    fun devices_returnsInitialOnlineDevices() = runBlocking {
        val repo = MockAdbRepository(initialScanDelayMs = 0)
        repo.awaitDevicesLoaded()
        val devices = repo.devices.value
        assertTrue(devices.isNotEmpty())
        assertTrue(devices.any { it.status == DeviceStatus.ONLINE })
    }

    @Test
    fun isPackageInstalled_returnsTrueAfterInstall() = runBlocking {
        val repo = MockAdbRepository(initialScanDelayMs = 0)
        repo.awaitDevicesLoaded()
        val device = repo.devices.value.first { it.status == DeviceStatus.ONLINE }
        val apkPath = "C:\\temp\\demo.apk"
        assertEquals(false, repo.isPackageInstalled(device.id, "com.mock.demo"))
        val result = repo.installApkOnDevice(device.id, apkPath)
        assertTrue(result.success)
        assertTrue(repo.isPackageInstalled(device.id, result.metadata!!.packageName))
    }

    @Test
    fun rebootDevice_marksOffline() = runBlocking {
        val repo = MockAdbRepository(initialScanDelayMs = 0)
        repo.awaitDevicesLoaded()
        val deviceId = repo.devices.value.first { it.status == DeviceStatus.ONLINE }.id
        repo.rebootDevice(deviceId)
        assertEquals(DeviceStatus.OFFLINE, repo.devices.value.first { it.id == deviceId }.status)
    }
}
