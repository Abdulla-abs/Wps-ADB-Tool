package `fun`.abbas.wps_adb.ui.decompile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.FileNode
import `fun`.abbas.wps_adb.theme.CarbonColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DexActionDialog(
    dexFile: FileNode.File,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Backdrop overlay
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x990A0C0E))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        // Dialog Card Container
        Column(
            modifier = Modifier
                .width(480.dp)
                .background(CarbonColors.SurfaceContainerLow, RoundedCornerShape(12.dp))
                .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(12.dp))
                .clickable(enabled = false, onClick = {}) // Stop click propagation
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "DEX Actions",
                        color = CarbonColors.Primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        Text(
                            text = "Select method for ",
                            color = CarbonColors.Outline,
                            fontSize = 12.sp
                        )
                        Text(
                            text = dexFile.name,
                            color = CarbonColors.Tertiary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "✕",
                    color = CarbonColors.Outline,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Actions Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val actions = listOf(
                    "dex编辑器++" to "DEX_EDITOR_PLUS",
                    "dex转smali" to "DEX_TO_SMALI",
                    "dex转jar" to "DEX_TO_JAR",
                    "dex转java" to "DEX_TO_JAVA",
                    "dex修复" to "DEX_REPAIR",
                    "替换包名/类名" to "REPLACE_CLASS_NAME",
                    "控制流混淆" to "OBFUSCATE",
                    "dex属性" to "SHOW_PROPERTIES"
                )

                for (i in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val firstAction = actions[i * 2]
                        val secondAction = actions[i * 2 + 1]

                        DexActionButton(
                            label = firstAction.first,
                            onClick = { onAction(firstAction.second) },
                            modifier = Modifier.weight(1f)
                        )
                        DexActionButton(
                            label = secondAction.first,
                            onClick = { onAction(secondAction.second) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CarbonColors.SurfaceContainerLowest, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Cancel",
                    color = CarbonColors.Outline,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun DexActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(CarbonColors.SurfaceContainer, RoundedCornerShape(8.dp))
            .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when {
            label.contains("编辑器") -> "📝"
            label.contains("转smali") -> "🔄"
            label.contains("转jar") -> "☕"
            label.contains("转java") -> "☕"
            label.contains("修复") -> "🔧"
            label.contains("替换") -> "🔍"
            label.contains("混淆") -> "🌀"
            else -> "📊"
        }
        
        Text(text = icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = CarbonColors.OnSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
