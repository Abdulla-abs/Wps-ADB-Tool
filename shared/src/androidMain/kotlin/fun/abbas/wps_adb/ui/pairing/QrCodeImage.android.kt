package `fun`.abbas.wps_adb.ui.pairing

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import qrcode.QRCode

@Composable
actual fun QrCodeImage(
    payload: String,
    modifier: Modifier,
) {
    val bitmap = remember(payload) {
        val png = QRCode.ofSquares().withSize(12).build(payload).render().getBytes()
        BitmapFactory.decodeByteArray(png, 0, png.size).asImageBitmap()
    }
    Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
}
