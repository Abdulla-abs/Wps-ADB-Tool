package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.MockAdbRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertTrue

class MockAppLogcatSessionTest {

    @Test
    fun stopAppLogcat_isIdempotentWhenNotStarted() {
        val repository = MockAdbRepository()
        repository.stopAppLogcat("tab-none")
        repository.stopAppLogcat("tab-none")
    }

    @Test
    fun stopAppLogcat_stopsActiveFlow() = runBlocking {
        val repository = MockAdbRepository(initialScanDelayMs = 0)
        val device = repository.devices.first { it.isNotEmpty() }.first()
        val tabId = "applog_test"
        val collected = mutableListOf<Any>()

        val job = launch {
            repository.startAppLogcat(device.id, "com.mock.demo", tabId).collect {
                collected.add(it)
            }
        }

        delay(100)
        assertTrue(collected.isNotEmpty())
        repository.stopAppLogcat(tabId)
        repository.stopAppLogcat(tabId)
        job.join()
    }

    @Test
    fun stopAllAppLogcatSessions_closesEveryActiveTab() = runBlocking {
        val repository = MockAdbRepository(initialScanDelayMs = 0)
        val device = repository.devices.first { it.isNotEmpty() }.first()

        val job1 = launch {
            repository.startAppLogcat(device.id, "com.mock.one", "tab-1").collect { }
        }
        val job2 = launch {
            repository.startAppLogcat(device.id, "com.mock.two", "tab-2").collect { }
        }

        delay(100)
        repository.stopAllAppLogcatSessions()
        job1.join()
        job2.join()

        assertTrue(job1.isCompleted)
        assertTrue(job2.isCompleted)
    }
}
