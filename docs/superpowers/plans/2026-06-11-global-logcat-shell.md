# 全局 Logcat 与 Shell 按钮 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Desktop 底部「Logcat 控制台」展示真实 `adb logcat` 流；设备墙「Shell」聚焦该设备 logcat。

**Architecture:** 新增独立 `logcatLogs` StateFlow 与 `startGlobalLogcat`/`stopGlobalLogcat`；`TerminalLogsPanel` 双标签分离操作事件与 logcat；ViewModel 管理进程生命周期与设备过滤。

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, ProcessBuilder, 复用 `LogcatLineParser` / `JvmAdbRunner`

**Spec:** `docs/superpowers/specs/2026-06-11-global-logcat-shell-design.md`

---

## File Map

| 文件 | 职责 |
|------|------|
| `shared/.../data/AdbRepository.kt` | 扩展 logcat API |
| `shared/.../data/JvmAdbRunner.kt` | `startGlobalLogcat(serial)` |
| `shared/.../data/JvmAdbRepository.kt` | 多设备 logcat 会话管理 |
| `shared/.../data/MockAdbRepository.kt` | Mock logcat Flow |
| `shared/.../viewmodel/AppUiState.kt` | `logTrayMode`, `logcatDeviceFilter` |
| `shared/.../viewmodel/AppViewModel.kt` | 生命周期、Shell、killAdb 接线 |
| `shared/.../ui/logs/TerminalLogsPanel.kt` | 双标签 UI |
| `shared/.../ui/layout/AppShell.kt` | 传参接线 |
| `shared/.../composeResources/values/strings.xml` | i18n |
| `shared/.../composeResources/values-zh/strings.xml` | i18n 中文 |
| `shared/src/jvmTest/.../GlobalLogcatTest.kt` | 新建测试 |
| `shared/src/jvmTest/.../AppViewModelLogcatTest.kt` | 新建测试 |

---

### Task 1: AdbRepository 接口扩展

**Files:**
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/AdbRepository.kt`
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/MockAdbRepository.kt`（桩实现使编译通过）

- [x] **Step 1: 扩展接口**

在 `AdbRepository` 末尾增加：

```kotlin
val logcatLogs: StateFlow<List<AdbLog>>

fun startGlobalLogcat(deviceId: String? = null)
fun stopGlobalLogcat()
fun clearLogcatLogs()
```

- [x] **Step 2: Mock 最小桩**

在 `MockAdbRepository` 添加：

```kotlin
private val _logcatLogs = MutableStateFlow<List<AdbLog>>(emptyList())
override val logcatLogs: StateFlow<List<AdbLog>> = _logcatLogs.asStateFlow()

override fun startGlobalLogcat(deviceId: String?) { /* Task 2 填充 */ }
override fun stopGlobalLogcat() { /* Task 2 填充 */ }
override fun clearLogcatLogs() { _logcatLogs.value = emptyList() }
```

- [x] **Step 3: 编译验证**

Run: `./gradlew :shared:compileKotlinJvm`
Expected: FAIL on `JvmAdbRepository` 未实现（预期）

- [x] **Step 4: JvmAdbRepository 临时空实现**

```kotlin
private val _logcatLogs = MutableStateFlow<List<AdbLog>>(emptyList())
override val logcatLogs: StateFlow<List<AdbLog>> = _logcatLogs.asStateFlow()
override fun startGlobalLogcat(deviceId: String?) {}
override fun stopGlobalLogcat() {}
override fun clearLogcatLogs() { _logcatLogs.value = emptyList() }
```

Run: `./gradlew :shared:compileKotlinJvm`
Expected: PASS

---

### Task 2: JvmAdbRunner.startGlobalLogcat

**Files:**
- Modify: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/JvmAdbRunner.kt`
- Create: `shared/src/jvmTest/kotlin/fun/abbas/wps_adb/JvmAdbRunnerGlobalLogcatTest.kt`

- [x] **Step 1: 写失败测试**

```kotlin
class JvmAdbRunnerGlobalLogcatTest {
    @Test
    fun `global logcat command includes threadtime and serial`() {
        val command = JvmAdbRunner.globalLogcatCommand(
            adbPath = "C:/adb/adb.exe",
            serial = "emulator-5554",
        )
        assertEquals(
            listOf("C:/adb/adb.exe", "-s", "emulator-5554", "logcat", "-v", "threadtime"),
            command,
        )
    }
}
```

- [x] **Step 2: 运行确认 FAIL**

Run: `./gradlew :shared:jvmTest --tests "fun.abbas.wps_adb.JvmAdbRunnerGlobalLogcatTest"`
Expected: FAIL

- [x] **Step 3: 实现**

在 `JvmAdbRunner` 添加：

```kotlin
companion object {
    fun globalLogcatCommand(adbPath: String, serial: String): List<String> =
        listOf(adbPath, "-s", serial, "logcat", "-v", "threadtime")
}

fun startGlobalLogcat(serial: String): LogcatSession {
    val command = globalLogcatCommand(resolveAdbPath(), serial)
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    return LogcatSession(process = process, filterPidClientSide = false, pid = 0)
}
```

- [x] **Step 4: 运行测试 PASS**

Run: `./gradlew :shared:jvmTest --tests "fun.abbas.wps_adb.JvmAdbRunnerGlobalLogcatTest"`
Expected: PASS

---

### Task 3: JvmAdbRepository 全局 logcat 会话

**Files:**
- Modify: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/JvmAdbRepository.kt`

- [x] **Step 1: 状态字段**

```kotlin
private val globalLogcatJobs = mutableMapOf<String, Job>() // key = serial
private var globalLogcatFilterDeviceId: String? = null
```

- [x] **Step 2: appendLogcatLine 辅助**

```kotlin
private fun appendLogcatLine(log: AdbLog) {
    val retention = _settings.value.logRetention.coerceAtLeast(1)
    _logcatLogs.update { (it + log).takeLast(retention) }
}
```

- [x] **Step 3: startGlobalLogcat 实现**

逻辑要点：
1. `globalLogcatFilterDeviceId = deviceId`
2. `stopAllGlobalLogcatProcesses()` 后按 filter 决定 serial 列表：
   - `deviceId != null` → 该设备 serial（离线则 return + addLog 警告）
   - `deviceId == null` → 所有 `ONLINE` 设备
3. 每个 serial 若 `globalLogcatJobs` 无则 `scope.launch(Dispatchers.IO)`：
   - `runner.startGlobalLogcat(serial)`
   - `useLines` → `LogcatLineParser.parse(line, deviceId=对应id, tabId="global", sessionId, lineIndex++)`
   - `appendLogcatLine`
   - finally `process.destroy()`

- [x] **Step 4: stop / clear**

```kotlin
override fun stopGlobalLogcat() {
    globalLogcatFilterDeviceId = null
    stopAllGlobalLogcatProcesses()
}

private fun stopAllGlobalLogcatProcesses() {
    globalLogcatJobs.values.forEach { it.cancel() }
    globalLogcatJobs.clear()
}

override fun clearLogcatLogs() { _logcatLogs.value = emptyList() }
```

- [x] **Step 5: killAdb / onCleared 挂钩**

在 `killAdbServer()` 与 `restartAdbServer()` 开头调用 `stopGlobalLogcat()`。

- [x] **Step 6: 编译**

Run: `./gradlew :shared:compileKotlinJvm`
Expected: PASS

---

### Task 4: MockAdbRepository 演示 logcat

**Files:**
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/MockAdbRepository.kt`

- [x] **Step 1: 实现 startGlobalLogcat**

```kotlin
private var mockGlobalLogcatJob: Job? = null

override fun startGlobalLogcat(deviceId: String?) {
    mockGlobalLogcatJob?.cancel()
    mockGlobalLogcatJob = scope.launch {
        var i = 0
        while (isActive) {
            delay(800)
            val device = deviceId?.let { id -> _devices.value.find { it.id == id } }
                ?: _devices.value.filter { it.status == DeviceStatus.ONLINE }.randomOrNull()
                ?: continue
            val line = "03-15 10:14:22.123  1234  1234 I MockTag: heartbeat #$i"
            appendMockLogcat(device.id, line, i++)
        }
    }
}

override fun stopGlobalLogcat() {
    mockGlobalLogcatJob?.cancel()
    mockGlobalLogcatJob = null
}
```

`appendMockLogcat` 内部用 `LogcatLineParser.parse` + `_logcatLogs.update`。

---

### Task 5: AppUiState + AppViewModel

**Files:**
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/viewmodel/AppUiState.kt`
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/viewmodel/AppViewModel.kt`
- Create: `shared/src/jvmTest/kotlin/fun/abbas/wps_adb/AppViewModelLogcatTest.kt`

- [x] **Step 1: 枚举与状态**

`AppUiState.kt`:

```kotlin
enum class LogTrayMode { EVENTS, LOGCAT }

data class AppUiState(
  // ...existing...
  val logTrayMode: LogTrayMode = LogTrayMode.EVENTS,
  val logcatDeviceFilter: String? = null,
)
```

- [x] **Step 2: 写失败测试**

```kotlin
@Test
fun `onTerminalDevice opens tray logcat mode and sets device filter`() = runTest {
    val repo = MockAdbRepository()
    val vm = AppViewModel(repo)
    val device = repo.devices.value.first()
    vm.onTerminalDevice(device)
    assertTrue(vm.uiState.value.isLogTrayOpen)
    assertEquals(LogTrayMode.LOGCAT, vm.uiState.value.logTrayMode)
    assertEquals(device.id, vm.uiState.value.logcatDeviceFilter)
}
```

- [x] **Step 3: ViewModel 方法**

```kotlin
val logcatLogs = repository.logcatLogs

fun setLogTrayMode(mode: LogTrayMode) = _localState.update { it.copy(logTrayMode = mode) }

fun toggleLogTray() {
    val willOpen = !_localState.value.isLogTrayOpen
    _localState.update { it.copy(isLogTrayOpen = !it.isLogTrayOpen) }
    syncGlobalLogcat(willOpen)
}

private fun syncGlobalLogcat(trayOpen: Boolean) {
    if (trayOpen && _localState.value.isAdbActive) {
        repository.startGlobalLogcat(_localState.value.logcatDeviceFilter)
    } else {
        repository.stopGlobalLogcat()
    }
}

fun onTerminalDevice(device: Device) {
    _localState.update {
        it.copy(isLogTrayOpen = true, logTrayMode = LogTrayMode.LOGCAT, logcatDeviceFilter = device.id)
    }
    repository.startGlobalLogcat(device.id)
}

fun clearLogcatLogs() = repository.clearLogcatLogs()

fun showAllDevicesLogcat() {
    _localState.update { it.copy(logcatDeviceFilter = null) }
    if (_localState.value.isLogTrayOpen) repository.startGlobalLogcat(null)
}
```

更新 `killAdb()`：已有逻辑后 `repository.stopGlobalLogcat()`。

`onCleared()` 已有 `stopAllAppLogcat` 旁加 `repository.stopGlobalLogcat()`。

- [x] **Step 4: 运行测试**

Run: `./gradlew :shared:jvmTest --tests "fun.abbas.wps_adb.AppViewModelLogcatTest"`
Expected: PASS

---

### Task 6: TerminalLogsPanel 双标签 UI

**Files:**
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/logs/TerminalLogsPanel.kt`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`
- Modify: `shared/src/commonMain/composeResources/values-zh/strings.xml`
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/layout/AppShell.kt`

- [x] **Step 1: i18n**

`values/strings.xml`:

```xml
<string name="logs_tab_events">Events</string>
<string name="logs_tab_logcat">Logcat</string>
<string name="logs_logcat_filter_all">All devices</string>
<string name="logs_logcat_filter_device">Device: %1$s</string>
<string name="logs_clear_logcat">Clear logcat</string>
```

`values-zh/strings.xml` 对应中文。

- [x] **Step 2: 扩展 TerminalLogsPanel 签名**

```kotlin
fun TerminalLogsPanel(
    mode: LogTrayMode,
    onModeChange: (LogTrayMode) -> Unit,
    eventLogs: List<AdbLog>,
    logcatLogs: List<AdbLog>,
    logcatFilterLabel: String?,
    onShowAllDevices: () -> Unit,
    onClearEvents: () -> Unit,
    onClearLogcat: () -> Unit,
    onClose: () -> Unit,
    logRetention: Int = 2500,
    ...
)
```

顶栏：两个 `FilterChip` 切换 mode；Logcat 模式显示 filter 文案 + 「显示全部」链接；Clear 根据 mode 调不同回调。

- [x] **Step 3: AppShell 接线**

```kotlin
val logcatLogs by viewModel.logcatLogs.collectAsState()
val filterDevice = uiState.logcatDeviceFilter?.let { id -> devices.find { it.id == id }?.name }
TerminalLogsPanel(
    mode = uiState.logTrayMode,
    onModeChange = viewModel::setLogTrayMode,
    eventLogs = logs,
    logcatLogs = logcatLogs,
    logcatFilterLabel = filterDevice?.let { stringResource(Res.string.logs_logcat_filter_device, it) },
    onShowAllDevices = viewModel::showAllDevicesLogcat,
    onClearEvents = viewModel::clearLogs,
    onClearLogcat = viewModel::clearLogcatLogs,
    onClose = viewModel::toggleLogTray,
    logRetention = settings.logRetention,
)
```

`setLogTrayMode` 切换到 LOGCAT 时若托盘已开则 `repository.startGlobalLogcat(filter)`。

- [x] **Step 4: 编译 Desktop**

Run: `./gradlew :desktopApp:compileKotlin`
Expected: PASS

---

### Task 7: Settings 文案修正

**Files:**
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`
- Modify: `shared/src/commonMain/composeResources/values-zh/strings.xml`

- [x] **Step 1: 更新 auto_trust_subtitle**

EN: `Ensures a local ADB RSA key pair exists (first USB connection still requires on-device approval)`

ZH: `确保本机存在 ADB RSA 密钥对（首次 USB 连接仍需在设备上确认）`

---

### Task 8: 验收

- [x] **Step 1: 全量 JVM 测试**

Run: `./gradlew :shared:jvmTest`
Expected: PASS

- [x] **Step 2: Desktop 编译**

Run: `./gradlew :desktopApp:compileKotlin`
Expected: PASS

- [x] **Step 3: 手动验收清单**

1. 启动 Desktop，打开 Logcat 托盘 → Logcat 标签有实时行（连接真机时）
2. 事件标签仍显示安装/配对等操作日志
3. 点击设备 Shell → 自动切 Logcat 且仅该设备行
4. 「显示全部设备」恢复多设备合并
5. kill ADB 后 logcat 进程停止，无僵尸进程（任务管理器无残留 `adb logcat`）
6. Mock 模式（无 adb）仍可演示 Logcat 标签

- [x] **Step 4: 更新 spec 状态**

将 `2026-06-11-global-logcat-shell-design.md` 状态改为「已实现」。

---

## Self-Review Checklist

| Spec 要求 | Task |
|-----------|------|
| 双列表分离 | Task 1, 6 |
| 真实 logcat | Task 2, 3 |
| Shell 聚焦设备 | Task 5 |
| 生命周期 | Task 3, 5 |
| Mock 演示 | Task 4 |
| Settings 文案 | Task 7 |
| 交互 shell 不做 | 明确排除 |

---

## 执行选项

计划保存后可选：

1. **Subagent-Driven** — 每 Task 派发子代理 + 阶段评审（推荐）
2. **Inline Execution** — 本会话按 Task 顺序直接实现
