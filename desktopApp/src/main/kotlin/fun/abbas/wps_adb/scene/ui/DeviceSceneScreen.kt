package `fun`.abbas.wps_adb.scene.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.abbas.wps_adb.scene.SceneOption
import `fun`.abbas.wps_adb.scene.SceneRuntimeHost
import `fun`.abbas.wps_adb.scene.SceneView
import `fun`.abbas.wps_adb.theme.CarbonColors
import `fun`.abbas.wps_adb.viewmodel.AppViewModel

/**
 * Main 3D Device Scene Screen providing a split-pane layout:
 * - Top: Scene Switcher Toolbar ([SceneToolbar])
 * - Left: 3D Scene Viewport ([SceneView])
 * - Right: Scene and Device Inspector ([DeviceSceneInspector])
 */
@Composable
fun DeviceSceneScreen(
    runtimeHost: SceneRuntimeHost,
    viewModel: AppViewModel,
    scenes: List<SceneOption>? = null,
    actions: SceneDeviceActions? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val resolvedActions = actions ?: remember(viewModel, scope) {
        DefaultSceneDeviceActions(viewModel, scope)
    }

    val hostState by runtimeHost.state.collectAsState()
    val hostScenes by runtimeHost.availableScenes.collectAsState()
    val resolvedScenes = scenes ?: hostScenes

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonColors.Background),
    ) {
        if (resolvedScenes.isNotEmpty()) {
            SceneToolbar(
                scenes = resolvedScenes,
                selectedSceneId = hostState.activeSceneId,
                onSceneSelected = runtimeHost::selectScene,
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // Left: 3D WebGL / JCEF Canvas
            SceneView(
                host = runtimeHost,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )

            // Divider between Canvas and Inspector
            VerticalDivider(
                thickness = 1.dp,
                color = CarbonColors.OutlineVariant.copy(alpha = 0.4f),
            )

            // Right: Inspector Panel
            DeviceSceneInspector(
                runtimeHost = runtimeHost,
                viewModel = viewModel,
                actions = resolvedActions,
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight(),
            )
        }
    }
}
