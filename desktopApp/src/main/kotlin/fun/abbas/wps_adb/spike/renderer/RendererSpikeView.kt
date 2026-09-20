package `fun`.abbas.wps_adb.spike.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.awt.BorderLayout
import java.awt.Component
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JPanel
import javax.swing.SwingUtilities

data class SpikeLogEntry(
    val time: String,
    val direction: String,
    val type: String,
    val details: String
)

@Composable
fun RendererSpikeView(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var browserComponent by remember { mutableStateOf<Component?>(null) }
    var hostManager by remember { mutableStateOf<CefHostManager?>(null) }
    var isInitializing by remember { mutableStateOf(true) }
    var initError by remember { mutableStateOf<String?>(null) }

    // Bridge States
    var webglVendor by remember { mutableStateOf("Detecting...") }
    var webglRenderer by remember { mutableStateOf("Detecting...") }
    var selectedObjectId by remember { mutableStateOf("device_pixel_8") }
    var selectedObjectName by remember { mutableStateOf("Google Pixel 8 (USB)") }
    var selectedObjectStatus by remember { mutableStateOf("online") }
    var hoveredObjectName by remember { mutableStateOf("None") }
    var isRotating by remember { mutableStateOf(true) }
    var recreationCount by remember { mutableStateOf(0) }

    val logs = remember { mutableStateListOf<SpikeLogEntry>() }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    fun addLog(direction: String, type: String, details: String) {
        val entry = SpikeLogEntry(
            time = timeFormat.format(Date()),
            direction = direction,
            type = type,
            details = details
        )
        logs.add(0, entry)
        if (logs.size > 200) {
            logs.removeAt(logs.lastIndex)
        }
    }

    // Message handler from JS -> Kotlin
    fun handleJsMessage(jsonStr: String) {
        try {
            val obj = JSONObject(jsonStr)
            val type = obj.optString("type", "UNKNOWN")
            val payload = obj.optJSONObject("payload") ?: JSONObject()

            when (type) {
                "SCENE_READY" -> {
                    addLog("JS → KT", type, "Scene loaded. Initial objects: ${payload.optJSONArray("initialObjects")?.length() ?: 0}")
                    val webgl = payload.optJSONObject("webgl")
                    if (webgl != null) {
                        webglVendor = webgl.optString("vendor", "N/A")
                        webglRenderer = webgl.optString("renderer", "N/A")
                    }
                }
                "OBJECT_SELECTED" -> {
                    val id = payload.optString("id")
                    val name = payload.optString("name")
                    val status = payload.optString("status")
                    selectedObjectId = id
                    selectedObjectName = name
                    selectedObjectStatus = status
                    addLog("JS → KT", type, "ID: $id | Name: $name | Status: $status")
                }
                "OBJECT_HOVERED" -> {
                    val name = payload.optString("name")
                    hoveredObjectName = name
                    addLog("JS → KT", type, "Hovered: $name")
                }
                "OBJECT_UNHOVERED" -> {
                    hoveredObjectName = "None"
                }
                "ROTATION_TOGGLED" -> {
                    isRotating = payload.optBoolean("isRotating")
                    addLog("JS → KT", type, "Rotating: $isRotating")
                }
                "COLOR_UPDATED" -> {
                    addLog("JS → KT", type, "ID: ${payload.optString("id")} -> Color: ${payload.optString("hexColor")}")
                }
                "STATUS_UPDATED" -> {
                    selectedObjectStatus = payload.optString("status")
                    addLog("JS → KT", type, "ID: ${payload.optString("id")} -> Status: $selectedObjectStatus")
                }
                "SCENE_RESIZED" -> {
                    addLog("JS → KT", type, "Viewport: ${payload.optInt("width")}x${payload.optInt("height")}")
                }
                "GLTF_LOADER_AVAILABLE" -> {
                    addLog("JS → KT", type, "GLTFLoader available: ${payload.optBoolean("available")}")
                }
                else -> {
                    addLog("JS → KT", type, payload.toString())
                }
            }
        } catch (e: Exception) {
            addLog("JS → KT", "PARSE_ERROR", e.message ?: jsonStr)
        }
    }

    // Initialize Host Manager on background thread to avoid blocking Compose UI
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val mgr = CefHostManager { msg ->
                    coroutineScope.launch(Dispatchers.Main) {
                        handleJsMessage(msg)
                    }
                }
                val comp = mgr.initializeBrowser()
                hostManager = mgr
                browserComponent = comp
                isInitializing = false
                addLog("SYSTEM", "HOST_INIT", "JCEF Chromium Host & Local HTTP Server initialized successfully")
            } catch (t: Throwable) {
                t.printStackTrace()
                initError = t.message ?: t.toString()
                isInitializing = false
                addLog("SYSTEM", "HOST_ERROR", "Initialization failed: ${t.message}")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            hostManager?.dispose()
            hostManager = null
        }
    }

    val panelContainer = remember { JPanel(BorderLayout()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // --- 1. Header Toolbar ---
        Surface(
            color = Color(0xFF1E293B),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Phase 0 / Three.js Renderer Host Spike",
                            color = Color(0xFFF8FAFC),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isInitializing) Color(0xFFF59E0B) else if (initError != null) Color(0xFFEF4444) else Color(0xFF10B981),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isInitializing) "INITIALIZING CEF" else if (initError != null) "ERROR" else "JCEF ACTIVE",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (onClose != null) {
                        Button(
                            onClick = onClose,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                        ) {
                            Text("Back to Wall", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Status info strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GPU: $webglRenderer ($webglVendor)",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Selected: $selectedObjectName ($selectedObjectId) [$selectedObjectStatus]",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Hovered: $hoveredObjectName",
                        color = Color(0xFFA7F3D0),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons testing Kotlin -> JS and lifecycle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            addLog("KT → JS", "TOGGLE_ROTATION", "Toggling scene rotation")
                            hostManager?.executeJavaScript("window.wpsRenderer.toggleRotation();")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (isRotating) "Pause Rotation" else "Resume Rotation", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val colors = listOf("#10b981", "#3b82f6", "#ef4444", "#f59e0b", "#8b5cf6", "#ec4899", "#06b6d4")
                            val randomColor = colors.random()
                            addLog("KT → JS", "CHANGE_COLOR", "Setting $selectedObjectId color to $randomColor")
                            hostManager?.executeJavaScript("window.wpsRenderer.setObjectColor('$selectedObjectId', '$randomColor');")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Randomize Color", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val newStatus = if (selectedObjectStatus == "online") "offline" else "online"
                            addLog("KT → JS", "SET_STATUS", "Setting $selectedObjectId status to $newStatus")
                            hostManager?.executeJavaScript("window.wpsRenderer.setDeviceStatus('$selectedObjectId', '$newStatus');")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Toggle Status", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            addLog("KT → JS", "RESET_CAMERA", "Resetting camera perspective")
                            hostManager?.executeJavaScript("window.wpsRenderer.resetCamera();")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Reset Camera", fontSize = 12.sp)
                    }

                    // Dispose & Recreate Test
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                addLog("SYSTEM", "RECREATE_START", "Disposing and recreating CefBrowser...")
                                withContext(Dispatchers.IO) {
                                    val newComp = hostManager?.recreateBrowser()
                                    withContext(Dispatchers.Main) {
                                        browserComponent = newComp
                                        recreationCount++
                                        addLog("SYSTEM", "RECREATE_DONE", "CefBrowser recreated successfully (#$recreationCount)")
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Recreate Renderer (#$recreationCount)", fontSize = 12.sp)
                    }
                }
            }
        }

        // --- 2. Center & Bottom Split ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Center Viewport: JCEF SwingPanel
            Box(
                modifier = Modifier
                    .weight(0.68f)
                    .fillMaxHeight()
                    .background(Color(0xFF0C0E11)),
                contentAlignment = Alignment.Center
            ) {
                if (isInitializing) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Initializing Chromium WebGL Runtime...", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        Text("(Downloading/extracting CEF native binaries on first run)", color = Color(0xFF64748B), fontSize = 11.sp)
                    }
                } else if (initError != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("Renderer Host Initialization Failed", color = Color(0xFFEF4444), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(initError ?: "", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                    }
                } else if (browserComponent != null) {
                    key(recreationCount) {
                        SwingPanel(
                            factory = {
                                panelContainer.removeAll()
                                panelContainer.add(browserComponent!!, BorderLayout.CENTER)
                                panelContainer.revalidate()
                                panelContainer.repaint()
                                panelContainer
                            },
                            update = { panel ->
                                SwingUtilities.invokeLater {
                                    panel.revalidate()
                                    panel.repaint()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Right: Real-time IPC Log Panel
            Column(
                modifier = Modifier
                    .weight(0.32f)
                    .fillMaxHeight()
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF1E293B))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bridge IPC Events (${logs.size})",
                        color = Color(0xFFF1F5F9),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { logs.clear() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Clear", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0F1D), RoundedCornerShape(6.dp))
                        .padding(6.dp)
                ) {
                    items(logs) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = entry.time,
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val tagColor = when (entry.direction) {
                                    "JS → KT" -> Color(0xFF38BDF8)
                                    "KT → JS" -> Color(0xFFA78BFA)
                                    else -> Color(0xFFF59E0B)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(tagColor.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = entry.direction,
                                        color = tagColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = entry.type,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = entry.details,
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                            )
                            HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
