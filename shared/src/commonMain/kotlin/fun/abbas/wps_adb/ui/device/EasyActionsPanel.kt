package `fun`.abbas.wps_adb.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.EasyActionCategory
import `fun`.abbas.wps_adb.model.EasyActionDefinition
import `fun`.abbas.wps_adb.model.EasyActionKind
import `fun`.abbas.wps_adb.model.defaultExpanded
import `fun`.abbas.wps_adb.theme.CarbonColors
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.easy_action_category_app
import wpsadbtool.shared.generated.resources.easy_action_category_developer
import wpsadbtool.shared.generated.resources.easy_action_category_device_hardware
import wpsadbtool.shared.generated.resources.easy_action_category_display
import wpsadbtool.shared.generated.resources.easy_action_category_storage_memory
import wpsadbtool.shared.generated.resources.easy_action_category_system
import wpsadbtool.shared.generated.resources.easy_action_category_system_software
import wpsadbtool.shared.generated.resources.easy_action_clear_cache
import wpsadbtool.shared.generated.resources.easy_action_clear_data
import wpsadbtool.shared.generated.resources.easy_action_dev_dont_keep_activities_off
import wpsadbtool.shared.generated.resources.easy_action_dev_dont_keep_activities_on
import wpsadbtool.shared.generated.resources.easy_action_dev_gpu_overdraw_off
import wpsadbtool.shared.generated.resources.easy_action_dev_gpu_overdraw_on
import wpsadbtool.shared.generated.resources.easy_action_dev_gpu_profile_off
import wpsadbtool.shared.generated.resources.easy_action_dev_gpu_profile_on
import wpsadbtool.shared.generated.resources.easy_action_dev_layout_bounds_off
import wpsadbtool.shared.generated.resources.easy_action_dev_layout_bounds_on
import wpsadbtool.shared.generated.resources.easy_action_dev_pointer_location_off
import wpsadbtool.shared.generated.resources.easy_action_dev_pointer_location_on
import wpsadbtool.shared.generated.resources.easy_action_dev_refresh_rate_off
import wpsadbtool.shared.generated.resources.easy_action_dev_refresh_rate_on
import wpsadbtool.shared.generated.resources.easy_action_dev_strict_mode_off
import wpsadbtool.shared.generated.resources.easy_action_dev_strict_mode_on
import wpsadbtool.shared.generated.resources.easy_action_dev_surface_updates_off
import wpsadbtool.shared.generated.resources.easy_action_dev_surface_updates_on
import wpsadbtool.shared.generated.resources.easy_action_dev_view_updates_off
import wpsadbtool.shared.generated.resources.easy_action_dev_view_updates_on
import wpsadbtool.shared.generated.resources.easy_action_force_stop
import wpsadbtool.shared.generated.resources.easy_action_info_android_version
import wpsadbtool.shared.generated.resources.easy_action_info_available_memory
import wpsadbtool.shared.generated.resources.easy_action_info_battery_level
import wpsadbtool.shared.generated.resources.easy_action_info_battery_status
import wpsadbtool.shared.generated.resources.easy_action_info_build_id
import wpsadbtool.shared.generated.resources.easy_action_info_cpu_abi
import wpsadbtool.shared.generated.resources.easy_action_info_data_storage
import wpsadbtool.shared.generated.resources.easy_action_info_device_brand
import wpsadbtool.shared.generated.resources.easy_action_info_device_manufacturer
import wpsadbtool.shared.generated.resources.easy_action_info_device_model
import wpsadbtool.shared.generated.resources.easy_action_info_hardware_platform
import wpsadbtool.shared.generated.resources.easy_action_info_kernel_version
import wpsadbtool.shared.generated.resources.easy_action_info_screen_density
import wpsadbtool.shared.generated.resources.easy_action_info_screen_size
import wpsadbtool.shared.generated.resources.easy_action_info_sdk_version
import wpsadbtool.shared.generated.resources.easy_action_info_security_patch
import wpsadbtool.shared.generated.resources.easy_action_info_total_memory
import wpsadbtool.shared.generated.resources.easy_action_reboot
import wpsadbtool.shared.generated.resources.easy_action_recovery
import wpsadbtool.shared.generated.resources.easy_action_screen_record
import wpsadbtool.shared.generated.resources.easy_action_screen_record_stop
import wpsadbtool.shared.generated.resources.easy_action_screenshot
import wpsadbtool.shared.generated.resources.easy_action_screenshot_clipboard
import wpsadbtool.shared.generated.resources.shell_easy_actions_title

@Composable
fun EasyActionsPanel(
    actions: List<EasyActionDefinition>,
    isScreenRecording: Boolean,
    toggleStates: Map<EasyActionKind, Boolean>,
    onAction: (EasyActionKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandedCategories = remember {
        mutableStateMapOf<EasyActionCategory, Boolean>().apply {
            EasyActionCategory.entries.forEach { category ->
                this[category] = category.defaultExpanded()
            }
        }
    }

    Column(
        modifier = modifier
            .background(CarbonColors.SurfaceContainer)
            .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(topStart = 8.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.shell_easy_actions_title),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CarbonColors.Outline,
        )
        EasyActionCategory.entries.forEach { category ->
            val categoryActions = actions.filter { it.category == category }
            if (categoryActions.isEmpty()) return@forEach
            val expanded = expandedCategories[category] == true
            CollapsibleCategorySection(
                title = easyActionCategoryLabel(category),
                expanded = expanded,
                onToggle = { expandedCategories[category] = !expanded },
            ) {
                categoryActions.forEach { action ->
                    val label = when {
                        action.kind == EasyActionKind.SCREEN_RECORD && isScreenRecording ->
                            stringResource(Res.string.easy_action_screen_record_stop)
                        action.isToggle ->
                            easyActionToggleLabel(action.kind, toggleStates[action.kind] == true)
                        else -> easyActionLabel(action.kind)
                    }
                    EasyActionButton(
                        label = label,
                        destructive = action.destructive,
                        onClick = { onAction(action.kind) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsibleCategorySection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = CarbonColors.Secondary,
            )
            Text(
                text = if (expanded) "▾" else "▸",
                fontSize = 10.sp,
                color = CarbonColors.Outline,
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = { content() })
        }
    }
}

@Composable
private fun EasyActionButton(
    label: String,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (destructive) CarbonColors.Error.copy(alpha = 0.5f) else CarbonColors.OutlineVariant
    Text(
        text = label,
        fontSize = 12.sp,
        color = if (destructive) CarbonColors.Error else CarbonColors.OnSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(CarbonColors.SurfaceContainerLow, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun easyActionCategoryLabel(category: EasyActionCategory): String = when (category) {
    EasyActionCategory.SYSTEM -> stringResource(Res.string.easy_action_category_system)
    EasyActionCategory.DISPLAY -> stringResource(Res.string.easy_action_category_display)
    EasyActionCategory.APP_CONTROL -> stringResource(Res.string.easy_action_category_app)
    EasyActionCategory.DEVELOPER -> stringResource(Res.string.easy_action_category_developer)
    EasyActionCategory.DEVICE_HARDWARE -> stringResource(Res.string.easy_action_category_device_hardware)
    EasyActionCategory.SYSTEM_SOFTWARE -> stringResource(Res.string.easy_action_category_system_software)
    EasyActionCategory.STORAGE_MEMORY -> stringResource(Res.string.easy_action_category_storage_memory)
}

@Composable
private fun easyActionToggleLabel(kind: EasyActionKind, enabled: Boolean): String = when (kind) {
    EasyActionKind.TOGGLE_SHOW_REFRESH_RATE -> if (enabled) {
        stringResource(Res.string.easy_action_dev_refresh_rate_off)
    } else {
        stringResource(Res.string.easy_action_dev_refresh_rate_on)
    }
    EasyActionKind.TOGGLE_POINTER_LOCATION -> if (enabled) {
        stringResource(Res.string.easy_action_dev_pointer_location_off)
    } else {
        stringResource(Res.string.easy_action_dev_pointer_location_on)
    }
    EasyActionKind.TOGGLE_SHOW_SURFACE_UPDATES -> if (enabled) {
        stringResource(Res.string.easy_action_dev_surface_updates_off)
    } else {
        stringResource(Res.string.easy_action_dev_surface_updates_on)
    }
    EasyActionKind.TOGGLE_SHOW_VIEW_UPDATES -> if (enabled) {
        stringResource(Res.string.easy_action_dev_view_updates_off)
    } else {
        stringResource(Res.string.easy_action_dev_view_updates_on)
    }
    EasyActionKind.TOGGLE_GPU_OVERDRAW -> if (enabled) {
        stringResource(Res.string.easy_action_dev_gpu_overdraw_off)
    } else {
        stringResource(Res.string.easy_action_dev_gpu_overdraw_on)
    }
    EasyActionKind.TOGGLE_STRICT_MODE -> if (enabled) {
        stringResource(Res.string.easy_action_dev_strict_mode_off)
    } else {
        stringResource(Res.string.easy_action_dev_strict_mode_on)
    }
    EasyActionKind.TOGGLE_GPU_PROFILE_BARS -> if (enabled) {
        stringResource(Res.string.easy_action_dev_gpu_profile_off)
    } else {
        stringResource(Res.string.easy_action_dev_gpu_profile_on)
    }
    EasyActionKind.TOGGLE_DONT_KEEP_ACTIVITIES -> if (enabled) {
        stringResource(Res.string.easy_action_dev_dont_keep_activities_off)
    } else {
        stringResource(Res.string.easy_action_dev_dont_keep_activities_on)
    }
    EasyActionKind.TOGGLE_SHOW_LAYOUT_BOUNDS -> if (enabled) {
        stringResource(Res.string.easy_action_dev_layout_bounds_off)
    } else {
        stringResource(Res.string.easy_action_dev_layout_bounds_on)
    }
    else -> easyActionLabel(kind)
}

@Composable
private fun easyActionLabel(kind: EasyActionKind): String = when (kind) {
    EasyActionKind.REBOOT -> stringResource(Res.string.easy_action_reboot)
    EasyActionKind.RECOVERY_MODE -> stringResource(Res.string.easy_action_recovery)
    EasyActionKind.CLEAR_APP_CACHE -> stringResource(Res.string.easy_action_clear_cache)
    EasyActionKind.TAKE_SCREENSHOT -> stringResource(Res.string.easy_action_screenshot)
    EasyActionKind.TAKE_SCREENSHOT_TO_CLIPBOARD -> stringResource(Res.string.easy_action_screenshot_clipboard)
    EasyActionKind.SCREEN_RECORD -> stringResource(Res.string.easy_action_screen_record)
    EasyActionKind.FORCE_STOP_APP -> stringResource(Res.string.easy_action_force_stop)
    EasyActionKind.CLEAR_APP_DATA -> stringResource(Res.string.easy_action_clear_data)
    EasyActionKind.INFO_DEVICE_MODEL -> stringResource(Res.string.easy_action_info_device_model)
    EasyActionKind.INFO_DEVICE_BRAND -> stringResource(Res.string.easy_action_info_device_brand)
    EasyActionKind.INFO_DEVICE_MANUFACTURER -> stringResource(Res.string.easy_action_info_device_manufacturer)
    EasyActionKind.INFO_HARDWARE_PLATFORM -> stringResource(Res.string.easy_action_info_hardware_platform)
    EasyActionKind.INFO_CPU_ABI -> stringResource(Res.string.easy_action_info_cpu_abi)
    EasyActionKind.INFO_SCREEN_SIZE -> stringResource(Res.string.easy_action_info_screen_size)
    EasyActionKind.INFO_SCREEN_DENSITY -> stringResource(Res.string.easy_action_info_screen_density)
    EasyActionKind.INFO_BATTERY_LEVEL -> stringResource(Res.string.easy_action_info_battery_level)
    EasyActionKind.INFO_BATTERY_STATUS -> stringResource(Res.string.easy_action_info_battery_status)
    EasyActionKind.INFO_ANDROID_VERSION -> stringResource(Res.string.easy_action_info_android_version)
    EasyActionKind.INFO_SDK_VERSION -> stringResource(Res.string.easy_action_info_sdk_version)
    EasyActionKind.INFO_SECURITY_PATCH -> stringResource(Res.string.easy_action_info_security_patch)
    EasyActionKind.INFO_BUILD_ID -> stringResource(Res.string.easy_action_info_build_id)
    EasyActionKind.INFO_KERNEL_VERSION -> stringResource(Res.string.easy_action_info_kernel_version)
    EasyActionKind.INFO_DATA_STORAGE -> stringResource(Res.string.easy_action_info_data_storage)
    EasyActionKind.INFO_TOTAL_MEMORY -> stringResource(Res.string.easy_action_info_total_memory)
    EasyActionKind.INFO_AVAILABLE_MEMORY -> stringResource(Res.string.easy_action_info_available_memory)
    EasyActionKind.TOGGLE_SHOW_REFRESH_RATE,
    EasyActionKind.TOGGLE_POINTER_LOCATION,
    EasyActionKind.TOGGLE_SHOW_SURFACE_UPDATES,
    EasyActionKind.TOGGLE_SHOW_VIEW_UPDATES,
    EasyActionKind.TOGGLE_GPU_OVERDRAW,
    EasyActionKind.TOGGLE_STRICT_MODE,
    EasyActionKind.TOGGLE_GPU_PROFILE_BARS,
    EasyActionKind.TOGGLE_DONT_KEEP_ACTIVITIES,
    EasyActionKind.TOGGLE_SHOW_LAYOUT_BOUNDS,
    -> error("Use easyActionToggleLabel for developer toggles")
}
