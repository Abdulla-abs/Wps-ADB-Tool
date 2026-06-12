# JediTerm 设备 Shell + 简易操作面板 设计规格

> 状态：已实现（首版）  
> 关联：`2026-06-11-remaining-work-backlog.md`（RW-F03）、`2026-06-11-global-logcat-shell-design.md`、`2026-06-11-scrcpy-bridge-design.md`  
> 决策日期：2026-06-12

## 1. 背景与目标

### 1.1 需求来源

Backlog **RW-F03** 要求提供真实 `adb shell` 交互能力。当前设备墙「Shell」按钮仅打开底部 Logcat 托盘并聚焦单设备（`global-logcat-shell` 规格），**与用户对「Shell 控制台」的心智不符**。

产品确认：

1. 集成 **[JediTerm](https://github.com/JetBrains/jediterm)** 作为 Desktop 端嵌入式终端（VT100 / PTY）
2. 面向**非专业用户**，提供 **Easy Actions（简易操作）** 面板，一键执行常见运维动作，无需手敲命令
3. UI 参考用户提供的线框图：主区域 JediTerm 控制台 + 右侧分类快捷按钮 + 顶栏面包屑导航

### 1.2 目标

1. 设备墙点击 **Shell** → 经 **共享元素转场**（首选）或 **横向滑动转场**（降级）进入 **设备 Shell 全屏主内容视图**（非 400dp SidePanel）
2. Desktop（JVM）通过 JediTerm + `adb -s <serial> shell` 建立 **可交互 PTY 会话**
3. 右侧 **Easy Actions** 面板提供分组快捷操作（SYSTEM / DISPLAY / APP CONTROL）
4. 顶栏面包屑：`设备墙 > {device.name} > Shell`，可返回设备墙
5. 关闭 Shell 视图、设备离线、`killAdb`、应用退出时 **必须销毁 shell 子进程与 JediTerm 会话**
6. 快捷操作经 `AdbRepository` 执行（结构化 adb 命令），结果写入全局操作日志；可选在终端 echo 一行摘要
7. Mock / Android 端展示降级 UI（模拟终端 + 禁用真实 PTY 提示）

### 1.3 非目标（首版）

- SidePanel 内嵌 Shell 标签（400dp 过窄；Shell 使用主内容区）
- 替换底部 Logcat 控制台（保留；Shell 按钮职责从「聚焦 logcat」改为「打开 Shell 视图」）
- 软件内打包 adb / JediTerm 原生库（adb 仍用 Settings 路径；JediTerm 为 Gradle 依赖）
- Easy Actions 全量覆盖 adb 命令集（首版仅 UI 图所列 7 项 + 确认对话框）
- 终端多标签 / 分屏 / SSH
- 终端内 AI 辅助、命令历史云同步
- 实时 PID/CPU 仪表盘（首版可显示 serial + 会话状态；性能指标为可选增强）

---

## 2. 方案对比与决策

### 方案 A — 主内容区 Shell 视图 + JediTerm（推荐）

设备墙 Shell → `AppUiState.shellSession` → 主内容区渲染 `DeviceShellScreen`（JediTerm + Easy Actions）。

| 优点 | 缺点 |
|------|------|
| 与 UI 图一致（宽终端 + 右侧操作栏 + 面包屑） | 新增主内容路由状态 |
| 终端有足够横向空间 | JediTerm 为 Swing，需 `SwingPanel` 互操作 |
| 与 SidePanel（Mirror/AppLog）职责分离 | |

### 方案 B — SidePanel Shell 标签 + 动态加宽至 720dp

| 优点 | 缺点 |
|------|------|
| 与 Mirror/AppLog 标签模型一致 | 与 UI 图布局不符（终端挤在右侧栏） |
| | 宽 SidePanel 挤压设备墙 |

### 方案 C — 外部终端桥接（cmd / Windows Terminal）

| 优点 | 缺点 |
|------|------|
| 实现极快 | 跳出应用，无法做 Easy Actions 同屏 |
| | 与用户「集成 JediTerm」诉求不符 |

**决策：采用方案 A。** Shell 为主内容子视图；JediTerm 经 `SwingPanel` 嵌入 Compose Desktop。

### 2.2 设备墙 → Shell 转场方案

用户在设备墙点击 Shell 后，**不瞬间替换页面**，而是有明确的导航转场感（类似 Android Activity 共享元素，或 ViewPager 横向滑页）。

| 优先级 | 方案 | 说明 |
|--------|------|------|
| **P0 首选** | **共享元素转场** | 被点击设备卡片的 **预览区（截图/占位框）** 与 **设备名称** 作为 shared element， morph 到 Shell 页头部；其余内容 fade + 轻微 scale |
| **P1 降级** | **横向滑动（类 ViewPager）** | `AnimatedContent` + `slideInHorizontally` / `slideOutHorizontally`；进入 Shell 从右滑入，返回从左滑出 |

**降级触发条件（任一满足即切 P1）：**

1. 当前平台 `SharedTransitionLayout` 不可用或渲染异常（Compose Desktop 实验性 API）
2. 共享元素动画完成前无法正确测量 bounds（LazyGrid 滚动位置变化）
3. 手动 Settings 开关 `preferSlideShellTransition = true`（调试 / 用户偏好）
4. 实现阶段评估：JediTerm `SwingPanel` 与 shared bounds 动画同帧叠加出现明显闪烁

**转场期间 JediTerm 策略：** Shell PTY 在转场 **开始时** 即启动（不阻塞 adb 连接），但 `SwingPanel` **延迟挂载** 至转场结束（`onTransitionComplete`），避免 Swing/Skia 层叠闪烁。

**决策：首选共享元素；实现受阻时自动降级为横向滑动，不阻塞 RW-F03 交付。**

---

## 3. 架构

### 3.1 总体结构

```
AppShell
  ├─ Sidebar（导航不变）
  ├─ AppHeader（含 Shell 面包屑，shellRoute == Shell 时）
  ├─ Main Content（NavTab.WALL）
  │    └─ DeviceWallHost                    ← 新增：设备墙导航容器
  │         ├─ SharedTransitionLayout（首选）或 AnimatedContent（降级）
  │         ├─ Route = Grid → DeviceWallScreen（卡片带 sharedElement key）
  │         └─ Route = Shell(deviceId) → DeviceShellScreen
  │              ├─ DeviceShellHeader（共享元素目标：预览 + 标题）
  │              ├─ JediTermPanel（转场完成后挂载 SwingPanel）
  │              └─ EasyActionsPanel
  ├─ Main Content（NavTab.GROUPS / SETTINGS → 不变）
  ├─ SidePanel（Mirror / AppLog，与 Shell 独立）
  └─ TerminalLogsPanel（全局 Logcat，职责不变）
```

### 3.2 服务层：DeviceShellService

类比 `ScrcpyMirrorService`，本软件**不实现 VT100 解析**，仅编排 adb shell 进程与 JediTerm 生命周期：

```
AppViewModel
  ├─ openDeviceShell(device)
  ├─ closeDeviceShell()
  └─ DeviceShellService
        ├─ start(sessionId, serial) → PtyProcess(adb -s serial shell)
        ├─ stop(sessionId)
        ├─ createTerminalWidget(sessionId) → JComponent（JediTermWidget）
        └─ isRunning(sessionId)
```

| 组件 | 职责 |
|------|------|
| `JvmDeviceShellService` | Pty4J 启动 adb shell、JediTermWidget 创建/销毁 |
| `DeviceShellSessionHolder` | `sessionId → Process + Widget` 映射 |
| `TabSessionManager` | 登记 `TabListenKind.DEVICE_SHELL` |
| `AdbRepository` | Easy Actions 结构化命令（reboot、screenshot 等） |

### 3.3 JediTerm 集成方式

| 项 | 说明 |
|----|------|
| 依赖 | `org.jetbrains.jediterm:jediterm-core` + `jediterm-pty`（jvmMain） |
| UI 嵌入 | Compose `SwingPanel(factory = { jediTermWidget })` |
| 主题 | 子类 `DefaultSettingsProvider`，对齐 Carbon 深色（背景 `#161616`、主色 `#42BE65`） |
| 连接 | `PtyProcessBuilder(adbPath, "-s", serial, "shell")` → `PtyProcessTtyConnector` |
| 环境 | 继承 `ADB` 环境变量（与 scrcpy 一致，指向 Settings 解析路径） |
| 平台 | **仅 jvmMain**；commonMain 用 `expect/actual` 或占位 Composable |

### 3.4 与 Logcat 的关系

| 入口 | 首版行为 |
|------|----------|
| 设备墙 **Shell** | 打开 `DeviceShellScreen`（本规格） |
| 底栏 **Logcat 控制台** | 不变；手动切 Logcat 标签查看全局/多设备日志 |
| Shell 视图内（可选） | 顶栏链接「查看 Logcat」→ 打开托盘并聚焦当前设备 |

---

## 4. 数据模型

### 4.1 新增枚举与状态

```kotlin
enum class DeviceShellSessionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR,
    UNAVAILABLE,   // 非 Desktop 或 adb 不可用
}

enum class TabListenKind {
    // ...existing
    DEVICE_SHELL,  // 新增
}
```

### 4.2 设备墙路由与 AppUiState 扩展

```kotlin
/** 设备墙主内容区导航（仅 NavTab.WALL 生效） */
sealed class DeviceWallRoute {
    data object Grid : DeviceWallRoute()
    data class Shell(val deviceId: String) : DeviceWallRoute()
}

enum class ShellTransitionKind {
    SHARED_ELEMENT,  // 首选
    SLIDE,           // 降级：横向滑动
}

data class DeviceShellSession(
    val deviceId: String,
    val sessionState: DeviceShellSessionState = DeviceShellSessionState.IDLE,
    val errorMessage: String? = null,
    val isScreenRecording: Boolean = false,
    /** 转场结束后才 true；控制 JediTerm SwingPanel 挂载 */
    val terminalSurfaceReady: Boolean = false,
)

// AppUiState
val deviceWallRoute: DeviceWallRoute = DeviceWallRoute.Grid,
val shellSession: DeviceShellSession? = null,  // deviceWallRoute is Shell 时非 null
val shellTransitionKind: ShellTransitionKind = ShellTransitionKind.SHARED_ELEMENT,
```

**Shared element key 约定（仅 SHARED_ELEMENT 模式）：**

| Key | 源（DeviceCard） | 目标（DeviceShellHeader） |
|-----|------------------|---------------------------|
| `shell-hero-{deviceId}` | 截图/占位预览 Box | Shell 顶栏左侧设备预览 |
| `shell-title-{deviceId}` | 设备名称 Text | Shell 顶栏设备名称 |

`sessionId` 固定为 `shell_${deviceId}`，与 `SidePanelController` 命名风格一致。

### 4.3 Easy Actions 模型

```kotlin
enum class EasyActionCategory { SYSTEM, DISPLAY, APP_CONTROL }

enum class EasyActionKind {
    REBOOT,
    RECOVERY_MODE,
    CLEAR_APP_CACHE,      // 单应用清缓存（非系统级 trim-caches）
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
```

### 4.4 服务接口（commonMain）

```kotlin
interface DeviceShellService {
    fun isAvailable(): Boolean
    fun start(sessionId: String, serial: String): DeviceShellStartResult
    fun stop(sessionId: String)
    fun stopAll()
    fun isRunning(sessionId: String): Boolean
    /** Desktop：返回可嵌入 SwingPanel 的 JComponent；其他平台 null */
    fun createTerminalComponent(sessionId: String): Any?
    fun setExitListener(listener: ((sessionId: String, exitCode: Int) -> Unit)?)
}

class NoOpDeviceShellService : DeviceShellService { /* Desktop 以外 */ }
```

---

## 5. Easy Actions 命令映射（首版）

所有操作经 `AdbRepository.runDeviceAction(deviceId, action)` 执行，**不**向 JediTerm  stdin 注入（避免与交互式 shell 冲突）。执行结果 `addLog` 到操作事件流。

| 按钮 | 分类 | adb / shell 命令 | 备注 |
|------|------|------------------|------|
| Reboot Device | SYSTEM | `adb reboot` | 已有 `rebootDevice` |
| Recovery Mode | SYSTEM | `adb reboot recovery` | 二次确认；设备将离线 |
| Clear Cache | APP CONTROL | 见下方「单应用清缓存」 | **单应用**；需包名；不清数据 |
| Take Screenshot | DISPLAY | `adb exec-out screencap -p` → 本地文件 | 复用 `captureScreenshot`；完成后 toast + 打开目录 |
| Screen Record | DISPLAY | `adb shell screenrecord /sdcard/wps-adb-{ts}.mp4` | Toggle：再次点击发送 Ctrl+C；完成后 `adb pull` |
| Force Stop App | APP CONTROL | `adb shell am force-stop <pkg>` | 需包名对话框 |
| Clear Data | APP CONTROL | `adb shell pm clear <pkg>` | 需包名 + destructive 确认 |

**单应用清缓存（Clear Cache）命令链：**

仅清除指定应用 cache，**不**调用 `pm trim-caches`（系统级），**不**等同于 `pm clear`（清数据）。

| 优先级 | 命令 | 适用 |
|--------|------|------|
| P0 | `adb shell cmd package clear-app-cache --user 0 <pkg>` | Android 8+（API 26+） |
| P1 | `adb shell pm clear --cache-only <pkg>` | Android 12+（部分 ROM 支持） |
| 失败 | — | 日志提示「设备/系统不支持该命令」，建议改用系统设置或 `Clear Data` |

**包名输入：** 复用简易对话框（手动输入 + 最近 AppLog 标签中的 packageName 列表）；首版不做完整已安装应用列表扫描（可 Phase 2：`pm list packages`）。Clear Cache / Force Stop / Clear Data 共用 `PackageNameDialog`。

---

## 6. UI 规格

### 6.1 DeviceShellScreen 布局

```
┌─────────────────────────────────────────────────────────────────┐
│ ← 设备墙  /  Pixel 6 - Test A  /  Shell          [查看 Logcat] │
├──────────────────────────────────────────────┬──────────────────┤
│ DeviceShellHeader                            │  EASY ACTIONS    │
│  Pixel 6 · 2201117PG · JediTerm · CONNECTED  │                  │
├──────────────────────────────────────────────┤  SYSTEM          │
│                                              │  [Reboot]        │
│           JediTerm (SwingPanel)              │  [Recovery]      │
│           黑底绿字 / Carbon 主题              │                  │
│           （转场完成后挂载）                   │  DISPLAY         │
│                                              │  [Screenshot]    │
│                                              │  [Screen Record] │
│                                              │                  │
│                                              │  APP CONTROL     │
│                                              │  [Clear Cache]   │
│                                              │  [Force Stop]    │
│                                              │  [Clear Data]    │
└──────────────────────────────────────────────┴──────────────────┘
```

- Easy Actions 栏固定宽度 **240dp**
- JediTerm 区域 `weight(1f)`，最小高度占满主内容区（扣除 Header/Footer）
- Footer 状态栏保持可见

### 6.2 DeviceWallHost 转场规格

**容器：** `DeviceWallHost` 包裹 `DeviceWallScreen` 与 `DeviceShellScreen`，仅在 `NavTab.WALL` 下渲染。

**首选 — 共享元素（`SharedTransitionLayout` + `AnimatedContent`）：**

```
点击 DeviceCard.Shell
  → deviceWallRoute = Shell(deviceId)
  → shellSession 创建，PTY 立即 start
  → 卡片 preview/title sharedElement 飞向 ShellHeader
  → 转场 300ms（Material motion 标准时长）
  → onTransitionComplete → terminalSurfaceReady = true → 挂载 JediTermPanel
```

**降级 — 横向滑动：**

```
进入：slideInHorizontally { fullWidth } + fadeIn
返回：slideOutHorizontally { fullWidth } + fadeOut
时长 280ms，easing FastOutSlowIn
```

**系统返回：** 面包屑「设备墙」、Esc 键（Desktop）、Android 系统返回键均触发 `closeDeviceShell()`，转场方向与进入相反。

### 6.3 交互

| 操作 | 行为 |
|------|------|
| 设备墙 Shell | `openDeviceShell(device)` → 转场进入 Shell；同设备已在 Shell 则 no-op |
| 面包屑「设备墙」/ Esc | `closeDeviceShell()` → 转场回到 Grid |
| destructive 操作 | `AlertDialog` 二次确认 |
| 需包名操作（Clear Cache / Force Stop / Clear Data） | `PackageNameDialog` |
| Screen Record 进行中 | 按钮变「停止录屏」；超时 180s 自动停止 |
| 设备离线 | 会话变 `DISCONNECTED`；终端只读 + 横幅提示 |

### 6.4 i18n（中英）

新增 `shell_*`、`easy_action_*`、`shell_breadcrumb_*` 等 strings；按钮文案与 UI 图对齐。

---

## 7. 生命周期与 teardown

| 触发 | 动作 |
|------|------|
| 面包屑返回 / Esc / 切换 Sidebar 到其他 NavTab | `closeDeviceShell()` + 反向转场 |
| 同设备重复点击 Shell | 已在 Shell 视图则保持 |
| 点击其他设备 Shell | 先 `stop` 旧 session，再 `start` 新 session |
| 设备 `DISCONNECT` / 离线 | `stopDeviceShell(sessionId)` |
| `killAdb` / `restartAdb` | `stopAllDeviceShells()` |
| `ViewModel.onCleared` | `stopAll()` 兜底 |

顺序：**先 stop PTY 进程 + 关闭 JediTermWidget，再清除 `shellSession` 状态**。

---

## 8. Mock / Android 降级

`NoOpDeviceShellService` + `DeviceShellScreen` 占位：

- 显示 Carbon 风格假终端（静态提示 + 禁用输入）
- Easy Actions 在 Mock 下可走 `MockAdbRepository` 模拟日志
- 文案：「真实 Shell 需 Desktop 版并连接 ADB」

---

## 9. 测试计划

| 类型 | 用例 |
|------|------|
| 单元 | `DeviceShellSessionState` 转换；`shellTabId` 生成 |
| 单元 | `JvmDeviceShellService` 构造 adb 命令行（`-s`、环境变量） |
| 单元 | `AppViewModel.openDeviceShell` / `closeDeviceShell` 状态 |
| 单元 | Easy Action → repository 方法调用（Mock runner） |
| 单元 | `killAdb` / 设备断开 → `stop` 被调用 |
| 手动 | 真机 → Shell → 输入 `getprop` → 有输出 |
| 手动 | Reboot / Screenshot / 录屏 / pm clear |
| 手动 | 关闭 Shell 视图 → adb shell 进程不存在 |

CI 使用 **假 Pty 进程**（`echo` / `cmd /c`）替代真实 adb shell。

---

## 10. 风险与缓解

| 风险 | 缓解 |
|------|------|
| SharedTransition 在 Desktop 不稳定 | 自动降级 SLIDE；Settings 可强制滑动 |
| 转场期间 SwingPanel 闪烁 | `terminalSurfaceReady` 延迟挂载 JediTerm |
| LazyGrid 卡片 shared bounds 不准 | 点击时记录卡片 `deviceId`；转场前滚动至可见（可选） |
| JediTerm Swing 与 Compose 渲染层叠 | `SwingPanel` + 固定尺寸；避免与 SidePanel 重叠区域 |
| 单应用清缓存 API 差异 | P0/P1 命令链 + 失败友好提示 |
| Windows PTY 兼容性 | Pty4J 官方支持；CI 以 Linux/Windows 矩阵手动验收 |
| `adb shell` 无 PTY 导致交互异常 | 使用 PtyProcess 包装本地 adb 客户端进程 |
| 录屏长时间占用 | 180s 上限 + 停止按钮 |
| 非专业用户误触 Recovery/Clear | destructive 二次确认 + 操作日志 |
| JediTerm 依赖体积 | 仅 jvmMain 引入；不影响 Android AAR |

---

## 11. 实施阶段概览

| Phase | 内容 |
|-------|------|
| **SH1** | 模型 + `DeviceWallRoute` + `DeviceShellService` 接口 + Gradle 依赖 |
| **SH2** | `JvmDeviceShellService` + JediTerm 主题 + 单元测试 |
| **SH3** | `DeviceWallHost` 转场（SharedElement + Slide 降级）+ sharedElement key 接线 |
| **SH4** | `DeviceShellScreen` + Easy Actions UI + i18n |
| **SH5** | `AdbRepository` Easy Actions API（含单应用 `clearAppCache`） |
| **SH6** | `AppViewModel` 集成；Shell 按钮改路由；teardown 测试 |
| **SH7** | Mock 降级 + README/backlog 更新 + 手动验收 |

---

## 12. 关联文档变更

| 文档 | 变更 |
|------|------|
| `2026-06-11-global-logcat-shell-design.md` | 注明设备墙 Shell 按钮职责已迁移至本规格 |
| `2026-06-11-remaining-work-backlog.md` | RW-F03 状态 → 实施中 |
| `README.md` | Shell 功能描述更新 |

---

## 13. 修订记录

| 日期 | 变更 |
|------|------|
| 2026-06-12 | 初稿 — JediTerm + Easy Actions + 主内容 Shell 视图 |
| 2026-06-12 | 评审修订 — 共享元素转场（Slide 降级）；Clear Cache 改为单应用清缓存 |

批准后下一步：生成 `docs/superpowers/plans/2026-06-12-jediterm-shell.md` 实施计划。
