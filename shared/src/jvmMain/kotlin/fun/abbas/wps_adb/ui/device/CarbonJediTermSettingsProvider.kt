package `fun`.abbas.wps_adb.ui.device

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import java.awt.Font

/**
 * JediTerm theme aligned with the app Carbon palette:
 * dark background (#161616) and green default text (#60F99E).
 */
class CarbonJediTermSettingsProvider : DefaultSettingsProvider() {
    @Suppress("DEPRECATION")
    override fun getDefaultStyle(): TextStyle =
        TextStyle(
            TerminalColor.rgb(FOREGROUND_R, FOREGROUND_G, FOREGROUND_B),
            TerminalColor.rgb(BACKGROUND_R, BACKGROUND_G, BACKGROUND_B),
        )

    override fun getTerminalFont(): Font =
        Font(Font.MONOSPACED, Font.PLAIN, terminalFontSize.toInt())

    override fun getTerminalFontSize(): Float = 13f

    override fun getSelectionColor(): TextStyle =
        TextStyle(
            TerminalColor.rgb(ON_PRIMARY_R, ON_PRIMARY_G, ON_PRIMARY_B),
            TerminalColor.rgb(FOREGROUND_R, FOREGROUND_G, FOREGROUND_B),
        )

    private companion object {
        // CarbonColors.Primary — default shell text
        private const val FOREGROUND_R = 0x60
        private const val FOREGROUND_G = 0xF9
        private const val FOREGROUND_B = 0x9E

        // Design spec terminal background
        private const val BACKGROUND_R = 0x16
        private const val BACKGROUND_G = 0x16
        private const val BACKGROUND_B = 0x16

        // CarbonColors.OnPrimary — selection text
        private const val ON_PRIMARY_R = 0x00
        private const val ON_PRIMARY_G = 0x39
        private const val ON_PRIMARY_B = 0x1C
    }
}
