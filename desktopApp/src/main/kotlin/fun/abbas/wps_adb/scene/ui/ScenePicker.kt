package `fun`.abbas.wps_adb.scene.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.scene.SceneRuntimeHost
import `fun`.abbas.wps_adb.theme.CarbonColors

@Composable
fun SceneToolbar(
    runtimeHost: SceneRuntimeHost? = null,
    onNavigate: ((InspectorPage) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val hostState = runtimeHost?.state?.collectAsState()?.value

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CarbonColors.Surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "3D Device Scene",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CarbonColors.OnSurface,
                )

                if (runtimeHost != null && hostState != null) {
                    SceneModeToolbar(
                        currentMode = hostState.interactionMode,
                        onModeSelected = runtimeHost::setInteractionMode,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (runtimeHost != null && onNavigate != null) {
                    OutlinedButton(
                        onClick = { onNavigate(InspectorPage.ImportScene) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("+ Import", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { onNavigate(InspectorPage.ManageScenes) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("Manage", fontSize = 12.sp)
                    }
                }

            }
        }
    }
}
