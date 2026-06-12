package `fun`.abbas.wps_adb.ui.device

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.theme.CarbonColors
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.shell_package_dialog_cancel
import wpsadbtool.shared.generated.resources.shell_package_dialog_confirm
import wpsadbtool.shared.generated.resources.shell_package_dialog_hint
import wpsadbtool.shared.generated.resources.shell_package_dialog_title

@Composable
fun PackageNameDialog(
    recentPackages: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var packageName by rememberSaveable { mutableStateOf(recentPackages.firstOrNull().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.shell_package_dialog_title)) },
        text = {
            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it },
                label = { Text(stringResource(Res.string.shell_package_dialog_hint)) },
                singleLine = true,
            )
            if (recentPackages.isNotEmpty()) {
                Text(
                    text = recentPackages.joinToString("\n"),
                    fontSize = 11.sp,
                    color = CarbonColors.Outline,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(packageName.trim()) },
                enabled = packageName.isNotBlank(),
            ) {
                Text(stringResource(Res.string.shell_package_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.shell_package_dialog_cancel))
            }
        },
    )
}
