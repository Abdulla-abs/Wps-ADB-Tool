# WpsAdbTool UI 原型迁移至 Kotlin Multiplatform 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 参照 `wpsAdbToolForWeb` React 原型，用 Compose Multiplatform 实现共享 UI，交付 **Android + Desktop 双端**应用。**不实现 Web 端，不实现 KmpHub 模块。**

**Architecture:** 在 `shared` 模块中实现 Carbon 主题 + Composable UI + ViewModel；通过 `AdbRepository` expect/actual 隔离 jvmMain / androidMain 平台实现；Desktop/Android 复用现有 app 模块。

**Tech Stack:** Kotlin 2.4.0、Compose Multiplatform 1.11.1、Material3、StateFlow + ViewModel、Gradle KTS

**范围外（明确排除）：**
- wasmJs / webApp / 浏览器目标
- KmpHub 代码浏览页及 Sidebar 中对应导航项

**设计规格:** [2026-06-09-web-to-kmp-design.md](../specs/2026-06-09-web-to-kmp-design.md)

---

## 文件结构总览

| 操作 | 路径 | 职责 |
|------|------|------|
| 新建 | `shared/src/commonMain/kotlin/.../theme/*` | Carbon 主题 |
| 新建 | `shared/src/commonMain/kotlin/.../model/*` | 数据模型 |
| 新建 | `shared/src/commonMain/kotlin/.../data/*` | Repository + Mock |
| 新建 | `shared/src/commonMain/kotlin/.../viewmodel/*` | ViewModel |
| 新建 | `shared/src/commonMain/kotlin/.../ui/**` | UI 组件（不含 KmpHub） |
| 修改 | `shared/src/commonMain/kotlin/.../App.kt` | 替换占位 UI |
| 新建 | `shared/src/jvmMain/kotlin/.../data/PlatformAdbRepository.kt` | Desktop actual |
| 新建 | `shared/src/androidMain/kotlin/.../data/PlatformAdbRepository.kt` | Android actual |
| 修改 | `desktopApp/src/main/kotlin/.../main.kt` | 窗口尺寸 + CarbonTheme |

---

## Phase 1: 领域模型与 Mock 数据层

### Task 1.1: 定义数据模型

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/model/Device.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/model/AdbLog.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/model/AppSettings.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/model/Enums.kt`

- [ ] **Step 1: 编写 Enums.kt**

```kotlin
package fun.abbas.wps_adb.model

enum class ConnectionType { WIFI, USB, EMULATOR }
enum class DeviceType { PHYSICAL, EMULATOR }
enum class DeviceStatus { ONLINE, OFFLINE, UNAUTHORIZED }
enum class LogLevel { V, D, I, W, E }
enum class FilterTab { ALL, PHYSICAL, EMULATORS }
enum class SortParam { NAME, SERIAL, BATTERY }
enum class NavTab { WALL, GROUPS, SETTINGS }  // 不含 KMP_CODE，不实现 KmpHub
enum class DeviceAction { REBOOT, DISCONNECT }
```

- [ ] **Step 2: 编写 Device.kt**

```kotlin
package fun.abbas.wps_adb.model

data class DeviceApp(
    val name: String,
    val packageName: String,
    val iconKey: String,
)

data class Device(
    val id: String,
    val name: String,
    val serial: String,
    val type: DeviceType,
    val connectionType: ConnectionType,
    val status: DeviceStatus,
    val androidVersion: String,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val storageUsed: String,
    val storageTotal: String,
    val storagePercent: Int,
    val screenshotUrl: String,
    val screenDescription: String,
    val apps: List<DeviceApp> = emptyList(),
    val currentAppIndex: Int = 0,
    val activityLog: List<String> = emptyList(),
)
```

- [ ] **Step 3: 编写 AdbLog.kt 和 AppSettings.kt**

```kotlin
// AdbLog.kt
package fun.abbas.wps_adb.model

data class AdbLog(
    val id: String,
    val timestamp: String,
    val tag: String,
    val level: LogLevel,
    val message: String,
    val deviceId: String? = null,
)
```

```kotlin
// AppSettings.kt
package fun.abbas.wps_adb.model

data class AppSettings(
    val adbPath: String = "/usr/local/bin/adb",
    val minPort: Int = 5555,
    val maxPort: Int = 5585,
    val scanIntervalSec: Int = 15,
    val parallelThreads: Int = 4,
    val logRetention: Int = 2500,
    val autoApproveKey: Boolean = true,
    val diagnosticTelemetry: Boolean = false,
)
```

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/model/
git commit -m "feat: add domain models migrated from web types.ts"
```

### Task 1.2: AdbRepository 接口与 Mock 实现

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/AdbRepository.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/MockData.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/MockAdbRepository.kt`
- Test: `shared/src/commonTest/kotlin/fun/abbas/wps_adb/MockAdbRepositoryTest.kt`

- [ ] **Step 1: 编写 failing test**

```kotlin
package fun.abbas.wps_adb

import fun.abbas.wps_adb.data.MockAdbRepository
import fun.abbas.wps_adb.model.DeviceStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockAdbRepositoryTest {
    @Test
    fun observeDevices_returnsInitialOnlineDevices() = runTest {
        val repo = MockAdbRepository()
        val devices = repo.observeDevices().value
        assertTrue(devices.isNotEmpty())
        assertTrue(devices.any { it.status == DeviceStatus.ONLINE })
    }

    @Test
    fun rebootDevice_marksOfflineThenOnline() = runTest {
        val repo = MockAdbRepository()
        val deviceId = repo.observeDevices().value.first { it.status == DeviceStatus.ONLINE }.id
        repo.rebootDevice(deviceId)
        assertEquals(DeviceStatus.OFFLINE, repo.observeDevices().value.first { it.id == deviceId }.status)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :shared:cleanJvmTest :shared:jvmTest --tests "fun.abbas.wps_adb.MockAdbRepositoryTest"`
Expected: FAIL — class not found

- [ ] **Step 3: 编写 AdbRepository 接口**

```kotlin
package fun.abbas.wps_adb.data

import fun.abbas.wps_adb.model.*
import kotlinx.coroutines.flow.StateFlow

interface AdbRepository {
    val devices: StateFlow<List<Device>>
    val logs: StateFlow<List<AdbLog>>
    val isAdbActive: StateFlow<Boolean>
    val settings: StateFlow<AppSettings>

    suspend fun refreshDevices()
    suspend fun pairWirelessDevice(ip: String, port: Int): Result<Device>
    suspend fun rebootDevice(deviceId: String)
    suspend fun disconnectDevice(deviceId: String)
    suspend fun installApk(fileName: String)
    suspend fun killAdbServer()
    suspend fun restartAdbServer()
    fun addLog(level: LogLevel, tag: String, message: String, deviceId: String? = null)
    fun clearLogs()
    suspend fun saveSettings(settings: AppSettings)
    suspend fun runBatchAction(group: FilterTab, actionKey: String): List<String>
}
```

- [ ] **Step 4: 从 wpsAdbToolForWeb/src/data.ts 移植 MockData.kt**

将 `INITIAL_DEVICES` 和 `INITIAL_LOGS` 数组转为 Kotlin 列表。示例首条设备：

```kotlin
package fun.abbas.wps_adb.data

import fun.abbas.wps_adb.model.*

object MockData {
    val initialDevices = listOf(
        Device(
            id = "pixel6",
            name = "Pixel 6 - Test A",
            serial = "2201117PG",
            type = DeviceType.PHYSICAL,
            connectionType = ConnectionType.WIFI,
            status = DeviceStatus.ONLINE,
            androidVersion = "Android 13",
            batteryLevel = 85,
            isCharging = true,
            storageUsed = "108GB",
            storageTotal = "128GB",
            storagePercent = 85,
            screenshotUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuArxeJhmFHsT2MVqW446qyOJToROLQXO4X_AiTgP-v5qTgDbRfIroiXMqTC-dkOh0WNuBYOkKnD4XrXSzHeNvL86yqS8ftj-FMtrPL5zuK8BU-yAp8IybcWtTqRL9aEeBKBCKG-gvcG7DHilUVRkFCVkhJT5a2DzOa3bLXvnOXzQ3-WmEvok5s9UK6lkVHHODGYHqor_rEQ7xmH7lovV6pjohHrhDgMBjsAWshByn9l4cSvY9LkurlK9MwRtC1En4Y-50c2IWbVc0Fc",
            screenDescription = "Social Feed Client UI",
            apps = listOf(
                DeviceApp("PhotoFlow Feed", "com.android.photoflow", "photo_library"),
                DeviceApp("System Settings", "com.android.settings", "settings"),
            ),
            activityLog = listOf(
                "activity_manager: Starting activity: Intent { act=android.intent.action.MAIN }",
                "photoflow: Refreshing network feed image indices...",
            ),
        ),
        // ... 其余 4 台设备从 data.ts 逐条移植
    )

    val initialLogs = listOf(
        AdbLog("1", "10:14:19.402", "AdbDaemon", LogLevel.I, "ADB Server version 1.0.41 starting...", "system"),
        // ... 其余 7 条
    )
}
```

- [ ] **Step 5: 实现 MockAdbRepository.kt**

核心逻辑对应 `App.tsx` 中的 handler 函数：

```kotlin
package fun.abbas.wps_adb.data

import fun.abbas.wps_adb.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MockAdbRepository : AdbRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _devices = MutableStateFlow(MockData.initialDevices)
    override val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _logs = MutableStateFlow(MockData.initialLogs)
    override val logs: StateFlow<List<AdbLog>> = _logs.asStateFlow()

    private val _isAdbActive = MutableStateFlow(true)
    override val isAdbActive: StateFlow<Boolean> = _isAdbActive.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override suspend fun rebootDevice(deviceId: String) {
        val target = _devices.value.find { it.id == deviceId } ?: return
        addLog(LogLevel.W, "DeviceManager", "reboot instruction piped to target client: ${target.serial}", deviceId)
        _devices.update { list -> list.map { if (it.id == deviceId) it.copy(status = DeviceStatus.OFFLINE) else it } }
        scope.launch {
            delay(2500)
            _devices.update { list ->
                list.map { if (it.id == deviceId) it.copy(status = DeviceStatus.ONLINE, batteryLevel = minOf(100, it.batteryLevel + 2)) else it }
            }
            addLog(LogLevel.I, "DeviceManager", "Handshake restored. Target client initialized successfully: ${target.serial}", deviceId)
        }
    }

    override suspend fun disconnectDevice(deviceId: String) {
        val target = _devices.value.find { it.id == deviceId } ?: return
        addLog(LogLevel.E, "DeviceManager", "Forced disconnection socket drop on request: ${target.serial}", deviceId)
        _devices.update { list -> list.map { if (it.id == deviceId) it.copy(status = DeviceStatus.OFFLINE) else it } }
    }

    override fun addLog(level: LogLevel, tag: String, message: String, deviceId: String?) {
        val now = kotlinx.datetime.Clock.System.now()
        // 简化时间戳格式
        val ts = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}:${now.second.toString().padStart(2, '0')}.${now.nanosecondOfSecond / 1_000_000}"
        _logs.update { it + AdbLog("log_${System.currentTimeMillis()}", ts, tag, level, message, deviceId) }
    }

    // ... 其余方法：pairWirelessDevice, installApk, killAdbServer, restartAdbServer, saveSettings, runBatchAction
    // 逻辑直接对应 App.tsx handleXxx 函数
}
```

- [ ] **Step 6: 在 shared/build.gradle.kts commonMain 添加依赖**

```kotlin
implementation(libs.kotlinx.coroutines.core)  // 需在 libs.versions.toml 添加
```

在 `gradle/libs.versions.toml` 添加：

```toml
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version = "0.6.1" }
```

- [ ] **Step 7: 运行测试确认通过**

Run: `./gradlew :shared:jvmTest --tests "fun.abbas.wps_adb.MockAdbRepositoryTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add shared/ gradle/libs.versions.toml
git commit -m "feat: add AdbRepository interface and MockAdbRepository"
```

### Task 1.3: 平台 Repository 工厂

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/AdbRepositoryFactory.kt`
- Create: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/PlatformAdbRepository.kt`
- Create: `shared/src/androidMain/kotlin/fun/abbas/wps_adb/data/PlatformAdbRepository.kt`

- [ ] **Step 1: commonMain expect 声明**

```kotlin
// AdbRepositoryFactory.kt
package fun.abbas.wps_adb.data

expect fun createAdbRepository(): AdbRepository
```

- [ ] **Step 2: jvmMain / androidMain actual 实现（首版均返回 MockAdbRepository）**

```kotlin
// jvmMain 与 androidMain 各自:
package fun.abbas.wps_adb.data

actual fun createAdbRepository(): AdbRepository = MockAdbRepository()
```

- [ ] **Step 3: Commit**

```bash
git add shared/src/
git commit -m "feat: add expect/actual AdbRepository factory for all platforms"
```

---

## Phase 2: Carbon 主题

### Task 2.1: 实现 CarbonColorScheme 与 CarbonTheme

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/theme/CarbonColors.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/theme/CarbonTheme.kt`

- [ ] **Step 1: CarbonColors.kt — 从 index.css 精确映射**

```kotlin
package fun.abbas.wps_adb.theme

import androidx.compose.ui.graphics.Color

object CarbonColors {
    val Primary = Color(0xFF60F99E)
    val OnPrimary = Color(0xFF00391C)
    val PrimaryContainer = Color(0xFF3DDC84)
    val OnPrimaryContainer = Color(0xFF005C31)

    val Secondary = Color(0xFFADC6FF)
    val SecondaryContainer = Color(0xFF4B8EFF)

    val Error = Color(0xFFFFB4AB)
    val OnError = Color(0xFF690005)

    val Background = Color(0xFF111316)
    val OnBackground = Color(0xFFE2E2E6)
    val Surface = Color(0xFF111316)
    val OnSurface = Color(0xFFE2E2E6)
    val OnSurfaceVariant = Color(0xFFBBCBBC)

    val SurfaceContainerLowest = Color(0xFF0C0E11)
    val SurfaceContainerLow = Color(0xFF1A1C1F)
    val SurfaceContainer = Color(0xFF1E2023)
    val SurfaceContainerHigh = Color(0xFF282A2D)
    val SurfaceContainerHighest = Color(0xFF333538)

    val Outline = Color(0xFF869587)
    val OutlineVariant = Color(0xFF3C4A3F)
}
```

- [ ] **Step 2: CarbonTheme.kt**

```kotlin
package fun.abbas.wps_adb.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val carbonDarkScheme = darkColorScheme(
    primary = CarbonColors.Primary,
    onPrimary = CarbonColors.OnPrimary,
    primaryContainer = CarbonColors.PrimaryContainer,
    onPrimaryContainer = CarbonColors.OnPrimaryContainer,
    secondary = CarbonColors.Secondary,
    secondaryContainer = CarbonColors.SecondaryContainer,
    error = CarbonColors.Error,
    onError = CarbonColors.OnError,
    background = CarbonColors.Background,
    onBackground = CarbonColors.OnBackground,
    surface = CarbonColors.Surface,
    onSurface = CarbonColors.OnSurface,
    onSurfaceVariant = CarbonColors.OnSurfaceVariant,
    surfaceContainerLowest = CarbonColors.SurfaceContainerLowest,
    surfaceContainerLow = CarbonColors.SurfaceContainerLow,
    surfaceContainer = CarbonColors.SurfaceContainer,
    surfaceContainerHigh = CarbonColors.SurfaceContainerHigh,
    surfaceContainerHighest = CarbonColors.SurfaceContainerHighest,
    outline = CarbonColors.Outline,
    outlineVariant = CarbonColors.OutlineVariant,
)

val CarbonTypography = Typography(
    bodySmall = Typography().bodySmall.copy(fontFamily = FontFamily.SansSerif),
)

@Composable
fun CarbonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = carbonDarkScheme,
        typography = CarbonTypography,
        content = content,
    )
}
```

- [ ] **Step 3: 修改 App.kt 验证主题**

```kotlin
@Composable
fun App() {
    CarbonTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Text("WpsAdbTool", color = MaterialTheme.colorScheme.primary)
        }
    }
}
```

- [ ] **Step 4: Desktop / Android 启动验证**

Run: `./gradlew :desktopApp:run`
Run: `./gradlew :androidApp:assembleDebug`
Expected: 深黑背景 + 绿色标题文字

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/theme/ shared/src/commonMain/kotlin/fun/abbas/wps_adb/App.kt
git commit -m "feat: add Carbon dark theme from web prototype"
```

---

## Phase 3: 应用骨架 — AppShell 布局

### Task 3.1: 导航状态与 AppViewModel

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/viewmodel/AppViewModel.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/viewmodel/AppUiState.kt`

- [ ] **Step 1: AppUiState.kt**

```kotlin
package fun.abbas.wps_adb.viewmodel

import fun.abbas.wps_adb.model.*

data class AppUiState(
    val activeTab: NavTab = NavTab.WALL,
    val filterTab: FilterTab = FilterTab.ALL,
    val searchQuery: String = "",
    val sortParam: SortParam = SortParam.NAME,
    val isLogTrayOpen: Boolean = true,
    val isPairingDialogOpen: Boolean = false,
    val mirroredDevice: Device? = null,
    val isAdbActive: Boolean = true,
    val isRestartingAdb: Boolean = false,
)
```

- [ ] **Step 2: AppViewModel.kt — 聚合 AdbRepository 状态**

```kotlin
package fun.abbas.wps_adb.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fun.abbas.wps_adb.data.AdbRepository
import fun.abbas.wps_adb.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(private val repository: AdbRepository) : ViewModel() {
    private val _localState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = combine(
        _localState,
        repository.devices,
        repository.logs,
        repository.isAdbActive,
    ) { local, devices, logs, adbActive ->
        local.copy(isAdbActive = adbActive)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppUiState())

    val devices = repository.devices
    val logs = repository.logs

    fun setActiveTab(tab: NavTab) { _localState.update { it.copy(activeTab = tab) } }
    fun setFilterTab(tab: FilterTab) { _localState.update { it.copy(filterTab = tab) } }
    fun setSearchQuery(q: String) { _localState.update { it.copy(searchQuery = q) } }
    fun setSortParam(p: SortParam) { _localState.update { it.copy(sortParam = p) } }
    fun toggleLogTray() { _localState.update { it.copy(isLogTrayOpen = !it.isLogTrayOpen) } }
    fun openPairingDialog() { _localState.update { it.copy(isPairingDialogOpen = true) } }
    fun closePairingDialog() { _localState.update { it.copy(isPairingDialogOpen = false) } }
    fun setMirroredDevice(d: Device?) { _localState.update { it.copy(mirroredDevice = d) } }

    fun onDeviceAction(deviceId: String, action: DeviceAction) = viewModelScope.launch {
        when (action) {
            DeviceAction.REBOOT -> repository.rebootDevice(deviceId)
            DeviceAction.DISCONNECT -> repository.disconnectDevice(deviceId)
        }
    }

    fun killAdb() = viewModelScope.launch { repository.killAdbServer() }
    fun restartAdb() = viewModelScope.launch {
        _localState.update { it.copy(isRestartingAdb = true) }
        repository.restartAdbServer()
        _localState.update { it.copy(isRestartingAdb = false) }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/viewmodel/
git commit -m "feat: add AppViewModel with navigation and ADB state"
```

### Task 3.2: AppShell 布局组件

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/layout/AppShell.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/layout/Sidebar.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/layout/AppHeader.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/layout/StatusFooter.kt`
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/App.kt`

- [ ] **Step 1: Sidebar.kt — 对应 Sidebar.tsx 240px 固定宽度**

布局结构：
- Logo + 在线设备计数
- 导航项：**Device Wall / Groups / Settings**（共 3 项；Web 原型中的 KmpHub 页**不实现**）
- APK 安装区（Desktop 用 DragAndDrop；Android 用文件选择器）
- 底部帮助链接

关键尺寸：`Modifier.width(240.dp).fillMaxHeight()`

- [ ] **Step 2: AppHeader.kt — 搜索框 + 筛选 Tab + 排序 + Add Wireless 按钮**

对应 `App.tsx` 第 209-299 行 header 区域。

- [ ] **Step 3: StatusFooter.kt — ADB 状态 + Kill/Restart 按钮**

对应 `App.tsx` 第 363-404 行 footer，固定 `height(32.dp)`。

- [ ] **Step 4: AppShell.kt 组合**

```kotlin
@Composable
fun AppShell(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val devices by viewModel.devices.collectAsState()

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Sidebar(
            activeTab = uiState.activeTab,
            onTabChange = viewModel::setActiveTab,
            onlineCount = devices.count { it.status == DeviceStatus.ONLINE },
            onApkInstall = { viewModel.installApk(it) },
            modifier = Modifier.width(240.dp).fillMaxHeight(),
        )
        Column(Modifier.weight(1f).fillMaxHeight()) {
            AppHeader(/* ... */)
            Box(Modifier.weight(1f)) {
                when (uiState.activeTab) {
                    NavTab.WALL -> DeviceWallScreen(/* placeholder */)
                    NavTab.GROUPS -> GroupManagementScreen(/* placeholder */)
                    NavTab.SETTINGS -> SettingsScreen(/* placeholder */)
                }
            }
            StatusFooter(/* ... */)
        }
    }
}
```

- [ ] **Step 5: 更新 App.kt**

```kotlin
@Composable
fun App() {
    CarbonTheme {
        val viewModel = viewModel { AppViewModel(createAdbRepository()) }
        AppShell(viewModel)
    }
}
```

- [ ] **Step 6: Desktop / Android 验证导航切换**

Expected: 点击 Sidebar 三项（Wall / Groups / Settings）可切换内容区；**无 KmpHub 入口**

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/layout/ shared/src/commonMain/kotlin/fun/abbas/wps_adb/App.kt
git commit -m "feat: add AppShell layout with Sidebar, Header, Footer"
```

---

## Phase 4: Device Wall 设备墙

### Task 4.1: DeviceCard 组件

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/device/DeviceCard.kt`

- [ ] **Step 1: 实现 DeviceCard — 对应 DeviceGrid.tsx 单卡片部分**

包含：
- 设备名 + 序列号 + 状态指示灯（online 绿色 glow / offline 红色）
- 连接类型图标（Wifi / Usb / Monitor）
- 截图区域（AsyncImage 加载 screenshotUrl）
- 电池 + 存储信息
- Hover 操作按钮：Mirror / Terminal / Reboot / Disconnect

样式：`Card` + `border(1.dp, OutlineVariant)` + hover `shadow`

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/device/DeviceCard.kt
git commit -m "feat: add DeviceCard composable"
```

### Task 4.2: DeviceGrid 与 DeviceWallScreen

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/device/DeviceGrid.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/device/DeviceWallScreen.kt`

- [ ] **Step 1: DeviceGrid — 筛选 + LazyVerticalGrid**

```kotlin
@Composable
fun DeviceGrid(
    devices: List<Device>,
    filterTab: FilterTab,
    searchQuery: String,
    sortParam: SortParam,
    onMirror: (Device) -> Unit,
    onTerminal: (Device) -> Unit,
    onAction: (String, DeviceAction) -> Unit,
) {
    val filtered = devices
        .filter { device ->
            when (filterTab) {
                FilterTab.PHYSICAL -> device.type == DeviceType.PHYSICAL
                FilterTab.EMULATORS -> device.type == DeviceType.EMULATOR
                FilterTab.ALL -> true
            }
        }
        .filter { device ->
            val q = searchQuery.lowercase()
            device.name.lowercase().contains(q) ||
                device.serial.lowercase().contains(q) ||
                device.androidVersion.lowercase().contains(q)
        }
        .let { list ->
            when (sortParam) {
                SortParam.SERIAL -> list.sortedBy { it.serial }
                SortParam.BATTERY -> list.sortedByDescending { it.batteryLevel }
                SortParam.NAME -> list.sortedBy { it.name }
            }
        }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(filtered, key = { it.id }) { device ->
            DeviceCard(device, onMirror, onTerminal, onAction)
        }
    }
}
```

- [ ] **Step 2: DeviceWallScreen 包装标题 + DeviceGrid**

- [ ] **Step 3: 在 AppShell 中接入，Desktop / Android 验证 5 台 Mock 设备显示**

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/device/
git commit -m "feat: add DeviceWall with filter, search, and sort"
```

---

## Phase 5: 模态交互 — 配对与投屏

### Task 5.1: PairingDialog 三步向导

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/pairing/PairingDialog.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/pairing/PairingSteps.kt`

- [ ] **Step 1: 实现 3 步向导 UI — 对应 PairingModal.tsx**

Step 1: USB 连接 + 启用 TCP/IP 模拟开关  
Step 2: IP/Port 输入 + 自动扫描  
Step 3: 连接结果（loading / success / failure）

对话框尺寸：`640.dp × 480.dp`，左侧步骤指示器 `220.dp`

- [ ] **Step 2: 连接 MockAdbRepository.pairWirelessDevice**

- [ ] **Step 3: 在 AppShell 中当 isPairingDialogOpen 时显示**

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/pairing/
git commit -m "feat: add wireless ADB pairing dialog with 3-step wizard"
```

### Task 5.2: MirrorDrawer 投屏侧栏

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/mirror/MirrorDrawer.kt`

- [ ] **Step 1: 实现右侧滑出 Drawer — 对应 MirrorDrawer.tsx**

包含：
- 设备截图 + 应用切换 Tab
- 模拟 Shell 终端（输入框 + 输出区，getprop/pm list 等模拟回复）
- 亮度/WiFi/蓝牙/开发者选项滑块
- 底部操作：Rotate / Volume / PowerOff

宽度：`400.dp`，从右侧 AnimatedVisibility 滑入

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/mirror/
git commit -m "feat: add MirrorDrawer with shell terminal and device controls"
```

---

## Phase 6: 日志、分组、设置

### Task 6.1: TerminalLogsPanel

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/logs/TerminalLogsPanel.kt`

- [ ] **Step 1: 底部浮动 Logcat 面板 — 对应 TerminalLogs.tsx**

- 固定底部 `height(280.dp)`，Logcat 标题栏
- 级别过滤 Chips：ALL / V / D / I / W / E
- 搜索框 + 自动滚动开关
- LazyColumn 日志行，等宽字体，级别着色
- Clear / Export / Close 按钮

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/logs/
git commit -m "feat: add TerminalLogsPanel with level filtering"
```

### Task 6.2: GroupManagementScreen

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/groups/GroupManagementScreen.kt`

- [ ] **Step 1: 分组批量操作 — 对应 GroupManagement.tsx**

- 三个分组 Tab：All / Physical / Emulators
- 批量操作按钮：Install APK / Reboot / Logcat Dump / Clear Cache
- 进度条 + 控制台输出区
- 调用 `repository.runBatchAction()`

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/groups/
git commit -m "feat: add GroupManagementScreen with batch actions"
```

### Task 6.3: SettingsScreen

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: 设置表单 — 对应 SettingsPanel.tsx**

- ADB 路径、端口范围、扫描间隔、并行线程、日志保留
- 自动授权密钥、诊断遥测 Switch
- Save 按钮 + Snackbar 成功提示

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/settings/
git commit -m "feat: add SettingsScreen for ADB configuration"
```

---

## Phase 7: 收尾与部署

### Task 7.1: 实时日志模拟

**Files:**
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/MockAdbRepository.kt`

- [ ] **Step 1: 添加后台日志定时器 — 对应 App.tsx useEffect 第 72-92 行**

在 MockAdbRepository 初始化时启动 coroutine，每 7200ms 向随机在线设备写入 Debug 日志。

- [ ] **Step 2: Commit**

```bash
git commit -m "feat: add simulated live logcat feed in MockAdbRepository"
```

### Task 7.2: Desktop 窗口配置

**Files:**
- Modify: `desktopApp/src/main/kotlin/fun/abbas/wps_adb/main.kt`

- [ ] **Step 1: 设置窗口默认尺寸 1280×800**

```kotlin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "WpsAdbTool",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        App()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git commit -m "feat: set desktop window default size to 1280x800"
```

### Task 7.3: 归档 Web 原型参考

- [ ] **Step 1: 在 wpsAdbToolForWeb/README.md 顶部添加说明**

```markdown
> **UI 参考原型**: 此 React 项目仅作 Compose Multiplatform 双端实现的 UI 设计参考。
> 不构建 Web 端，不迁移 KmpHub 模块。
> 请参阅 `docs/superpowers/specs/2026-06-09-web-to-kmp-design.md`
```

- [ ] **Step 2: Commit**

```bash
git commit -m "docs: clarify wpsAdbToolForWeb as UI reference only"
```

---

## Phase 8（后续）: Desktop 真实 ADB 接入

> 此阶段不在首版范围内，但接口已预留。

### Task 8.1: JvmAdbRepository

**Files:**
- Create: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/JvmAdbRepository.kt`
- Modify: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/PlatformAdbRepository.kt`

- [ ] **Step 1: 用 ProcessBuilder 执行 `adb devices -l`**

- [ ] **Step 2: 解析输出映射为 Device 列表**

- [ ] **Step 3: 实现 install / reboot / connect 等命令**

- [ ] **Step 4: PlatformAdbRepository actual 改为返回 JvmAdbRepository**

---

## 依赖清单

需在 `gradle/libs.versions.toml` 新增：

```toml
[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version = "0.6.1" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version = "3.1.0" }
coil-network = { module = "io.coil-kt.coil3:coil-network-ktor3", version = "3.1.0" }
```

Coil 3 用于 DeviceCard 截图加载（Desktop / Android）。

需在 `shared/build.gradle.kts` commonMain 添加：

```kotlin
implementation(libs.kotlinx.coroutines.core)
implementation(libs.kotlinx.datetime)
implementation(libs.coil.compose)
```

---

## 验证清单

| 命令 | 预期 |
|------|------|
| `./gradlew :shared:jvmTest` | 全部 PASS |
| `./gradlew :desktopApp:run` | 桌面窗口 1280×800 完整 UI |
| `./gradlew :androidApp:assembleDebug` | APK 构建成功 |
| Sidebar 导航 | 仅 Wall / Groups / Settings，无 KmpHub |
| 项目结构 | 无 webApp 模块、无 wasmJs 配置 |

---

## 预估工时

| 阶段 | 任务数 | 预估 |
|------|--------|------|
| P1 数据层 | 3 | 1 天 |
| P2 主题 | 1 | 0.5 天 |
| P3 骨架布局 | 2 | 1 天 |
| P4 Device Wall | 2 | 1.5 天 |
| P5 模态交互 | 2 | 2 天 |
| P6 日志/分组/设置 | 3 | 1.5 天 |
| P7 收尾 | 3 | 0.5 天 |
| **合计** | **16** | **~8 天** |

---

## Self-Review

**Spec 覆盖:** 设计规格验收标准均有对应 Task。KmpHub 明确排除于范围外；NavTab 仅含 WALL/GROUPS/SETTINGS。无 wasmJs/webApp Task。ADB 平台策略在 Task 1.3 + Phase 8 覆盖。

**Placeholder 扫描:** 无 TBD/TODO。MockData 中"其余设备"需在 Task 1.2 Step 4 执行时从 data.ts 完整移植。

**类型一致性:** Device/AdbLog/NavTab 等枚举在 Task 1.1 定义，后续 Task 均引用同一 package。
