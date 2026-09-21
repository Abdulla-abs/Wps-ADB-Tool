package `fun`.abbas.wps_adb.data.scene

import `fun`.abbas.wps_adb.model.scene.CURRENT_SCENE_SCHEMA_VERSION

open class SceneStorageException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class SceneNotFoundException(
    val sceneId: String,
) : SceneStorageException("Scene '$sceneId' was not found")

class SceneValidationException(
    message: String,
) : SceneStorageException(message)

class UnsupportedSchemaVersionException(
    val actualVersion: Int,
    val supportedVersion: Int = CURRENT_SCENE_SCHEMA_VERSION,
) : SceneStorageException("Unsupported schemaVersion $actualVersion (expected $supportedVersion)")

class InvalidGlbException(
    message: String,
) : SceneStorageException(message)

class SceneParseException(
    message: String,
    cause: Throwable? = null,
) : SceneStorageException(message, cause)

class SceneResourceNotFoundException(
    val sceneId: String,
    val resourcePath: String,
) : SceneStorageException("Resource '$resourcePath' referenced in scene '$sceneId' manifest does not exist")
