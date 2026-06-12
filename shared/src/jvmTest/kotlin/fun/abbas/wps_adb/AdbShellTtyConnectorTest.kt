package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.AdbShellTtyConnector
import com.jediterm.core.util.TermSize
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AdbShellTtyConnectorTest {
    @Test
    fun normalizeLineEndings_convertsCarriageReturnToNewline() {
        assertEquals("\n", AdbShellTtyConnector.normalizeLineEndings("\r"))
        assertEquals("uptime\n", AdbShellTtyConnector.normalizeLineEndings("uptime\r"))
        assertEquals("uptime\r\n", AdbShellTtyConnector.normalizeLineEndings("uptime\r\n"))
    }

    @Test
    fun normalizeEnterByte_convertsSingleCarriageReturnByte() {
        val normalized = AdbShellTtyConnector.normalizeEnterByte(byteArrayOf('\r'.code.toByte()))
        assertContentEquals(byteArrayOf('\n'.code.toByte()), normalized)
    }

    @Test
    fun termSizeToWinSize_preservesColumnsAndRows() {
        val winSize = AdbShellTtyConnector.termSizeToWinSize(TermSize(120, 40))
        assertEquals(120, winSize.columns)
        assertEquals(40, winSize.rows)
    }
}
