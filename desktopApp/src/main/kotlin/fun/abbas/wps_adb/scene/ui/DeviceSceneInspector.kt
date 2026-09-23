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
import `fun`.abbas.wps_adb.model.scene.SceneAssetInstance
import `fun`.abbas.wps_adb.model.scene.SceneInteractionMode
import `fun`.abbas.wps_adb.model.scene.SceneTransform
import `fun`.abbas.wps_adb.model.scene.SceneVector3
import `fun`.abbas.wps_adb.scene.SceneRuntimeHost
import `fun`.abbas.wps_adb.theme.CarbonColors
import `fun`.abbas.wps_adb.viewmodel.AppViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI

@Composable
fun DeviceSceneInspector(
    runtimeHost: SceneRuntimeHost,
    viewModel: AppViewModel,
    actions: SceneDeviceActions,
    currentPage: InspectorPage = InspectorPage.Overview,
    onNavigate: (InspectorPage) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val runtimeController = runtimeHost.sceneRuntimeController
    val hostController = runtimeHost.hostController

    val hostState by runtimeHost.state.collectAsState()
    val activeScene by (runtimeController?.activeScene ?: remember { MutableStateFlow(null) }).collectAsState()
    val resolvedState by (runtimeController?.resolvedState ?: remember { MutableStateFlow(null) }).collectAsState()
    val selectedObjectId by (runtimeController?.selectedObjectId ?: remember { MutableStateFlow(null) }).collectAsState()
    val runtimeCamera by (runtimeController?.runtimeCamera ?: remember { MutableStateFlow(null) }).collectAsState()

    val connectedDevices by viewModel.devices.collectAsState()
    val matchingBinding = resolvedState?.bindings?.find { it.binding.objectId == selectedObjectId }
    val matchingAsset = activeScene?.assets?.find { it.id == selectedObjectId }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(CarbonColors.Surface)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- Header ---
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (currentPage != InspectorPage.Overview) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onNavigate(InspectorPage.Overview) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("← Back", fontSize = 12.sp)
                    }

                    Text(
                        text = when (currentPage) {
                            InspectorPage.ImportScene -> "Import Environment"
                            InspectorPage.ManageScenes -> "Manage Scenes"
                            InspectorPage.ImportAsset -> "Import Asset"
                            InspectorPage.Overview -> "3D Scene Inspector"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CarbonColors.OnSurface,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "3D Scene Inspector",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CarbonColors.OnSurface,
                    )

                    // Mode Indicator Badge
                    val (modeLabel, modeColor) = when (hostState.interactionMode) {
                        SceneInteractionMode.VIEW -> "VIEW" to Color(0xFF3B82F6)
                        SceneInteractionMode.BINDING -> "BINDING" to Color(0xFF10B981)
                        SceneInteractionMode.EDITING -> "EDITING" to Color(0xFFF59E0B)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(modeColor.copy(alpha = 0.15f))
                            .border(1.dp, modeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = modeLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = modeColor,
                        )
                    }
                }

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
        }

        HorizontalDivider(color = CarbonColors.OutlineVariant.copy(alpha = 0.5f))

        // --- Route Between Embedded Subpages or Overview ---
        when (currentPage) {
            InspectorPage.ImportScene -> {
                SceneImportPage(
                    runtimeHost = runtimeHost,
                    onComplete = { onNavigate(InspectorPage.Overview) },
                    onCancel = { onNavigate(InspectorPage.Overview) },
                )
            }
            InspectorPage.ManageScenes -> {
                SceneManagePage(
                    runtimeHost = runtimeHost,
                    onBack = { onNavigate(InspectorPage.Overview) },
                    onNavigateToImport = { onNavigate(InspectorPage.ImportScene) },
                )
            }
            InspectorPage.ImportAsset -> {
                val currentActiveScene = activeScene
                if (currentActiveScene != null) {
                    SceneAssetImportPage(
                        sceneId = currentActiveScene.id,
                        runtimeHost = runtimeHost,
                        onComplete = { onNavigate(InspectorPage.Overview) },
                        onCancel = { onNavigate(InspectorPage.Overview) },
                    )
                } else {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = CarbonColors.SurfaceContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Please load or select a scene before importing assets.",
                            fontSize = 12.sp,
                            color = CarbonColors.Outline,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            InspectorPage.Overview -> {
                // --- Scene Management Quick Card ---
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = CarbonColors.SurfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Scene Management",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CarbonColors.OnSurface,
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = { onNavigate(InspectorPage.ImportScene) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            ) {
                                Text("+ Import Scene", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { onNavigate(InspectorPage.ManageScenes) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            ) {
                                Text("Manage Scenes", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // --- Camera Tools (Auto-saved) ---
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
                            text = "Camera (Auto-saved)",
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

                            if (selectedObjectId != null) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            hostController.selectObject(selectedObjectId, focusCamera = true)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text("Focus Object", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // Mode-Specific Panels
                // ==========================================
                when (hostState.interactionMode) {
                    SceneInteractionMode.VIEW -> {
                        ViewModePanel(
                            selectedObjectId = selectedObjectId,
                            matchingBinding = matchingBinding,
                            actions = actions,
                        )
                    }
                    SceneInteractionMode.BINDING -> {
                        BindingModePanel(
                            activeSceneId = activeScene?.id,
                            selectedObjectId = selectedObjectId,
                            matchingBinding = matchingBinding,
                            connectedDevices = connectedDevices,
                            runtimeHost = runtimeHost,
                        )
                    }
                    SceneInteractionMode.EDITING -> {
                        EditingModePanel(
                            activeScene = activeScene,
                            selectedObjectId = selectedObjectId,
                            matchingAsset = matchingAsset,
                            runtimeHost = runtimeHost,
                            onOpenImportPage = { onNavigate(InspectorPage.ImportAsset) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * VIEW Mode: Device status inspection and quick actions (Mirror, Terminal, Logcat, etc.).
 */
@Composable
private fun ViewModePanel(
    selectedObjectId: String?,
    matchingBinding: `fun`.abbas.wps_adb.model.scene.ResolvedBinding?,
    actions: SceneDeviceActions,
) {
    if (selectedObjectId == null) {
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonColors.SurfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "No Device Selected",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = CarbonColors.OnSurface,
                )
                Text(
                    text = "Click any 3D device in the viewport to inspect its status or launch quick actions.",
                    fontSize = 12.sp,
                    color = CarbonColors.Outline,
                )
            }
        }
        return
    }

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
                    Text("Selected Slot", fontSize = 11.sp, color = CarbonColors.Outline)
                    Text(
                        text = selectedObjectId,
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = matchingBinding?.status?.name ?: "UNBOUND",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                }
            }

            HorizontalDivider(color = CarbonColors.OutlineVariant.copy(alpha = 0.3f))

            val boundDevice = matchingBinding?.device
            if (boundDevice != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Connected Device", fontSize = 11.sp, color = CarbonColors.Outline)
                    Text(boundDevice.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = CarbonColors.OnSurface)
                    Text("Serial: ${boundDevice.serial} (${boundDevice.connectionType})", fontSize = 11.sp, color = CarbonColors.Outline)
                }

                Text("Quick Actions", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CarbonColors.OnSurface)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            } else {
                Text(
                    text = "This slot is not currently bound to any active device. Switch to BINDING mode to assign a connected ADB device.",
                    fontSize = 12.sp,
                    color = CarbonColors.Outline,
                )
            }
        }
    }
}

/**
 * BINDING Mode: Slot to Device Identity mapping, binding, re-binding, and unbinding.
 * Completely embedded without floating DropdownMenu or Dialog popups.
 */
@Composable
private fun BindingModePanel(
    activeSceneId: String?,
    selectedObjectId: String?,
    matchingBinding: `fun`.abbas.wps_adb.model.scene.ResolvedBinding?,
    connectedDevices: List<Device>,
    runtimeHost: SceneRuntimeHost,
) {
    val scope = rememberCoroutineScope()
    val sceneRepo = runtimeHost.sceneRepository
    val runtimeController = runtimeHost.sceneRuntimeController

    if (selectedObjectId == null) {
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonColors.SurfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("No Slot Selected", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = CarbonColors.OnSurface)
                Text(
                    text = "Click any 3D device slot to view or configure its ADB binding.",
                    fontSize = 12.sp,
                    color = CarbonColors.Outline,
                )
            }
        }
        return
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonColors.SurfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Slot Binding: $selectedObjectId", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
            HorizontalDivider(color = CarbonColors.OutlineVariant.copy(alpha = 0.3f))

            if (matchingBinding != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Bound Identity Reference", fontSize = 11.sp, color = CarbonColors.Outline)
                    Text(
                        text = matchingBinding.binding.deviceIdentity.value,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CarbonColors.OnSurfaceVariant,
                    )
                }

                // Inline Unbind Confirmation
                var confirmingUnbind by remember(selectedObjectId) { mutableStateOf(false) }
                if (confirmingUnbind) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CarbonColors.Error.copy(alpha = 0.08f))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Unbind device from this slot?",
                            fontSize = 11.sp,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Medium,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                onClick = { confirmingUnbind = false },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text("Cancel", fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    confirmingUnbind = false
                                    val sId = activeSceneId
                                    val objId = selectedObjectId
                                    if (sId != null && sceneRepo != null) {
                                        scope.launch {
                                            val updated = sceneRepo.unbindDevice(sId, objId)
                                            runtimeController?.updateScene(updated)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text("Confirm Unbind", fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { confirmingUnbind = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CarbonColors.Error),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        Text("Unbind Device", fontSize = 11.sp)
                    }
                }

                HorizontalDivider(color = CarbonColors.OutlineVariant.copy(alpha = 0.2f))

                // Embedded Rebind Device Picker
                var selectedDeviceForRebind by remember(selectedObjectId) { mutableStateOf<Device?>(null) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Rebind to another connected device:", fontSize = 11.sp, color = CarbonColors.Outline)

                    if (connectedDevices.isEmpty()) {
                        Text("No ADB devices currently connected.", fontSize = 11.sp, color = CarbonColors.OutlineVariant)
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            connectedDevices.forEach { dev ->
                                DeviceSelectRow(
                                    device = dev,
                                    isSelected = dev.id == selectedDeviceForRebind?.id,
                                    onClick = { selectedDeviceForRebind = dev },
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val dev = selectedDeviceForRebind
                                val sId = activeSceneId
                                val objId = selectedObjectId
                                if (dev != null && sId != null && sceneRepo != null) {
                                    scope.launch {
                                        val updated = sceneRepo.bindDevice(sId, objId, DeviceIdentityRef(dev.identity.value))
                                        runtimeController?.updateScene(updated)
                                        selectedDeviceForRebind = null
                                    }
                                }
                            },
                            enabled = selectedDeviceForRebind != null,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("Rebind Device", fontSize = 11.sp)
                        }
                    }
                }
            } else {
                Text(
                    text = "This slot is currently unassigned.",
                    fontSize = 12.sp,
                    color = CarbonColors.Outline,
                )

                var selectedDeviceForBind by remember(selectedObjectId) { mutableStateOf(connectedDevices.firstOrNull()) }

                if (connectedDevices.isEmpty()) {
                    Text(
                        text = "No ADB devices currently connected.",
                        fontSize = 12.sp,
                        color = CarbonColors.OutlineVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Select Device to Bind:", fontSize = 11.sp, color = CarbonColors.Outline)

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            connectedDevices.forEach { dev ->
                                DeviceSelectRow(
                                    device = dev,
                                    isSelected = dev.id == selectedDeviceForBind?.id,
                                    onClick = { selectedDeviceForBind = dev },
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val dev = selectedDeviceForBind
                                val sId = activeSceneId
                                val objId = selectedObjectId
                                if (dev != null && sId != null && sceneRepo != null) {
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

/**
 * Inline device selection item for Binding mode.
 */
@Composable
private fun DeviceSelectRow(
    device: Device,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isSelected) CarbonColors.PrimaryContainer.copy(alpha = 0.35f) else CarbonColors.Surface
    val border = if (isSelected) CarbonColors.Primary else CarbonColors.OutlineVariant.copy(alpha = 0.4f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = device.name,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = CarbonColors.OnSurface,
            )
            Text(
                text = "${device.serial} (${device.connectionType})",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = CarbonColors.Outline,
            )
        }
        if (isSelected) {
            Text(
                text = "✓",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CarbonColors.Primary,
            )
        }
    }
}

/**
 * EDITING Mode: Asset list, + Import Asset, 3D Transform numeric editor, and Delete Asset.
 * Deletion confirmation is rendered inline without AlertDialog popups.
 */
@Composable
private fun EditingModePanel(
    activeScene: `fun`.abbas.wps_adb.model.scene.DeviceScene?,
    selectedObjectId: String?,
    matchingAsset: SceneAssetInstance?,
    runtimeHost: SceneRuntimeHost,
    onOpenImportPage: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val runtimeController = runtimeHost.sceneRuntimeController
    val hostController = runtimeHost.hostController
    val persistenceCoordinator = runtimeHost.persistenceCoordinator

    var assetIdPendingDelete by remember(selectedObjectId) { mutableStateOf<String?>(null) }

    // --- Asset List Header ---
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonColors.SurfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Scene Assets", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
                Button(
                    onClick = onOpenImportPage,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("+ Import Asset", fontSize = 11.sp)
                }
            }

            val assets = activeScene?.assets.orEmpty()
            if (assets.isEmpty()) {
                Text(
                    text = "No imported assets in this scene. Click '+ Import Asset' to place a .glb model.",
                    fontSize = 12.sp,
                    color = CarbonColors.Outline,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    assets.forEach { asset ->
                        val isSelected = asset.id == selectedObjectId
                        val bg = if (isSelected) CarbonColors.PrimaryContainer.copy(alpha = 0.4f) else CarbonColors.Surface
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg)
                                .clickable {
                                    runtimeController?.selectObject(asset.id)
                                    scope.launch { hostController.selectObject(asset.id) }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(asset.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = CarbonColors.OnSurface)
                                Text(asset.id, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = CarbonColors.Outline)
                            }
                            Text("📦", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // --- Transform Numeric Editor for Selected Asset ---
    if (matchingAsset != null) {
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
                        Text("Asset Transform", fontSize = 11.sp, color = CarbonColors.Outline)
                        Text(matchingAsset.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
                    }

                    if (assetIdPendingDelete != matchingAsset.id) {
                        OutlinedButton(
                            onClick = { assetIdPendingDelete = matchingAsset.id },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CarbonColors.Error),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("Delete", fontSize = 11.sp)
                        }
                    }
                }

                // Inline Delete Confirmation
                if (assetIdPendingDelete == matchingAsset.id) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CarbonColors.Error.copy(alpha = 0.08f))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Delete \"${matchingAsset.name}\" (${matchingAsset.id}) from scene?",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEF4444),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            OutlinedButton(
                                onClick = { assetIdPendingDelete = null },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text("Cancel", fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    val assetId = matchingAsset.id
                                    val sceneId = activeScene?.id
                                    assetIdPendingDelete = null
                                    if (sceneId != null) {
                                        scope.launch {
                                            runtimeHost.deleteAsset(sceneId, assetId)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text("Confirm Delete", fontSize = 11.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(color = CarbonColors.OutlineVariant.copy(alpha = 0.3f))

                // Transform Update Helper
                fun updateTransform(newTransform: SceneTransform) {
                    val sId = activeScene?.id ?: return
                    runtimeController?.updateRuntimeTransform(matchingAsset.id, newTransform)
                    scope.launch {
                        hostController.setObjectTransform(matchingAsset.id, newTransform)
                    }
                    persistenceCoordinator?.scheduleTransformSave(sId, matchingAsset.id, newTransform)
                }

                // Position X, Y, Z
                Vector3InputRow(
                    label = "Position (m)",
                    vector = matchingAsset.transform.position,
                    step = 0.1,
                    onVectorChanged = { newPos ->
                        updateTransform(matchingAsset.transform.copy(position = newPos))
                    }
                )

                // Rotation X, Y, Z (converted from radians to degrees for editing)
                val radToDeg = 180.0 / PI
                val degToRad = PI / 180.0
                val rotationDeg = SceneVector3(
                    x = matchingAsset.transform.rotation.x * radToDeg,
                    y = matchingAsset.transform.rotation.y * radToDeg,
                    z = matchingAsset.transform.rotation.z * radToDeg,
                )
                Vector3InputRow(
                    label = "Rotation (°)",
                    vector = rotationDeg,
                    step = 15.0,
                    onVectorChanged = { newRotDeg ->
                        val newRotRad = SceneVector3(
                            x = newRotDeg.x * degToRad,
                            y = newRotDeg.y * degToRad,
                            z = newRotDeg.z * degToRad,
                        )
                        updateTransform(matchingAsset.transform.copy(rotation = newRotRad))
                    }
                )

                // Scale X, Y, Z
                Vector3InputRow(
                    label = "Scale",
                    vector = matchingAsset.transform.scale,
                    step = 0.1,
                    onVectorChanged = { newScale ->
                        updateTransform(matchingAsset.transform.copy(scale = newScale))
                    }
                )
            }
        }
    } else if (selectedObjectId != null) {
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonColors.SurfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Fixed Object: $selectedObjectId", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
                Text(
                    text = "Environment model base and device slots are fixed and cannot be transformed. Only imported scene assets can be modified.",
                    fontSize = 12.sp,
                    color = CarbonColors.Outline,
                )
            }
        }
    }
}

/**
 * Compact numeric editor row for a 3D vector (X, Y, Z).
 */
@Composable
private fun Vector3InputRow(
    label: String,
    vector: SceneVector3,
    step: Double,
    onVectorChanged: (SceneVector3) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = CarbonColors.OnSurfaceVariant)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AxisNumericField(axis = "X", value = vector.x, modifier = Modifier.weight(1f)) {
                onVectorChanged(vector.copy(x = it))
            }
            AxisNumericField(axis = "Y", value = vector.y, modifier = Modifier.weight(1f)) {
                onVectorChanged(vector.copy(y = it))
            }
            AxisNumericField(axis = "Z", value = vector.z, modifier = Modifier.weight(1f)) {
                onVectorChanged(vector.copy(z = it))
            }
        }
    }
}

@Composable
private fun AxisNumericField(
    axis: String,
    value: Double,
    modifier: Modifier = Modifier,
    onValueCommit: (Double) -> Unit,
) {
    var text by remember(value) { mutableStateOf("%.2f".format(value)) }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toDoubleOrNull()?.let(onValueCommit)
        },
        prefix = {
            Text(
                text = axis,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = when (axis) {
                    "X" -> Color(0xFFEF4444)
                    "Y" -> Color(0xFF10B981)
                    else -> Color(0xFF3B82F6)
                },
            )
        },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
        modifier = modifier,
    )
}
