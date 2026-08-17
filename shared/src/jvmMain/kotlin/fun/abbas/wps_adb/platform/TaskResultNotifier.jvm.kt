package `fun`.abbas.wps_adb.platform

import java.awt.Color
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

private object JvmTaskResultNotifier {
    private val lock = Any()
    private var trayIcon: TrayIcon? = null

    fun show(title: String, message: String, isSuccess: Boolean) {
        if (!SystemTray.isSupported()) return
        runCatching {
            synchronized(lock) {
                val icon = trayIcon ?: createTrayIcon().also { created ->
                    trayIcon = created
                    SystemTray.getSystemTray().add(created)
                }
                val type = if (isSuccess) TrayIcon.MessageType.INFO else TrayIcon.MessageType.ERROR
                icon.displayMessage(title, message, type)
            }
        }
    }

    private fun createTrayIcon(): TrayIcon =
        TrayIcon(createTrayImage(), "WpsAdbTool").apply {
            isImageAutoSize = true
        }

    private fun createTrayImage(): Image {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = Color(0xFF0F62FE.toInt())
        graphics.fillRoundRect(0, 0, 16, 16, 4, 4)
        graphics.dispose()
        return image
    }
}

actual fun notifyTaskResultWhenBackground(title: String, message: String, isSuccess: Boolean) {
    if (AppWindowFocus.isFocused()) return
    JvmTaskResultNotifier.show(title, message, isSuccess)
}
