package `fun`.abbas.wps_adb.scene.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.scene.SceneRuntimeHost
import `fun`.abbas.wps_adb.theme.CarbonColors
import kotlinx.coroutines.launch

@Composable
fun SceneManagePage(
    runtimeHost: SceneRuntimeHost,
    onBack: () -> Unit,
    onNavigateToImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val repo = runtimeHost.sceneRepository
    val hostState by runtimeHost.state.collectAsState()

    var scenes by remember {
        mutableStateOf(
            try {
                repo?.listScenes() ?: emptyList()
            } catch (_: Throwable) {
                emptyList()
            }
        )
    }

    var sceneIdPendingDelete by remember { mutableStateOf<String?>(null) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }

    fun reloadScenes() {
        scenes = try {
            repo?.listScenes() ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
        runtimeHost.refreshScenes()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Quick Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${scenes.size} Scenes Available",
                fontSize = 12.sp,
                color = CarbonColors.Outline,
            )

            Button(
                onClick = onNavigateToImport,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("+ Import Scene", fontSize = 11.sp)
            }
        }

        if (deleteErrorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonColors.Error.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = deleteErrorMessage!!,
                    color = CarbonColors.Error,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        if (scenes.isEmpty()) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonColors.SurfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No scenes found",
                        color = CarbonColors.Outline,
                        fontSize = 13.sp,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                scenes.forEach { scene ->
                    val isActive = scene.id == hostState.activeSceneId
                    val isPendingDelete = sceneIdPendingDelete == scene.id

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) CarbonColors.SurfaceContainerHighest else CarbonColors.SurfaceContainer,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isActive) 1.dp else 0.dp,
                                color = if (isActive) CarbonColors.Primary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                            ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Scene info row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = scene.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = CarbonColors.OnSurface,
                                    )
                                    if (isActive) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(CarbonColors.Primary.copy(alpha = 0.2f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CarbonColors.Primary,
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "ID: ${scene.id}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = CarbonColors.Outline,
                                )
                                Text(
                                    text = "Assets: ${scene.assets.size}",
                                    fontSize = 10.sp,
                                    color = CarbonColors.Outline,
                                )
                                Text(
                                    text = "Bindings: ${scene.bindings.size}",
                                    fontSize = 10.sp,
                                    color = CarbonColors.Outline,
                                )
                            }

                            HorizontalDivider(color = CarbonColors.OutlineVariant.copy(alpha = 0.2f))

                            // Action or Inline Confirmation
                            if (isPendingDelete) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CarbonColors.Error.copy(alpha = 0.08f))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "Delete \"${scene.name}\"? Model files and bindings will also be removed.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFEF4444),
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                    ) {
                                        OutlinedButton(
                                            onClick = { sceneIdPendingDelete = null },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text("Cancel", fontSize = 11.sp)
                                        }

                                        Button(
                                            onClick = {
                                                val toDeleteId = scene.id
                                                sceneIdPendingDelete = null
                                                deleteErrorMessage = null
                                                scope.launch {
                                                    val success = runtimeHost.deleteScene(toDeleteId)
                                                    if (!success) {
                                                        deleteErrorMessage = "Failed to delete scene '$toDeleteId'"
                                                    }
                                                    reloadScenes()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text("Confirm Delete", fontSize = 11.sp)
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (!isActive) {
                                        FilledTonalButton(
                                            onClick = {
                                                scope.launch {
                                                    runtimeHost.selectScene(scene.id)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text("Activate", fontSize = 11.sp)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            sceneIdPendingDelete = scene.id
                                            deleteErrorMessage = null
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                    ) {
                                        Text("Delete", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Back to Overview Button
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text("Done", fontSize = 12.sp)
        }
    }
}
