package `fun`.abbas.wps_adb.ui.decompile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.decompile_empty_title
import wpsadbtool.shared.generated.resources.decompile_new_session
import wpsadbtool.shared.generated.resources.decompile_search_placeholder
import wpsadbtool.shared.generated.resources.decompile_tab_manifest
import wpsadbtool.shared.generated.resources.decompile_tab_resources
import wpsadbtool.shared.generated.resources.decompile_tab_smali
import `fun`.abbas.wps_adb.theme.CarbonColors

@Composable
fun DecompileTopBar(
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(CarbonColors.Surface)
            .border(width = 1.dp, color = CarbonColors.OutlineVariant)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.decompile_empty_title),
                color = CarbonColors.Primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(CarbonColors.OutlineVariant),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DisabledDecompileTab(stringResource(Res.string.decompile_tab_manifest), selected = true)
                DisabledDecompileTab(stringResource(Res.string.decompile_tab_smali))
                DisabledDecompileTab(stringResource(Res.string.decompile_tab_resources))
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(256.dp)
                    .background(CarbonColors.SurfaceContainerLow, RoundedCornerShape(999.dp))
                    .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(999.dp))
                    .padding(horizontal = 36.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.decompile_search_placeholder),
                    color = CarbonColors.OnSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.alpha(0.7f),
                )
            }
            Row(
                modifier = Modifier
                    .background(CarbonColors.PrimaryContainer, RoundedCornerShape(4.dp))
                    .clickable(onClick = onNewSession)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "+", color = CarbonColors.OnPrimaryContainer, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(Res.string.decompile_new_session),
                    color = CarbonColors.OnPrimaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun DisabledDecompileTab(label: String, selected: Boolean = false) {
    Text(
        text = label,
        color = if (selected) CarbonColors.Primary else CarbonColors.OnSurfaceVariant.copy(alpha = 0.6f),
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier
            .alpha(if (selected) 1f else 0.6f)
            .padding(bottom = 4.dp),
    )
}
