package `fun`.abbas.wps_adb.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.theme.CarbonColors
import kotlinx.coroutines.delay

private const val TOAST_DISMISS_MS = 3000L

@Composable
fun ToastBanner(
    message: String,
    isSuccess: Boolean,
    toastId: Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(toastId) {
        delay(TOAST_DISMISS_MS)
        onDismiss()
    }

    Text(
        text = message,
        modifier = modifier
            .background(
                color = if (isSuccess) CarbonColors.Primary else CarbonColors.Error,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = if (isSuccess) CarbonColors.OnPrimary else CarbonColors.OnError,
    )
}
