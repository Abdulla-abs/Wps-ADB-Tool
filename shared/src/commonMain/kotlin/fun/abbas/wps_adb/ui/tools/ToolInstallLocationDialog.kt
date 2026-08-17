package `fun`.abbas.wps_adb.ui.tools

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import `fun`.abbas.wps_adb.model.ToolKind
import `fun`.abbas.wps_adb.platform.pickDirectory
import `fun`.abbas.wps_adb.theme.CarbonColors
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.tool_install_browse
import wpsadbtool.shared.generated.resources.tool_install_cancel
import wpsadbtool.shared.generated.resources.tool_install_confirm
import wpsadbtool.shared.generated.resources.tool_install_dialog_adb_title
import wpsadbtool.shared.generated.resources.tool_install_dialog_hint
import wpsadbtool.shared.generated.resources.tool_install_dialog_path_label
import wpsadbtool.shared.generated.resources.tool_install_dialog_scrcpy_title
import wpsadbtool.shared.generated.resources.tool_install_pick_dir_title

@Composable
fun ToolInstallLocationDialog(
    kind: ToolKind,
    initialPath: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember(kind, initialPath) { mutableStateOf(initialPath) }
    val scope = rememberCoroutineScope()
    val title = when (kind) {
        ToolKind.ADB -> stringResource(Res.string.tool_install_dialog_adb_title)
        ToolKind.SCRCPY -> stringResource(Res.string.tool_install_dialog_scrcpy_title)
    }
    val pickTitle = stringResource(Res.string.tool_install_pick_dir_title)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CarbonColors.SurfaceContainer, RoundedCornerShape(12.dp))
                .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = CarbonColors.OnSurface,
            )
            Text(
                stringResource(Res.string.tool_install_dialog_hint),
                fontSize = 11.sp,
                color = CarbonColors.OnSurfaceVariant,
            )
            Text(
                stringResource(Res.string.tool_install_dialog_path_label),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = CarbonColors.Outline,
            )
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CarbonColors.Primary,
                    unfocusedBorderColor = CarbonColors.OutlineVariant,
                    focusedTextColor = CarbonColors.OnSurface,
                    unfocusedTextColor = CarbonColors.OnSurface,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            pickDirectory(path.ifBlank { null }, pickTitle)?.let { path = it }
                        }
                    },
                ) {
                    Text(stringResource(Res.string.tool_install_browse))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.tool_install_cancel))
                }
                Button(
                    onClick = { onConfirm(path.trim()) },
                    enabled = path.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CarbonColors.Primary,
                        contentColor = CarbonColors.OnPrimary,
                    ),
                ) {
                    Text(stringResource(Res.string.tool_install_confirm), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
