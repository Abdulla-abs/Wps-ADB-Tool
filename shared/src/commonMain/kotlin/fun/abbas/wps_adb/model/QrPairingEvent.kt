package `fun`.abbas.wps_adb.model

sealed class QrPairingEvent {
    data class QrReady(
        val payload: String,
        val serviceName: String,
    ) : QrPairingEvent()

    data object WaitingForScan : QrPairingEvent()

    data class PairingInProgress(val endpoint: String) : QrPairingEvent()

    data class Connecting(val endpoint: String) : QrPairingEvent()

    data class Success(val device: Device) : QrPairingEvent()

    data class Failure(val message: String) : QrPairingEvent()

    data object Cancelled : QrPairingEvent()
}
