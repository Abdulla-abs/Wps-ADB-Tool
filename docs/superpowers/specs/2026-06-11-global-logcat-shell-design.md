# 全局 Logcat 与 Shell 按钮 设计规格

> 状态：已实现  
> 创建日期：2026-06-11  
> 关联：`2026-06-11-remaining-work-backlog.md`、`2026-06-11-scrcpy-bridge-design.md` §1.3

## 1. 背景

上一轮代码审查确认 Desktop 端存在两处「有按钮、能力不符」：

| 入口 | UI 承诺 | 现状 |
|------|---------|------|
| 侧栏/底栏「Logcat 控制台」 | 查看系统 logcat | 仅展示 `addLog()` 写入的操作事件 |
| 设备墙「Shell」 | Shell 终端 | 打开托盘 + 写一条假日志，无 logcat 也无交互终端 |

AppLog 标签内按包名过滤的 logcat **已实现**（`JvmAdbRepository.startAppLogcat`），可复用 `JvmAdbRunner.startLogcat` 与 `LogcatLineParser`。

本规格聚焦：**让 Logcat 控制台名副其实**；Shell 按钮对齐 Web 原型语义（聚焦某设备的调试输出流），**首版不做交互式 `adb shell` 终端**（与 scrcpy-bridge §1.3 非目标一致，单独 Phase）。

## 2. 目标

1. 底部 **Logcat 控制台** 支持实时 `adb logcat` 流（Desktop JVM 真实模式）
2. 保留现有 **操作事件** 日志，不与 logcat 混在同一列表造成解析混乱
3. 点击设备墙 **Shell** → 打开控制台并 **仅显示该设备** 的 logcat；再次点击其他设备可切换过滤
4. `killAdb` / 应用退出 / 关闭控制台时正确 **停止 logcat 子进程**
5. Mock 模式可演示（合成 logcat 行或复用 Mock 定时 feed）
6. Settings 中 **自动信任 RSA** 副标题与 hint 对齐（文案修复，无新后端）

## 3. 非目标

- Mirror 标签或独立标签内的交互式 `adb shell` REPL（后续 `RW-F03`）
- 全局 logcat 级别/tag 持久化过滤 UI（首版仅设备过滤 + 沿用 `logRetention`）
- 崩溃遥测真实上报（保持禁用 +「即将推出」）
- 配对清单自动检测 USB/无线状态（仍为手动勾选）

## 4. 方案对比与决策

### 方案 A — 托盘双标签（推荐）

`TerminalLogsPanel` 增加 **「事件」|「Logcat」** 两个子标签：

- **事件**：现有 `repository.logs`（安装、配对、批量等）
- **Logcat**：新 `repository.logcatLogs`，来自后台 `adb logcat -v threadtime`

Shell 按钮：打开托盘、切到 Logcat 标签、设置 `deviceId` 过滤。

| 优点 | 缺点 |
|------|------|
| 语义清晰，不破坏现有操作日志 | 托盘 UI 略增复杂度 |
| 复用 `SelectableLogList`、`logRetention` | 需管理 logcat 进程生命周期 |
| 与 AppLog 标签职责分离（全局 vs 单应用） | |

### 方案 B — 合并单列表

logcat 行与事件行写入同一 `logs` StateFlow，用 `tag` 区分。

| 优点 | 缺点 |
|------|------|
| 改动面小 | 清空/保留策略冲突；用户难以区分事件与 logcat |
| | 高流量 logcat 挤掉操作事件 |

### 方案 C — Shell 启动外部终端

`ProcessBuilder("cmd", "/c", "adb", "-s", serial, "shell")`。

| 优点 | 缺点 |
|------|------|
| 真实交互 shell | 跳出应用；Windows/macOS/Linux 分支多 |
| | 与「Logcat 控制台」仍是两套东西 |

**决策：采用方案 A。** Shell 首版 = 「聚焦该设备的 logcat 流」，不是独立终端窗口。

## 5. 架构

```
AppViewModel
  ├─ logTrayMode: EVENTS | LOGCAT          (AppUiState)
  ├─ logcatDeviceFilter: String?           (null = 全部在线设备合并*)
  ├─ startGlobalLogcat() / stopGlobalLogcat()
  └─ onTerminalDevice(device) → filter + open tray + LOGCAT mode

AdbRepository (interface)
  ├─ logs: StateFlow<List<AdbLog>>         (unchanged, 操作事件)
  ├─ logcatLogs: StateFlow<List<AdbLog>>   (new)
  ├─ fun startGlobalLogcat(deviceId: String?)
  ├─ fun stopGlobalLogcat()
  └─ fun setGlobalLogcatDeviceFilter(deviceId: String?)

JvmAdbRepository
  ├─ globalLogcatClosers: Map<String, () -> Unit>  (key = serial)
  └─ JvmAdbRunner.startGlobalLogcat(serial) → Process, no --pid

TerminalLogsPanel
  ├─ Tab: 事件 → logs
  └─ Tab: Logcat → logcatLogs (filtered by deviceId in VM or UI)
```

\* **多设备策略（首版）：** 未指定 `deviceId` 时，对**每台在线设备**各启一个 `adb -s SERIAL logcat` 进程，合并进同一 `logcatLogs`（`AdbLog.deviceId` 区分来源）。仅 1 台在线时可不显示设备列。指定 `deviceId` 后只保留该设备会话，停止其他 serial 的进程。

## 6. 数据流与生命周期

| 事件 | 行为 |
|------|------|
| 打开 Logcat 托盘（且 `isAdbActive`） | `startGlobalLogcat(currentFilter)` |
| 关闭托盘 | `stopGlobalLogcat()` |
| 点击 Shell | `setFilter(deviceId)` + 打开托盘 + LOGCAT 模式；若已在跑则热切换 filter |
| `killAdb` / `restartAdb` | `stopGlobalLogcat()`（已有 clear side panel 逻辑旁追加） |
| `onCleared()` | `stopGlobalLogcat()` |
| `saveSettings.logRetention` 变更 | 新行 append 时 `takeLast(logRetention)` 作用于 `logcatLogs` |
| 设备离线 | 停止该 serial 对应 logcat 进程；若当前 filter 指向离线设备则清除 filter |

**logcat 命令：** `adb -s <serial> logcat -v threadtime`（与 AppLog 旧设备 client-side pid 过滤路径一致，但不传 pid）。

**解析：** 复用 `LogcatLineParser.parse(..., tabId = "global", ...)`。

## 7. UI 变更

### 7.1 TerminalLogsPanel

- 顶栏增加 **事件 / Logcat** 分段切换（或两个小 Tab）
- Logcat 模式下标题旁显示过滤状态：`全部设备` / `仅 {device.name}`
- Logcat 模式增加 **「显示全部设备」** 文字按钮（清除 filter）
- **清空** 按钮：仅清空当前标签对应列表（`clearLogs()` vs `clearLogcatLogs()`）

### 7.2 Shell 按钮

- 行为变更见 §6；无需改 DeviceWall 组件签名（仍 `onTerminal`）
- 可选：Shell 点击后高亮态（`AppUiState.logcatDeviceFilter == device.id`）— 首版可省略

### 7.3 Settings 文案

- `settings_auto_trust_subtitle` 改为与 hint 一致：「确保本机 ADB 密钥对存在（首次 USB 仍需在设备上确认）」
- 遥测：保持 `enabled = false`，不改动

### 7.4 i18n

新增/调整 strings（中英）：

- `logs_tab_events` / `logs_tab_logcat`
- `logs_logcat_filter_all` / `logs_logcat_filter_device`
- `logs_clear_logcat`

## 8. Mock / Android

| 平台 | 行为 |
|------|------|
| JVM + ADB 可用 | 真实 logcat |
| MockAdbRepository | `startGlobalLogcat` 启动协程定时 `trySend` 合成 `LogcatLineParser` 格式行 |
| Android | `NoOpGlobalLogcat` 或空 Flow（本规格暂不考虑 Android 真实实现） |

## 9. 错误处理

- ADB 不可用：Logcat 标签显示单行提示「ADB 未连接」，不启动进程
- 单设备 logcat 进程 exit != 0：append `LogLevel.E` 系统行，不崩溃
- 同一 serial 重复 `start`：幂等，不重复起进程

## 10. 测试

| 测试 | 类型 |
|------|------|
| `LogcatLineParser` | 已有 commonTest |
| `JvmAdbRunner.startGlobalLogcat` 命令构造 | jvmTest（mock ProcessBuilder 或测 buildList） |
| `MockAdbRepository.startGlobalLogcat` 产出 Flow | commonTest / jvmTest |
| `AppViewModel.onTerminalDevice` 更新 filter + tray | jvmTest |
| `stopGlobalLogcat` 在 killAdb 时调用 | 扩展现有 teardown test |

## 11. 实施顺序

1. **G1** Repository 接口 + JVM 实现 + Mock
2. **G2** ViewModel 生命周期 + Shell 接线
3. **G3** TerminalLogsPanel 双标签 UI + i18n
4. **G4** Settings 文案 + 文档状态更新
5. **G5** 验收：`./gradlew :shared:jvmTest :desktopApp:compileKotlin`

## 12. 远期（不在本规格）

- `RW-F03` Mirror / 独立 Shell 标签：交互式 `adb shell`（需 xterm/PTY 或内嵌终端组件）
- 全局 logcat tag/level 过滤器 UI
- 崩溃遥测

## 13. 修订记录

| 日期 | 变更 |
|------|------|
| 2026-06-11 | 初稿 |
