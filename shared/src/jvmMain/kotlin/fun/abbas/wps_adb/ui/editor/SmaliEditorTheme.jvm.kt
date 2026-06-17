package `fun`.abbas.wps_adb.ui.editor

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxScheme
import org.fife.ui.rsyntaxtextarea.Token
import java.awt.Color

/** Applies Carbon-inspired token colors for Smali editing. */
fun applySmaliEditorTheme(textArea: RSyntaxTextArea) {
    val scheme = (textArea.syntaxScheme?.clone() as? SyntaxScheme) ?: SyntaxScheme(true)

    fun style(type: Int, rgb: Int) {
        scheme.getStyle(type).foreground = Color(rgb)
    }

    style(Token.COMMENT_EOL, 0xFF869587.toInt())
    style(Token.LITERAL_STRING_DOUBLE_QUOTE, 0xFF60F99E.toInt())
    style(Token.LITERAL_CHAR, 0xFF60F99E.toInt())
    style(Token.LITERAL_BOOLEAN, 0xFFFFADA8.toInt())
    style(Token.LITERAL_NUMBER_DECIMAL_INT, 0xFFFFD4D1.toInt())
    style(Token.LITERAL_NUMBER_HEXADECIMAL, 0xFFFFD4D1.toInt())
    style(Token.LITERAL_NUMBER_FLOAT, 0xFFFFD4D1.toInt())
    style(Token.RESERVED_WORD, 0xFFADC6FF.toInt())
    style(Token.RESERVED_WORD_2, 0xFF4B8EFF.toInt())
    style(Token.DATA_TYPE, 0xFF3DDC84.toInt())
    style(Token.FUNCTION, 0xFFE2E2E6.toInt())
    style(Token.MARKUP_TAG_NAME, 0xFFADC6FF.toInt())
    style(Token.OPERATOR, 0xFFBBCBBC.toInt())
    style(Token.SEPARATOR, 0xFF869587.toInt())
    style(Token.VARIABLE, 0xFFE2E2E6.toInt())
    style(Token.IDENTIFIER, 0xFFE2E2E6.toInt())
    style(Token.ERROR_CHAR, 0xFFFFB4AB.toInt())
    style(Token.ERROR_STRING_DOUBLE, 0xFFFFB4AB.toInt())
    style(Token.ERROR_NUMBER_FORMAT, 0xFFFFB4AB.toInt())

    textArea.syntaxScheme = scheme
}
