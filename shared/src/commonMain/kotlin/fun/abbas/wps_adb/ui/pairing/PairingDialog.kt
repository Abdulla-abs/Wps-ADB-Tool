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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import `fun`.abbas.wps_adb.theme.CarbonColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.*
import wpsadbtool.shared.generated.resources.Res

private enum class ConnectState { IDLE, LOADING, SUCCESS, FAILURE }

@Composable
fun PairingDialog(
    onDismiss: () -> Unit,
    onPairComplete: suspend (ip: String, port: Int) -> Boolean,
) {
    var step by remember { mutableIntStateOf(1) }
    var usbConnected by remember { mutableStateOf(false) }
    var tcpEnabled by remember { mutableStateOf(false) }
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5555") }
    var isScanning by remember { mutableStateOf(false) }
    var scanName by remember { mutableStateOf<String?>(null) }
    var connectState by remember { mutableStateOf(ConnectState.IDLE) }
    val scope = rememberCoroutineScope()
    val defaultDeviceName = stringResource(Res.string.pairing_default_device)

    Dialog(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier
                .width(640.dp)
                .height(480.dp)
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
                StepIndicator(1, stringResource(Res.string.pairing_step1_title), stringResource(Res.string.pairing_step1_subtitle), step)
                StepIndicator(2, stringResource(Res.string.pairing_step2_title), stringResource(Res.string.pairing_step2_subtitle), step)
                StepIndicator(3, stringResource(Res.string.pairing_step3_title), stringResource(Res.string.pairing_step3_subtitle), step)
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
                        usbConnected = usbConnected,
                        tcpEnabled = tcpEnabled,
                        onUsbToggle = { usbConnected = !usbConnected },
                        onTcpToggle = { tcpEnabled = !tcpEnabled },
                        onNext = { step = 2 },
                    )
                    2 -> StepConnect(
                        ipAddress = ipAddress,
                        port = port,
                        isScanning = isScanning,
                        scanName = scanName,
                        onIpChange = { ipAddress = it },
                        onPortChange = { port = it },
                        onScan = {
                            isScanning = true
                            scanName = null
                            scope.launch {
                                delay(1800)
                                isScanning = false
                                scanName = defaultDeviceName
                                ipAddress = "192.168.1.105"
                            }
                        },
                        onConnect = {
                            step = 3
                            connectState = ConnectState.LOADING
                            scope.launch {
                                val success = onPairComplete(ipAddress, port.toIntOrNull() ?: 5555)
                                connectState = if (success) ConnectState.SUCCESS else ConnectState.FAILURE
                            }
                        },
                        onBack = { step = 1 },
                    )
                    3 -> StepFinalize(
                        connectState = connectState,
                        ipAddress = ipAddress,
                        port = port,
                        scanName = scanName,
                        defaultDeviceName = defaultDeviceName,
                        onFinalize = onDismiss,
                        onRetry = {
                            connectState = ConnectState.LOADING
                            scope.launch {
                                val success = onPairComplete(ipAddress, port.toIntOrNull() ?: 5555)
                                connectState = if (success) ConnectState.SUCCESS else ConnectState.FAILURE
                            }
                        },
                        onEdit = { step = 2; connectState = ConnectState.IDLE },
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
    usbConnected: Boolean,
    tcpEnabled: Boolean,
    onUsbToggle: () -> Unit,
    onTcpToggle: () -> Unit,
    onNext: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    }
    Button(
        onClick = onNext,
        enabled = usbConnected && tcpEnabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = CarbonColors.SecondaryContainer),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(stringResource(Res.string.pairing_prepare_next), fontWeight = FontWeight.Bold)
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
private fun StepConnect(
    ipAddress: String,
    port: String,
    isScanning: Boolean,
    scanName: String?,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onScan: () -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.pairing_connect_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
            Text(
                if (isScanning) stringResource(Res.string.pairing_scan_in_progress) else stringResource(Res.string.pairing_scan_idle),
                fontSize = 11.sp,
                color = CarbonColors.Primary,
                modifier = Modifier
                    .border(1.dp, CarbonColors.Primary.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                    .clickable(enabled = !isScanning, onClick = onScan)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
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
        if (scanName != null && !isScanning) {
            Text(
                stringResource(Res.string.pairing_scan_found, scanName, ipAddress, port),
                fontSize = 11.sp,
                color = CarbonColors.Primary,
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
private fun StepFinalize(
    connectState: ConnectState,
    ipAddress: String,
    port: String,
    scanName: String?,
    defaultDeviceName: String,
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
                Text(stringResource(Res.string.pairing_failure_message), fontSize = 12.sp, color = CarbonColors.OnSurfaceVariant)
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
