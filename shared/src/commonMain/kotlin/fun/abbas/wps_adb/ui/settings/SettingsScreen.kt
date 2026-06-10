package `fun`.abbas.wps_adb.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.AppSettings
import `fun`.abbas.wps_adb.theme.CarbonColors
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.*
import wpsadbtool.shared.generated.resources.Res

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var adbPath by remember(settings) { mutableStateOf(settings.adbPath) }
    var minPort by remember(settings) { mutableStateOf(settings.minPort.toString()) }
    var maxPort by remember(settings) { mutableStateOf(settings.maxPort.toString()) }
    var scanInterval by remember(settings) { mutableStateOf(settings.scanIntervalSec.toFloat()) }
    var parallelThreads by remember(settings) { mutableStateOf(settings.parallelThreads.toFloat()) }
    var logRetention by remember(settings) { mutableStateOf(settings.logRetention.toString()) }
    var autoApproveKey by remember(settings) { mutableStateOf(settings.autoApproveKey) }
    var diagnosticTelemetry by remember(settings) { mutableStateOf(settings.diagnosticTelemetry) }
    var showToast by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Text(stringResource(Res.string.settings_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
            Text(
                stringResource(Res.string.settings_subtitle),
                fontSize = 12.sp,
                color = CarbonColors.OnSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsCard(stringResource(Res.string.settings_transport_bindings), Modifier.weight(1f)) {
                FieldLabel(stringResource(Res.string.settings_adb_path))
                OutlinedTextField(
                    value = adbPath,
                    onValueChange = { adbPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel(stringResource(Res.string.settings_start_port))
                        OutlinedTextField(minPort, { minPort = it }, Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors())
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel(stringResource(Res.string.settings_end_port))
                        OutlinedTextField(maxPort, { maxPort = it }, Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors())
                    }
                }
            }
            SettingsCard(stringResource(Res.string.settings_parallel_allocation), Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FieldLabel(stringResource(Res.string.settings_scan_interval))
                    Text(stringResource(Res.string.settings_scan_interval_value, scanInterval.toInt()), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = CarbonColors.Primary)
                }
                Slider(scanInterval, { scanInterval = it }, valueRange = 5f..60f, steps = 10, colors = sliderColors())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FieldLabel(stringResource(Res.string.settings_max_threads))
                    Text(stringResource(Res.string.settings_max_threads_value, parallelThreads.toInt()), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = CarbonColors.Primary)
                }
                Slider(parallelThreads, { parallelThreads = it }, valueRange = 1f..8f, steps = 6, colors = sliderColors())
            }
        }

        SettingsCard(stringResource(Res.string.settings_developer_security), Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.settings_auto_trust_title), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CarbonColors.OnSurface)
                    Text(stringResource(Res.string.settings_auto_trust_subtitle), fontSize = 10.sp, color = CarbonColors.Outline)
                }
                Checkbox(autoApproveKey, { autoApproveKey = it }, colors = CheckboxDefaults.colors(checkedColor = CarbonColors.Primary))
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.settings_telemetry_title), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CarbonColors.OnSurface)
                    Text(stringResource(Res.string.settings_telemetry_subtitle), fontSize = 10.sp, color = CarbonColors.Outline)
                }
                Checkbox(diagnosticTelemetry, { diagnosticTelemetry = it }, colors = CheckboxDefaults.colors(checkedColor = CarbonColors.Primary))
            }
            FieldLabel(stringResource(Res.string.settings_log_retention))
            OutlinedTextField(logRetention, { logRetention = it }, Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors())
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = {
                    onSave(
                        AppSettings(
                            adbPath = adbPath,
                            minPort = minPort.toIntOrNull() ?: 5555,
                            maxPort = maxPort.toIntOrNull() ?: 5585,
                            scanIntervalSec = scanInterval.toInt(),
                            parallelThreads = parallelThreads.toInt(),
                            logRetention = logRetention.toIntOrNull() ?: 2500,
                            autoApproveKey = autoApproveKey,
                            diagnosticTelemetry = diagnosticTelemetry,
                        ),
                    )
                    showToast = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = CarbonColors.Primary, contentColor = CarbonColors.OnPrimary),
            ) {
                Text(stringResource(Res.string.settings_save), fontWeight = FontWeight.Bold)
            }
        }

        if (showToast) {
            Text(stringResource(Res.string.settings_save_success), fontSize = 12.sp, color = CarbonColors.Primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(12.dp))
            .background(CarbonColors.SurfaceContainer, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
        content()
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CarbonColors.Outline, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CarbonColors.Primary,
    unfocusedBorderColor = CarbonColors.OutlineVariant,
    focusedTextColor = CarbonColors.OnSurfaceVariant,
    unfocusedTextColor = CarbonColors.OnSurfaceVariant,
    focusedContainerColor = CarbonColors.SurfaceContainerLowest,
    unfocusedContainerColor = CarbonColors.SurfaceContainerLowest,
)

@Composable
private fun sliderColors() = SliderDefaults.colors(thumbColor = CarbonColors.Primary, activeTrackColor = CarbonColors.Primary)
