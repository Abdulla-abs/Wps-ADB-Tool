package `fun`.abbas.wps_adb.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import `fun`.abbas.wps_adb.model.PairingMethod
import `fun`.abbas.wps_adb.model.PortRangeValidator
import `fun`.abbas.wps_adb.model.QrPairingEvent
import `fun`.abbas.wps_adb.theme.CarbonColors
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.*
import wpsadbtool.shared.generated.resources.Res

private enum class ConnectState { IDLE, LOADING, SUCCESS, FAILURE }

@Composable
fun PairingDialog(
    onDismiss: () -> Unit,
    onPairComplete: suspend (ip: String, port: Int) -> Boolean,
    pairingMethod: PairingMethod,
    onPairingMethodChange: (PairingMethod) -> Unit,
    qrPairingEvent: QrPairingEvent?,
    qrPairingPayload: String?,
    onStartQrPairing: () -> Unit,
    onCancelQrPairing: () -> Unit,
    onRefreshQrPairing: () -> Unit,
    defaultPort: Int = 5555,
    minPort: Int = 5555,
    maxPort: Int = 5585,
) {
    var step by remember { mutableIntStateOf(1) }
    var usbConnected by remember { mutableStateOf(false) }
    var tcpEnabled by remember { mutableStateOf(false) }
    var wirelessEnabled by remember { mutableStateOf(false) }
    var ipAddress by remember { mutableStateOf("") }
    var port by remember(defaultPort, minPort, maxPort) { mutableStateOf(defaultPort.toString()) }
    var scanName by remember { mutableStateOf<String?>(null) }
    var connectState by remember { mutableStateOf(ConnectState.IDLE) }
    var failureMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val defaultDeviceName = stringResource(Res.string.pairing_default_device)
    val portRange = PortRangeValidator.normalizedRange(minPort, maxPort)
    val portOutOfRangeMessage = stringResource(Res.string.pairing_port_out_of_range, portRange.first, portRange.last)

    fun resolvePort(): Int? {
        val value = port.toIntOrNull() ?: defaultPort
        return value.takeIf { PortRangeValidator.isInRange(it, minPort, maxPort) }
    }

    fun connectManual() {
        val validPort = resolvePort()
        if (validPort == null) {
            step = 3
            connectState = ConnectState.FAILURE
            failureMessage = portOutOfRangeMessage
            return
        }
        step = 3
        connectState = ConnectState.LOADING
        scope.launch {
            val success = onPairComplete(ipAddress, validPort)
            connectState = if (success) ConnectState.SUCCESS else ConnectState.FAILURE
        }
    }

    LaunchedEffect(step, pairingMethod) {
        if (step == 2 && pairingMethod == PairingMethod.QR_WIRELESS) {
            onStartQrPairing()
        } else if (step != 2) {
            onCancelQrPairing()
        }
    }

    LaunchedEffect(qrPairingEvent) {
        when (val event = qrPairingEvent) {
            is QrPairingEvent.Failure -> {
                step = 3
                connectState = ConnectState.FAILURE
                failureMessage = event.message
            }
            is QrPairingEvent.Success -> {
                scanName = event.device.name
                ipAddress = event.device.serial.substringBefore(':').ifBlank { ipAddress }
                port = event.device.serial.substringAfter(':', port).ifBlank { port }
                step = 3
                connectState = ConnectState.SUCCESS
                failureMessage = null
            }
            else -> Unit
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier
                .width(720.dp)
                .height(520.dp)
                .background(CarbonColors.SurfaceContainer, RoundedCornerShape(12.dp))
                .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(12.dp)),
        ) {
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(CarbonColors.SurfaceContainerLow)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                StepIndicator(
                    num = 1,
                    title = stringResource(Res.string.pairing_step1_title),
                    subtitle = if (pairingMethod == PairingMethod.QR_WIRELESS) {
                        stringResource(Res.string.pairing_step1_subtitle_qr)
                    } else {
                        stringResource(Res.string.pairing_step1_subtitle)
                    },
                    currentStep = step,
                )
                StepIndicator(
                    num = 2,
                    title = stringResource(Res.string.pairing_step2_title),
                    subtitle = if (pairingMethod == PairingMethod.QR_WIRELESS) {
                        stringResource(Res.string.pairing_step2_subtitle_qr)
                    } else {
                        stringResource(Res.string.pairing_step2_subtitle)
                    },
                    currentStep = step,
                )
                StepIndicator(
                    num = 3,
                    title = stringResource(Res.string.pairing_step3_title),
                    subtitle = stringResource(Res.string.pairing_step3_subtitle),
                    currentStep = step,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                when (step) {
                    1 -> StepPrepare(
                        pairingMethod = pairingMethod,
                        onPairingMethodChange = onPairingMethodChange,
                        usbConnected = usbConnected,
                        tcpEnabled = tcpEnabled,
                        wirelessEnabled = wirelessEnabled,
                        onUsbToggle = { usbConnected = !usbConnected },
                        onTcpToggle = { tcpEnabled = !tcpEnabled },
                        onWirelessToggle = { wirelessEnabled = !wirelessEnabled },
                        onNext = { step = 2 },
                    )
                    2 -> if (pairingMethod == PairingMethod.QR_WIRELESS) {
                        StepConnectQr(
                            qrPairingEvent = qrPairingEvent,
                            qrPairingPayload = qrPairingPayload,
                            onRefreshQrPairing = onRefreshQrPairing,
                            onBack = {
                                onCancelQrPairing()
                                step = 1
                            },
                        )
                    } else {
                        StepConnectManual(
                            ipAddress = ipAddress,
                            port = port,
                            onIpChange = { ipAddress = it },
                            onPortChange = { port = it },
                            onConnect = ::connectManual,
                            onBack = { step = 1 },
                        )
                    }
                    3 -> StepFinalize(
                        connectState = connectState,
                        ipAddress = ipAddress,
                        port = port,
                        scanName = scanName,
                        defaultDeviceName = defaultDeviceName,
                        failureMessage = failureMessage,
                        onFinalize = onDismiss,
                        onRetry = {
                            failureMessage = null
                            if (pairingMethod == PairingMethod.QR_WIRELESS) {
                                step = 2
                                connectState = ConnectState.IDLE
                                onRefreshQrPairing()
                            } else {
                                connectManual()
                            }
                        },
                        onEdit = {
                            failureMessage = null
                            onCancelQrPairing()
                            step = 2
                            connectState = ConnectState.IDLE
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(num: Int, title: String, subtitle: String, currentStep: Int) {
    val active = currentStep == num
    val done = currentStep > num
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(1.dp, if (done || active) CarbonColors.Primary else CarbonColors.Outline, CircleShape)
                .background(if (done) CarbonColors.Primary.copy(alpha = 0.1f) else CarbonColors.SurfaceContainerLow, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (done) "✓" else num.toString().padStart(2, '0'),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (done || active) CarbonColors.Primary else CarbonColors.Outline,
            )
        }
        Column {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) CarbonColors.Primary else CarbonColors.OnSurfaceVariant)
            Text(subtitle, fontSize = 10.sp, color = CarbonColors.Outline)
        }
    }
}

@Composable
private fun StepPrepare(
    pairingMethod: PairingMethod,
    onPairingMethodChange: (PairingMethod) -> Unit,
    usbConnected: Boolean,
    tcpEnabled: Boolean,
    wirelessEnabled: Boolean,
    onUsbToggle: () -> Unit,
    onTcpToggle: () -> Unit,
    onWirelessToggle: () -> Unit,
    onNext: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PairingMethodSelector(pairingMethod, onPairingMethodChange)
        if (pairingMethod == PairingMethod.LEGACY_TCP) {
            Text(stringResource(Res.string.pairing_prepare_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
            Text(
                stringResource(Res.string.pairing_prepare_desc),
                fontSize = 12.sp,
                color = CarbonColors.OnSurfaceVariant,
            )
            ChecklistItem(
                stringResource(Res.string.pairing_checklist_usb_title),
                stringResource(Res.string.pairing_checklist_usb_subtitle),
                usbConnected,
                onUsbToggle,
            )
            ChecklistItem(
                stringResource(Res.string.pairing_checklist_tcp_title),
                stringResource(Res.string.pairing_checklist_tcp_subtitle),
                tcpEnabled,
                onTcpToggle,
            )
        } else {
            Text(stringResource(Res.string.pairing_prepare_qr_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
            Text(
                stringResource(Res.string.pairing_prepare_qr_desc),
                fontSize = 12.sp,
                color = CarbonColors.OnSurfaceVariant,
            )
            ChecklistItem(
                stringResource(Res.string.pairing_checklist_wireless_title),
                stringResource(Res.string.pairing_checklist_wireless_subtitle),
                wirelessEnabled,
                onWirelessToggle,
            )
        }
    }
    val canProceed = when (pairingMethod) {
        PairingMethod.LEGACY_TCP -> usbConnected && tcpEnabled
        PairingMethod.QR_WIRELESS -> wirelessEnabled
    }
    Button(
        onClick = onNext,
        enabled = canProceed,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = CarbonColors.SecondaryContainer),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(stringResource(Res.string.pairing_prepare_next), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PairingMethodSelector(
    selected: PairingMethod,
    onSelect: (PairingMethod) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PairingMethodOption(
            label = stringResource(Res.string.pairing_method_legacy),
            selected = selected == PairingMethod.LEGACY_TCP,
            onClick = { onSelect(PairingMethod.LEGACY_TCP) },
        )
        PairingMethodOption(
            label = stringResource(Res.string.pairing_method_qr),
            selected = selected == PairingMethod.QR_WIRELESS,
            onClick = { onSelect(PairingMethod.QR_WIRELESS) },
        )
    }
}

@Composable
private fun PairingMethodOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = CarbonColors.Primary),
        )
        Text(label, fontSize = 12.sp, color = CarbonColors.OnSurface)
    }
}

@Composable
private fun ChecklistItem(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .border(1.dp, if (checked) CarbonColors.Primary else CarbonColors.OutlineVariant, RoundedCornerShape(8.dp))
            .background(if (checked) CarbonColors.Primary.copy(alpha = 0.05f) else CarbonColors.SurfaceContainerLowest)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CarbonColors.OnSurface)
            Text(subtitle, fontSize = 10.sp, color = CarbonColors.Outline)
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = CarbonColors.Primary))
    }
}

@Composable
private fun StepConnectManual(
    ipAddress: String,
    port: String,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.pairing_connect_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
        Text(
            stringResource(Res.string.pairing_scan_unavailable),
            fontSize = 11.sp,
            color = CarbonColors.OnSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(8.dp))
                .background(CarbonColors.SurfaceContainerLow)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = ipAddress,
                onValueChange = onIpChange,
                label = { Text(stringResource(Res.string.pairing_ip_label), fontSize = 10.sp) },
                placeholder = { Text("192.168.1.105", fontFamily = FontFamily.Monospace) },
                modifier = Modifier.weight(3f),
                singleLine = true,
                colors = fieldColors(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text(stringResource(Res.string.pairing_port_label), fontSize = 10.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = fieldColors(),
            )
        }
    }
    Column {
        Button(
            onClick = onConnect,
            enabled = ipAddress.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CarbonColors.Primary, contentColor = CarbonColors.OnPrimary),
        ) { Text(stringResource(Res.string.pairing_connect_button)) }
        Text(
            stringResource(Res.string.pairing_back),
            fontSize = 12.sp,
            color = CarbonColors.Outline,
            modifier = Modifier.clickable(onClick = onBack).padding(top = 8.dp),
        )
    }
}

@Composable
private fun StepConnectQr(
    qrPairingEvent: QrPairingEvent?,
    qrPairingPayload: String?,
    onRefreshQrPairing: () -> Unit,
    onBack: () -> Unit,
) {
    val qrPayload = qrPairingPayload
    val statusText = when (qrPairingEvent) {
        is QrPairingEvent.QrReady, is QrPairingEvent.WaitingForScan, null ->
            stringResource(Res.string.pairing_qr_waiting)
        is QrPairingEvent.PairingInProgress ->
            stringResource(Res.string.pairing_qr_pairing, qrPairingEvent.endpoint)
        is QrPairingEvent.Connecting ->
            stringResource(Res.string.pairing_qr_connecting, qrPairingEvent.endpoint)
        else -> stringResource(Res.string.pairing_qr_waiting)
    }
    val isBusy = qrPairingEvent is QrPairingEvent.PairingInProgress ||
        qrPairingEvent is QrPairingEvent.Connecting

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.pairing_connect_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(8.dp))
                    .background(CarbonColors.OnPrimary, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    qrPayload != null -> QrCodeImage(payload = qrPayload, modifier = Modifier.fillMaxSize())
                    else -> CircularProgressIndicator(color = CarbonColors.Primary, modifier = Modifier.size(32.dp))
                }
            }
            Text(
                stringResource(Res.string.pairing_qr_instructions),
                fontSize = 11.sp,
                color = CarbonColors.OnSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isBusy) {
                CircularProgressIndicator(color = CarbonColors.Primary, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            Text(statusText, fontSize = 12.sp, color = CarbonColors.Primary)
        }
    }
    Column {
        Button(
            onClick = onRefreshQrPairing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CarbonColors.SecondaryContainer),
        ) { Text(stringResource(Res.string.pairing_qr_refresh)) }
        Text(
            stringResource(Res.string.pairing_back),
            fontSize = 12.sp,
            color = CarbonColors.Outline,
            modifier = Modifier.clickable(onClick = onBack).padding(top = 8.dp),
        )
    }
}

@Composable
private fun StepFinalize(
    connectState: ConnectState,
    ipAddress: String,
    port: String,
    scanName: String?,
    defaultDeviceName: String,
    failureMessage: String?,
    onFinalize: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (connectState) {
            ConnectState.LOADING -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = CarbonColors.Primary)
                Text(stringResource(Res.string.pairing_handshaking, ipAddress, port), fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
                Text(stringResource(Res.string.pairing_adb_connect_cmd, ipAddress, port), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = CarbonColors.Outline)
            }
            ConnectState.SUCCESS -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                Text("✓", fontSize = 40.sp, color = CarbonColors.Primary)
                Text(stringResource(Res.string.pairing_success_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CarbonColors.Primary)
                Text(
                    stringResource(Res.string.pairing_success_message, scanName ?: defaultDeviceName, ipAddress, port),
                    fontSize = 12.sp,
                    color = CarbonColors.OnSurfaceVariant,
                )
                Button(onClick = onFinalize, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CarbonColors.SurfaceContainerHigh)) {
                    Text(stringResource(Res.string.pairing_finalize_button), fontWeight = FontWeight.Bold)
                }
            }
            ConnectState.FAILURE -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                Text("!", fontSize = 32.sp, color = CarbonColors.Error)
                Text(stringResource(Res.string.pairing_failure_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CarbonColors.Error)
                Text(
                    failureMessage?.let { stringResource(Res.string.pairing_qr_failure_message, it) }
                        ?: stringResource(Res.string.pairing_failure_message),
                    fontSize = 12.sp,
                    color = CarbonColors.OnSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onEdit, colors = ButtonDefaults.buttonColors(containerColor = CarbonColors.SurfaceContainerHighest)) {
                        Text(stringResource(Res.string.pairing_edit_host), fontSize = 12.sp)
                    }
                    Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = CarbonColors.Error)) {
                        Text(stringResource(Res.string.pairing_retry), fontSize = 12.sp)
                    }
                }
            }
            ConnectState.IDLE -> {}
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CarbonColors.Primary,
    unfocusedBorderColor = CarbonColors.OutlineVariant,
    focusedTextColor = CarbonColors.Primary,
    unfocusedTextColor = CarbonColors.Primary,
    cursorColor = CarbonColors.Primary,
    focusedContainerColor = CarbonColors.SurfaceContainerLowest,
    unfocusedContainerColor = CarbonColors.SurfaceContainerLowest,
)
