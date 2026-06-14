package `fun`.abbas.wps_adb.ui.editor

import androidx.compose.runtime.Composable
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

@Composable
actual fun CodeEditorBridge(
    content: String,
    onContentChange: (String) -> Unit,
    syntax: String,
    modifier: Modifier
) {
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
                    "smali" -> SyntaxConstants.SYNTAX_STYLE_NONE // 可以之后编写Smali高亮描述规则文件，此处使用纯文本
                    else -> SyntaxConstants.SYNTAX_STYLE_NONE
                }
                isCodeFoldingEnabled = true
                antiAliasingEnabled = true
                background = Color(12, 14, 17)
                caretColor = Color(96, 249, 158)
                foreground = Color(226, 226, 230)
                currentLineHighlightColor = Color(28, 30, 34)
                marginLineColor = Color(60, 74, 63)
            }

            textArea.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) {
                    onContentChange(textArea.text)
                }
                override fun removeUpdate(e: DocumentEvent?) {
                    onContentChange(textArea.text)
                }
                override fun changedUpdate(e: DocumentEvent?) {
                    onContentChange(textArea.text)
                }
            })

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
            // 避免无限循环输入更新，仅当内容确实不同且未聚焦时重设
            if (textArea.text != content && !textArea.hasFocus()) {
                textArea.text = content
            }
        }
    )
}
