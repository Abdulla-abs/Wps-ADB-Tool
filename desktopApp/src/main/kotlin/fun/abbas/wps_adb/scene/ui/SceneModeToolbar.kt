package `fun`.abbas.wps_adb.scene.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.scene.SceneInteractionMode
import `fun`.abbas.wps_adb.theme.CarbonColors

/**
 * Toolbar control for switching between VIEW, BINDING, and EDITING interaction modes.
 */
@Composable
fun SceneModeToolbar(
    currentMode: SceneInteractionMode,
    onModeSelected: (SceneInteractionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = listOf(
        Triple(SceneInteractionMode.VIEW, "View", "👁️"),
        Triple(SceneInteractionMode.BINDING, "Binding", "🔗"),
        Triple(SceneInteractionMode.EDITING, "Editing", "📐"),
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CarbonColors.SurfaceContainer)
            .border(1.dp, CarbonColors.OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEach { (mode, label, icon) ->
            val isSelected = currentMode == mode
            val bgColor = if (isSelected) CarbonColors.PrimaryContainer else Color.Transparent
            val contentColor = if (isSelected) CarbonColors.OnPrimaryContainer else CarbonColors.OnSurfaceVariant
            val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = icon,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = fontWeight,
                        color = contentColor,
                    )
                }
            }
        }
    }
}
