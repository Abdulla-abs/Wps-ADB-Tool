package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.MockAdbRepository
import `fun`.abbas.wps_adb.model.QrPairingEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MockAdbRepositoryQrPairingTest {

    @Test
    fun pairWirelessViaQr_emitsReadyThenSuccess() = runTest {
        val repo = MockAdbRepository(initialScanDelayMs = 0, refreshDelayMs = 0)
        val events = repo.pairWirelessViaQr().toList()
        assertTrue(events.any { it is QrPairingEvent.QrReady })
        assertIs<QrPairingEvent.Success>(events.last())
    }

    @Test
    fun cancelQrPairing_emitsCancelled() = runTest {
        val repo = MockAdbRepository(initialScanDelayMs = 0, refreshDelayMs = 0)
        val events = mutableListOf<QrPairingEvent>()
        val job = launch {
            repo.pairWirelessViaQr().collect { events.add(it) }
        }
        advanceUntilIdle()
        repo.cancelQrPairing()
        advanceUntilIdle()
        job.cancel()
        assertTrue(events.any { it is QrPairingEvent.Cancelled })
    }
}
