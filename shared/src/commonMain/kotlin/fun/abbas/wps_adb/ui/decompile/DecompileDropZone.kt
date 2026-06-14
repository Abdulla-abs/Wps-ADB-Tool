package `fun`.abbas.wps_adb.ui.decompile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.platform.pickApkFile
import `fun`.abbas.wps_adb.theme.CarbonColors
import kotlinx.coroutines.launch

@Composable
fun DecompileDropZone(
    onApkImport: (String) -> Unit,
    progress: Float?,
    taskName: String,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .background(CarbonColors.Background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        if (progress != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    color = CarbonColors.Primary,
                    trackColor = CarbonColors.SurfaceContainerHighest,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = taskName,
                    color = CarbonColors.OnSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = CarbonColors.Primary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .border(
                        width = 2.dp,
                        color = CarbonColors.Primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(CarbonColors.SurfaceContainerLow, RoundedCornerShape(12.dp))
                    .clickable {
                        scope.launch {
                            val path = pickApkFile()
                            if (path != null) {
                                onApkImport(path)
                            }
                        }
                    }
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "📥",
                    fontSize = 48.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "导入 APK 开始反编译",
                    color = CarbonColors.OnSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "点击此处选择 APK 文件或拖放文件到此处",
                    color = CarbonColors.Outline,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Recent project
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .background(CarbonColors.SurfaceContainer, RoundedCornerShape(8.dp))
                        .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "最近导入项目",
                        color = CarbonColors.Outline,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "com.android.settings (settings.apk)",
                        color = CarbonColors.Primary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
