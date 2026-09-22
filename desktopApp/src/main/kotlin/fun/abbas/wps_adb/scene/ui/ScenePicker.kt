package `fun`.abbas.wps_adb.scene.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.scene.SceneOption
import `fun`.abbas.wps_adb.theme.CarbonColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenePicker(
    scenes: List<SceneOption>,
    selectedSceneId: String?,
    onSceneSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = scenes.firstOrNull { it.id == selectedSceneId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.name ?: "Select Scene",
            onValueChange = {},
            readOnly = true,
            label = { Text("Active Scene", fontSize = 11.sp) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .widthIn(min = 220.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CarbonColors.Primary,
                unfocusedBorderColor = CarbonColors.OutlineVariant,
                focusedTextColor = CarbonColors.OnSurface,
                unfocusedTextColor = CarbonColors.OnSurface,
            ),
            singleLine = true,
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            scenes.forEach { scene ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = scene.name,
                            fontWeight = if (scene.id == selectedSceneId) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSceneSelected(scene.id)
                    },
                )
            }
        }
    }
}

@Composable
fun SceneToolbar(
    scenes: List<SceneOption>,
    selectedSceneId: String?,
    onSceneSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CarbonColors.Surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "3D Device Scene",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CarbonColors.OnSurface,
                )
            }

            ScenePicker(
                scenes = scenes,
                selectedSceneId = selectedSceneId,
                onSceneSelected = onSceneSelected,
            )
        }
    }
}
