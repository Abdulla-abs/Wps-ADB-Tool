package `fun`.abbas.wps_adb.spike.renderer

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main(args: Array<String>) {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "WpsAdbTool — Phase 0 / Three.js Renderer Host Spike",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            RendererSpikeView(
                onClose = ::exitApplication
            )
        }
    }
}
