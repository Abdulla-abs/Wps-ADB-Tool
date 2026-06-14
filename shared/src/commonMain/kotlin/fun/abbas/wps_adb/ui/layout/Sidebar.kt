package `fun`.abbas.wps_adb.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.NavTab
import `fun`.abbas.wps_adb.platform.pickApkFile
import `fun`.abbas.wps_adb.theme.CarbonColors
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.*
import wpsadbtool.shared.generated.resources.Res

@Composable
fun Sidebar(
    activeTab: NavTab,
    onTabChange: (NavTab) -> Unit,
    onlineCount: Int,
    onApkInstall: suspend (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var installingFile by remember { mutableStateOf<String?>(null) }
    var installProgress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .background(CarbonColors.SurfaceContainerLow)
            .border(width = 1.dp, color = CarbonColors.OutlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(stringResource(Res.string.app_name), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CarbonColors.OnSurface)
            Text(
                stringResource(Res.string.adb_daemon_version),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = CarbonColors.Outline,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(CarbonColors.SurfaceContainer, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NavItem(stringResource(Res.string.nav_device_wall), activeTab == NavTab.WALL) { onTabChange(NavTab.WALL) }
            NavItem(stringResource(Res.string.nav_group_command), activeTab == NavTab.GROUPS) { onTabChange(NavTab.GROUPS) }
            NavItem(stringResource(Res.string.nav_decompile), activeTab == NavTab.DECOMPILE) { onTabChange(NavTab.DECOMPILE) }
            NavItem(stringResource(Res.string.nav_global_settings), activeTab == NavTab.SETTINGS) { onTabChange(NavTab.SETTINGS) }
        }

        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            if (installingFile != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CarbonColors.Primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .background(CarbonColors.Primary.copy(alpha = 0.05f))
                        .padding(12.dp),
                ) {
                    Text(stringResource(Res.string.sidebar_installing_apk), fontSize = 12.sp, color = CarbonColors.OnSurface)
                    Text(installingFile!!, fontSize = 10.sp, color = CarbonColors.Outline)
                    LinearProgressIndicator(
                        progress = { installProgress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        color = CarbonColors.Primary,
                    )
                    Text("${(installProgress * 100).toInt()}%", fontSize = 10.sp, color = CarbonColors.Primary, modifier = Modifier.align(Alignment.End))
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, CarbonColors.OutlineVariant, RoundedCornerShape(12.dp))
                        .clickable {
                            scope.launch {
                                val path = pickApkFile() ?: return@launch
                                val displayName = path.substringAfterLast('/').substringAfterLast('\\')
                                installingFile = displayName
                                installProgress = 0.1f
                                val progressJob = async {
                                    while (installProgress < 0.9f) {
                                        delay(300)
                                        installProgress = (installProgress + 0.1f).coerceAtMost(0.9f)
                                    }
                                }
                                onApkInstall(path)
                                progressJob.await()
                                installProgress = 1f
                                delay(400)
                                installingFile = null
                                installProgress = 0f
                            }
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(Res.string.sidebar_drop_apk_title), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = CarbonColors.OnSurfaceVariant)
                        Text(stringResource(Res.string.sidebar_drop_apk_subtitle, onlineCount), fontSize = 9.sp, color = CarbonColors.Outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) CarbonColors.SurfaceContainerHigh else CarbonColors.SurfaceContainerLow,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = if (selected) CarbonColors.Primary else CarbonColors.Outline,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
