package `fun`.abbas.wps_adb.ui.decompile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.abbas.wps_adb.viewmodel.AppUiState
import `fun`.abbas.wps_adb.viewmodel.AppViewModel

@Composable
fun DecompileStudioScreen(
    uiState: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (uiState.decompileWorkspace == null) {
            DecompileEmptyStateScreen(
                onApkImport = { apkPath -> viewModel.importApkToWorkspace(apkPath) },
                onRecentProjectClick = { project -> viewModel.openRecentDecompileProject(project) },
                onManageProjects = { viewModel.openDecompileProjectManager() },
                onCloseProjectManager = { viewModel.closeDecompileProjectManager() },
                onOpenProject = { project -> viewModel.openRecentDecompileProject(project) },
                onDeleteProject = { project -> viewModel.deleteDecompileProject(project) },
                recentProjects = uiState.recentDecompileProjects,
                showProjectManager = uiState.showDecompileProjectManager,
                progress = uiState.decompileProgress,
                taskName = uiState.currentTaskName,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (uiState.activeDexEditorProject != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                DexEditorPlusScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onBack = { viewModel.closeDexEditorPlus() },
                    modifier = Modifier.fillMaxSize()
                )
                if (uiState.decompileProgress != null) {
                    DecompileImportProgressOverlay(
                        progress = uiState.decompileProgress,
                        taskName = uiState.currentTaskName,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                DecompileWorkspaceToolbar(
                    packageName = uiState.decompileWorkspace!!.packageName,
                    onExportApk = { viewModel.buildAndExportSignedApk() },
                )
                Row(modifier = Modifier.weight(1f)) {
                    ProjectExplorer(
                        rootFolder = uiState.fileTreeRoot,
                        onFileClick = { node -> viewModel.handleFileNodeClick(node) },
                        onExitProject = { viewModel.clearDecompileWorkspace() },
                        modifier = Modifier.width(300.dp).fillMaxHeight()
                    )
                    CodeWorkspace(
                        tabs = uiState.openTabs,
                        activeTabId = uiState.activeTabId,
                        onSelectTab = { id -> viewModel.setActiveEditorTab(id) },
                        onCloseTab = { id -> viewModel.closeEditorTab(id) },
                        onContentChange = { id, content -> viewModel.updateEditorContent(id, content) },
                        onSave = { viewModel.saveActiveEditorTab() },
                        isOverlayActive = uiState.showDexDialogForFile != null ||
                            uiState.showDexMultiSelectDialog ||
                            uiState.decompileProgress != null,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            if (uiState.decompileProgress != null) {
                DecompileImportProgressOverlay(
                    progress = uiState.decompileProgress,
                    taskName = uiState.currentTaskName,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (uiState.showDexDialogForFile != null) {
                DexActionDialog(
                    dexFile = uiState.showDexDialogForFile,
                    onDismiss = { viewModel.dismissDexActionDialog() },
                    onAction = { action -> viewModel.executeDexAction(uiState.showDexDialogForFile, action) }
                )
            }

            if (uiState.showDexMultiSelectDialog) {
                DexMultiSelectDialog(
                    candidates = uiState.dexMultiSelectCandidates,
                    defaultSelectedPath = uiState.dexMultiSelectDefaultPath,
                    onDismiss = { viewModel.dismissDexMultiSelectDialog() },
                    onConfirm = { selected -> viewModel.confirmDexEditorPlusSelection(selected) },
                )
            }
        }
    }
}
