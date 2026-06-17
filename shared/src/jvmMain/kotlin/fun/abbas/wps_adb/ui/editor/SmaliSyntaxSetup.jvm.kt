package `fun`.abbas.wps_adb.ui.editor

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory

/** RSyntaxTextArea syntax style key for Smali (matches JADX). */
const val SYNTAX_STYLE_SMALI = "text/smali"

private var installed = false

/** Registers [SmaliTokenMaker] with RSyntaxTextArea. Safe to call multiple times. */
fun installSmaliSyntaxHighlighting() {
    if (installed) return
    val factory = TokenMakerFactory.getDefaultInstance()
    check(factory is AbstractTokenMakerFactory) {
        "Unexpected TokenMakerFactory: ${factory.javaClass.name}"
    }
    factory.putMapping(SYNTAX_STYLE_SMALI, "fun.abbas.wps_adb.ui.editor.SmaliTokenMaker")
    installed = true
}
