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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.EditorTab
import `fun`.abbas.wps_adb.model.EditorType
import `fun`.abbas.wps_adb.model.FileNode
import `fun`.abbas.wps_adb.theme.CarbonColors
import `fun`.abbas.wps_adb.ui.editor.CodeEditorBridge
import `fun`.abbas.wps_adb.viewmodel.AppUiState
import `fun`.abbas.wps_adb.viewmodel.AppViewModel

@Composable
fun DexEditorPlusScreen(
    uiState: AppUiState,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableStateOf(0) } // 0: 浏览, 1: 搜索, 2: 常量
    var showJadxProgressOverlay by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.background(CarbonColors.Background)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(CarbonColors.SurfaceContainerLow)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← 返回工作空间",
                color = CarbonColors.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "DEX 编辑器 Plus | ${uiState.activeDexEditorProject ?: "DEX"}",
                color = CarbonColors.OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Sub-Header / Editor Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(CarbonColors.SurfaceContainerLowest)
                .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(0.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.fillMaxHeight()) {
                TabItem("浏览", selectedSubTab == 0) { selectedSubTab = 0 }
                TabItem("搜索", selectedSubTab == 1) { selectedSubTab = 1 }
                TabItem("常量", selectedSubTab == 2) { selectedSubTab = 2 }
            }
        }

        // Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedSubTab) {
                0 -> {
                    // Browse Tab Content (Split Pane)
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left folder explorer
                        ProjectExplorer(
                            rootFolder = uiState.dexBrowseTree,
                            onFileClick = { node -> viewModel.handleFileNodeClick(node) },
                            modifier = Modifier.width(280.dp).fillMaxHeight()
                        )
                        // Right Editor Area
                        val activeTab = uiState.openTabs.find { it.id == uiState.activeTabId && it.type == EditorType.SMALI }
                        if (activeTab != null) {
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                // Smali specific toolbar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .background(CarbonColors.SurfaceContainerLow)
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Row(
                                            modifier = Modifier
                                                .background(CarbonColors.SurfaceContainerHigh, RoundedCornerShape(4.dp))
                                                .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(4.dp))
                                                .clickable { showJadxProgressOverlay = true }
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "smali转java", color = CarbonColors.Primary, fontSize = 11.sp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Row(
                                            modifier = Modifier
                                                .background(CarbonColors.SurfaceContainerHigh, RoundedCornerShape(4.dp))
                                                .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(4.dp))
                                                .clickable { /* Export code */ }
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "导出", color = CarbonColors.Secondary, fontSize = 11.sp)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "${activeTab.title} • utf-8",
                                            color = CarbonColors.Outline,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "✕",
                                            color = CarbonColors.Outline,
                                            fontSize = 11.sp,
                                            modifier = Modifier.clickable { viewModel.closeEditorTab(activeTab.id) }
                                        )
                                    }
                                }

                                if (showJadxProgressOverlay) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .background(CarbonColors.SurfaceContainerLowest)
                                    )
                                } else {
                                    CodeEditorBridge(
                                        content = activeTab.currentContent,
                                        onContentChange = { content -> viewModel.updateEditorContent(activeTab.id, content) },
                                        syntax = "smali",
                                        modifier = Modifier.fillMaxWidth().weight(1f)
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "请在左侧浏览包结构并选择 smali 文件进行编辑",
                                    color = CarbonColors.Outline,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Search Tab Content
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CarbonColors.SurfaceContainer, RoundedCornerShape(6.dp))
                                .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🔍 ", fontSize = 14.sp)
                            BasicTextField(
                                value = uiState.dexSearchQuery,
                                onValueChange = { query -> viewModel.performDexSearch(query) },
                                textStyle = TextStyle(color = CarbonColors.OnSurface, fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    if (uiState.dexSearchQuery.isEmpty()) {
                                        Text(text = "输入搜索关键词以检索 Smali 代码...", color = CarbonColors.Outline, fontSize = 13.sp)
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(uiState.dexSearchResults) { hit ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CarbonColors.SurfaceContainerLow, RoundedCornerShape(6.dp))
                                        .border(1.dp, CarbonColors.OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .clickable {
                                            // Navigation back to browse mode can be implemented here
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = hit.filePath.substringAfterLast("/").substringAfterLast("\\") + " @ 行 " + hit.lineNumber,
                                        color = CarbonColors.Primary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = hit.lineContent.trim(),
                                        color = CarbonColors.OnSurface,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Constants Tab Content
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            text = "String Constant Pool",
                            color = CarbonColors.Primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(uiState.dexConstantsList) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CarbonColors.SurfaceContainerLowest, RoundedCornerShape(6.dp))
                                        .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(6.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "#${item.index} (引用次数: ${item.referenceCount})",
                                            color = CarbonColors.Outline,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        var editingText by remember { mutableStateOf(item.value) }
                                        BasicTextField(
                                            value = editingText,
                                            onValueChange = {
                                                editingText = it
                                                viewModel.editDexConstant(item.index, it)
                                            },
                                            textStyle = TextStyle(
                                                color = CarbonColors.OnSurface,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        )
                                    }
                                    Text(text = "✏️", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Generating JADX code overlay dialog
        if (showJadxProgressOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x990A0C0E)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(420.dp)
                        .background(CarbonColors.SurfaceContainerLow, RoundedCornerShape(12.dp))
                        .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(12.dp))
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Generating Java Code",
                        color = CarbonColors.Primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "JADX Engine is processing the selected smali file.",
                        color = CarbonColors.Outline,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(CarbonColors.SurfaceContainerLowest, RoundedCornerShape(6.dp))
                            .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    ) {
                        Text(text = "> Initializing decompiler engine...", color = CarbonColors.Primary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "> Parsing class constant pool...", color = CarbonColors.OnSurface, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "> Reconstructing control flow graph...", color = CarbonColors.OnSurface, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "> Generating Java source...", color = CarbonColors.Outline, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Cancel",
                            color = CarbonColors.Outline,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable { showJadxProgressOverlay = false }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Open Java View",
                            color = CarbonColors.Primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { showJadxProgressOverlay = false }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = CarbonColors.OutlineVariant,
                shape = RoundedCornerShape(0.dp)
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = if (selected) CarbonColors.Primary else CarbonColors.Outline,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            if (selected) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(CarbonColors.Primary)
                )
            }
        }
    }
}
