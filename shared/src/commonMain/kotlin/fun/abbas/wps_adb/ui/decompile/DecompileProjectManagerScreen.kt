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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.RecentDecompileProject
import `fun`.abbas.wps_adb.theme.CarbonColors
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.decompile_project_delete
import wpsadbtool.shared.generated.resources.decompile_project_manager_back
import wpsadbtool.shared.generated.resources.decompile_project_manager_empty
import wpsadbtool.shared.generated.resources.decompile_project_manager_title
import wpsadbtool.shared.generated.resources.decompile_project_open

@Composable
fun DecompileProjectManagerScreen(
    projects: List<RecentDecompileProject>,
    onBack: () -> Unit,
    onOpenProject: (RecentDecompileProject) -> Unit,
    onDeleteProject: (RecentDecompileProject) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonColors.Background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(CarbonColors.SurfaceContainerLow)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.decompile_project_manager_back),
                color = CarbonColors.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.decompile_project_manager_title),
                color = CarbonColors.OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        if (projects.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.decompile_project_manager_empty),
                    color = CarbonColors.Outline,
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(projects, key = { it.workspacePath }) { project ->
                    DecompileProjectManagerCard(
                        project = project,
                        onOpen = { onOpenProject(project) },
                        onDelete = { onDeleteProject(project) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DecompileProjectManagerCard(
    project: RecentDecompileProject,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CarbonColors.SurfaceContainerLow, RoundedCornerShape(8.dp))
            .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = project.packageName,
                color = CarbonColors.Primary,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = project.apkFileName,
                color = CarbonColors.OnSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProjectManagerActionButton(
                label = stringResource(Res.string.decompile_project_delete),
                color = CarbonColors.Error,
                onClick = onDelete,
            )
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            ProjectManagerActionButton(
                label = stringResource(Res.string.decompile_project_open),
                color = CarbonColors.Primary,
                onClick = onOpen,
            )
        }
    }
}

@Composable
private fun ProjectManagerActionButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(CarbonColors.SurfaceContainerHigh, RoundedCornerShape(4.dp))
            .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
