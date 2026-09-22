package `fun`.abbas.wps_adb.data.scene.runtime

import `fun`.abbas.wps_adb.data.scene.DefaultSceneBindingResolver
import `fun`.abbas.wps_adb.data.scene.SceneBindingResolver
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.ResolvedSceneState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DefaultSceneRuntimeController(
    devicesFlow: StateFlow<List<Device>>,
    private val resolver: SceneBindingResolver = DefaultSceneBindingResolver(),
    scope: CoroutineScope,
    started: SharingStarted = SharingStarted.Eagerly,
) : SceneRuntimeController {

    private val _activeScene = MutableStateFlow<DeviceScene?>(null)
    override val activeScene: StateFlow<DeviceScene?> = _activeScene.asStateFlow()

    override val resolvedState: StateFlow<ResolvedSceneState?> = combine(
        _activeScene,
        devicesFlow,
    ) { scene, devices ->
        if (scene == null) {
            null
        } else {
            try {
                resolver.resolve(scene, devices)
            } catch (_: Throwable) {
                // Error isolation: Prevent unexpected resolver errors from crashing the flow
                ResolvedSceneState(scene = scene, bindings = emptyList())
            }
        }
    }.stateIn(
        scope = scope,
        started = started,
        initialValue = _activeScene.value?.let { scene ->
            try {
                resolver.resolve(scene, devicesFlow.value)
            } catch (_: Throwable) {
                ResolvedSceneState(scene = scene, bindings = emptyList())
            }
        },
    )

    private val _selectedObjectId = MutableStateFlow<String?>(null)
    override val selectedObjectId: StateFlow<String?> = _selectedObjectId.asStateFlow()

    override fun setScene(scene: DeviceScene?) {
        _activeScene.value = scene
        _selectedObjectId.value = null
    }

    override fun selectObject(objectId: String?) {
        _selectedObjectId.value = objectId
    }
}

