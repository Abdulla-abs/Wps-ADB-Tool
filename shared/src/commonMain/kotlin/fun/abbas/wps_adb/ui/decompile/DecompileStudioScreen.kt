package `fun`.abbas.wps_adb.ui.decompile

import androidx.compose.foundation.layout.Box
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
            DecompileDropZone(
                onApkImport = { apkPath -> viewModel.importApkToWorkspace(apkPath) },
                progress = uiState.decompileProgress,
                taskName = uiState.currentTaskName,
                modifier = Modifier.fillMaxSize()
            )
        } else if (uiState.activeDexEditorProject != null) {
            DexEditorPlusScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = { viewModel.closeDexEditorPlus() },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                ProjectExplorer(
                    rootFolder = uiState.fileTreeRoot,
                    onFileClick = { node -> viewModel.handleFileNodeClick(node) },
                    modifier = Modifier.width(300.dp).fillMaxHeight()
                )
                CodeWorkspace(
                    tabs = uiState.openTabs,
                    activeTabId = uiState.activeTabId,
                    onSelectTab = { id -> viewModel.setActiveEditorTab(id) },
                    onCloseTab = { id -> viewModel.closeEditorTab(id) },
                    onContentChange = { id, content -> viewModel.updateEditorContent(id, content) },
                    isOverlayActive = uiState.showDexDialogForFile != null,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            if (uiState.showDexDialogForFile != null) {
                DexActionDialog(
                    dexFile = uiState.showDexDialogForFile,
                    onDismiss = { viewModel.dismissDexActionDialog() },
                    onAction = { action -> viewModel.executeDexAction(uiState.showDexDialogForFile, action) }
                )
            }
        }
    }
}
