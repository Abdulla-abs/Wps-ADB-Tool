package `fun`.abbas.wps_adb.ui.decompile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.abbas.wps_adb.model.RecentDecompileProject
import `fun`.abbas.wps_adb.theme.CarbonColors

@Composable
fun DecompileEmptyStateScreen(
    onApkImport: (String) -> Unit,
    onRecentProjectClick: (RecentDecompileProject) -> Unit,
    onManageProjects: () -> Unit,
    onCloseProjectManager: () -> Unit,
    onOpenProject: (RecentDecompileProject) -> Unit,
    onDeleteProject: (RecentDecompileProject) -> Unit,
    recentProjects: List<RecentDecompileProject>,
    showProjectManager: Boolean,
    progress: Float?,
    taskName: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(CarbonColors.Background)) {
        if (showProjectManager) {
            DecompileProjectManagerScreen(
                projects = recentProjects,
                onBack = onCloseProjectManager,
                onOpenProject = onOpenProject,
                onDeleteProject = onDeleteProject,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                DecompileDropZone(
                    onApkImport = onApkImport,
                    onRecentProjectClick = onRecentProjectClick,
                    onManageProjects = onManageProjects,
                    recentProjects = recentProjects,
                    modifier = Modifier.width(320.dp).fillMaxHeight(),
                )
                DecompileEmptyEditorPane(modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }

        if (progress != null) {
            DecompileImportProgressOverlay(
                progress = progress,
                taskName = taskName,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
