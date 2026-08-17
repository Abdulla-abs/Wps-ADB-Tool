package `fun`.abbas.wps_adb.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.ToolInstallPhase
import `fun`.abbas.wps_adb.model.ToolInstallProgress
import `fun`.abbas.wps_adb.model.ToolKind
import `fun`.abbas.wps_adb.theme.CarbonColors
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.tool_install_adb_missing_prefix
import wpsadbtool.shared.generated.resources.tool_install_adb_missing_link
import wpsadbtool.shared.generated.resources.tool_install_dismiss_error
import wpsadbtool.shared.generated.resources.tool_install_progress_title_adb
import wpsadbtool.shared.generated.resources.tool_install_progress_title_scrcpy
import wpsadbtool.shared.generated.resources.tool_install_scrcpy_missing_hint
import wpsadbtool.shared.generated.resources.tool_install_scrcpy_download

@Composable
fun AdbMissingHintPanel(
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefix = stringResource(Res.string.tool_install_adb_missing_prefix)
    val link = stringResource(Res.string.tool_install_adb_missing_link)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(12.dp))
            .background(CarbonColors.SurfaceContainerLow)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(prefix, fontSize = 13.sp, color = CarbonColors.OnSurfaceVariant)
            Text(
                link,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = CarbonColors.Primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onDownloadClick),
            )
        }
    }
}

@Composable
fun ToolInstallProgressPanel(
    progress: ToolInstallProgress,
    onDismissFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (progress.kind) {
        ToolKind.ADB -> stringResource(Res.string.tool_install_progress_title_adb)
        ToolKind.SCRCPY -> stringResource(Res.string.tool_install_progress_title_scrcpy)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(12.dp))
            .background(CarbonColors.SurfaceContainerLow)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
        if (progress.phase != ToolInstallPhase.FAILED) {
            LinearProgressIndicator(
                progress = { progress.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = CarbonColors.Primary,
                trackColor = CarbonColors.OutlineVariant,
            )
        }
        Text(
            progress.errorMessage ?: progress.message,
            fontSize = 11.sp,
            color = if (progress.phase == ToolInstallPhase.FAILED) CarbonColors.Error else CarbonColors.OnSurfaceVariant,
        )
        if (progress.phase == ToolInstallPhase.FAILED) {
            Button(
                onClick = onDismissFailure,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CarbonColors.SurfaceContainerHighest,
                    contentColor = CarbonColors.OnSurface,
                ),
            ) {
                Text(stringResource(Res.string.tool_install_dismiss_error))
            }
        }
    }
}

@Composable
fun ScrcpyMissingHintPanel(
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(Res.string.tool_install_scrcpy_missing_hint),
            fontSize = 12.sp,
            color = CarbonColors.OnSurfaceVariant,
        )
        Button(
            onClick = onDownloadClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = CarbonColors.Primary,
                contentColor = CarbonColors.OnPrimary,
            ),
        ) {
            Text(stringResource(Res.string.tool_install_scrcpy_download), fontWeight = FontWeight.Bold)
        }
    }
}
