package `fun`.abbas.wps_adb.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane
import androidx.compose.ui.graphics.Color as ComposeColor
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Composable
actual fun CodeEditorBridge(
    content: String,
    onContentChange: (String) -> Unit,
    syntax: String,
    modifier: Modifier,
    readOnly: Boolean,
) {
    val onContentChangeState = rememberUpdatedState(onContentChange)
    val suppressDocumentEvents = remember { AtomicBoolean(false) }
    val textAreaRef = remember { AtomicReference<RSyntaxTextArea?>(null) }

    remember { installSmaliSyntaxHighlighting() }

    DisposableEffect(readOnly) {
        if (!readOnly) {
            DecompileEditorFlush.register {
                textAreaRef.get()?.let { area ->
                    onContentChangeState.value(area.text)
                }
            }
        }
        onDispose {
            if (!readOnly) {
                DecompileEditorFlush.unregister()
            }
        }
    }

    SwingPanel(
        background = ComposeColor(0xFF111316),
        modifier = modifier,
        factory = {
            val panel = JPanel(BorderLayout())
            val textArea = RSyntaxTextArea(25, 80).apply {
                text = content
                syntaxEditingStyle = when (syntax.lowercase()) {
                    "xml" -> SyntaxConstants.SYNTAX_STYLE_XML
                    "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA
                    "smali" -> SYNTAX_STYLE_SMALI
                    "json" -> SyntaxConstants.SYNTAX_STYLE_JSON
                    "properties" -> SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE
                    "yaml" -> SyntaxConstants.SYNTAX_STYLE_YAML
                    "html" -> SyntaxConstants.SYNTAX_STYLE_HTML
                    "css" -> SyntaxConstants.SYNTAX_STYLE_CSS
                    "markdown" -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN
                    else -> SyntaxConstants.SYNTAX_STYLE_NONE
                }
                isEditable = !readOnly
                isCodeFoldingEnabled = true
                antiAliasingEnabled = true
                background = Color(12, 14, 17)
                caretColor = Color(96, 249, 158)
                foreground = Color(226, 226, 230)
                currentLineHighlightColor = Color(28, 30, 34)
                marginLineColor = Color(60, 74, 63)
                if (syntax.equals("smali", ignoreCase = true)) {
                    applySmaliEditorTheme(this)
                }
            }
            textAreaRef.set(textArea)

            if (!readOnly) {
                textArea.document.addDocumentListener(object : DocumentListener {
                    private fun notifyChange() {
                        if (suppressDocumentEvents.get()) return
                        onContentChangeState.value(textArea.text)
                    }

                    override fun insertUpdate(e: DocumentEvent?) = notifyChange()
                    override fun removeUpdate(e: DocumentEvent?) = notifyChange()
                    override fun changedUpdate(e: DocumentEvent?) = notifyChange()
                })
            }

            val scrollPane = RTextScrollPane(textArea).apply {
                lineNumbersEnabled = true
                isIconRowHeaderEnabled = false
            }
            panel.add(scrollPane, BorderLayout.CENTER)
            panel
        },
        update = { panel ->
            val scrollPane = panel.getComponent(0) as RTextScrollPane
            val textArea = scrollPane.viewport.view as RSyntaxTextArea
            textArea.isEditable = !readOnly
            if (textArea.text != content) {
                suppressDocumentEvents.set(true)
                try {
                    textArea.text = content
                } finally {
                    suppressDocumentEvents.set(false)
                }
            }
        }
    )
}
