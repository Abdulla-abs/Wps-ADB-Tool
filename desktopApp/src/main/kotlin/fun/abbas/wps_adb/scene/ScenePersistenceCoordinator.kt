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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coordinates debounced disk writes for Camera and Transform changes,
 * providing per-scene job cancellation, per-scene disk write serialization,
 * flush-before-switch, and graceful shutdown.
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

    private val sceneMutexes = mutableMapOf<String, Mutex>()

    private fun getSceneMutex(sceneId: String): Mutex = synchronized(lock) {
        sceneMutexes.getOrPut(sceneId) { Mutex() }
    }

    fun scheduleCameraSave(sceneId: String, camera: SceneCamera) {
        synchronized(lock) {
            pendingCameraSaves[sceneId] = camera
            cameraJobs[sceneId]?.cancel()
            cameraJobs[sceneId] = scope.launch(ioDispatcher) {
                delay(debounceMillis)
                getSceneMutex(sceneId).withLock {
                    coroutineContext.ensureActive()
                    val camToSave = synchronized(lock) {
                        if (cameraJobs[sceneId] == coroutineContext[Job]) {
                            cameraJobs.remove(sceneId)
                            pendingCameraSaves.remove(sceneId)
                        } else null
                    }
                    if (camToSave != null) {
                        try {
                            repository.saveCamera(sceneId, camToSave)
                        } catch (t: Throwable) {
                            if (t is kotlinx.coroutines.CancellationException) throw t
                            t.printStackTrace()
                        }
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
                getSceneMutex(sceneId).withLock {
                    coroutineContext.ensureActive()
                    val transformToSave = synchronized(lock) {
                        if (transformJobs[key] == coroutineContext[Job]) {
                            transformJobs.remove(key)
                            pendingTransformSaves.remove(key)
                        } else null
                    }
                    if (transformToSave != null) {
                        try {
                            repository.updateTransform(sceneId, objectId, transformToSave)
                        } catch (t: Throwable) {
                            if (t is kotlinx.coroutines.CancellationException) throw t
                            t.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    suspend fun flushScene(sceneId: String) {
        withContext(ioDispatcher) {
            getSceneMutex(sceneId).withLock {
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

                if (camToSave != null) {
                    repository.saveCamera(sceneId, camToSave)
                }
                for ((objId, transform) in transformsToSave) {
                    repository.updateTransform(sceneId, objId, transform)
                }
            }
        }
    }

    suspend fun discardScene(sceneId: String) {
        withContext(ioDispatcher) {
            getSceneMutex(sceneId).withLock {
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
        }
    }

    suspend fun flush() {
        val sceneIds = synchronized(lock) {
            (pendingCameraSaves.keys + pendingTransformSaves.keys.map { it.first }).distinct()
        }
        var firstException: Throwable? = null
        for (sId in sceneIds) {
            try {
                flushScene(sId)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (firstException == null) firstException = t
            }
        }
        firstException?.let { throw it }
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

    fun cancelTransformSave(sceneId: String, objectId: String) {
        val key = sceneId to objectId
        synchronized(lock) {
            transformJobs.remove(key)?.cancel()
            pendingTransformSaves.remove(key)
        }
    }

    suspend fun close() {
        flush()
        coordinatorJob.cancel()
    }
}
