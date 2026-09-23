package `fun`.abbas.wps_adb.scene.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.data.scene.SceneImportResult
import `fun`.abbas.wps_adb.scene.SceneRuntimeHost
import `fun`.abbas.wps_adb.theme.CarbonColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File
import java.io.FilenameFilter

@Composable
fun SceneAssetImportPage(
    sceneId: String,
    runtimeHost: SceneRuntimeHost,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var filePath by remember { mutableStateOf("") }
    var assetName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonColors.SurfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Import 3D Asset",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CarbonColors.OnSurface,
                )
                Text(
                    text = "Select a .glb model to place into scene '$sceneId'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CarbonColors.Outline,
                )
            }

            // File selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = filePath,
                    onValueChange = {
                        filePath = it
                        errorMessage = null
                        if (assetName.isBlank() && it.isNotBlank()) {
                            assetName = File(it).nameWithoutExtension
                        }
                    },
                    label = { Text("Asset GLB File", fontSize = 11.sp) },
                    placeholder = { Text("Path to .glb file", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isImporting,
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = {
                            val activeFrame = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
                            val dialog = FileDialog(activeFrame, "Select Asset GLB File", FileDialog.LOAD)
                            dialog.filenameFilter = FilenameFilter { _, name -> name.endsWith(".glb", ignoreCase = true) }
                            dialog.isVisible = true
                            val file = dialog.file
                            val dir = dialog.directory
                            if (file != null && dir != null) {
                                val fullFile = File(dir, file)
                                filePath = fullFile.absolutePath
                                errorMessage = null
                                if (assetName.isBlank()) {
                                    assetName = fullFile.nameWithoutExtension
                                }
                            }
                        },
                        enabled = !isImporting,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("Browse File...", fontSize = 11.sp)
                    }
                }
            }

            // Asset name
            OutlinedTextField(
                value = assetName,
                onValueChange = {
                    assetName = it
                    errorMessage = null
                },
                label = { Text("Asset Name", fontSize = 11.sp) },
                placeholder = { Text("e.g. Phone Stand", fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isImporting,
                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                )
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isImporting,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        val file = File(filePath.trim())
                        if (!file.exists() || !file.isFile) {
                            errorMessage = "Please select a valid existing file"
                            return@Button
                        }
                        if (!file.name.endsWith(".glb", ignoreCase = true)) {
                            errorMessage = "Only .glb format is supported"
                            return@Button
                        }

                        isImporting = true
                        errorMessage = null

                        scope.launch(Dispatchers.IO) {
                            val result = runtimeHost.importAsset(
                                sceneId = sceneId,
                                sourceFile = file,
                                assetName = assetName.trim().ifBlank { null },
                            )
                            withContext(Dispatchers.Main) {
                                isImporting = false
                                when (result) {
                                    is SceneImportResult.Success -> {
                                        onComplete()
                                    }
                                    is SceneImportResult.Failure -> {
                                        errorMessage = result.message
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isImporting && filePath.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Importing...", fontSize = 12.sp)
                    } else {
                        Text("Import Asset", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
