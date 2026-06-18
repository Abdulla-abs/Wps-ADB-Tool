package `fun`.abbas.wps_adb.ui.device

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.DefaultEasyActions
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceShellSessionState
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.EasyActionKind
import `fun`.abbas.wps_adb.model.ShellTransitionKind
import `fun`.abbas.wps_adb.theme.CarbonColors
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.shell_action_view_logcat
import wpsadbtool.shared.generated.resources.shell_breadcrumb_shell
import wpsadbtool.shared.generated.resources.shell_breadcrumb_wall
import wpsadbtool.shared.generated.resources.shell_header_connected
import wpsadbtool.shared.generated.resources.shell_header_connecting
import wpsadbtool.shared.generated.resources.shell_header_disconnected
import wpsadbtool.shared.generated.resources.shell_header_error
import wpsadbtool.shared.generated.resources.shell_terminal_hint
import wpsadbtool.shared.generated.resources.shell_terminal_hidden_by_sidepanel
import wpsadbtool.shared.generated.resources.shell_terminal_loading

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DeviceShellScreen(
    device: Device,
    sessionState: DeviceShellSessionState,
    errorMessage: String?,
    terminalComponent: Any?,
    terminalSurfaceReady: Boolean,
    isScreenRecording: Boolean,
    developerOptionStates: Map<EasyActionKind, Boolean> = emptyMap(),
    transitionKind: ShellTransitionKind,
    onBack: () -> Unit,
    onOpenLogcat: () -> Unit,
    onTerminalMounted: () -> Unit,
    onEasyAction: (EasyActionKind) -> Unit,
    suppressTerminalSurface: Boolean = false,
    terminalHiddenMessage: String? = null,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonColors.Background)
            .padding(24.dp),
    ) {
        DeviceShellHeader(
            device = device,
            sessionState = sessionState,
            errorMessage = errorMessage,
            transitionKind = transitionKind,
            onBack = onBack,
            onOpenLogcat = onOpenLogcat,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
        )
        Text(
            text = stringResource(Res.string.shell_terminal_hint),
            fontSize = 11.sp,
            color = CarbonColors.OnSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(8.dp)),
            ) {
                when {
                    suppressTerminalSurface -> ShellTerminalPlaceholder(
                        message = terminalHiddenMessage ?: stringResource(Res.string.shell_terminal_hidden_by_sidepanel),
                        modifier = Modifier.fillMaxSize(),
                    )
                    terminalSurfaceReady -> JediTermPanel(
                        terminalComponent = terminalComponent,
                        onMounted = onTerminalMounted,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> ShellTerminalPlaceholder(modifier = Modifier.fillMaxSize())
                }
            }
            EasyActionsPanel(
                actions = DefaultEasyActions,
                isScreenRecording = isScreenRecording,
                toggleStates = developerOptionStates,
                onAction = onEasyAction,
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .padding(start = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DeviceShellHeader(
    device: Device,
    sessionState: DeviceShellSessionState,
    errorMessage: String?,
    transitionKind: ShellTransitionKind,
    onBack: () -> Unit,
    onOpenLogcat: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.shell_breadcrumb_wall),
                fontSize = 12.sp,
                color = CarbonColors.Primary,
                modifier = Modifier.clickable(onClick = onBack),
            )
            Text("/", fontSize = 12.sp, color = CarbonColors.Outline)
            val titleModifier = if (
                transitionKind == ShellTransitionKind.SHARED_ELEMENT &&
                sharedTransitionScope != null &&
                animatedVisibilityScope != null
            ) {
                with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        rememberSharedContentState(ShellSharedElementKeys.title(device.id)),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            } else {
                Modifier
            }
            Text(
                text = device.name,
                fontSize = 12.sp,
                color = CarbonColors.OnSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = titleModifier,
            )
            Text("/", fontSize = 12.sp, color = CarbonColors.Outline)
            Text(
                text = stringResource(Res.string.shell_breadcrumb_shell),
                fontSize = 12.sp,
                color = CarbonColors.OnSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = shellSessionStatusLabel(sessionState, errorMessage),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = CarbonColors.Outline,
            )
            Text(
                text = stringResource(Res.string.shell_action_view_logcat),
                fontSize = 11.sp,
                color = CarbonColors.Primary,
                modifier = Modifier.clickable(onClick = onOpenLogcat),
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val heroModifier = if (
            transitionKind == ShellTransitionKind.SHARED_ELEMENT &&
            sharedTransitionScope != null &&
            animatedVisibilityScope != null
        ) {
            with(sharedTransitionScope) {
                Modifier
                    .sharedElement(
                        rememberSharedContentState(ShellSharedElementKeys.hero(device.id)),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                    .width(72.dp)
                    .height(48.dp)
            }
        } else {
            Modifier.width(72.dp).height(48.dp)
        }
        Box(
            modifier = heroModifier
                .clip(RoundedCornerShape(6.dp))
                .background(CarbonColors.SurfaceContainerLowest)
                .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (device.status == DeviceStatus.ONLINE) "●" else "○",
                color = if (device.status == DeviceStatus.ONLINE) CarbonColors.Primary else CarbonColors.Error,
            )
        }
        Column {
            Text(device.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
            Text(device.serial, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = CarbonColors.Outline)
        }
    }
}

@Composable
private fun ShellTerminalPlaceholder(
    modifier: Modifier = Modifier,
    message: String = stringResource(Res.string.shell_terminal_loading),
) {
    Box(
        modifier = modifier.background(CarbonColors.SurfaceContainerLowest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            fontSize = 12.sp,
            color = CarbonColors.Outline,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun shellSessionStatusLabel(sessionState: DeviceShellSessionState, errorMessage: String?): String =
    when (sessionState) {
        DeviceShellSessionState.CONNECTING -> stringResource(Res.string.shell_header_connecting)
        DeviceShellSessionState.CONNECTED -> stringResource(Res.string.shell_header_connected)
        DeviceShellSessionState.DISCONNECTED -> stringResource(Res.string.shell_header_disconnected)
        DeviceShellSessionState.ERROR -> errorMessage ?: stringResource(Res.string.shell_header_error)
        DeviceShellSessionState.UNAVAILABLE -> stringResource(Res.string.shell_header_error)
        DeviceShellSessionState.IDLE -> ""
    }
