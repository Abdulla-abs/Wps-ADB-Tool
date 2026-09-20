package `fun`.abbas.wps_adb

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import `fun`.abbas.wps_adb.ui.editor.installSmaliSyntaxHighlighting

import `fun`.abbas.wps_adb.spike.renderer.RendererSpikeView

fun main(args: Array<String>) {
    installSmaliSyntaxHighlighting()
    val isSpikeRequested = args.contains("--spike-3d") || System.getProperty("wpsadb.spike.3d") == "true"

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = if (isSpikeRequested) "WpsAdbTool — 3D Renderer Spike" else "WpsAdbTool",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            if (isSpikeRequested) {
                RendererSpikeView(onClose = ::exitApplication)
            } else {
                App()
            }
        }
    }
}
