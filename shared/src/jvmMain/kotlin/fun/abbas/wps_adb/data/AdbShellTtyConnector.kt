package `fun`.abbas.wps_adb.data

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import java.nio.charset.StandardCharsets

internal class AdbShellTtyConnector(
    private val ptyProcess: PtyProcess,
) : ProcessTtyConnector(ptyProcess, StandardCharsets.UTF_8) {

    override fun getName(): String = "adb-shell"

    override fun write(string: String) {
        super.write(normalizeLineEndings(string))
    }

    override fun write(bytes: ByteArray) {
        super.write(normalizeEnterByte(bytes))
    }

    override fun resize(termSize: TermSize) {
        ptyProcess.setWinSize(WinSize(termSize.getRows(), termSize.getColumns()))
    }

    companion object {
        internal fun normalizeLineEndings(input: String): String =
            when {
                input == "\r" -> "\n"
                input.endsWith("\r") && !input.endsWith("\r\n") -> input.dropLast(1) + "\n"
                else -> input
            }

        internal fun normalizeEnterByte(bytes: ByteArray): ByteArray =
            if (bytes.size == 1 && bytes[0] == '\r'.code.toByte()) {
                byteArrayOf('\n'.code.toByte())
            } else {
                bytes
            }
    }
}
