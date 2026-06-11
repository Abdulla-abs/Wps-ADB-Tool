package `fun`.abbas.wps_adb.ui.sidepanel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.MirrorSessionState
import `fun`.abbas.wps_adb.model.ScrcpyConnectionOptions
import `fun`.abbas.wps_adb.model.ScrcpyMaxFps
import `fun`.abbas.wps_adb.model.ScrcpyMaxSize
import `fun`.abbas.wps_adb.model.ScrcpyVideoBitRate
import `fun`.abbas.wps_adb.theme.CarbonColors
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.mirror_action_download
import wpsadbtool.shared.generated.resources.mirror_action_start
import wpsadbtool.shared.generated.resources.mirror_action_stop
import wpsadbtool.shared.generated.resources.mirror_battery
import wpsadbtool.shared.generated.resources.mirror_connection_settings
import wpsadbtool.shared.generated.resources.mirror_device_subtitle
import wpsadbtool.shared.generated.resources.mirror_download_hint
import wpsadbtool.shared.generated.resources.mirror_error_prefix
import wpsadbtool.shared.generated.resources.mirror_fps_15
import wpsadbtool.shared.generated.resources.mirror_fps_30
import wpsadbtool.shared.generated.resources.mirror_fps_60
import wpsadbtool.shared.generated.resources.mirror_fps_default
import wpsadbtool.shared.generated.resources.mirror_max_size_1080
import wpsadbtool.shared.generated.resources.mirror_max_size_1440
import wpsadbtool.shared.generated.resources.mirror_max_size_720
import wpsadbtool.shared.generated.resources.mirror_max_size_original
import wpsadbtool.shared.generated.resources.mirror_option_always_on_top
import wpsadbtool.shared.generated.resources.mirror_option_enable_audio
import wpsadbtool.shared.generated.resources.mirror_option_show_touches
import wpsadbtool.shared.generated.resources.mirror_option_stay_awake
import wpsadbtool.shared.generated.resources.mirror_option_turn_screen_off
import wpsadbtool.shared.generated.resources.mirror_option_view_only
import wpsadbtool.shared.generated.resources.mirror_quality_bitrate
import wpsadbtool.shared.generated.resources.mirror_quality_fps
import wpsadbtool.shared.generated.resources.mirror_quality_max_size
import wpsadbtool.shared.generated.resources.mirror_settings_locked_hint
import wpsadbtool.shared.generated.resources.mirror_state_error
import wpsadbtool.shared.generated.resources.mirror_state_idle
import wpsadbtool.shared.generated.resources.mirror_state_running
import wpsadbtool.shared.generated.resources.mirror_state_starting
import wpsadbtool.shared.generated.resources.mirror_state_stopped
import wpsadbtool.shared.generated.resources.mirror_state_unavailable
import wpsadbtool.shared.generated.resources.mirror_title
import wpsadbtool.shared.generated.resources.mirror_unavailable_hint
import wpsadbtool.shared.generated.resources.mirror_video_bitrate_16m
import wpsadbtool.shared.generated.resources.mirror_video_bitrate_2m
import wpsadbtool.shared.generated.resources.mirror_video_bitrate_4m
import wpsadbtool.shared.generated.resources.mirror_video_bitrate_8m
import wpsadbtool.shared.generated.resources.mirror_video_bitrate_default

private const val SCRCPY_RELEASES_URL = "https://github.com/Genymobile/scrcpy/releases"

@Composable
fun MirrorTabContent(
    device: Device,
    sessionState: MirrorSessionState,
    errorMessage: String?,
    connectionOptions: ScrcpyConnectionOptions,
    settingsEditable: Boolean,
    onStartMirror: () -> Unit,
    onStopMirror: () -> Unit,
    onConnectionOptionsChange: (ScrcpyConnectionOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                stringResource(Res.string.mirror_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = CarbonColors.OnSurface,
            )
            Text(
                stringResource(Res.string.mirror_device_subtitle, device.name, device.serial),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = CarbonColors.Outline,
            )
        }

        DeviceInfoCard(device)
        StatusBadge(sessionState = sessionState)

        ConnectionSettingsCard(
            options = connectionOptions,
            enabled = settingsEditable,
            onOptionsChange = onConnectionOptionsChange,
        )

        if (!settingsEditable && sessionState != MirrorSessionState.UNAVAILABLE) {
            Text(
                stringResource(Res.string.mirror_settings_locked_hint),
                fontSize = 9.sp,
                color = CarbonColors.Outline,
            )
        }

        when (sessionState) {
            MirrorSessionState.UNAVAILABLE -> {
                Text(
                    stringResource(Res.string.mirror_unavailable_hint),
                    fontSize = 11.sp,
                    color = CarbonColors.OnSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { uriHandler.openUri(SCRCPY_RELEASES_URL) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.mirror_action_download))
                }
                Text(
                    stringResource(Res.string.mirror_download_hint),
                    fontSize = 9.sp,
                    color = CarbonColors.Outline,
                )
            }
            MirrorSessionState.RUNNING -> {
                Button(
                    onClick = onStopMirror,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CarbonColors.Error.copy(alpha = 0.2f),
                        contentColor = CarbonColors.Error,
                    ),
                ) {
                    Text(stringResource(Res.string.mirror_action_stop), fontWeight = FontWeight.Bold)
                }
            }
            MirrorSessionState.STARTING -> Unit
            else -> {
                Button(
                    onClick = onStartMirror,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CarbonColors.Primary,
                        contentColor = CarbonColors.OnPrimary,
                    ),
                ) {
                    Text(stringResource(Res.string.mirror_action_start), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                stringResource(Res.string.mirror_error_prefix, errorMessage),
                fontSize = 10.sp,
                color = CarbonColors.Error,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionSettingsCard(
    options: ScrcpyConnectionOptions,
    enabled: Boolean,
    onOptionsChange: (ScrcpyConnectionOptions) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CarbonColors.SurfaceContainerLowest, RoundedCornerShape(8.dp))
            .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(Res.string.mirror_connection_settings),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = CarbonColors.Outline,
        )

        ToggleRow(
            label = stringResource(Res.string.mirror_option_enable_audio),
            checked = options.enableAudio,
            enabled = enabled,
            onToggle = { onOptionsChange(options.copy(enableAudio = !options.enableAudio)) },
        )

        SettingGroup(stringResource(Res.string.mirror_quality_max_size)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OptionChip(stringResource(Res.string.mirror_max_size_original), options.maxSize == ScrcpyMaxSize.ORIGINAL, enabled) {
                    onOptionsChange(options.copy(maxSize = ScrcpyMaxSize.ORIGINAL))
                }
                OptionChip(stringResource(Res.string.mirror_max_size_720), options.maxSize == ScrcpyMaxSize.P720, enabled) {
                    onOptionsChange(options.copy(maxSize = ScrcpyMaxSize.P720))
                }
                OptionChip(stringResource(Res.string.mirror_max_size_1080), options.maxSize == ScrcpyMaxSize.P1080, enabled) {
                    onOptionsChange(options.copy(maxSize = ScrcpyMaxSize.P1080))
                }
                OptionChip(stringResource(Res.string.mirror_max_size_1440), options.maxSize == ScrcpyMaxSize.P1440, enabled) {
                    onOptionsChange(options.copy(maxSize = ScrcpyMaxSize.P1440))
                }
            }
        }

        SettingGroup(stringResource(Res.string.mirror_quality_bitrate)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OptionChip(stringResource(Res.string.mirror_video_bitrate_default), options.videoBitRate == ScrcpyVideoBitRate.DEFAULT, enabled) {
                    onOptionsChange(options.copy(videoBitRate = ScrcpyVideoBitRate.DEFAULT))
                }
                OptionChip(stringResource(Res.string.mirror_video_bitrate_2m), options.videoBitRate == ScrcpyVideoBitRate.LOW, enabled) {
                    onOptionsChange(options.copy(videoBitRate = ScrcpyVideoBitRate.LOW))
                }
                OptionChip(stringResource(Res.string.mirror_video_bitrate_4m), options.videoBitRate == ScrcpyVideoBitRate.MEDIUM, enabled) {
                    onOptionsChange(options.copy(videoBitRate = ScrcpyVideoBitRate.MEDIUM))
                }
                OptionChip(stringResource(Res.string.mirror_video_bitrate_8m), options.videoBitRate == ScrcpyVideoBitRate.HIGH, enabled) {
                    onOptionsChange(options.copy(videoBitRate = ScrcpyVideoBitRate.HIGH))
                }
                OptionChip(stringResource(Res.string.mirror_video_bitrate_16m), options.videoBitRate == ScrcpyVideoBitRate.ULTRA, enabled) {
                    onOptionsChange(options.copy(videoBitRate = ScrcpyVideoBitRate.ULTRA))
                }
            }
        }

        SettingGroup(stringResource(Res.string.mirror_quality_fps)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OptionChip(stringResource(Res.string.mirror_fps_default), options.maxFps == ScrcpyMaxFps.DEFAULT, enabled) {
                    onOptionsChange(options.copy(maxFps = ScrcpyMaxFps.DEFAULT))
                }
                OptionChip(stringResource(Res.string.mirror_fps_15), options.maxFps == ScrcpyMaxFps.FPS15, enabled) {
                    onOptionsChange(options.copy(maxFps = ScrcpyMaxFps.FPS15))
                }
                OptionChip(stringResource(Res.string.mirror_fps_30), options.maxFps == ScrcpyMaxFps.FPS30, enabled) {
                    onOptionsChange(options.copy(maxFps = ScrcpyMaxFps.FPS30))
                }
                OptionChip(stringResource(Res.string.mirror_fps_60), options.maxFps == ScrcpyMaxFps.FPS60, enabled) {
                    onOptionsChange(options.copy(maxFps = ScrcpyMaxFps.FPS60))
                }
            }
        }

        ToggleRow(
            label = stringResource(Res.string.mirror_option_stay_awake),
            checked = options.stayAwake,
            enabled = enabled,
            onToggle = { onOptionsChange(options.copy(stayAwake = !options.stayAwake)) },
        )
        ToggleRow(
            label = stringResource(Res.string.mirror_option_turn_screen_off),
            checked = options.turnScreenOff,
            enabled = enabled,
            onToggle = { onOptionsChange(options.copy(turnScreenOff = !options.turnScreenOff)) },
        )
        ToggleRow(
            label = stringResource(Res.string.mirror_option_show_touches),
            checked = options.showTouches,
            enabled = enabled,
            onToggle = { onOptionsChange(options.copy(showTouches = !options.showTouches)) },
        )
        ToggleRow(
            label = stringResource(Res.string.mirror_option_always_on_top),
            checked = options.alwaysOnTop,
            enabled = enabled,
            onToggle = { onOptionsChange(options.copy(alwaysOnTop = !options.alwaysOnTop)) },
        )
        ToggleRow(
            label = stringResource(Res.string.mirror_option_view_only),
            checked = options.viewOnly,
            enabled = enabled,
            onToggle = { onOptionsChange(options.copy(viewOnly = !options.viewOnly)) },
        )
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = CarbonColors.OnSurfaceVariant)
        content()
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 10.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = when {
            !enabled -> CarbonColors.Outline
            selected -> CarbonColors.Primary
            else -> CarbonColors.OnSurfaceVariant
        },
        modifier = Modifier
            .border(
                1.dp,
                when {
                    !enabled -> CarbonColors.OutlineVariant.copy(alpha = 0.5f)
                    selected -> CarbonColors.Primary
                    else -> CarbonColors.OutlineVariant
                },
                RoundedCornerShape(6.dp),
            )
            .background(
                when {
                    selected && enabled -> CarbonColors.Primary.copy(alpha = 0.12f)
                    else -> CarbonColors.SurfaceContainer
                },
                RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 10.sp, color = if (enabled) CarbonColors.OnSurface else CarbonColors.Outline)
        Checkbox(
            checked = checked,
            onCheckedChange = { if (enabled) onToggle() },
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = CarbonColors.Primary),
        )
    }
}

@Composable
private fun DeviceInfoCard(device: Device) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CarbonColors.SurfaceContainerLowest, RoundedCornerShape(8.dp))
            .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        InfoRow(label = "Serial", value = device.serial)
        InfoRow(
            label = "Battery",
            value = stringResource(Res.string.mirror_battery, device.batteryLevel),
        )
        InfoRow(label = "Android", value = device.androidVersion)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 10.sp, color = CarbonColors.Outline)
        Text(value, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = CarbonColors.OnSurface)
    }
}

@Composable
private fun StatusBadge(sessionState: MirrorSessionState) {
    val (label, color) = when (sessionState) {
        MirrorSessionState.IDLE -> stringResource(Res.string.mirror_state_idle) to CarbonColors.Outline
        MirrorSessionState.STARTING -> stringResource(Res.string.mirror_state_starting) to CarbonColors.Primary
        MirrorSessionState.RUNNING -> stringResource(Res.string.mirror_state_running) to CarbonColors.Primary
        MirrorSessionState.STOPPED -> stringResource(Res.string.mirror_state_stopped) to CarbonColors.OnSurfaceVariant
        MirrorSessionState.ERROR -> stringResource(Res.string.mirror_state_error) to CarbonColors.Error
        MirrorSessionState.UNAVAILABLE -> stringResource(Res.string.mirror_state_unavailable) to CarbonColors.Error
    }
    Text(
        label.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
