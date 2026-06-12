package `fun`.abbas.wps_adb.data

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.ui.JediTermWidget
import javax.swing.SwingUtilities

internal object JediTermSizeSync {
    fun scheduleSync(widget: JediTermWidget) {
        SwingUtilities.invokeLater { syncNow(widget) }
    }

    fun syncNow(widget: JediTermWidget) {
        if (!widget.isSessionRunning) return

        val panel = widget.terminalPanel
        val rawSize = panel.getTerminalSizeFromComponent() ?: return
        val termSize = JediTerminal.ensureTermMinimumSize(rawSize)
        widget.terminalStarter?.postResize(termSize, RequestOrigin.User)
    }
}
