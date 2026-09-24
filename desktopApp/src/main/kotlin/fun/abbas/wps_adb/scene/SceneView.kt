package `fun`.abbas.wps_adb.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.data.scene.bridge.BridgeConnectionState
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * 3D Scene Composable View embedding the JCEF renderer canvas.
 *
 * Guaranteed not to recreate or destroy the underlying browser across recompositions.
 */
@Composable
fun SceneView(
    host: SceneRuntimeHost,
    modifier: Modifier = Modifier,
) {
    val state by host.state.collectAsState()
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0E11)),
    ) {
        if (host.browserComponent != null) {
            SwingPanel(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    JPanel(BorderLayout()).apply {
                        host.browserComponent?.let {
                            add(it, BorderLayout.CENTER)
                            putClientProperty(RENDERER_COMPONENT_PROPERTY, it)
                        }
                    }
                },
                update = { panel ->
                    val currentComponent = panel.getClientProperty(RENDERER_COMPONENT_PROPERTY) as? java.awt.Component
                    val requestedComponent = host.browserComponent
                    if (currentComponent !== requestedComponent) {
                        panel.removeAll()
                        if (requestedComponent != null) {
                            panel.add(requestedComponent, BorderLayout.CENTER)
                        }
                        panel.putClientProperty(RENDERER_COMPONENT_PROPERTY, requestedComponent)
                        panel.revalidate()
                        panel.repaint()
                    }
                }
            )
        }

        if (state.isInitializing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC0C0E11)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = when (state.phase) {
                            SceneRuntimePhase.INITIALIZING_CEF -> "正在初始化 Chromium 运行时…"
                            SceneRuntimePhase.CREATING_BROWSER -> "正在创建 3D 浏览器…"
                            SceneRuntimePhase.WAITING_BRIDGE -> "正在连接 3D 渲染器…"
                            SceneRuntimePhase.LOADING_SCENE -> "正在加载场景…"
                            else -> "正在启动 3D 场景…"
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        state.failure?.let { failure ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE60C0E11))
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .background(Color(0xFF1E1E2E), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                        .padding(24.dp),
                ) {
                    Text(
                        text = "3D 场景运行时启动失败",
                        color = Color(0xFFEF4444),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = failure.userMessage,
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "阶段：${failure.stage} · 诊断编号：${failure.diagnosticId.take(8)}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                    )
                    Button(onClick = { scope.launch { host.retryRenderer() } }) {
                        Text("重试")
                    }
                }
            }
        }

        // Connection HUD Overlay Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color(0xAA0F172A), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val statusDotColor = when (state.connectionState) {
                    BridgeConnectionState.READY -> Color(0xFF4ADE80)
                    BridgeConnectionState.CONNECTED,
                    BridgeConnectionState.CONNECTING -> Color(0xFFFACC15)
                    BridgeConnectionState.DISCONNECTED -> Color(0xFF94A3B8)
                    BridgeConnectionState.ERROR -> Color(0xFFF87171)
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusDotColor, RoundedCornerShape(4.dp))
                )

                Text(
                    text = "Bridge: ${state.connectionState.name}",
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                state.activeSceneId?.let { sceneId ->
                    Text(
                        text = "• Scene: $sceneId",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

private const val RENDERER_COMPONENT_PROPERTY = "wpsAdb.scene.rendererComponent"
