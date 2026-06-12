package `fun`.abbas.wps_adb.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.FilterTab
import `fun`.abbas.wps_adb.model.NavTab
import `fun`.abbas.wps_adb.model.SortParam
import `fun`.abbas.wps_adb.theme.CarbonColors
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.*
import wpsadbtool.shared.generated.resources.Res

@Composable
fun AppHeader(
    activeTab: NavTab,
    filterTab: FilterTab,
    searchQuery: String,
    sortParam: SortParam,
    onFilterChange: (FilterTab) -> Unit,
    onSearchChange: (String) -> Unit,
    onSortChange: (SortParam) -> Unit,
    onRefresh: () -> Unit,
    onAddWireless: () -> Unit,
    endInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(CarbonColors.Surface)
            .border(width = 1.dp, color = CarbonColors.OutlineVariant.copy(alpha = 0.6f))
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(stringResource(Res.string.header_search_placeholder), fontSize = 12.sp, color = CarbonColors.Outline) },
                modifier = Modifier.width(280.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CarbonColors.Primary,
                    unfocusedBorderColor = CarbonColors.OutlineVariant,
                    focusedTextColor = CarbonColors.OnSurface,
                    unfocusedTextColor = CarbonColors.OnSurface,
                    cursorColor = CarbonColors.Primary,
                    focusedContainerColor = CarbonColors.SurfaceContainerLow,
                    unfocusedContainerColor = CarbonColors.SurfaceContainerLow,
                ),
                shape = RoundedCornerShape(50),
            )

            if (activeTab == NavTab.WALL) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilterChip(stringResource(Res.string.filter_all_devices), filterTab == FilterTab.ALL) { onFilterChange(FilterTab.ALL) }
                    FilterChip(stringResource(Res.string.filter_physical), filterTab == FilterTab.PHYSICAL) { onFilterChange(FilterTab.PHYSICAL) }
                    FilterChip(stringResource(Res.string.filter_emulators), filterTab == FilterTab.EMULATORS) { onFilterChange(FilterTab.EMULATORS) }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (activeTab == NavTab.WALL) {
                SortSelector(sortParam, onSortChange)
                Text(
                    "↻",
                    fontSize = 16.sp,
                    color = CarbonColors.Outline,
                    modifier = Modifier
                        .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(8.dp))
                        .clickable(onClick = onRefresh)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Button(
                onClick = onAddWireless,
                modifier = Modifier.padding(end = endInset),
                colors = ButtonDefaults.buttonColors(containerColor = CarbonColors.Primary, contentColor = CarbonColors.OnPrimary),
                shape = RoundedCornerShape(50),
            ) {
                Text(stringResource(Res.string.header_add_wireless), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) CarbonColors.Primary else CarbonColors.Outline,
        modifier = Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    )
}

@Composable
private fun SortSelector(sortParam: SortParam, onSortChange: (SortParam) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(1.dp, CarbonColors.OutlineVariant.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .background(CarbonColors.SurfaceContainerLow)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text("${stringResource(Res.string.sort_prefix)} ", fontSize = 10.sp, color = CarbonColors.Outline)
        listOf(
            SortParam.NAME to stringResource(Res.string.sort_name),
            SortParam.SERIAL to stringResource(Res.string.sort_serial),
            SortParam.BATTERY to stringResource(Res.string.sort_battery),
        ).forEach { (param, label) ->
            val interactionSource = remember { MutableInteractionSource() }
            Text(
                label,
                fontSize = 12.sp,
                color = if (sortParam == param) CarbonColors.Primary else CarbonColors.OnSurfaceVariant,
                fontWeight = if (sortParam == param) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clickable(interactionSource = interactionSource, indication = null) { onSortChange(param) },
            )
        }
    }
}
