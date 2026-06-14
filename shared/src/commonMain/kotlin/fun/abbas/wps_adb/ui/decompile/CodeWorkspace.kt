package `fun`.abbas.wps_adb.ui.decompile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.EditorTab
import `fun`.abbas.wps_adb.theme.CarbonColors
import `fun`.abbas.wps_adb.ui.editor.CodeEditorBridge

@Composable
fun CodeWorkspace(
    tabs: List<EditorTab>,
    activeTabId: String?,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onContentChange: (String, String) -> Unit,
    isOverlayActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val activeTab = tabs.find { it.id == activeTabId }

    Column(
        modifier = modifier.background(CarbonColors.Background)
    ) {
        if (tabs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "⚙️",
                        fontSize = 64.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "无活跃编辑器会话",
                        color = CarbonColors.OnSurfaceVariant,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "请在左侧文件树双击选择 XML 或 Smali 文件进行查看或编辑。",
                        color = CarbonColors.Outline,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Editor Tabs Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(CarbonColors.SurfaceContainerLowest)
                    .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(0.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isActive = tab.id == activeTabId
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(if (isActive) CarbonColors.Background else CarbonColors.SurfaceContainerLowest)
                            .border(
                                width = 1.dp,
                                color = CarbonColors.OutlineVariant,
                                shape = RoundedCornerShape(0.dp)
                            )
                            .clickable { onSelectTab(tab.id) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tab.title,
                            color = if (isActive) CarbonColors.Primary else CarbonColors.Outline,
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                        if (tab.isDirty) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "●", color = CarbonColors.Primary, fontSize = 8.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "✕",
                            color = CarbonColors.Outline,
                            fontSize = 10.sp,
                            modifier = Modifier.clickable { onCloseTab(tab.id) }
                        )
                    }
                }
            }

            // Breadcrumbs Bar
            if (activeTab != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CarbonColors.SurfaceContainerLow)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Workspace",
                        color = CarbonColors.Outline,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = " > ",
                        color = CarbonColors.OutlineVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        text = activeTab.filePath.substringAfterLast("/").substringAfterLast("\\"),
                        color = CarbonColors.OnSurface,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isOverlayActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(CarbonColors.SurfaceContainerLow)
                    )
                } else {
                    CodeEditorBridge(
                        content = activeTab.currentContent,
                        onContentChange = { newContent -> onContentChange(activeTab.id, newContent) },
                        syntax = when (activeTab.type) {
                            `fun`.abbas.wps_adb.model.EditorType.XML -> "xml"
                            `fun`.abbas.wps_adb.model.EditorType.JAVA -> "java"
                            `fun`.abbas.wps_adb.model.EditorType.SMALI -> "smali"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}
