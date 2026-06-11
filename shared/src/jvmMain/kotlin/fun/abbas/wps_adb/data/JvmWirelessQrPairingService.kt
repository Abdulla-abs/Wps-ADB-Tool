package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.QrPairingEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class JvmWirelessQrPairingService(
    private val runner: JvmAdbRunner,
    private val mdns: AdbMdnsDiscovery = AdbMdnsDiscovery(),
    private val pairingTimeoutSec: Long = 120,
    private val onDevicesRefresh: suspend () -> Unit,
    private val findDevice: suspend (String) -> Device?,
    private val updateWirelessStore: suspend (Device) -> Unit,
) {
    private var activeJob: Job? = null

    fun pair(): Flow<QrPairingEvent> = channelFlow {
        val creds = AdbQrPayloadBuilder.generate()
        send(QrPairingEvent.QrReady(creds.payload, creds.serviceName))
        send(QrPairingEvent.WaitingForScan)

        try {
            coroutineScope {
                activeJob = coroutineContext[Job]
                val pairingEndpoint = withTimeoutOrNull(pairingTimeoutSec.seconds) {
                    mdns.listen(AdbMdnsDiscovery.PAIRING_TYPE) { name ->
                        AdbMdnsDiscovery.matchesInstanceName(creds.serviceName, name)
                    }.first()
                } ?: run {
                    send(QrPairingEvent.Failure("Pairing timeout: device not found via mDNS"))
                    return@coroutineScope
                }

                val pairTarget = AdbMdnsDiscovery.formatEndpoint(pairingEndpoint.host, pairingEndpoint.port)
                send(QrPairingEvent.PairingInProgress(pairTarget))

                val pairResult = runner.pair(pairTarget, creds.password)
                val pairOutput = pairResult.output.lowercase()
                if (!pairResult.success && "already paired" !in pairOutput) {
                    send(QrPairingEvent.Failure("adb pair failed: ${pairResult.output}"))
                    return@coroutineScope
                }

                val pairingHost = pairingEndpoint.host
                val connectEndpoint = withTimeoutOrNull(30.seconds) {
                    mdns.listen(AdbMdnsDiscovery.CONNECT_TYPE) { true }.first { endpoint ->
                        endpoint.host == pairingHost
                    }
                } ?: withTimeoutOrNull(15.seconds) {
                    mdns.listen(AdbMdnsDiscovery.CONNECT_TYPE) { true }.first()
                } ?: run {
                    send(QrPairingEvent.Failure("Connect timeout: check Wireless debugging screen for IP"))
                    return@coroutineScope
                }

                val connectTarget = AdbMdnsDiscovery.formatEndpoint(connectEndpoint.host, connectEndpoint.port)
                send(QrPairingEvent.Connecting(connectTarget))

                val connectResult = runner.run(listOf("connect", connectTarget))
                val output = connectResult.output.lowercase()
                val connected = connectResult.success || "connected" in output || "already connected" in output
                if (!connected) {
                    send(QrPairingEvent.Failure("adb connect failed: ${connectResult.output}"))
                    return@coroutineScope
                }

                onDevicesRefresh()
                val device = findDevice(connectTarget)
                    ?: findDevice(connectEndpoint.host)
                if (device != null) {
                    updateWirelessStore(device)
                    send(QrPairingEvent.Success(device))
                } else {
                    send(QrPairingEvent.Failure("Device not found after connect"))
                }
            }
        } catch (_: CancellationException) {
            send(QrPairingEvent.Cancelled)
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }
}
