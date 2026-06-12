package `fun`.abbas.wps_adb.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.DefaultEasyActions
import `fun`.abbas.wps_adb.model.EasyActionCategory
import `fun`.abbas.wps_adb.model.EasyActionDefinition
import `fun`.abbas.wps_adb.model.EasyActionKind
import `fun`.abbas.wps_adb.theme.CarbonColors
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.easy_action_category_app
import wpsadbtool.shared.generated.resources.easy_action_category_display
import wpsadbtool.shared.generated.resources.easy_action_category_system
import wpsadbtool.shared.generated.resources.easy_action_clear_cache
import wpsadbtool.shared.generated.resources.easy_action_clear_data
import wpsadbtool.shared.generated.resources.easy_action_force_stop
import wpsadbtool.shared.generated.resources.easy_action_reboot
import wpsadbtool.shared.generated.resources.easy_action_recovery
import wpsadbtool.shared.generated.resources.easy_action_screen_record
import wpsadbtool.shared.generated.resources.easy_action_screen_record_stop
import wpsadbtool.shared.generated.resources.easy_action_screenshot
import wpsadbtool.shared.generated.resources.shell_easy_actions_title

@Composable
fun EasyActionsPanel(
    actions: List<EasyActionDefinition>,
    isScreenRecording: Boolean,
    onAction: (EasyActionKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(CarbonColors.SurfaceContainer)
            .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(topStart = 8.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
            Text(
                text = easyActionCategoryLabel(category),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = CarbonColors.Secondary,
            )
            categoryActions.forEach { action ->
                val label = when (action.kind) {
                    EasyActionKind.SCREEN_RECORD if isScreenRecording ->
                        stringResource(Res.string.easy_action_screen_record_stop)
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
}

@Composable
private fun easyActionLabel(kind: EasyActionKind): String = when (kind) {
    EasyActionKind.REBOOT -> stringResource(Res.string.easy_action_reboot)
    EasyActionKind.RECOVERY_MODE -> stringResource(Res.string.easy_action_recovery)
    EasyActionKind.CLEAR_APP_CACHE -> stringResource(Res.string.easy_action_clear_cache)
    EasyActionKind.TAKE_SCREENSHOT -> stringResource(Res.string.easy_action_screenshot)
    EasyActionKind.SCREEN_RECORD -> stringResource(Res.string.easy_action_screen_record)
    EasyActionKind.FORCE_STOP_APP -> stringResource(Res.string.easy_action_force_stop)
    EasyActionKind.CLEAR_APP_DATA -> stringResource(Res.string.easy_action_clear_data)
}
