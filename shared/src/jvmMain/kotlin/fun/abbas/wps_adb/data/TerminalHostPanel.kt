package `fun`.abbas.wps_adb.data

import com.jediterm.terminal.ui.JediTermWidget
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.FocusTraversalPolicy
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

internal class TerminalHostPanel(
    val widget: JediTermWidget,
) : JPanel(BorderLayout()) {
    init {
        isFocusable = false
        isFocusCycleRoot = true
        focusTraversalPolicy = SingleComponentFocusPolicy(widget.terminalPanel)

        widget.isFocusable = false
        widget.terminalPanel.isFocusable = true
        widget.terminalPanel.enableInputMethods(false)

        add(widget, BorderLayout.CENTER)

        widget.terminalPanel.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) {
                    requestTerminalFocus()
                }
            },
        )
    }

    fun requestTerminalFocus(): Boolean = widget.terminalPanel.requestFocusInWindow()

    override fun requestFocusInWindow(): Boolean = requestTerminalFocus()
}

private class SingleComponentFocusPolicy(
    private val focusTarget: Component,
) : FocusTraversalPolicy() {
    override fun getComponentAfter(focusCycleRoot: Container?, aComponent: Component?): Component = focusTarget

    override fun getComponentBefore(focusCycleRoot: Container?, aComponent: Component?): Component = focusTarget

    override fun getFirstComponent(focusCycleRoot: Container?): Component = focusTarget

    override fun getLastComponent(focusCycleRoot: Container?): Component = focusTarget

    override fun getDefaultComponent(focusCycleRoot: Container?): Component = focusTarget
}
