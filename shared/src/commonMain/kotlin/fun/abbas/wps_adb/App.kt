package `fun`.abbas.wps_adb

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import `fun`.abbas.wps_adb.data.createAdbRepository
import `fun`.abbas.wps_adb.data.createDeviceShellService
import `fun`.abbas.wps_adb.data.createScrcpyMirrorService
import `fun`.abbas.wps_adb.platform.TrackAppWindowFocus
import `fun`.abbas.wps_adb.theme.CarbonTheme
import `fun`.abbas.wps_adb.ui.layout.AppShell
import `fun`.abbas.wps_adb.viewmodel.AppViewModel

import androidx.compose.runtime.LaunchedEffect
import `fun`.abbas.wps_adb.model.NavTab

@Composable
fun App(
    viewModel: AppViewModel? = null,
    initialNavTab: NavTab? = null,
    sceneContent: (@Composable (AppViewModel) -> Unit)? = null,
) {
    TrackAppWindowFocus()
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).build()
    }
    CarbonTheme {
        val appViewModel = viewModel ?: viewModel {
            val repository = createAdbRepository()
            AppViewModel(
                repository = repository,
                scrcpyMirrorService = createScrcpyMirrorService(
                    scrcpyPathProvider = { repository.settings.value.scrcpyPath },
                    adbPathProvider = { repository.settings.value.adbPath },
                ),
                deviceShellService = createDeviceShellService(
                    adbPathProvider = { repository.settings.value.adbPath },
                ),
            )
        }
        LaunchedEffect(initialNavTab) {
            if (initialNavTab != null) {
                if (initialNavTab == NavTab.SCENE && !appViewModel.settings.value.threeDSceneEnabled) {
                    appViewModel.saveSettings(appViewModel.settings.value.copy(threeDSceneEnabled = true))
                }
                appViewModel.setActiveTab(initialNavTab)
            }
        }
        AppShell(
            viewModel = appViewModel,
            sceneContent = sceneContent?.let { content -> { content(appViewModel) } },
        )
    }
}
