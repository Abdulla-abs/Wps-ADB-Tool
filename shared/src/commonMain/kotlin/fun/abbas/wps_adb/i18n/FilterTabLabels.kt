package `fun`.abbas.wps_adb.i18n

import androidx.compose.runtime.Composable
import `fun`.abbas.wps_adb.model.FilterTab
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.*
import wpsadbtool.shared.generated.resources.Res

@Composable
fun FilterTab.localizedName(): String = when (this) {
    FilterTab.ALL -> stringResource(Res.string.groups_filter_all)
    FilterTab.PHYSICAL -> stringResource(Res.string.groups_filter_physical)
    FilterTab.EMULATORS -> stringResource(Res.string.groups_filter_emulators)
}
