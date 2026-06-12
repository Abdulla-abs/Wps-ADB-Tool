# JediTerm 设备 Shell + 简易操作面板 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Desktop 端设备墙 Shell 打开 JediTerm 交互终端与 Easy Actions 快捷面板；非专业用户可一键运维，专业用户仍可手敲 adb shell 命令。

**Architecture:** Shell 为设备墙主内容子路由（`DeviceWallRoute.Shell`），经 `DeviceWallHost` 实现共享元素转场（降级横向滑动）；JediTerm 转场结束后挂载；Easy Actions 经 `AdbRepository` 执行结构化命令。

**Tech Stack:** Kotlin Multiplatform, Compose Desktop, Compose Animation (SharedTransitionLayout), JediTerm, Pty4J, SwingPanel interop

**Spec:** `docs/superpowers/specs/2026-06-12-jediterm-shell-design.md`

---

## File Map

| 文件 | 职责 |
|------|------|
| `model/DeviceWallRoute.kt` | 设备墙 Grid / Shell 路由 |
| `model/ShellTransitionKind.kt` | SHARED_ELEMENT / SLIDE |
| `ui/device/DeviceWallHost.kt` | 转场容器（SharedTransition + 降级 Slide） |
| `model/EasyActionKind.kt` | 快捷操作定义 |
| `model/TabListenKind.kt` | + `DEVICE_SHELL` |
| `data/DeviceShellService.kt` | 接口 + NoOp |
| `jvmMain/.../JvmDeviceShellService.kt` | JediTerm + Pty 实现 |
| `jvmMain/.../PlatformDeviceShellService.jvm.kt` | expect/actual 工厂 |
| `jvmMain/.../CarbonJediTermSettingsProvider.kt` | Carbon 深色主题 |
| `ui/device/DeviceShellScreen.kt` | Shell 主界面 + Easy Actions |
| `ui/device/JediTermPanel.jvm.kt` | SwingPanel 封装 |
| `ui/device/PackageNameDialog.kt` | 包名输入对话框 |
| `viewmodel/AppUiState.kt` | + `shellSession` |
| `viewmodel/AppViewModel.kt` | open/close shell、teardown |
| `data/AdbRepository.kt` | Easy Actions API |
| `jvmMain/.../JvmAdbRepository.kt` | 命令实现 |
| `ui/layout/AppShell.kt` | 接入 DeviceWallHost |
| `ui/device/DeviceWallScreen.kt` | 卡片 sharedElement key + Shell 按钮 |

---

## Phase SH1 — 模型与接口

### Task 1: 会话状态与 Easy Action 模型

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/model/DeviceShellSessionState.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/model/EasyActionKind.kt`
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/model/TabListenKind.kt`
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/viewmodel/AppUiState.kt`

- [ ] **Step 1: 添加 DeviceShellSessionState**

```kotlin
package `fun`.abbas.wps_adb.model

enum class DeviceShellSessionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR,
    UNAVAILABLE,
}
```

- [ ] **Step 2: 添加 EasyAction 模型**

```kotlin
package `fun`.abbas.wps_adb.model

enum class EasyActionCategory { SYSTEM, DISPLAY, APP_CONTROL }

enum class EasyActionKind {
    REBOOT,
    RECOVERY_MODE,
    CLEAR_APP_CACHE,
    TAKE_SCREENSHOT,
    SCREEN_RECORD,
    FORCE_STOP_APP,
    CLEAR_APP_DATA,
}

data class EasyActionDefinition(
    val kind: EasyActionKind,
    val category: EasyActionCategory,
    val requiresPackage: Boolean = false,
    val destructive: Boolean = false,
)

val DefaultEasyActions: List<EasyActionDefinition> = listOf(
    EasyActionDefinition(EasyActionKind.REBOOT, EasyActionCategory.SYSTEM, destructive = true),
    EasyActionDefinition(EasyActionKind.RECOVERY_MODE, EasyActionCategory.SYSTEM, destructive = true),
    EasyActionDefinition(EasyActionKind.TAKE_SCREENSHOT, EasyActionCategory.DISPLAY),
    EasyActionDefinition(EasyActionKind.SCREEN_RECORD, EasyActionCategory.DISPLAY),
    EasyActionDefinition(EasyActionKind.CLEAR_APP_CACHE, EasyActionCategory.APP_CONTROL, requiresPackage = true),
    EasyActionDefinition(EasyActionKind.FORCE_STOP_APP, EasyActionCategory.APP_CONTROL, requiresPackage = true),
    EasyActionDefinition(EasyActionKind.CLEAR_APP_DATA, EasyActionCategory.APP_CONTROL, requiresPackage = true, destructive = true),
)
```

- [ ] **Step 3: TabListenKind 增加 DEVICE_SHELL**

```kotlin
enum class TabListenKind {
    APP_LOGCAT,
    MIRROR_FRAME,
    SCRCPY_PROCESS,
    DEVICE_SHELL,
}
```

- [ ] **Step 4: AppUiState 增加路由与 shellSession**

```kotlin
sealed class DeviceWallRoute {
    data object Grid : DeviceWallRoute()
    data class Shell(val deviceId: String) : DeviceWallRoute()
}

enum class ShellTransitionKind { SHARED_ELEMENT, SLIDE }

data class DeviceShellSession(
    val deviceId: String,
    val sessionState: DeviceShellSessionState = DeviceShellSessionState.IDLE,
    val errorMessage: String? = null,
    val isScreenRecording: Boolean = false,
    val terminalSurfaceReady: Boolean = false,
)

// AppUiState:
val deviceWallRoute: DeviceWallRoute = DeviceWallRoute.Grid,
val shellSession: DeviceShellSession? = null,
val shellTransitionKind: ShellTransitionKind = ShellTransitionKind.SHARED_ELEMENT,
```

- [ ] **Step 5: SidePanelController 增加 shellSessionId**

在 `SidePanelController` object 中添加：

```kotlin
fun shellSessionId(deviceId: String): String = "shell_$deviceId"
```

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/model/ \
  shared/src/commonMain/kotlin/fun/abbas/wps_adb/viewmodel/AppUiState.kt \
  shared/src/commonMain/kotlin/fun/abbas/wps_adb/viewmodel/SidePanelController.kt
git commit -m "feat: add device shell session and easy action models"
```

---

### Task 2: DeviceShellService 接口

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/DeviceShellService.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/PlatformDeviceShellService.kt`
- Create: `shared/src/androidMain/kotlin/fun/abbas/wps_adb/data/PlatformDeviceShellService.android.kt`

- [ ] **Step 1: 定义接口**

```kotlin
package `fun`.abbas.wps_adb.data

data class DeviceShellStartResult(
    val success: Boolean,
    val message: String,
)

interface DeviceShellService {
    fun isAvailable(): Boolean
    fun start(sessionId: String, serial: String): DeviceShellStartResult
    fun stop(sessionId: String)
    fun stopAll()
    fun isRunning(sessionId: String): Boolean
    fun createTerminalComponent(sessionId: String): Any?
    fun setExitListener(listener: ((sessionId: String, exitCode: Int) -> Unit)?)
}

class NoOpDeviceShellService : DeviceShellService {
    override fun isAvailable(): Boolean = false
    override fun start(sessionId: String, serial: String) =
        DeviceShellStartResult(false, "Desktop + ADB required")
    override fun stop(sessionId: String) = Unit
    override fun stopAll() = Unit
    override fun isRunning(sessionId: String): Boolean = false
    override fun createTerminalComponent(sessionId: String): Any? = null
    override fun setExitListener(listener: ((sessionId: String, exitCode: Int) -> Unit)?) = Unit
}

expect fun createDeviceShellService(): DeviceShellService
```

- [ ] **Step 2: Android actual 返回 NoOp**

```kotlin
actual fun createDeviceShellService(): DeviceShellService = NoOpDeviceShellService()
```

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: add DeviceShellService interface and NoOp implementation"
```

---

## Phase SH2 — JediTerm JVM 实现

### Task 3: Gradle 依赖

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`

- [ ] **Step 1: libs.versions.toml 添加**

```toml
jediterm = "3.57"
pty4j = "0.13.2"
```

```toml
jediterm-core = { module = "org.jetbrains.jediterm:jediterm-core", version.ref = "jediterm" }
jediterm-pty = { module = "org.jetbrains.jediterm:jediterm-pty", version.ref = "jediterm" }
pty4j = { module = "org.jetbrains.pty4j:pty4j", version.ref = "pty4j" }
```

- [ ] **Step 2: jvmMain dependencies**

```kotlin
jvmMain.dependencies {
    // ...existing
    implementation(libs.jediterm.core)
    implementation(libs.jediterm.pty)
    implementation(libs.pty4j)
}
```

- [ ] **Step 3: 验证编译**

Run: `./gradlew :shared:compileKotlinJvm`
Expected: BUILD SUCCESSFUL（若 Maven 解析失败，改用 `3.42` 等稳定版）

- [ ] **Step 4: Commit**

```bash
git commit -m "build: add JediTerm and Pty4J dependencies for jvmMain"
```

---

### Task 4: CarbonJediTermSettingsProvider

**Files:**
- Create: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/ui/device/CarbonJediTermSettingsProvider.kt`

- [ ] **Step 1: 实现 SettingsProvider**

```kotlin
package `fun`.abbas.wps_adb.ui.device

import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import java.awt.Color
import java.awt.Font

class CarbonJediTermSettingsProvider : DefaultSettingsProvider() {
    override fun getDefaultForeground(): Color = Color(0xF4, 0xF4, 0xF4)
    override fun getDefaultBackground(): Color = Color(0x16, 0x16, 0x16)
    override fun getTerminalFont(): Font = Font(Font.MONOSPACED, Font.PLAIN, 13)
}
```

- [ ] **Step 2: Commit**

```bash
git commit -m "feat: add Carbon-themed JediTerm settings provider"
```

---

### Task 5: JvmDeviceShellService

**Files:**
- Create: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/JvmDeviceShellService.kt`
- Create: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/PlatformDeviceShellService.jvm.kt`
- Test: `shared/src/jvmTest/kotlin/fun/abbas/wps_adb/JvmDeviceShellServiceTest.kt`

- [ ] **Step 1: 写失败测试 — adb 命令构造**

```kotlin
@Test
fun buildShellCommand_includesSerialAndShellSubcommand() {
    val command = JvmDeviceShellService.buildShellCommand(
        adbPath = "/opt/adb",
        serial = "emulator-5554",
    )
    assertEquals(listOf("/opt/adb", "-s", "emulator-5554", "shell"), command)
}
```

- [ ] **Step 2: 运行测试确认 FAIL**

Run: `./gradlew :shared:jvmTest --tests "fun.abbas.wps_adb.JvmDeviceShellServiceTest"`
Expected: FAIL — class not found

- [ ] **Step 3: 实现 JvmDeviceShellService**

核心逻辑：

```kotlin
class JvmDeviceShellService(
    private val adbPathProvider: () -> String,
) : DeviceShellService {
    private val sessions = ConcurrentHashMap<String, ShellSession>()
    @Volatile private var exitListener: ((String, Int) -> Unit)? = null

    override fun start(sessionId: String, serial: String): DeviceShellStartResult {
        if (isRunning(sessionId)) return DeviceShellStartResult(true, "Already running")
        return try {
            val command = buildShellCommand(resolveAdbPath(adbPathProvider()), serial)
            val env = mapOf("ADB" to resolveAdbPath(adbPathProvider()))
            val process = PtyProcessBuilder()
                .setCommand(*command.toTypedArray())
                .setEnvironment(env)
                .setRedirectErrorStream(true)
                .start()
            val widget = JediTermWidget(CarbonJediTermSettingsProvider())
            val connector = PtyProcessTtyConnector(process, StandardCharsets.UTF_8)
            widget.setTtyConnector(connector)
            widget.start()
            sessions[sessionId] = ShellSession(process, widget)
            watchExit(sessionId, process)
            DeviceShellStartResult(true, "Shell started")
        } catch (e: Exception) {
            DeviceShellStartResult(false, e.message ?: "Failed to start shell")
        }
    }

    override fun createTerminalComponent(sessionId: String): Any? =
        sessions[sessionId]?.widget

    companion object {
        fun buildShellCommand(adbPath: String, serial: String): List<String> =
            listOf(adbPath, "-s", serial, "shell")
    }
}
```

- [ ] **Step 4: jvm actual 工厂**

```kotlin
actual fun createDeviceShellService(): DeviceShellService =
    JvmDeviceShellService(adbPathProvider = { /* 从 settings 注入 */ })
```

- [ ] **Step 5: 运行测试 PASS**

Run: `./gradlew :shared:jvmTest --tests "fun.abbas.wps_adb.JvmDeviceShellServiceTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: add JvmDeviceShellService with JediTerm PTY shell"
```

---

### Task 6: JediTermPanel Compose 封装

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/device/JediTermPanel.kt`
- Create: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/ui/device/JediTermPanel.jvm.kt`
- Create: `shared/src/androidMain/kotlin/fun/abbas/wps_adb/ui/device/JediTermPanel.android.kt`

- [ ] **Step 1: expect Composable**

```kotlin
@Composable
expect fun JediTermPanel(
    terminalComponent: Any?,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 2: jvm actual — SwingPanel**

```kotlin
@Composable
actual fun JediTermPanel(terminalComponent: Any?, modifier: Modifier) {
    if (terminalComponent is JComponent) {
        SwingPanel(
            factory = { terminalComponent },
            modifier = modifier.background(CarbonColors.SurfaceContainerLowest),
        )
    } else {
        Box(modifier = modifier.background(CarbonColors.SurfaceContainerLowest)) {
            Text("Terminal unavailable", color = CarbonColors.Outline)
        }
    }
}
```

- [ ] **Step 3: android actual — 占位**

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: add JediTermPanel SwingPanel wrapper for Compose Desktop"
```

---

## Phase SH3 — DeviceWallHost 转场

### Task 7: Gradle animation 依赖

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`

- [ ] **Step 1: commonMain 添加 compose animation**

```kotlin
commonMain.dependencies {
    implementation(libs.compose.animation)
}
```

`libs.versions.toml`:

```toml
compose-animation = { module = "org.jetbrains.compose.animation:animation", version.ref = "composeMultiplatform" }
```

- [ ] **Step 2: Commit**

```bash
git commit -m "build: add compose animation for shell shared element transition"
```

---

### Task 8: DeviceWallHost（SharedElement + Slide 降级）

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/device/DeviceWallHost.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/device/ShellSharedElementKeys.kt`
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/device/DeviceWallScreen.kt`
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/layout/AppShell.kt`

- [ ] **Step 1: Shared element key 工具**

```kotlin
object ShellSharedElementKeys {
    fun hero(deviceId: String) = "shell-hero-$deviceId"
    fun title(deviceId: String) = "shell-title-$deviceId"
}
```

- [ ] **Step 2: DeviceWallHost — 首选 SharedTransitionLayout**

```kotlin
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DeviceWallHost(
    route: DeviceWallRoute,
    transitionKind: ShellTransitionKind,
    devices: List<Device>,
    shellSession: DeviceShellSession?,
    /* ...callbacks... */
) {
    when (transitionKind) {
        ShellTransitionKind.SHARED_ELEMENT -> {
            SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    },
                    label = "deviceWallRoute",
                ) { target ->
                    when (target) {
                        DeviceWallRoute.Grid -> DeviceWallScreen(
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@AnimatedContent,
                            /* ... */
                        )
                        is DeviceWallRoute.Shell -> {
                            val device = devices.find { it.id == target.deviceId } ?: return@AnimatedContent
                            DeviceShellScreen(
                                device = device,
                                terminalSurfaceReady = shellSession?.terminalSurfaceReady == true,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedContent,
                                /* ... */
                            )
                        }
                    }
                }
            }
        }
        ShellTransitionKind.SLIDE -> {
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    if (targetState is DeviceWallRoute.Shell) {
                        slideInHorizontally { it } + fadeIn(tween(280)) togetherWith
                            slideOutHorizontally { -it / 3 } + fadeOut(tween(280))
                    } else {
                        slideInHorizontally { -it } + fadeIn(tween(280)) togetherWith
                            slideOutHorizontally { it } + fadeOut(tween(280))
                    }
                },
                label = "deviceWallRouteSlide",
            ) { /* same when branches without sharedElement modifiers */ }
        }
    }
}
```

- [ ] **Step 3: DeviceCard 添加 sharedElement（SHARED_ELEMENT 模式）**

在预览 Box 与设备名 Text 上：

```kotlin
Modifier.sharedElement(
    sharedTransitionScope.rememberSharedContentState(ShellSharedElementKeys.hero(device.id)),
    animatedVisibilityScope = animatedVisibilityScope,
)
```

- [ ] **Step 4: DeviceShellHeader 添加对应 sharedElement 目标**

- [ ] **Step 5: AppShell 用 DeviceWallHost 替换直接渲染 DeviceWallScreen**

- [ ] **Step 6: 转场完成回调**

`AppViewModel.markShellTerminalReady()` 在 `LaunchedEffect(route)` 转场结束 300ms 后调用，设置 `terminalSurfaceReady = true`。

- [ ] **Step 7: 平台降级检测**

`JvmShellTransitionCapabilities.preferredKind()`：Desktop 默认 `SHARED_ELEMENT`；若检测失败或 `AppSettings.preferSlideShellTransition` 则 `SLIDE`。

- [ ] **Step 8: Commit**

```bash
git commit -m "feat: add DeviceWallHost with shared element and slide fallback transitions"
```

---

## Phase SH4 — Shell UI

### Task 9: DeviceShellScreen + EasyActionsPanel

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/device/DeviceShellScreen.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/device/EasyActionsPanel.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/device/PackageNameDialog.kt`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`
- Modify: `shared/src/commonMain/composeResources/values-zh/strings.xml`

- [ ] **Step 1: EasyActionsPanel — 按 category 分组渲染 DefaultEasyActions**

每个按钮：`Icon + label`；destructive 用 `CarbonColors.Error` 边框；点击回调 `onAction(EasyActionKind)`。

- [ ] **Step 2: DeviceShellScreen 布局**

```kotlin
@Composable
fun DeviceShellScreen(
    device: Device,
    sessionState: DeviceShellSessionState,
    errorMessage: String?,
    terminalComponent: Any?,
    terminalSurfaceReady: Boolean,
    isScreenRecording: Boolean,
    onBack: () -> Unit,
    onOpenLogcat: () -> Unit,
    onEasyAction: (EasyActionKind) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Column(modifier) {
        DeviceShellHeader(/* sharedElement 目标 */)
        Row(Modifier.weight(1f)) {
            if (terminalSurfaceReady) {
                JediTermPanel(terminalComponent, Modifier.weight(1f).fillMaxHeight())
            } else {
                ShellTerminalPlaceholder(Modifier.weight(1f).fillMaxHeight())
            }
            EasyActionsPanel(/* ... */)
        }
    }
}
```

- [ ] **Step 3: i18n strings（中英）**

关键 key：`shell_breadcrumb_wall`、`shell_breadcrumb_shell`、`shell_header_session`、`shell_action_view_logcat`、`easy_action_reboot`、`easy_action_recovery`、`easy_action_clear_cache`、`easy_action_screenshot`、`easy_action_screen_record`、`easy_action_force_stop`、`easy_action_clear_data`、`shell_confirm_recovery_title` 等。

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: add DeviceShellScreen with Easy Actions panel UI"
```

---

### Task 10: AppHeader 面包屑

**Files:**
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/layout/AppHeader.kt`

- [ ] **Step 1: AppHeader 增加 shellBreadcrumb 参数**

当 `deviceWallRoute is DeviceWallRoute.Shell` 时显示：`设备墙 > {deviceName} > Shell`（设备墙可点击 → `onShellBack`）

- [ ] **Step 2: Commit**

```bash
git commit -m "feat: add shell breadcrumb to AppHeader"
```

---

## Phase SH5 — Repository Easy Actions

### Task 11: AdbRepository API

**Files:**
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/AdbRepository.kt`
- Modify: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/JvmAdbRepository.kt`
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/MockAdbRepository.kt`
- Test: `shared/src/jvmTest/kotlin/fun/abbas/wps_adb/JvmEasyActionsTest.kt`

- [ ] **Step 1: 接口方法**

```kotlin
suspend fun rebootToRecovery(deviceId: String): Boolean
suspend fun clearAppCache(deviceId: String, packageName: String): Boolean
suspend fun takeScreenshotToDownloads(deviceId: String): String?
suspend fun startScreenRecord(deviceId: String): Boolean
suspend fun stopScreenRecord(deviceId: String): String?
suspend fun forceStopApp(deviceId: String, packageName: String): Boolean
suspend fun clearAppData(deviceId: String, packageName: String): Boolean
```

- [ ] **Step 2: Jvm 实现 — clearAppCache 命令链**

```kotlin
suspend fun clearAppCache(deviceId: String, packageName: String): Boolean {
    val serial = deviceSerial(deviceId) ?: return false
    val primary = runner.run(
        listOf("shell", "cmd", "package", "clear-app-cache", "--user", "0", packageName),
        serial = serial,
    )
    if (primary.success) return true
    val fallback = runner.run(
        listOf("shell", "pm", "clear", "--cache-only", packageName),
        serial = serial,
    )
    if (fallback.success) return true
    addLog(LogLevel.W, "EasyAction", "clear-app-cache not supported on ${serial} for $packageName", deviceId)
    return false
}
```

- Recovery: `runner.run(listOf("reboot", "recovery"), serial)`
- Screenshot / Screen record / Force stop / Clear data：同 spec §5

- [ ] **Step 3: 单元测试 clearAppCache 命令构造**

```kotlin
@Test
fun clearAppCacheCommand_usesCmdPackageClearAppCache() {
    val cmd = JvmEasyActionCommands.clearAppCache("com.example.app")
    assertEquals(listOf("shell", "cmd", "package", "clear-app-cache", "--user", "0", "com.example.app"), cmd)
}
```

- [ ] **Step 4: Mock 实现 — addLog 模拟**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add easy action adb commands including per-app clearAppCache"
```

---

## Phase SH6 — ViewModel 集成

### Task 12: AppViewModel Shell 生命周期

**Files:**
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/viewmodel/AppViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/device/DeviceWallScreen.kt`（无需改签名，`onTerminal` 行为变）
- Test: `shared/src/jvmTest/kotlin/fun/abbas/wps_adb/AppViewModelDeviceShellTest.kt`
- Modify: `desktopApp/src/jvmMain/kotlin/.../App.kt`（注入 createDeviceShellService）

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun `onTerminalDevice sets shell route and session`() = runTest {
    val vm = AppViewModel(repo, deviceShellService = fakeShellService)
    vm.onTerminalDevice(device)
    assertTrue(vm.uiState.value.deviceWallRoute is DeviceWallRoute.Shell)
    assertNotNull(vm.uiState.value.shellSession)
    assertEquals(device.id, vm.uiState.value.shellSession?.deviceId)
}
```

- [ ] **Step 2: 重写 onTerminalDevice**

```kotlin
fun onTerminalDevice(device: Device) {
    val route = _localState.value.deviceWallRoute
    if (route is DeviceWallRoute.Shell && route.deviceId == device.id) return
    closeDeviceShell()
    val sessionId = SidePanelController.shellSessionId(device.id)
    _localState.update {
        it.copy(
            deviceWallRoute = DeviceWallRoute.Shell(device.id),
            shellSession = DeviceShellSession(device.id, DeviceShellSessionState.CONNECTING),
        )
    }
    // PTY 立即 start；terminalSurfaceReady 由 DeviceWallHost 转场完成后设置
    tabSessionManager.start(sessionId, TabListenKind.DEVICE_SHELL)
    val result = deviceShellService.start(sessionId, device.serial)
    updateShellSession { /* CONNECTED / ERROR */ }
}

fun closeDeviceShell() {
    val session = _localState.value.shellSession ?: return
    teardownDeviceShell(SidePanelController.shellSessionId(session.deviceId))
    _localState.update {
        it.copy(deviceWallRoute = DeviceWallRoute.Grid, shellSession = null)
    }
}

fun markShellTerminalReady() {
    updateShellSession { it.copy(terminalSurfaceReady = true) }
}
```

- [ ] **Step 3: runEasyAction — CLEAR_APP_CACHE**

```kotlin
EasyActionKind.CLEAR_APP_CACHE -> {
    val pkg = packageName ?: return@launch
    repository.clearAppCache(session.deviceId, pkg)
}
```

在 `killAdb`、`onCleared`、`disconnectDevice`、`setActiveTab`（离开 WALL 时）调用 `closeDeviceShell()`。

`teardownDeviceShell(sessionId)`:

```kotlin
deviceShellService.stop(sessionId)
tabSessionManager.stop(sessionId, TabListenKind.DEVICE_SHELL)
```

- [ ] **Step 4: runEasyAction 处理确认流**

```kotlin
fun runEasyAction(kind: EasyActionKind, packageName: String? = null) {
    val session = _localState.value.shellSession ?: return
    viewModelScope.launch {
        when (kind) {
            EasyActionKind.REBOOT -> repository.rebootDevice(session.deviceId)
            EasyActionKind.RECOVERY_MODE -> repository.rebootToRecovery(session.deviceId)
            // ...
        }
    }
}
```

- [ ] **Step 5: 移除 onTerminalDevice 中的 logcat 聚焦逻辑**

Logcat 聚焦改由 Shell 视图「查看 Logcat」链接触发：

```kotlin
fun openShellDeviceLogcat() {
    val deviceId = _localState.value.shellSession?.deviceId ?: return
    _localState.update { it.copy(isLogTrayOpen = true, logTrayMode = LogTrayMode.LOGCAT, logcatDeviceFilter = deviceId) }
    repository.startGlobalLogcat(deviceId)
}
```

- [ ] **Step 6: 更新 AppViewModelLogcatTest**

原 `onTerminalDevice opens tray logcat` 测试改为 `openShellDeviceLogcat` 或删除并合并到新测试。

- [ ] **Step 7: 运行全量 jvmTest**

Run: `./gradlew :shared:jvmTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git commit -m "feat: integrate device shell session in AppViewModel with teardown"
```

---

## Phase SH7 — 收尾

### Task 13: 文档与验收

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-06-11-remaining-work-backlog.md`
- Modify: `docs/superpowers/specs/2026-06-12-jediterm-shell-design.md`（状态 → 已实现）

- [ ] **Step 1: README 更新 Shell 描述**

- [ ] **Step 2: 手动验收清单**

1. 点击 Shell → 共享元素转场（卡片预览/标题飞向 Shell 头）；若降级则横向滑入
2. 转场结束后 JediTerm 出现，可输入 `getprop`
3. Clear Cache 输入包名后仅清缓存（数据保留）
4. 面包屑/Esc 返回 → 反向转场回设备墙
5. Reboot / Screenshot / 录屏 / Force Stop / Clear Data 正常
6. killAdb / 退出无遗留 shell 进程

- [ ] **Step 3: 编译验证**

Run: `./gradlew :shared:jvmTest :desktopApp:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git commit -m "docs: update README and backlog for JediTerm shell feature"
```

---

## 验收标准（摘自 Spec §12）

- [ ] 设备墙 Shell 经转场进入 Shell 视图（SharedElement 首选 / Slide 降级）
- [ ] 转场完成后 JediTerm 可交互
- [ ] Clear Cache 为单应用清缓存（非 trim-caches）
- [ ] Easy Actions 7 项可用且 destructive 有确认
- [ ] 面包屑/Esc 可返回设备墙
- [ ] 关闭 Shell / 设备断开 / killAdb / 退出无遗留 shell 进程
- [ ] Mock / Android 降级不崩溃
- [ ] `./gradlew :shared:jvmTest` 通过
