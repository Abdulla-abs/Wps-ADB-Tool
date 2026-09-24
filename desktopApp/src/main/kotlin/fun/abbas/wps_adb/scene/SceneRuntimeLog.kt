package `fun`.abbas.wps_adb.scene

import java.util.logging.Level
import java.util.logging.Logger

internal object SceneRuntimeLog {
    private val logger = Logger.getLogger("WpsAdbTool.SceneRuntime")

    fun transition(from: SceneRuntimePhase, to: SceneRuntimePhase, attempt: Long) {
        logger.log(
            Level.INFO,
            "event=phase-transition component=scene-runtime from={0} to={1} attempt={2}",
            arrayOf<Any>(from, to, attempt),
        )
    }

    fun failure(failure: SceneRuntimeFailure, cause: Throwable) {
        // Do not log exception messages here: they may contain user file paths or URLs.
        logger.log(
            Level.SEVERE,
            "event=failure component=scene-runtime stage={0} category={1} diagnosticId={2} exception={3}",
            arrayOf(failure.stage, failure.category, failure.diagnosticId, cause.javaClass.name),
        )
    }

    fun cleanupFailure(resource: String, cause: Throwable) {
        logger.log(
            Level.WARNING,
            "event=cleanup-failure component=scene-runtime resource={0} exception={1}",
            arrayOf(resource, cause.javaClass.name),
        )
    }
}
