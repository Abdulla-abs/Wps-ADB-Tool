package `fun`.abbas.wps_adb.ui.decompile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.FileNode
import `fun`.abbas.wps_adb.theme.CarbonColors
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.decompile_dex_multi_select_all
import wpsadbtool.shared.generated.resources.decompile_dex_multi_select_confirm
import wpsadbtool.shared.generated.resources.decompile_dex_multi_select_none
import wpsadbtool.shared.generated.resources.decompile_dex_multi_select_title

@Composable
fun DexMultiSelectDialog(
    candidates: List<FileNode.File>,
    defaultSelectedPath: String?,
    onDismiss: () -> Unit,
    onConfirm: (List<FileNode.File>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedPaths by remember(candidates, defaultSelectedPath) {
        mutableStateOf(
            if (defaultSelectedPath != null) setOf(defaultSelectedPath) else emptySet(),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x990A0C0E))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .background(CarbonColors.SurfaceContainerLow, RoundedCornerShape(12.dp))
                .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(12.dp))
                .clickable(enabled = false, onClick = {}),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = stringResource(Res.string.decompile_dex_multi_select_title),
                    color = CarbonColors.Primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "✕",
                    color = CarbonColors.Outline,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.decompile_dex_multi_select_all),
                    color = CarbonColors.Secondary,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        selectedPaths = candidates.map { it.path }.toSet()
                    },
                )
                Text(
                    text = stringResource(Res.string.decompile_dex_multi_select_none),
                    color = CarbonColors.Outline,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { selectedPaths = emptySet() },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(candidates, key = { it.path }) { dex ->
                    val checked = dex.path in selectedPaths
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CarbonColors.SurfaceContainer, RoundedCornerShape(6.dp))
                            .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(6.dp))
                            .clickable {
                                selectedPaths = if (checked) {
                                    selectedPaths - dex.path
                                } else {
                                    selectedPaths + dex.path
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                selectedPaths = if (isChecked) {
                                    selectedPaths + dex.path
                                } else {
                                    selectedPaths - dex.path
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = CarbonColors.Primary,
                                uncheckedColor = CarbonColors.Outline,
                                checkmarkColor = CarbonColors.OnPrimary,
                            ),
                        )
                        Column {
                            Text(
                                text = dex.name,
                                color = CarbonColors.OnSurface,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = formatDexSize(dex.size),
                                color = CarbonColors.Outline,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        CarbonColors.SurfaceContainerLowest,
                        RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Cancel",
                    color = CarbonColors.Outline,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.decompile_dex_multi_select_confirm),
                    color = if (selectedPaths.isNotEmpty()) CarbonColors.Primary else CarbonColors.Outline,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(enabled = selectedPaths.isNotEmpty()) {
                            val selected = candidates.filter { it.path in selectedPaths }
                            onConfirm(selected)
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun formatDexSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
