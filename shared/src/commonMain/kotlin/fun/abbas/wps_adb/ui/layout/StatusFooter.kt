package `fun`.abbas.wps_adb.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.theme.CarbonColors
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.*
import wpsadbtool.shared.generated.resources.Res

@Composable
fun StatusFooter(
    isAdbActive: Boolean,
    isRestarting: Boolean,
    onlineCount: Int,
    isLogTrayOpen: Boolean,
    onToggleLogTray: () -> Unit,
    onKillAdb: () -> Unit,
    onRestartAdb: () -> Unit,
    isSidePanelExpanded: Boolean,
    sidePanelTabCount: Int,
    onOpenSidePanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(CarbonColors.SurfaceContainerLowest)
            .border(width = 1.dp, color = CarbonColors.OutlineVariant.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (isAdbActive) CarbonColors.Primary else CarbonColors.Error,
                            CircleShape,
                        ),
                )
                Text(
                    text = buildString {
                        append(stringResource(Res.string.footer_adb_daemon))
                        append(" ")
                        append(
                            when {
                                isRestarting -> stringResource(Res.string.footer_adb_rebooting)
                                isAdbActive -> stringResource(Res.string.footer_adb_active)
                                else -> stringResource(Res.string.footer_adb_terminated)
                            },
                        )
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CarbonColors.Outline,
                )
            }
            Text(
                stringResource(Res.string.footer_cluster_status, onlineCount),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = CarbonColors.Outline,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FooterAction(if (isLogTrayOpen) stringResource(Res.string.action_logcat_hide) else stringResource(Res.string.action_logcat_show), onToggleLogTray)
            FooterAction(stringResource(Res.string.footer_kill_daemon), onKillAdb, enabled = isAdbActive && !isRestarting, isError = true)
            FooterAction(
                stringResource(Res.string.footer_restart_adb),
                onRestartAdb,
                enabled = !isRestarting,
            )
            if (!isSidePanelExpanded) {
                FooterAction(
                    label = if (sidePanelTabCount > 0) {
                        stringResource(Res.string.footer_open_sidepanel_with_count, sidePanelTabCount)
                    } else {
                        stringResource(Res.string.footer_open_sidepanel)
                    },
                    onClick = onOpenSidePanel,
                )
            }
        }
    }
}

@Composable
private fun FooterAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        label,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = when {
            !enabled -> CarbonColors.Outline.copy(alpha = 0.3f)
            isError -> CarbonColors.Error
            else -> CarbonColors.Outline
        },
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    )
}
