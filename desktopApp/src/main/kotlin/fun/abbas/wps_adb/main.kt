package `fun`.abbas.wps_adb

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import `fun`.abbas.wps_adb.data.createAdbRepository
import `fun`.abbas.wps_adb.data.createDeviceShellService
import `fun`.abbas.wps_adb.data.createScrcpyMirrorService
import `fun`.abbas.wps_adb.data.scene.SceneStore
import `fun`.abbas.wps_adb.scene.SceneRuntimeHost
import `fun`.abbas.wps_adb.scene.SceneView
import `fun`.abbas.wps_adb.spike.renderer.RendererSpikeView
import `fun`.abbas.wps_adb.ui.editor.installSmaliSyntaxHighlighting
import `fun`.abbas.wps_adb.viewmodel.AppViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Application-level container holding the [SceneRuntimeHost] across Composable lifecycles.
 * Prevents Composable Recomposition from creating or destroying the JCEF browser.
 */
object SceneRuntimeContainer {
    private var appScope: CoroutineScope? = null
    private var instance: SceneRuntimeHost? = null
    var hostFactory: ((CoroutineScope) -> SceneRuntimeHost)? = null

    @Synchronized
    fun getOrCreate(
        runtimeController: `fun`.abbas.wps_adb.data.scene.runtime.SceneRuntimeController? = null,
        repository: `fun`.abbas.wps_adb.data.scene.DeviceSceneRepository? = null,
    ): SceneRuntimeHost {
        val existing = instance
        if (existing != null) return existing
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        appScope = scope
        val host = hostFactory?.invoke(scope) ?: SceneRuntimeHost(
            parentScope = scope,
            sceneRuntimeController = runtimeController,
            sceneRepository = repository,
        )
        instance = host
        return host
    }

    @Synchronized
    fun dispose() {
        instance?.dispose()
        instance = null
        appScope?.cancel()
        appScope = null
    }

    @Synchronized
    fun resetForTest() {
        dispose()
        hostFactory = null
    }
}

/**
 * Convenient helper for Composable scopes to access the Application-level runtime host.
 */
@Composable
fun rememberSceneRuntime(
    runtimeController: `fun`.abbas.wps_adb.data.scene.runtime.SceneRuntimeController? = null,
    repository: `fun`.abbas.wps_adb.data.scene.DeviceSceneRepository? = null,
): SceneRuntimeHost {
    return SceneRuntimeContainer.getOrCreate(runtimeController, repository)
}

fun main(args: Array<String>) {
    installSmaliSyntaxHighlighting()
    val isSpikeRequested = args.contains("--spike-3d") || System.getProperty("wpsadb.spike.3d") == "true"
    val isSceneRequested = args.contains("--scene-3d") || args.contains("--scene") ||
            System.getProperty("wpsadb.scene.3d") == "true" ||
            System.getProperty("wpsadb.scene") == "true"

    application {
        // Only initialize SceneRuntimeHost when Scene view is requested
        val sceneRuntimeHost = if (isSceneRequested) {
            val repository = createAdbRepository()
            val appViewModel = AppViewModel(
                repository = repository,
                scrcpyMirrorService = createScrcpyMirrorService(
                    scrcpyPathProvider = { repository.settings.value.scrcpyPath },
                    adbPathProvider = { repository.settings.value.adbPath },
                ),
                deviceShellService = createDeviceShellService(
                    adbPathProvider = { repository.settings.value.adbPath },
                ),
            )
            val sceneStore = SceneStore()
            rememberSceneRuntime(
                runtimeController = appViewModel.sceneRuntimeController,
                repository = sceneStore,
            )
        } else null

        Window(
            onCloseRequest = {
                SceneRuntimeContainer.dispose()
                exitApplication()
            },
            title = if (isSceneRequested) "WpsAdbTool — 3D Scene" else if (isSpikeRequested) "WpsAdbTool — 3D Renderer Spike" else "WpsAdbTool",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            if (isSceneRequested && sceneRuntimeHost != null) {
                SceneView(host = sceneRuntimeHost)
            } else if (isSpikeRequested) {
                RendererSpikeView(onClose = ::exitApplication)
            } else {
                App()
            }
        }
    }
}
