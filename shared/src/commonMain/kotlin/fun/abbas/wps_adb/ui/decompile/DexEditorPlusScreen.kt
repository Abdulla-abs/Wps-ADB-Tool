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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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

        DexEditorTabRow(
            selectedTab = selectedSubTab,
            onTabSelected = { selectedSubTab = it },
        )

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
                            showRootFolder = false,
                            modifier = Modifier.width(280.dp).fillMaxHeight()
                        )
                        // Right Editor Area
                        val activeTab = uiState.openTabs.find {
                            it.id == uiState.activeTabId &&
                                (it.type == EditorType.SMALI || it.type == EditorType.JAVA)
                        }
                        if (activeTab != null) {
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                if (activeTab.type == EditorType.SMALI) {
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
                                            DexToolbarButton("smali转java") {
                                                viewModel.decompileActiveSmaliToJava()
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            DexToolbarButton("重组 DEX") {
                                                viewModel.reassembleDexFromEditor()
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            DexToolbarButton("导出", accent = CarbonColors.Secondary) {
                                                viewModel.exportActiveSmaliFile()
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "${activeTab.title} • utf-8",
                                                color = CarbonColors.Outline,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        Text(
                                            text = "✕",
                                            color = CarbonColors.Outline,
                                            fontSize = 11.sp,
                                            modifier = Modifier.clickable { viewModel.closeEditorTab(activeTab.id) }
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .background(CarbonColors.SurfaceContainerLow)
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "${activeTab.title} • Java preview",
                                            color = CarbonColors.Outline,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                        Text(
                                            text = "✕",
                                            color = CarbonColors.Outline,
                                            fontSize = 11.sp,
                                            modifier = Modifier.clickable { viewModel.closeEditorTab(activeTab.id) },
                                        )
                                    }
                                }

                                val isOverlayActive = uiState.decompileProgress != null
                                if (isOverlayActive) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .background(CarbonColors.SurfaceContainerLow),
                                    )
                                } else {
                                    CodeEditorBridge(
                                        content = activeTab.currentContent,
                                        onContentChange = { content ->
                                            if (activeTab.type == EditorType.SMALI) {
                                                viewModel.updateEditorContent(activeTab.id, content)
                                            }
                                        },
                                        syntax = if (activeTab.type == EditorType.SMALI) "smali" else "java",
                                        readOnly = activeTab.type == EditorType.JAVA,
                                        modifier = Modifier.fillMaxWidth().weight(1f),
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
                                            viewModel.openSmaliFromSearchHit(hit)
                                            selectedSubTab = 0
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
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "String Constant Pool",
                                color = CarbonColors.Primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            DexToolbarButton("保存常量") {
                                viewModel.saveDexEditorConstants()
                            }
                        }
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
                                        val label = if (item.sourceDex.isNotEmpty()) {
                                            "#${item.index} • ${item.sourceDex} (引用: ${item.referenceCount})"
                                        } else {
                                            "#${item.index} (引用次数: ${item.referenceCount})"
                                        }
                                        Text(
                                            text = label,
                                            color = CarbonColors.Outline,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        var editingText by remember(item.index, item.value) { mutableStateOf(item.value) }
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
    }
}

@Composable
private fun DexEditorTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf("浏览", "搜索", "常量")

    Column(modifier = modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            containerColor = CarbonColors.SurfaceContainerLowest,
            contentColor = CarbonColors.Primary,
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    val currentTab = tabPositions[selectedTab]
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(currentTab),
                        width = currentTab.width,
                        color = CarbonColors.Primary,
                        height = 3.dp,
                    )
                }
            },
            divider = {},
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    selectedContentColor = CarbonColors.Primary,
                    unselectedContentColor = CarbonColors.OnSurfaceVariant,
                    text = {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Medium else FontWeight.Normal,
                        )
                    },
                )
            }
        }
        HorizontalDivider(color = CarbonColors.OutlineVariant, thickness = 1.dp)
    }
}

@Composable
private fun DexToolbarButton(
    label: String,
    accent: androidx.compose.ui.graphics.Color = CarbonColors.Primary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(CarbonColors.SurfaceContainerHigh, RoundedCornerShape(4.dp))
            .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = accent, fontSize = 11.sp)
    }
}
