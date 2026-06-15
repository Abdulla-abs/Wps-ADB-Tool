package `fun`.abbas.wps_adb.ui.decompile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.decompile_export_apk
import wpsadbtool.shared.generated.resources.decompile_workspace_title
import `fun`.abbas.wps_adb.theme.CarbonColors

@Composable
fun DecompileWorkspaceToolbar(
    packageName: String,
    onExportApk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(CarbonColors.SurfaceContainerLow)
            .border(1.dp, CarbonColors.OutlineVariant)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.decompile_workspace_title, packageName),
            color = CarbonColors.OnSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Row(
            modifier = Modifier
                .background(CarbonColors.SurfaceContainerHigh, RoundedCornerShape(6.dp))
                .border(1.dp, CarbonColors.Primary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .clickable(onClick = onExportApk)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.decompile_export_apk),
                color = CarbonColors.Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
