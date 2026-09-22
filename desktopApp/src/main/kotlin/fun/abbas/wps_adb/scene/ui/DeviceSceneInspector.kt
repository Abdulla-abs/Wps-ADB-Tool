package `fun`.abbas.wps_adb.scene.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.scene.BindingStatus
import `fun`.abbas.wps_adb.model.scene.DeviceIdentityRef
import `fun`.abbas.wps_adb.scene.SceneRuntimeHost
import `fun`.abbas.wps_adb.theme.CarbonColors
import `fun`.abbas.wps_adb.viewmodel.AppViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSceneInspector(
    runtimeHost: SceneRuntimeHost,
    viewModel: AppViewModel,
    actions: SceneDeviceActions,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val runtimeController = runtimeHost.sceneRuntimeController
    val hostController = runtimeHost.hostController
    val sceneRepo = runtimeHost.sceneRepository

    val activeScene by (runtimeController?.activeScene ?: remember { MutableStateFlow(null) }).collectAsState()
    val resolvedState by (runtimeController?.resolvedState ?: remember { MutableStateFlow(null) }).collectAsState()
    val selectedObjectId by (runtimeController?.selectedObjectId ?: remember { MutableStateFlow(null) }).collectAsState()
    val runtimeCamera by (runtimeController?.runtimeCamera ?: remember { MutableStateFlow(null) }).collectAsState()

    val connectedDevices by viewModel.devices.collectAsState()

    val matchingBinding = resolvedState?.bindings?.find { it.binding.objectId == selectedObjectId }

    var saveCameraFeedback by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(CarbonColors.Surface)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- Header ---
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "3D Scene Inspector",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = CarbonColors.OnSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = activeScene?.name ?: "No Scene Loaded",
                    fontSize = 13.sp,
                    color = CarbonColors.Outline,
                )
                if (activeScene != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CarbonColors.SurfaceContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = activeScene!!.id,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = CarbonColors.OnSurfaceVariant,
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = CarbonColors.OutlineVariant.copy(alpha = 0.5f))

        // --- Camera Tools ---
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonColors.SurfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Camera",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CarbonColors.OnSurface,
                )

                if (runtimeCamera != null) {
                    Text(
                        text = "Pos: (%.1f, %.1f, %.1f) | FOV: %.0f°".format(
                            runtimeCamera!!.position.x,
                            runtimeCamera!!.position.y,
                            runtimeCamera!!.position.z,
                            runtimeCamera!!.fov,
                        ),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CarbonColors.Outline,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                hostController.resetCamera(activeScene?.camera)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Reset View", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val cam = runtimeCamera
                            val sId = activeScene?.id
                            if (cam != null && sId != null && sceneRepo != null) {
                                scope.launch {
                                    val updated = sceneRepo.saveCamera(sId, cam)
                                    runtimeController?.updateScene(updated)
                                    saveCameraFeedback = "View saved!"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Save View", fontSize = 12.sp)
                    }
                }

                if (saveCameraFeedback != null) {
                    Text(
                        text = saveCameraFeedback!!,
                        fontSize = 11.sp,
                        color = Color(0xFF10B981),
                    )
                }

                if (selectedObjectId != null) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                hostController.selectObject(selectedObjectId, focusCamera = true)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Focus Selected Object", fontSize = 12.sp)
                    }
                }
            }
        }

        // --- Selected Object & Binding Details ---
        if (selectedObjectId == null) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonColors.SurfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "No Object Selected",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = CarbonColors.OnSurface,
                    )
                    Text(
                        text = "Click any 3D device slot in the scene to inspect or manage its ADB binding.",
                        fontSize = 12.sp,
                        color = CarbonColors.Outline,
                    )
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = CarbonColors.SurfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Selected Slot",
                                fontSize = 11.sp,
                                color = CarbonColors.Outline,
                            )
                            Text(
                                text = selectedObjectId!!,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = CarbonColors.OnSurface,
                            )
                        }

                        val statusColor = when (matchingBinding?.status) {
                            BindingStatus.ONLINE -> Color(0xFF10B981)
                            BindingStatus.OFFLINE -> Color(0xFFEF4444)
                            else -> Color(0xFF64748B)
                        }
                        val statusText = matchingBinding?.status?.name ?: "UNBOUND"

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                            )
                        }
                    }

                    HorizontalDivider(color = CarbonColors.OutlineVariant.copy(alpha = 0.3f))

                    if (matchingBinding != null) {
                        // --- Bound Device Section ---
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Bound Identity",
                                fontSize = 11.sp,
                                color = CarbonColors.Outline,
                            )
                            Text(
                                text = matchingBinding.binding.deviceIdentity.value,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = CarbonColors.OnSurfaceVariant,
                            )
                        }

                        val boundDevice = matchingBinding.device
                        if (boundDevice != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Connected Device",
                                    fontSize = 11.sp,
                                    color = CarbonColors.Outline,
                                )
                                Text(
                                    text = boundDevice.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CarbonColors.OnSurface,
                                )
                                Text(
                                    text = "Serial: ${boundDevice.serial} (${boundDevice.connectionType})",
                                    fontSize = 11.sp,
                                    color = CarbonColors.Outline,
                                )
                            }

                            Text(
                                text = "Quick Actions",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CarbonColors.OnSurface,
                            )

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { actions.mirror(boundDevice) },
                                    modifier = Modifier.weight(1f),
                                    enabled = boundDevice.status == DeviceStatus.ONLINE,
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                ) {
                                    Text("Mirror", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { actions.terminal(boundDevice) },
                                    modifier = Modifier.weight(1f),
                                    enabled = boundDevice.status == DeviceStatus.ONLINE,
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                ) {
                                    Text("Terminal", fontSize = 11.sp)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { actions.logcat(boundDevice.id) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                ) {
                                    Text("Logcat", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { actions.installApk(boundDevice.id) },
                                    modifier = Modifier.weight(1f),
                                    enabled = boundDevice.status == DeviceStatus.ONLINE,
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                ) {
                                    Text("Install APK", fontSize = 11.sp)
                                }
                            }

                            if (boundDevice.status != DeviceStatus.ONLINE) {
                                OutlinedButton(
                                    onClick = { actions.reconnect(boundDevice.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                ) {
                                    Text("Reconnect Device", fontSize = 11.sp)
                                }
                            }
                        }

                        // Unbind button
                        OutlinedButton(
                            onClick = {
                                val sId = activeScene?.id
                                val objId = selectedObjectId
                                if (sId != null && objId != null && sceneRepo != null) {
                                    scope.launch {
                                        val updated = sceneRepo.unbindDevice(sId, objId)
                                        runtimeController?.updateScene(updated)
                                    }
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CarbonColors.Error),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        ) {
                            Text("Unbind Device", fontSize = 11.sp)
                        }

                        // Rebind selector
                        var rebindExpanded by remember { mutableStateOf(false) }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Rebind to another device:",
                                fontSize = 11.sp,
                                color = CarbonColors.Outline,
                            )
                            ExposedDropdownMenuBox(
                                expanded = rebindExpanded,
                                onExpandedChange = { rebindExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value = "Select device...",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rebindExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                                )
                                ExposedDropdownMenu(
                                    expanded = rebindExpanded,
                                    onDismissRequest = { rebindExpanded = false },
                                ) {
                                    connectedDevices.forEach { dev ->
                                        DropdownMenuItem(
                                            text = { Text("${dev.name} (${dev.serial})", fontSize = 12.sp) },
                                            onClick = {
                                                rebindExpanded = false
                                                val sId = activeScene?.id
                                                val objId = selectedObjectId
                                                if (sId != null && objId != null && sceneRepo != null) {
                                                    scope.launch {
                                                        val updated = sceneRepo.bindDevice(sId, objId, DeviceIdentityRef(dev.identity.value))
                                                        runtimeController?.updateScene(updated)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // --- Unbound Section ---
                        Text(
                            text = "This slot is not bound to any device identity.",
                            fontSize = 12.sp,
                            color = CarbonColors.Outline,
                        )

                        var selectedDeviceForBind by remember { mutableStateOf<Device?>(connectedDevices.firstOrNull()) }
                        var bindExpanded by remember { mutableStateOf(false) }

                        if (connectedDevices.isEmpty()) {
                            Text(
                                text = "No ADB devices currently connected.",
                                fontSize = 12.sp,
                                color = CarbonColors.OutlineVariant,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Select Device to Bind:",
                                    fontSize = 11.sp,
                                    color = CarbonColors.Outline,
                                )

                                ExposedDropdownMenuBox(
                                    expanded = bindExpanded,
                                    onExpandedChange = { bindExpanded = it },
                                ) {
                                    OutlinedTextField(
                                        value = selectedDeviceForBind?.let { "${it.name} (${it.serial})" } ?: "Select Device",
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bindExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                    )
                                    ExposedDropdownMenu(
                                        expanded = bindExpanded,
                                        onDismissRequest = { bindExpanded = false },
                                    ) {
                                        connectedDevices.forEach { dev ->
                                            DropdownMenuItem(
                                                text = { Text("${dev.name} (${dev.serial})", fontSize = 12.sp) },
                                                onClick = {
                                                    selectedDeviceForBind = dev
                                                    bindExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        val dev = selectedDeviceForBind
                                        val sId = activeScene?.id
                                        val objId = selectedObjectId
                                        if (dev != null && sId != null && objId != null && sceneRepo != null) {
                                            scope.launch {
                                                val updated = sceneRepo.bindDevice(sId, objId, DeviceIdentityRef(dev.identity.value))
                                                runtimeController?.updateScene(updated)
                                            }
                                        }
                                    },
                                    enabled = selectedDeviceForBind != null,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text("Bind Device to Slot", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
