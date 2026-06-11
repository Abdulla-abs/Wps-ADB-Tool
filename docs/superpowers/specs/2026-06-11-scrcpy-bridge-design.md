# scrcpy 外部窗口投屏桥接 设计规格

> 状态：已实现  
> 关联：`2026-06-10-side-panel-tabs-design.md`（Mirror 标签扩展）  
> 决策日期：2026-06-11

## 1. 背景与目标

### 1.1 需求来源

设备墙「投屏」当前仅打开 `SidePanel` 的 `Mirror` 标签，内容为 Web 原型迁移的**模拟 UI**（假手机框 + 模拟 Shell），无真实屏幕画面。

用户确认采用 **独立 scrcpy 窗口**方案：由用户自行下载安装 [scrcpy](https://github.com/Genymobile/scrcpy)，本软件负责路径配置、进程启动与生命周期管理。

### 1.2 目标

1. Settings 新增 **scrcpy 可执行文件路径**（类比现有 `adbPath`）
2. Desktop（JVM）点击「投屏」→ 打开 Mirror 标签 → **自动启动 scrcpy 独立窗口**
3. scrcpy 进程通过 `ADB` 环境变量复用本软件配置的 adb 路径
4. Mirror 标签展示投屏状态（未配置 / 启动中 / 投屏中 / 已停止 / 错误），提供「开始 / 停止」控制
5. 关闭 Mirror 标签、设备断开、`killAdb`、应用退出时 **必须销毁对应 scrcpy 进程**
6. 用户手动关闭 scrcpy 窗口时，同步更新 Mirror 标签状态
7. Mock / Android 端保持可演示 UI，显示「仅 Desktop 支持真实投屏」提示

### 1.3 非目标（首版）

- 在 `SidePanel`（400dp）内嵌 scrcpy 视频流
- 自研 scrcpy 协议客户端 / H.264 解码渲染
- 软件内打包或自动下载 scrcpy（仅提供配置与下载指引）
- `adb screencap` 截图轮询降级（可作为后续独立 Phase）
- Mirror 标签内真实 `adb shell` 终端（保留模拟 Shell 或后续单独规格）
- scrcpy 高级参数 UI（码率、编码器、录屏等）— 首版用 scrcpy 默认参数

---

## 2. 架构决策

### 2.1 桥接模式：进程编排器

本软件**不解析 scrcpy 视频流**，仅作为编排层：

```
WpsAdbTool (Compose Desktop)
  │
  ├─ Settings: scrcpyPath + adbPath
  │
  ├─ 投屏触发
  │    ProcessBuilder(scrcpyPath, "-s", serial, "--window-title", title)
  │      .environment("ADB", resolvedAdbPath)
  │      .start()
  │
  └─ scrcpy 独立 SDL 窗口（键鼠控制、低延迟画面）
         │
         └─ scrcpy 内部 push server.jar + adb forward/reverse
```

### 2.2 与 SidePanel 职责划分

| 组件 | 职责 |
|------|------|
| scrcpy 窗口 | 实时画面 + 键鼠注入 + 剪贴板同步 |
| Mirror 标签 | 投屏状态面板、开始/停止、scrcpy 配置检测、下载指引 |
| `TabSessionManager` | 登记 `SCRCPY_PROCESS` 监听类型 |
| `JvmScrcpyMirrorService` | 进程启动/销毁、`tabId → Process` 映射 |

### 2.3 平台策略

| 目标 | 行为 |
|------|------|
| Desktop (JVM) | 真实 scrcpy 桥接 |
| Mock 模式 | `NoOpScrcpyMirrorService`，Mirror 标签显示演示态 |
| Android App | 同 Mock，提示需使用 Desktop 版 |

### 2.4 关键约束

- **禁止** 传递 `--kill-adb-on-close`（会终止共享 adb server，影响设备墙与其他标签）
- **禁止** 在 Composable 内直接 `ProcessBuilder`；须经 `ScrcpyMirrorService` + `TabSessionManager`
- scrcpy 窗口标题统一：`WpsAdbTool - {device.name}`，便于用户识别多设备投屏

---

## 3. 数据模型

### 3.1 新增枚举

```kotlin
enum class MirrorSessionState {
    IDLE,           // 标签已开，scrcpy 未启动
    STARTING,       // 进程启动中
    RUNNING,        // scrcpy 窗口活跃
    STOPPED,        // 用户或应用主动停止
    ERROR,          // 启动失败或异常退出
    UNAVAILABLE,    // scrcpy 未配置或不可执行（Desktop 检测失败）
}

enum class TabListenKind {
    APP_LOGCAT,
    MIRROR_FRAME,       // 保留，首版未用
    SCRCPY_PROCESS,     // 新增
}
```

### 3.2 `SidePanelTab.Mirror` 扩展

```kotlin
data class Mirror(
    override val id: String,
    override val title: String,
    override val device: Device,
    val sessionState: MirrorSessionState = MirrorSessionState.IDLE,
    val errorMessage: String? = null,
) : SidePanelTab()
```

### 3.3 `AppSettings` 扩展

```kotlin
data class AppSettings(
    val adbPath: String = "adb",
    val scrcpyPath: String = "scrcpy",   // 新增
    // ... 其余字段不变
)
```

路径解析规则与 `JvmAdbRunner.resolveAdbPath()` 一致：

- 空或 `"scrcpy"` → 依赖系统 PATH
- 绝对路径且文件存在 → 使用该路径
- 否则回退 `"scrcpy"`

### 3.4 服务接口（commonMain）

```kotlin
data class ScrcpyStartResult(
    val success: Boolean,
    val message: String,
)

interface ScrcpyMirrorService {
    fun isAvailable(): Boolean
    fun start(tabId: String, serial: String, deviceName: String): ScrcpyStartResult
    fun stop(tabId: String)
    fun stopAll()
    fun isRunning(tabId: String): Boolean
    /** 注册进程退出回调，用于同步 UI 状态 */
    fun onProcessExit(tabId: String, callback: () -> Unit)
}
```

| 实现 | 位置 | 行为 |
|------|------|------|
| `JvmScrcpyMirrorService` | jvmMain | 真实 ProcessBuilder |
| `NoOpScrcpyMirrorService` | commonMain | `isAvailable() = false`，start 返回失败说明 |

---

## 4. 组件与目录结构

```
shared/src/commonMain/kotlin/fun/abbas/wps_adb/
├── model/
│   ├── MirrorSessionState.kt          # 新增
│   └── SidePanelTab.kt                # Mirror 扩展 sessionState
├── data/
│   ├── ScrcpyMirrorService.kt         # 接口 + NoOp 实现
│   └── TabListenKind.kt               # + SCRCPY_PROCESS
├── viewmodel/
│   └── AppViewModel.kt                # 投屏启停、状态同步、teardown
└── ui/
    ├── settings/SettingsScreen.kt     # scrcpyPath 字段
    └── sidepanel/MirrorTabContent.kt  # 状态面板 UI（替换模拟手机框）

shared/src/jvmMain/kotlin/fun/abbas/wps_adb/
└── data/JvmScrcpyMirrorService.kt     # ProcessBuilder 实现

shared/src/jvmTest/kotlin/fun/abbas/wps_adb/
├── JvmScrcpyMirrorServiceTest.kt      # 命令行参数、路径解析
└── AppViewModelMirrorTeardownTest.kt  # 关标签/断开时杀进程
```

---

## 5. scrcpy 启动规格

### 5.1 启动命令

```bash
# 等价命令（由 JvmScrcpyMirrorService 构造）
ADB=<resolved_adb_path> \
  <scrcpy_path> \
    -s <device.serial> \
    --window-title "WpsAdbTool - <device.name>"
```

### 5.2 可用性检测

```bash
<scrcpy_path> --version   # exit 0 视为可用
```

Settings 保存时可选执行检测，结果写入底部日志（`Settings` / `ScrcpyService`）。

### 5.3 启动失败处理

| 场景 | `sessionState` | `errorMessage` 示例 |
|------|----------------|---------------------|
| scrcpy 未安装 / 不在 PATH | `UNAVAILABLE` | scrcpy not found — configure path in Settings |
| 设备 offline | `ERROR` | Device offline |
| 进程立即退出（非 0） | `ERROR` | scrcpy exited with code N |
| 同 tabId 已在运行 | 保持 `RUNNING`，不重复启动 | — |

进程 stderr 合并到 stdout（`redirectErrorStream(true)`），退出时末 200 字符记入 `errorMessage` 与全局日志。

---

## 6. 交互与数据流

### 6.1 设备墙投屏（主路径）

```
DeviceCard.onMirror
  → AppViewModel.onMirrorDevice(device)
  → SidePanelController.openMirrorTab(device)
  → if (scrcpyMirrorService.isAvailable())
       startScrcpyMirror(tabId, device)     // 自动启动
     else
       update Mirror tab → UNAVAILABLE
  → repository.addLog(I, "ScrcpyService", "Starting mirror: ${device.serial}", device.id)
```

同设备重复点击「投屏」：聚焦已有 Mirror 标签；若 `RUNNING` 则不重复启动。

### 6.2 Mirror 标签内操作

| 操作 | 条件 | 行为 |
|------|------|------|
| 「开始投屏」 | `IDLE` / `STOPPED` / `ERROR` | `startScrcpyMirror` |
| 「停止投屏」 | `RUNNING` | `stopScrcpyMirror` → `STOPPED` |
| 打开 scrcpy 下载页 | `UNAVAILABLE` | 外链 GitHub Releases |

首版 **移除** 模拟手机框、假 WiFi/蓝牙/亮度控件；保留精简设备信息（名称、serial、电量）与状态区。

### 6.3 进程退出同步

```
JvmScrcpyMirrorService
  → process.onExit()（后台线程）
  → callback(tabId)
  → AppViewModel 主线程更新 sessionState = STOPPED 或 ERROR
```

区分：

- 应用主动 `destroy()` → `STOPPED`
- 用户关 scrcpy 窗口 → `STOPPED`（非 ERROR）
- 启动后立即退出 → `ERROR`

---

## 7. 生命周期与 teardown

**原则**（延续 `2026-06-10-side-panel-tabs-design.md` §2.6）：关闭标签必停 scrcpy。

| 触发 | 动作 |
|------|------|
| 用户点击 Mirror 标签 `×` | `stopScrcpyMirror(tabId)` → `TabSessionManager.stopAll(tabId)` |
| 标签数超限驱逐 | 同上（驱逐前） |
| 设备 `DISCONNECT` | 关闭该设备全部标签（含 Mirror）→ stop scrcpy |
| `killAdb` / `restartAdb` | `stopAllScrcpyMirrors()` |
| `ViewModel.onCleared` | `stopAllScrcpyMirrors()` 兜底 |

`teardownTabListening(tabId)` 扩展：

```kotlin
scrcpyMirrorService.stop(tabId)
tabSessionManager.stop(tabId, TabListenKind.SCRCPY_PROCESS)
```

顺序：**先 stop 进程，再从 tabs 列表移除**（与 logcat 一致）。

---

## 8. Settings UI

在「Transport Bindings」卡片中，`adbPath` 下方新增：

| 字段 | 默认值 | 说明 |
|------|--------|------|
| scrcpy 路径 | `scrcpy` | 可执行文件路径，Windows 示例 `F:\scrcpy-win64-v3.3.4\scrcpy.exe` |

保存时：

1. 持久化 `AppSettings.scrcpyPath`
2. 执行 `isAvailable()` 检测
3. 日志：`ScrcpyService: scrcpy 3.3.4 detected` 或警告未找到

i18n 新增 `settings_scrcpy_path`、`mirror_*` 状态文案（中英）。

---

## 9. Mock / Android 降级

`NoOpScrcpyMirrorService`：

- `isAvailable()` → `false`
- `start()` → `ScrcpyStartResult(false, "Desktop + scrcpy required")`

`MirrorTabContent` 在 `UNAVAILABLE` 时展示：

- 说明文字：真实投屏需 Desktop 版并安装 scrcpy
- GitHub Releases 链接
- Mock 模式下可显示「演示模式」徽章，隐藏开始按钮或点击后 toast 提示

---

## 10. 测试计划

| 类型 | 用例 |
|------|------|
| 单元 | `JvmScrcpyMirrorService` 构造的命令行参数（`-s`、`--window-title`、`ADB` 环境变量） |
| 单元 | 路径解析：PATH / 绝对路径 / 不存在回退 |
| 单元 | 同 `tabId` 重复 `start` 幂等 |
| 单元 | `AppViewModel`：关标签 → `stop` 被调用 |
| 单元 | 设备断开 → 对应 tab scrcpy stop |
| 单元 | `killAdb` → `stopAll` |
| 手动 | Desktop 连接真机 → 投屏 → scrcpy 窗口出现 → 关标签窗口消失 |
| 手动 | 手动关 scrcpy 窗口 → Mirror 标签状态变 STOPPED |
| 手动 | 两设备同时投屏 → 两个 scrcpy 窗口，标题可区分 |

测试中使用可执行 **`echo` / `cmd /c` 假进程** 替代真实 scrcpy，避免 CI 依赖图形环境。

---

## 11. 实施阶段

| Phase | 内容 | 验收 |
|-------|------|------|
| **M1** | `AppSettings.scrcpyPath` + Settings UI + i18n | 可配置并检测 scrcpy |
| **M2** | `ScrcpyMirrorService` + `JvmScrcpyMirrorService` + 单元测试 | 可启动/停止假进程 |
| **M3** | `MirrorTabContent` 状态面板 UI 重写 | 移除模拟手机框 |
| **M4** | `AppViewModel` 集成 + teardown 全路径 + 测试 | 投屏端到端 |
| **M5** | 手动真机验收 + 文档更新 | `./gradlew :shared:jvmTest` 通过 |

---

## 12. 验收标准

- [x] Settings 可配置 scrcpy 路径，保存时检测可用性
- [x] Desktop 点击「投屏」自动打开 scrcpy 独立窗口
- [x] scrcpy 使用本软件配置的 adb（`ADB` 环境变量）
- [x] Mirror 标签显示正确状态，可手动开始/停止
- [x] 关闭 Mirror 标签后 scrcpy 进程被销毁
- [x] 用户手动关闭 scrcpy 窗口后，Mirror 标签状态同步为 STOPPED
- [x] 设备断开 / killAdb / 应用退出不遗留 scrcpy 进程
- [x] 同设备重复投屏不启动第二个 scrcpy 实例
- [x] Mock / Android 显示降级提示，不崩溃
- [x] 未使用 `--kill-adb-on-close`

---

## 13. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 用户未安装 scrcpy | Settings + Mirror 标签引导下载 |
| 多版本 scrcpy CLI 差异 | 首版仅依赖稳定选项（`-s`、`--window-title`）；版本检测日志 |
| Windows 杀进程残留 | `process.destroyForcibly()` 超时兜底（3s） |
| 无线设备 serial 含 `:` | 直接传给 `-s`，与 adb 行为一致 |
| CI 无图形环境 | 单元测试用假进程；scrcpy 手动验收 |

---

## 14. 关联文档变更

| 文档 | 变更 | 状态 |
|------|------|------|
| `2026-06-10-side-panel-tabs-design.md` | §1.3 非目标标注「外部窗口桥接见 scrcpy-bridge 规格」 | ✅ 已更新（side-panel 已实现） |
| `2026-06-09-web-to-kmp-design.md` | §1.3 生产级 scrcpy → 已通过外部窗口方案部分满足 | ✅ 已更新 |

批准后下一步：invoke `writing-plans` 生成 `docs/superpowers/plans/2026-06-11-scrcpy-bridge.md` 实施计划。
