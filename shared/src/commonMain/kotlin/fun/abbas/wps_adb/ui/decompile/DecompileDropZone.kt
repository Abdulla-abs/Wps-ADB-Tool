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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.decompile_drop_subtitle
import wpsadbtool.shared.generated.resources.decompile_drop_title
import wpsadbtool.shared.generated.resources.decompile_manage_projects
import wpsadbtool.shared.generated.resources.decompile_recent_project
import `fun`.abbas.wps_adb.model.RecentDecompileProject
import `fun`.abbas.wps_adb.platform.apkDropTarget
import `fun`.abbas.wps_adb.platform.pickApkFile
import `fun`.abbas.wps_adb.theme.CarbonColors
import kotlinx.coroutines.launch

@Composable
fun DecompileDropZone(
    onApkImport: (String) -> Unit,
    onRecentProjectClick: (RecentDecompileProject) -> Unit,
    onManageProjects: () -> Unit,
    recentProjects: List<RecentDecompileProject>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isDragOver by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .background(CarbonColors.SurfaceContainerLowest)
            .border(width = 1.dp, color = CarbonColors.OutlineVariant.copy(alpha = 0.5f))
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val dropZoneModifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .apkDropTarget(
                    enabled = true,
                    onDragEnter = { isDragOver = true },
                    onDragExit = { isDragOver = false },
                    onApkDropped = { path ->
                        isDragOver = false
                        onApkImport(path)
                    },
                )
                .clickable {
                    scope.launch {
                        val path = pickApkFile() ?: return@launch
                        onApkImport(path)
                    }
                }
                .drawDashedDropZoneBorder(isDragOver)
                .background(
                    if (isDragOver) CarbonColors.Primary.copy(alpha = 0.1f)
                    else CarbonColors.Primary.copy(alpha = 0.05f),
                    RoundedCornerShape(12.dp),
                )
                .padding(24.dp)

            Column(
                modifier = dropZoneModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(CarbonColors.SurfaceContainerHigh, RoundedCornerShape(12.dp))
                    .border(
                        width = if (isDragOver) 2.dp else 1.dp,
                        color = if (isDragOver) CarbonColors.Primary else CarbonColors.OutlineVariant,
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "↑", color = CarbonColors.Primary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.decompile_drop_title),
                color = CarbonColors.OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.decompile_drop_subtitle),
                color = CarbonColors.OnSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.85f),
            )

            if (recentProjects.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recentProjects.take(3).forEach { project ->
                        RecentProjectCard(
                            project = project,
                            onClick = { onRecentProjectClick(project) },
                        )
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(Res.string.decompile_manage_projects),
                color = CarbonColors.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CarbonColors.SurfaceContainer)
                    .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(6.dp))
                    .clickable(onClick = onManageProjects)
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RecentProjectCard(
    project: RecentDecompileProject,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(CarbonColors.SurfaceContainer)
            .border(1.dp, CarbonColors.OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "⚙", color = CarbonColors.OnSurfaceVariant, fontSize = 16.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.decompile_recent_project),
                color = CarbonColors.OnSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
            Text(
                text = project.packageName,
                color = CarbonColors.Primary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = project.apkFileName,
                color = CarbonColors.Outline,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun DecompileImportProgressOverlay(
    progress: Float,
    taskName: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .background(CarbonColors.SurfaceContainer, RoundedCornerShape(12.dp))
                .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(12.dp))
                .padding(32.dp),
        ) {
            CircularProgressIndicator(
                progress = { progress },
                color = CarbonColors.Primary,
                trackColor = CarbonColors.SurfaceContainerHighest,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = taskName,
                color = CarbonColors.OnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                color = CarbonColors.Primary,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            )
        }
    }
}

private fun Modifier.drawDashedDropZoneBorder(isDragOver: Boolean): Modifier = drawBehind {
    val strokeWidth = if (isDragOver) 3.dp.toPx() else 2.dp.toPx()
    val color = if (isDragOver) CarbonColors.Primary else Color(0xFF43E188)
    val pathEffect = if (isDragOver) null else PathEffect.dashPathEffect(floatArrayOf(8f, 12f), 0f)
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(12.dp.toPx()),
        style = Stroke(width = strokeWidth, pathEffect = pathEffect),
    )
}
