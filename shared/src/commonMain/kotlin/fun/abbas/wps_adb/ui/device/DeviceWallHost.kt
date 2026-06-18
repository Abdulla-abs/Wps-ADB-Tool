package `fun`.abbas.wps_adb.ui.device

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceShellSession
import `fun`.abbas.wps_adb.model.DeviceWallRoute
import `fun`.abbas.wps_adb.model.EasyActionKind
import `fun`.abbas.wps_adb.model.FilterTab
import `fun`.abbas.wps_adb.model.ShellTransitionKind
import `fun`.abbas.wps_adb.model.SortParam

private const val SHELL_TRANSITION_MS = 300

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DeviceWallHost(
    route: DeviceWallRoute,
    transitionKind: ShellTransitionKind,
    devices: List<Device>,
    shellSession: DeviceShellSession?,
    terminalComponent: Any?,
    isScanningDevices: Boolean,
    filterTab: FilterTab,
    searchQuery: String,
    sortParam: SortParam,
    deviceCustomOrder: List<String>,
    onReorderDevices: (filteredSerialsInDisplayOrder: List<String>, fromIndex: Int, toIndex: Int) -> Unit,
    onMirror: (Device) -> Unit,
    onTerminal: (Device) -> Unit,
    onAction: (String, `fun`.abbas.wps_adb.model.DeviceAction) -> Unit,
    onApkDrop: suspend (deviceId: String, apkPath: String) -> Unit,
    onReconnect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onShellBack: () -> Unit,
    onOpenShellLogcat: () -> Unit,
    onShellTerminalMounted: () -> Unit,
    onEasyAction: (EasyActionKind) -> Unit,
    onShellTransitionComplete: () -> Unit,
    suppressTerminalSurface: Boolean = false,
    terminalHiddenMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(route) {
        if (route is DeviceWallRoute.Shell) {
            kotlinx.coroutines.delay(SHELL_TRANSITION_MS.toLong())
            onShellTransitionComplete()
        }
    }

    when (transitionKind) {
        ShellTransitionKind.SHARED_ELEMENT -> {
            SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        fadeIn(tween(SHELL_TRANSITION_MS)) togetherWith fadeOut(tween(SHELL_TRANSITION_MS))
                    },
                    label = "deviceWallRoute",
                ) { target ->
                    DeviceWallRouteContent(
                        route = target,
                        devices = devices,
                        shellSession = shellSession,
                        terminalComponent = terminalComponent,
                        isScanningDevices = isScanningDevices,
                        filterTab = filterTab,
                        searchQuery = searchQuery,
                        sortParam = sortParam,
                        deviceCustomOrder = deviceCustomOrder,
                        onReorderDevices = onReorderDevices,
                        transitionKind = transitionKind,
                        onMirror = onMirror,
                        onTerminal = onTerminal,
                        onAction = onAction,
                        onApkDrop = onApkDrop,
                        onReconnect = onReconnect,
                        onRemove = onRemove,
                        onShellBack = onShellBack,
                        onOpenShellLogcat = onOpenShellLogcat,
                        onShellTerminalMounted = onShellTerminalMounted,
                        onEasyAction = onEasyAction,
                        suppressTerminalSurface = suppressTerminalSurface,
                        terminalHiddenMessage = terminalHiddenMessage,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@AnimatedContent,
                    )
                }
            }
        }
        ShellTransitionKind.SLIDE -> {
            AnimatedContent(
                targetState = route,
                modifier = modifier.fillMaxSize(),
                transitionSpec = {
                    if (targetState is DeviceWallRoute.Shell) {
                        slideInHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { it } +
                            fadeIn(tween(280)) togetherWith
                            slideOutHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { -it / 3 } +
                            fadeOut(tween(280))
                    } else {
                        slideInHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { -it } +
                            fadeIn(tween(280)) togetherWith
                            slideOutHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { it } +
                            fadeOut(tween(280))
                    }
                },
                label = "deviceWallRouteSlide",
            ) { target ->
                DeviceWallRouteContent(
                    route = target,
                    devices = devices,
                    shellSession = shellSession,
                    terminalComponent = terminalComponent,
                    isScanningDevices = isScanningDevices,
                    filterTab = filterTab,
                    searchQuery = searchQuery,
                    sortParam = sortParam,
                    deviceCustomOrder = deviceCustomOrder,
                    onReorderDevices = onReorderDevices,
                    transitionKind = transitionKind,
                    onMirror = onMirror,
                    onTerminal = onTerminal,
                    onAction = onAction,
                    onApkDrop = onApkDrop,
                    onReconnect = onReconnect,
                    onRemove = onRemove,
                    onShellBack = onShellBack,
                    onOpenShellLogcat = onOpenShellLogcat,
                    onShellTerminalMounted = onShellTerminalMounted,
                    onEasyAction = onEasyAction,
                    suppressTerminalSurface = suppressTerminalSurface,
                    terminalHiddenMessage = terminalHiddenMessage,
                    sharedTransitionScope = null,
                    animatedVisibilityScope = null,
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DeviceWallRouteContent(
    route: DeviceWallRoute,
    devices: List<Device>,
    shellSession: DeviceShellSession?,
    terminalComponent: Any?,
    isScanningDevices: Boolean,
    filterTab: FilterTab,
    searchQuery: String,
    sortParam: SortParam,
    deviceCustomOrder: List<String>,
    onReorderDevices: (filteredSerialsInDisplayOrder: List<String>, fromIndex: Int, toIndex: Int) -> Unit,
    transitionKind: ShellTransitionKind,
    onMirror: (Device) -> Unit,
    onTerminal: (Device) -> Unit,
    onAction: (String, `fun`.abbas.wps_adb.model.DeviceAction) -> Unit,
    onApkDrop: suspend (deviceId: String, apkPath: String) -> Unit,
    onReconnect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onShellBack: () -> Unit,
    onOpenShellLogcat: () -> Unit,
    onShellTerminalMounted: () -> Unit,
    onEasyAction: (EasyActionKind) -> Unit,
    suppressTerminalSurface: Boolean,
    terminalHiddenMessage: String?,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    when (route) {
        DeviceWallRoute.Grid -> DeviceWallScreen(
            devices = devices,
            isScanningDevices = isScanningDevices,
            filterTab = filterTab,
            searchQuery = searchQuery,
            sortParam = sortParam,
            deviceCustomOrder = deviceCustomOrder,
            onReorderDevices = onReorderDevices,
            onMirror = onMirror,
            onTerminal = onTerminal,
            onAction = onAction,
            onApkDrop = onApkDrop,
            onReconnect = onReconnect,
            onRemove = onRemove,
            transitionKind = transitionKind,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = Modifier.fillMaxSize(),
        )
        is DeviceWallRoute.Shell -> {
            val device = devices.find { it.id == route.deviceId }
            val session = shellSession
            if (device != null && session != null) {
                DeviceShellScreen(
                    device = device,
                    sessionState = session.sessionState,
                    errorMessage = session.errorMessage,
                    terminalComponent = terminalComponent,
                    terminalSurfaceReady = session.terminalSurfaceReady,
                    isScreenRecording = session.isScreenRecording,
                    developerOptionStates = session.developerOptionStates,
                    transitionKind = transitionKind,
                    onBack = onShellBack,
                    onOpenLogcat = onOpenShellLogcat,
                    onTerminalMounted = onShellTerminalMounted,
                    onEasyAction = onEasyAction,
                    suppressTerminalSurface = suppressTerminalSurface,
                    terminalHiddenMessage = terminalHiddenMessage,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
