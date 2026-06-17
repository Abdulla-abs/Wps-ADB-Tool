package `fun`.abbas.wps_adb.data

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

object JvmSystemClipboard {
    fun copyPngImage(pngBytes: ByteArray): Boolean {
        val image = ImageIO.read(ByteArrayInputStream(pngBytes)) ?: return false
        return copyImage(image)
    }

    private fun copyImage(image: BufferedImage): Boolean = try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(image), null)
        true
    } catch (_: Exception) {
        false
    }

    private class ImageTransferable(private val image: BufferedImage) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> =
            arrayOf(DataFlavor.imageFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
            DataFlavor.imageFlavor.equals(flavor)

        override fun getTransferData(flavor: DataFlavor): Any {
            if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
            return image
        }
    }
}
