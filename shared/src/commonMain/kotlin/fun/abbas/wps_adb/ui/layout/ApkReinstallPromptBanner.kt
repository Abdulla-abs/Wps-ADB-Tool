package `fun`.abbas.wps_adb.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.theme.CarbonColors
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.apk_reinstall_prompt_cancel
import wpsadbtool.shared.generated.resources.apk_reinstall_prompt_confirm
import wpsadbtool.shared.generated.resources.apk_reinstall_prompt_message

private const val PROMPT_DISMISS_SECONDS = 10

@Composable
fun ApkReinstallPromptBanner(
    promptId: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var secondsLeft by remember(promptId) { mutableIntStateOf(PROMPT_DISMISS_SECONDS) }

    LaunchedEffect(promptId) {
        secondsLeft = PROMPT_DISMISS_SECONDS
        repeat(PROMPT_DISMISS_SECONDS) {
            delay(1000)
            secondsLeft = PROMPT_DISMISS_SECONDS - it - 1
        }
        onDismiss()
    }

    Column(
        modifier = modifier
            .background(
                color = CarbonColors.SurfaceContainerHighest,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.apk_reinstall_prompt_message, secondsLeft),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = CarbonColors.OnSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(Res.string.apk_reinstall_prompt_confirm),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CarbonColors.Primary,
                )
            }
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.apk_reinstall_prompt_cancel),
                    fontSize = 12.sp,
                    color = CarbonColors.OnSurface,
                )
            }
        }
    }
}
