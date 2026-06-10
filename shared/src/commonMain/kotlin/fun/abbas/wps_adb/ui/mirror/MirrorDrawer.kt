package `fun`.abbas.wps_adb.ui.mirror

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.theme.CarbonColors
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.*
import wpsadbtool.shared.generated.resources.Res

@Composable
fun MirrorDrawer(
    device: Device,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeAppIndex by remember(device.id) { mutableIntStateOf(device.currentAppIndex) }
    var brightness by remember { mutableStateOf(78f) }
    var wifiEnabled by remember { mutableStateOf(true) }
    var bluetoothEnabled by remember { mutableStateOf(false) }
    var developerMode by remember { mutableStateOf(true) }
    var consoleInput by remember { mutableStateOf("") }
    var consoleOut by remember(device.id) { mutableStateOf(device.activityLog) }
    val shellHelp = stringResource(Res.string.mirror_shell_help)
    val shellNotFoundTemplate = stringResource(Res.string.mirror_shell_not_found, SHELL_CMD_PLACEHOLDER)

    Box(
        modifier = modifier
            .width(400.dp)
            .fillMaxHeight()
            .background(CarbonColors.SurfaceContainerLow)
            .border(width = 1.dp, color = CarbonColors.OutlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CarbonColors.SurfaceContainerHigh)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(stringResource(Res.string.mirror_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
                    Text(stringResource(Res.string.mirror_device_subtitle, device.name, device.serial), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = CarbonColors.Outline)
                }
                Text("✕", fontSize = 18.sp, color = CarbonColors.Outline, modifier = Modifier.clickable(onClick = onClose))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PhoneFrame(
                    device = device,
                    activeAppIndex = activeAppIndex,
                    brightness = brightness,
                    wifiEnabled = wifiEnabled,
                    bluetoothEnabled = bluetoothEnabled,
                    developerMode = developerMode,
                    onBrightnessChange = { brightness = it },
                    onWifiToggle = { wifiEnabled = !wifiEnabled },
                    onBluetoothToggle = { bluetoothEnabled = !bluetoothEnabled },
                    onDeveloperToggle = { developerMode = !developerMode },
                    onAppSwitch = { activeAppIndex = it },
                )

                Text(stringResource(Res.string.mirror_app_launcher), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CarbonColors.Outline)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppChip(stringResource(Res.string.mirror_app_photoflow), activeAppIndex == 0) { activeAppIndex = 0 }
                    AppChip(stringResource(Res.string.mirror_app_settings), activeAppIndex == 1) { activeAppIndex = 1 }
                }

                ShellConsole(
                    device = device,
                    consoleOut = consoleOut,
                    consoleInput = consoleInput,
                    onInputChange = { consoleInput = it },
                    onSend = { cmd ->
                        if (cmd.isBlank()) return@ShellConsole
                        val reply = simulateShellReply(device, cmd, shellHelp, shellNotFoundTemplate)
                        if (cmd.trim().lowercase() == "clear") {
                            consoleOut = emptyList()
                        } else {
                            consoleOut = consoleOut + "$ adb shell $cmd" + reply
                        }
                        consoleInput = ""
                    },
                )
            }
        }
    }
}

@Composable
private fun PhoneFrame(
    device: Device,
    activeAppIndex: Int,
    brightness: Float,
    wifiEnabled: Boolean,
    bluetoothEnabled: Boolean,
    developerMode: Boolean,
    onBrightnessChange: (Float) -> Unit,
    onWifiToggle: () -> Unit,
    onBluetoothToggle: () -> Unit,
    onDeveloperToggle: () -> Unit,
    onAppSwitch: (Int) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(240.dp)
                .height(480.dp)
                .background(CarbonColors.SurfaceContainerLowest, RoundedCornerShape(32.dp))
                .border(4.dp, CarbonColors.SurfaceContainerHighest, RoundedCornerShape(32.dp))
                .padding(10.dp),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CarbonColors.SurfaceContainerLowest, RoundedCornerShape(24.dp))
                .border(1.dp, CarbonColors.OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("10:14", fontSize = 9.sp, color = CarbonColors.OnSurface)
                Text(
                    if (wifiEnabled) {
                        stringResource(Res.string.mirror_wifi_battery, device.batteryLevel)
                    } else {
                        "${device.batteryLevel}%"
                    },
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CarbonColors.OnSurface,
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                when (activeAppIndex) {
                    0 -> Column {
                        Text(stringResource(Res.string.mirror_photoflow_client), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CarbonColors.Primary)
                        Text(device.screenDescription, fontSize = 10.sp, color = CarbonColors.OnSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.mirror_app_settings), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CarbonColors.Secondary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(Res.string.mirror_brightness), fontSize = 9.sp, color = CarbonColors.OnSurface)
                            Text("${brightness.toInt()}%", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = brightness,
                            onValueChange = onBrightnessChange,
                            valueRange = 10f..100f,
                            colors = SliderDefaults.colors(thumbColor = CarbonColors.Primary, activeTrackColor = CarbonColors.Primary),
                        )
                        ToggleRow(stringResource(Res.string.mirror_wifi), wifiEnabled, onWifiToggle)
                        ToggleRow(stringResource(Res.string.mirror_bluetooth), bluetoothEnabled, onBluetoothToggle)
                        ToggleRow(stringResource(Res.string.mirror_developer_mode), developerMode, onDeveloperToggle)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp).background(CarbonColors.SurfaceContainerLowest),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("◀", fontSize = 12.sp, color = CarbonColors.OnSurfaceVariant)
                Box(Modifier.clickable { onAppSwitch(0) }.padding(4.dp)) {
                    Text("○", fontSize = 12.sp, color = CarbonColors.OnSurfaceVariant)
                }
                Box(Modifier.clickable { onAppSwitch(1) }.padding(4.dp)) {
                    Text("□", fontSize = 12.sp, color = CarbonColors.OnSurfaceVariant)
                }
            }
        }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 9.sp, color = CarbonColors.OnSurface)
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = CarbonColors.Primary))
    }
}

@Composable
private fun AppChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) CarbonColors.Primary else CarbonColors.OnSurfaceVariant,
        modifier = Modifier
            .border(1.dp, if (selected) CarbonColors.Primary else CarbonColors.OutlineVariant, RoundedCornerShape(8.dp))
            .background(if (selected) CarbonColors.Primary.copy(alpha = 0.1f) else CarbonColors.SurfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun ShellConsole(
    device: Device,
    consoleOut: List<String>,
    consoleInput: String,
    onInputChange: (String) -> Unit,
    onSend: (String) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.mirror_adb_console), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CarbonColors.Outline)
            Text(device.serial, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = CarbonColors.Primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf("getprop", "dumpsys battery", "pm list packages").forEach { cmd ->
                Text(
                    cmd,
                    fontSize = 9.sp,
                    color = CarbonColors.Outline,
                    modifier = Modifier
                        .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(4.dp))
                        .clickable { onSend(cmd) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(CarbonColors.SurfaceContainerLowest, RoundedCornerShape(8.dp))
                .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(8.dp))
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (consoleOut.isEmpty()) {
                Text(stringResource(Res.string.mirror_console_empty), fontSize = 10.sp, color = CarbonColors.Outline, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            } else {
                consoleOut.forEach { line ->
                    Text(line, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = CarbonColors.OnSurfaceVariant)
                }
            }
        }
        OutlinedTextField(
            value = consoleInput,
            onValueChange = onInputChange,
            placeholder = { Text(stringResource(Res.string.mirror_console_placeholder), fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CarbonColors.Primary,
                unfocusedBorderColor = CarbonColors.OutlineVariant,
                focusedTextColor = CarbonColors.OnSurface,
                unfocusedTextColor = CarbonColors.OnSurface,
            ),
        )
        Text(
            stringResource(Res.string.mirror_console_hint),
            fontSize = 9.sp,
            color = CarbonColors.Outline,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private const val SHELL_CMD_PLACEHOLDER = "\uE000"

private fun simulateShellReply(device: Device, cmd: String, shellHelp: String, shellNotFoundTemplate: String): String {
    val clean = cmd.trim().lowercase()
    return when {
        "getprop" in clean -> "\n[ro.product.model]: [${device.name}]\n[ro.serialno]: [${device.serial}]"
        "pm list packages" in clean -> device.apps.joinToString("\n") { "package:${it.packageName}" }
        "dumpsys battery" in clean -> "\nlevel: ${device.batteryLevel}\nUSB powered: ${device.connectionType == ConnectionType.USB}"
        "help" in clean -> "\n$shellHelp"
        else -> "\n${shellNotFoundTemplate.replace(SHELL_CMD_PLACEHOLDER, cmd)}"
    }
}
