package `fun`.abbas.wps_adb.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

private val carbonDarkScheme = darkColorScheme(
    primary = CarbonColors.Primary,
    onPrimary = CarbonColors.OnPrimary,
    primaryContainer = CarbonColors.PrimaryContainer,
    onPrimaryContainer = CarbonColors.OnPrimaryContainer,
    secondary = CarbonColors.Secondary,
    secondaryContainer = CarbonColors.SecondaryContainer,
    error = CarbonColors.Error,
    onError = CarbonColors.OnError,
    background = CarbonColors.Background,
    onBackground = CarbonColors.OnBackground,
    surface = CarbonColors.Surface,
    onSurface = CarbonColors.OnSurface,
    onSurfaceVariant = CarbonColors.OnSurfaceVariant,
    surfaceContainerLowest = CarbonColors.SurfaceContainerLowest,
    surfaceContainerLow = CarbonColors.SurfaceContainerLow,
    surfaceContainer = CarbonColors.SurfaceContainer,
    surfaceContainerHigh = CarbonColors.SurfaceContainerHigh,
    surfaceContainerHighest = CarbonColors.SurfaceContainerHighest,
    outline = CarbonColors.Outline,
    outlineVariant = CarbonColors.OutlineVariant,
)

private val CarbonTypography = Typography(
    bodySmall = Typography().bodySmall.copy(fontFamily = FontFamily.SansSerif),
)

@Composable
fun CarbonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = carbonDarkScheme,
        typography = CarbonTypography,
        content = content,
    )
}
