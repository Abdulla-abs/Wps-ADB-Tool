package `fun`.abbas.wps_adb.ui.decompile

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Terminal
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.decompile_feature_jadx
import wpsadbtool.shared.generated.resources.decompile_feature_resign
import wpsadbtool.shared.generated.resources.decompile_feature_scan
import wpsadbtool.shared.generated.resources.decompile_no_project_active
import wpsadbtool.shared.generated.resources.decompile_no_project_hint
import `fun`.abbas.wps_adb.theme.CarbonColors

@Composable
fun DecompileEmptyEditorPane(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonColors.Surface)
            .drawBehind { drawCodeGrid() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            CodeOffIcon()
            Text(
                text = stringResource(Res.string.decompile_no_project_active),
                color = CarbonColors.OnSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.decompile_no_project_hint),
                color = CarbonColors.OnSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(420.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(top = 16.dp)
                    .height(IntrinsicSize.Max),
            ) {
                GlassFeatureCard(
                    icon = Icons.Outlined.Terminal,
                    iconColor = CarbonColors.Primary,
                    label = stringResource(Res.string.decompile_feature_jadx),
                )
                GlassFeatureCard(
                    icon = Icons.Outlined.ManageSearch,
                    iconColor = CarbonColors.Secondary,
                    label = stringResource(Res.string.decompile_feature_scan),
                )
                GlassFeatureCard(
                    icon = Icons.Outlined.AutoFixHigh,
                    iconColor = CarbonColors.OnTertiaryContainer,
                    label = stringResource(Res.string.decompile_feature_resign),
                )
            }
        }
    }
}

@Composable
private fun CodeOffIcon() {
    val iconShape = RoundedCornerShape(12.dp)
    val transition = rememberInfiniteTransition(label = "codeOffRipple")
    val rippleAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rippleAlpha",
    )
    val rippleScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rippleScale",
    )

    Box(
        modifier = Modifier.size(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = rippleScale
                    scaleY = rippleScale
                    alpha = rippleAlpha
                }
                .border(1.dp, CarbonColors.Primary.copy(alpha = 0.2f), iconShape),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(CarbonColors.SurfaceContainerHigh, iconShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "</>",
                color = CarbonColors.OnSurfaceVariant.copy(alpha = 0.3f),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun GlassFeatureCard(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(128.dp)
            .fillMaxHeight()
            .background(CarbonColors.SurfaceContainer.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .border(1.dp, CarbonColors.OutlineVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            color = CarbonColors.OnSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp,
            minLines = 2,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCodeGrid() {
    val gridColor = Color.White.copy(alpha = 0.03f)
    val step = 24.dp.toPx()
    var x = 0f
    while (x < size.width) {
        var y = 0f
        while (y < size.height) {
            drawCircle(gridColor, radius = 1f, center = Offset(x, y))
            y += step
        }
        x += step
    }
}
