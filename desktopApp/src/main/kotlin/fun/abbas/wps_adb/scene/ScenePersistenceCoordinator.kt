package `fun`.abbas.wps_adb.scene

import `fun`.abbas.wps_adb.data.scene.DeviceSceneRepository
import `fun`.abbas.wps_adb.model.scene.SceneCamera
import `fun`.abbas.wps_adb.model.scene.SceneTransform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates debounced disk writes for Camera and Transform changes,
 * providing per-scene job cancellation, flush-before-switch, and graceful shutdown.
 */
class ScenePersistenceCoordinator(
    private val repository: DeviceSceneRepository,
    parentScope: CoroutineScope,
    private val debounceMillis: Long = 500L,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val coordinatorJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + coordinatorJob)
    private val lock = Any()

    private val pendingCameraSaves = mutableMapOf<String, SceneCamera>()
    private val cameraJobs = mutableMapOf<String, Job>()

    private val pendingTransformSaves = mutableMapOf<Pair<String, String>, SceneTransform>()
    private val transformJobs = mutableMapOf<Pair<String, String>, Job>()

    fun scheduleCameraSave(sceneId: String, camera: SceneCamera) {
        synchronized(lock) {
            pendingCameraSaves[sceneId] = camera
            cameraJobs[sceneId]?.cancel()
            cameraJobs[sceneId] = scope.launch(ioDispatcher) {
                delay(debounceMillis)
                val camToSave = synchronized(lock) {
                    cameraJobs.remove(sceneId)
                    pendingCameraSaves.remove(sceneId)
                }
                if (camToSave != null) {
                    try {
                        repository.saveCamera(sceneId, camToSave)
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            }
        }
    }

    fun scheduleTransformSave(sceneId: String, objectId: String, transform: SceneTransform) {
        val key = sceneId to objectId
        synchronized(lock) {
            pendingTransformSaves[key] = transform
            transformJobs[key]?.cancel()
            transformJobs[key] = scope.launch(ioDispatcher) {
                delay(debounceMillis)
                val transformToSave = synchronized(lock) {
                    transformJobs.remove(key)
                    pendingTransformSaves.remove(key)
                }
                if (transformToSave != null) {
                    try {
                        repository.updateTransform(sceneId, objectId, transformToSave)
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            }
        }
    }

    suspend fun flushScene(sceneId: String) {
        val (camToSave, transformsToSave) = synchronized(lock) {
            cameraJobs.remove(sceneId)?.cancel()
            val cam = pendingCameraSaves.remove(sceneId)

            val matchingKeys = transformJobs.keys.filter { it.first == sceneId }
            val transforms = mutableListOf<Pair<String, SceneTransform>>()
            for (k in matchingKeys) {
                transformJobs.remove(k)?.cancel()
                pendingTransformSaves.remove(k)?.let { transforms.add(k.second to it) }
            }
            cam to transforms
        }

        withContext(ioDispatcher) {
            if (camToSave != null) {
                try {
                    repository.saveCamera(sceneId, camToSave)
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
            for ((objId, transform) in transformsToSave) {
                try {
                    repository.updateTransform(sceneId, objId, transform)
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }
    }

    suspend fun flush() {
        val (cameras, transforms) = synchronized(lock) {
            for (j in cameraJobs.values) j.cancel()
            cameraJobs.clear()
            val cams = pendingCameraSaves.toMap()
            pendingCameraSaves.clear()

            for (j in transformJobs.values) j.cancel()
            transformJobs.clear()
            val trans = pendingTransformSaves.toMap()
            pendingTransformSaves.clear()

            cams to trans
        }

        withContext(ioDispatcher) {
            for ((sId, cam) in cameras) {
                try {
                    repository.saveCamera(sId, cam)
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
            for ((key, transform) in transforms) {
                try {
                    repository.updateTransform(key.first, key.second, transform)
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }
    }

    fun cancelScene(sceneId: String) {
        synchronized(lock) {
            cameraJobs.remove(sceneId)?.cancel()
            pendingCameraSaves.remove(sceneId)

            val matchingKeys = transformJobs.keys.filter { it.first == sceneId }
            for (k in matchingKeys) {
                transformJobs.remove(k)?.cancel()
                pendingTransformSaves.remove(k)
            }
        }
    }

    suspend fun close() {
        flush()
        coordinatorJob.cancel()
    }
}
