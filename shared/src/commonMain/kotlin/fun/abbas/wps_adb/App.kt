package `fun`.abbas.wps_adb

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import `fun`.abbas.wps_adb.data.createAdbRepository
import `fun`.abbas.wps_adb.data.createScrcpyMirrorService
import `fun`.abbas.wps_adb.theme.CarbonTheme
import `fun`.abbas.wps_adb.ui.layout.AppShell
import `fun`.abbas.wps_adb.viewmodel.AppViewModel

@Composable
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).build()
    }
    CarbonTheme {
        val appViewModel = viewModel {
            val repository = createAdbRepository()
            AppViewModel(
                repository = repository,
                scrcpyMirrorService = createScrcpyMirrorService(
                    scrcpyPathProvider = { repository.settings.value.scrcpyPath },
                    adbPathProvider = { repository.settings.value.adbPath },
                ),
            )
        }
        AppShell(appViewModel)
    }
}
