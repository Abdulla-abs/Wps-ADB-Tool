package `fun`.abbas.wps_adb.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceAction
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.DeviceType
import `fun`.abbas.wps_adb.model.ScreenFormFactor
import `fun`.abbas.wps_adb.model.displayAspectRatio
import `fun`.abbas.wps_adb.model.isLandscapeScreen
import `fun`.abbas.wps_adb.model.FilterTab
import `fun`.abbas.wps_adb.model.ShellTransitionKind
import `fun`.abbas.wps_adb.model.SortParam
import `fun`.abbas.wps_adb.theme.CarbonColors
import `fun`.abbas.wps_adb.platform.apkDropTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil3.compose.SubcomposeAsyncImage
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.*
import wpsadbtool.shared.generated.resources.Res

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DeviceWallScreen(
    devices: List<Device>,
    isScanningDevices: Boolean,
    filterTab: FilterTab,
    searchQuery: String,
    sortParam: SortParam,
    onMirror: (Device) -> Unit,
    onTerminal: (Device) -> Unit,
    onAction: (String, DeviceAction) -> Unit,
    onApkDrop: suspend (deviceId: String, apkPath: String) -> Unit,
    onReconnect: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    transitionKind: ShellTransitionKind = ShellTransitionKind.SLIDE,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(Res.string.device_wall_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = CarbonColors.OnSurface,
            )
            if (isScanningDevices) {
                DeviceScanningTag()
            }
        }
        Text(
            stringResource(Res.string.device_wall_subtitle),
            fontSize = 12.sp,
            color = CarbonColors.OnSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        DeviceGrid(
            devices = devices,
            isScanningDevices = isScanningDevices,
            filterTab = filterTab,
            searchQuery = searchQuery,
            sortParam = sortParam,
            onMirror = onMirror,
            onTerminal = onTerminal,
            onAction = onAction,
            onApkDrop = onApkDrop,
            onReconnect = onReconnect,
            onRemove = onRemove,
            transitionKind = transitionKind,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun DeviceScanningTag(modifier: Modifier = Modifier) {
    val labelSize = 9.sp
    val indicatorSize = 9.dp
    Row(
        modifier = modifier
            .border(1.dp, CarbonColors.Primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .background(CarbonColors.Primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(Res.string.device_wall_scanning),
            fontSize = labelSize,
            fontWeight = FontWeight.Medium,
            color = CarbonColors.Primary,
        )
        CircularProgressIndicator(
            modifier = Modifier.size(indicatorSize),
            color = CarbonColors.Primary,
            trackColor = CarbonColors.OutlineVariant,
            strokeWidth = 1.dp,
        )
    }
}

@Composable
private fun DeviceScanningPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(Res.string.device_wall_scanning),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = CarbonColors.Primary,
        )
        LinearProgressIndicator(
            modifier = Modifier.width(220.dp).height(3.dp),
            color = CarbonColors.Primary,
            trackColor = CarbonColors.OutlineVariant,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DeviceGrid(
    devices: List<Device>,
    isScanningDevices: Boolean,
    filterTab: FilterTab,
    searchQuery: String,
    sortParam: SortParam,
    onMirror: (Device) -> Unit,
    onTerminal: (Device) -> Unit,
    onAction: (String, DeviceAction) -> Unit,
    onApkDrop: suspend (deviceId: String, apkPath: String) -> Unit,
    onReconnect: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    transitionKind: ShellTransitionKind = ShellTransitionKind.SLIDE,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val filtered = devices
        .filter { device ->
            when (filterTab) {
                FilterTab.PHYSICAL -> device.type == DeviceType.PHYSICAL
                FilterTab.EMULATORS -> device.type == DeviceType.EMULATOR
                FilterTab.ALL -> true
            }
        }
        .filter { device ->
            val q = searchQuery.lowercase()
            device.name.lowercase().contains(q) ||
                device.serial.lowercase().contains(q) ||
                device.androidVersion.lowercase().contains(q)
        }
        .let { list ->
            when (sortParam) {
                SortParam.SERIAL -> list.sortedBy { it.serial }
                SortParam.BATTERY -> list.sortedByDescending { it.batteryLevel }
                SortParam.NAME -> list.sortedBy { it.name }
            }
        }

    if (filtered.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp)
                .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(12.dp))
                .background(CarbonColors.SurfaceContainerLow)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isScanningDevices && devices.isEmpty() -> DeviceScanningPanel()
                else -> Text(stringResource(Res.string.device_wall_empty), color = CarbonColors.OnSurfaceVariant)
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 260.dp),
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(filtered, key = { it.id }) { device ->
                DeviceCard(
                    device = device,
                    onMirror = onMirror,
                    onTerminal = onTerminal,
                    onAction = onAction,
                    onApkDrop = onApkDrop,
                    onReconnect = onReconnect,
                    onRemove = onRemove,
                    transitionKind = transitionKind,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DeviceCard(
    device: Device,
    onMirror: (Device) -> Unit,
    onTerminal: (Device) -> Unit,
    onAction: (String, DeviceAction) -> Unit,
    onApkDrop: suspend (deviceId: String, apkPath: String) -> Unit,
    onReconnect: (String) -> Unit,
    onRemove: (String) -> Unit,
    transitionKind: ShellTransitionKind = ShellTransitionKind.SLIDE,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val isOnline = device.status == DeviceStatus.ONLINE
    var isDragOver by remember(device.id) { mutableStateOf(false) }
    var installingApk by remember(device.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .apkDropTarget(
                enabled = isOnline && installingApk == null,
                onDragEnter = { isDragOver = true },
                onDragExit = { isDragOver = false },
                onApkDropped = { path ->
                    isDragOver = false
                    val fileName = path.substringAfterLast('/').substringAfterLast('\\')
                    scope.launch {
                        installingApk = fileName
                        try {
                            onApkDrop(device.id, path)
                        } finally {
                            delay(400)
                            installingApk = null
                        }
                    }
                },
            )
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isDragOver) 2.dp else 1.dp,
                color = when {
                    isDragOver -> CarbonColors.Primary
                    isOnline -> CarbonColors.OutlineVariant
                    else -> CarbonColors.Error.copy(alpha = 0.15f)
                },
                shape = RoundedCornerShape(12.dp),
            )
            .background(CarbonColors.SurfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val titleModifier = if (
                    transitionKind == ShellTransitionKind.SHARED_ELEMENT &&
                    sharedTransitionScope != null &&
                    animatedVisibilityScope != null
                ) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(ShellSharedElementKeys.title(device.id)),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                } else {
                    Modifier
                }
                Text(
                    device.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CarbonColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = titleModifier,
                )
                Text(device.serial, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = CarbonColors.Outline)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formFactorLabel(device.formFactor), fontSize = 10.sp, color = CarbonColors.Secondary)
                Text(connectionLabel(device.connectionType), fontSize = 10.sp, color = CarbonColors.Outline)
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            when (device.status) {
                                DeviceStatus.ONLINE -> CarbonColors.Primary
                                DeviceStatus.OFFLINE -> CarbonColors.Error
                                DeviceStatus.UNAUTHORIZED -> CarbonColors.Secondary
                            },
                            CircleShape,
                        ),
                )
            }
        }

        val previewModifier = if (
            transitionKind == ShellTransitionKind.SHARED_ELEMENT &&
            sharedTransitionScope != null &&
            animatedVisibilityScope != null
        ) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    rememberSharedContentState(ShellSharedElementKeys.hero(device.id)),
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        } else {
            Modifier
        }
        Box(
            modifier = previewModifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .aspectRatio(device.displayAspectRatio())
                .clip(
                    RoundedCornerShape(
                        when (device.formFactor) {
                            ScreenFormFactor.TV -> 4.dp
                            ScreenFormFactor.TABLET -> 10.dp
                            else -> 8.dp
                        },
                    ),
                )
                .background(CarbonColors.SurfaceContainerLowest),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isOnline && device.screenshotUrl.isNotBlank() -> {
                    SubcomposeAsyncImage(
                        model = device.screenshotUrl,
                        contentDescription = device.screenDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = if (device.isLandscapeScreen()) ContentScale.Fit else ContentScale.Crop,
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(Res.string.device_loading_screen), fontSize = 11.sp, color = CarbonColors.Outline)
                            }
                        },
                        error = {
                            ScreenshotFallback(device)
                        },
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(CarbonColors.SurfaceContainerHighest.copy(alpha = 0.75f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("10:14", fontSize = 9.sp, color = CarbonColors.OnSurface)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${device.batteryLevel}%", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = CarbonColors.OnSurfaceVariant)
                                Text(stringResource(Res.string.status_lte), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = CarbonColors.Primary)
                            }
                        }
                    }
                    device.apps.getOrNull(device.currentAppIndex)?.let { app ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = 36.dp)
                                .padding(horizontal = 8.dp)
                                .background(CarbonColors.SurfaceContainerHighest.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                stringResource(Res.string.device_app_label, app.name),
                                fontSize = 10.sp,
                                color = CarbonColors.OnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                isOnline -> ScreenshotFallback(device)
                else -> OfflineDevicePrompt(
                    onReconnect = { onReconnect(device.id) },
                    onRemove = { onRemove(device.id) },
                )
            }
            if (isDragOver && isOnline) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CarbonColors.Primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(Res.string.device_drop_apk_hint),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CarbonColors.Primary,
                    )
                }
            }
            if (installingApk != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CarbonColors.SurfaceContainerHighest.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(Res.string.device_installing_apk), fontSize = 12.sp, color = CarbonColors.OnSurface)
                        Text(installingApk!!, fontSize = 10.sp, color = CarbonColors.Outline, modifier = Modifier.padding(top = 4.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(0.7f),
                            color = CarbonColors.Primary,
                        )
                    }
                }
            }
            if (isOnline) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(CarbonColors.SurfaceContainerHighest.copy(alpha = 0.9f))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Text(stringResource(Res.string.device_action_mirror), fontSize = 11.sp, color = CarbonColors.Primary, modifier = Modifier.clickable { onMirror(device) })
                    Text(stringResource(Res.string.device_action_shell), fontSize = 11.sp, color = CarbonColors.OnSurface, modifier = Modifier.clickable { onTerminal(device) })
                    Text(stringResource(Res.string.device_action_debug), fontSize = 11.sp, color = CarbonColors.OnSurface, modifier = Modifier.clickable { onAction(device.id, DeviceAction.DEBUG) })
                    Text(stringResource(Res.string.device_action_drop), fontSize = 11.sp, color = CarbonColors.Error, modifier = Modifier.clickable { onAction(device.id, DeviceAction.DISCONNECT) })
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(device.androidVersion, fontSize = 11.sp, color = CarbonColors.OnSurfaceVariant)
                Text(
                    if (isOnline) "${device.batteryLevel}%" else "--%",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CarbonColors.Primary,
                )
            }
            if (isOnline) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(Res.string.device_storage_label), fontSize = 10.sp, color = CarbonColors.Outline)
                    Text("${device.storagePercent}%", fontSize = 10.sp, color = CarbonColors.Outline)
                }
                LinearProgressIndicator(
                    progress = { device.storagePercent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = CarbonColors.SecondaryContainer,
                    trackColor = CarbonColors.SurfaceContainerHighest,
                )
            }
        }
    }
}

@Composable
private fun ScreenshotFallback(device: Device) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonColors.SurfaceContainerLow)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(Res.string.device_no_capture_title), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CarbonColors.OnSurfaceVariant)
        Text(
            stringResource(Res.string.device_connected),
            fontSize = 10.sp,
            color = CarbonColors.Outline,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            stringResource(Res.string.device_no_capture_hint),
            fontSize = 9.sp,
            color = CarbonColors.Outline,
            modifier = Modifier.padding(top = 8.dp),
        )
        device.apps.getOrNull(device.currentAppIndex)?.let { app ->
            Text(stringResource(Res.string.device_app_label, app.name), fontSize = 10.sp, color = CarbonColors.Primary, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun OfflineDevicePrompt(onReconnect: () -> Unit, onRemove: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.device_offline), fontSize = 12.sp, color = CarbonColors.Outline)
        Text(
            stringResource(Res.string.device_action_reconnect),
            fontSize = 12.sp,
            color = CarbonColors.Primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable(onClick = onReconnect),
        )
        Text(
            stringResource(Res.string.device_action_remove),
            fontSize = 12.sp,
            color = CarbonColors.Error,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable(onClick = onRemove),
        )
    }
}

@Composable
private fun formFactorLabel(factor: ScreenFormFactor): String = when (factor) {
    ScreenFormFactor.PHONE -> stringResource(Res.string.form_factor_phone)
    ScreenFormFactor.TABLET -> stringResource(Res.string.form_factor_tablet)
    ScreenFormFactor.TV -> stringResource(Res.string.form_factor_tv)
    ScreenFormFactor.UNKNOWN -> stringResource(Res.string.form_factor_unknown)
}

@Composable
private fun connectionLabel(type: ConnectionType): String = when (type) {
    ConnectionType.WIFI -> stringResource(Res.string.connection_wifi)
    ConnectionType.USB -> stringResource(Res.string.connection_usb)
    ConnectionType.EMULATOR -> stringResource(Res.string.connection_emulator)
}
