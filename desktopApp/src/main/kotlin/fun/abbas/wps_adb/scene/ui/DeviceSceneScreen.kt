package `fun`.abbas.wps_adb.scene.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.abbas.wps_adb.scene.SceneRuntimeHost
import `fun`.abbas.wps_adb.scene.SceneView
import `fun`.abbas.wps_adb.theme.CarbonColors
import `fun`.abbas.wps_adb.viewmodel.AppViewModel

/**
 * Main 3D Device Scene Screen providing a split-pane layout:
 * - Top: Scene Switcher Toolbar ([SceneToolbar])
 * - Left: 3D Scene Viewport ([SceneView])
 * - Right: Scene and Device Inspector ([DeviceSceneInspector])
 *
 * Inspector navigation state ([InspectorPage]) is hoisted here to ensure
 * all scene import, management, asset import, and device binding controls
 * are embedded cleanly in the right-side inspector without Compose popups
 * occluded by the JCEF airspace.
 */
@Composable
fun DeviceSceneScreen(
    runtimeHost: SceneRuntimeHost,
    viewModel: AppViewModel,
    actions: SceneDeviceActions? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val resolvedActions = actions ?: remember(viewModel, scope) {
        DefaultSceneDeviceActions(viewModel, scope)
    }

    var inspectorPage by remember { mutableStateOf<InspectorPage>(InspectorPage.Overview) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonColors.Background),
    ) {
        SceneToolbar(
            runtimeHost = runtimeHost,
            onNavigate = { inspectorPage = it },
        )

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
                currentPage = inspectorPage,
                onNavigate = { inspectorPage = it },
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight(),
            )
        }
    }
}
