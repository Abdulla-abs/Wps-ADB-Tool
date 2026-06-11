# SidePanel 标签化侧边栏 + APK 安装应用日志 设计规格

> 状态：已实现（2026-06-11）  
> 关联：`2026-06-09-web-to-kmp-design.md`（增量扩展）  
> 决策日期：2026-06-10

## 1. 背景与目标

### 1.1 需求来源

用户在设备墙将 APK 拖放到某台设备卡片进行安装。当 `adb install` 返回成功时，自动打开右侧浮层，用于调试刚安装的应用。浮层需提供应用运行日志监听能力，但**不应在应用未启动时自动开始监听**。

同时，现有设备卡片「投屏 / Mirror」入口产生的右侧浮层，与上述浮层**统一**为同一套 Chrome 风格多标签侧边栏（`SidePanel`），取代当前单一的 `MirrorDrawer`。

### 1.2 目标

1. 将 `MirrorDrawer` 及未来所有右侧浮层，统一迁入 **`SidePanel`**（Chrome 标签栏 + 内容区）
2. APK 安装成功后自动新建 **`AppLog` 标签**（精简右屏，无手机框）
3. 设备卡片「投屏」操作新建 **`Mirror` 标签**（从现有 `MirrorDrawer` 迁移内容）
4. `AppLog` 标签提供 **「打开应用」** 与 **「监听日志」** 两个独立操作，由用户显式触发
5. Desktop 真实 ADB 阶段支持包名解析、`monkey`/`am start` 启动应用、`adb logcat` 流式监听
6. **标签页内启动的一切长驻监听命令，在标签关闭时必须自动停止**，防止进程泄漏、重复监听与串流错乱

### 1.3 非目标（本规格首版）

- 生产级 scrcpy 视频流（`Mirror` 标签仍用静态截图 + 模拟帧，与现有 `MirrorDrawer` 一致）
- 完整 APK 反编译（apktool / jadx）— 包名解析使用业界轻量方案即可
- 将应用级日志合并进底部全局 `TerminalLogsPanel`（两者职责分离）
- Android 端真实 logcat 流（首版 Desktop 真实 ADB + 双端 Mock）

---

## 2. 架构决策

### 2.1 统一右侧浮层：SidePanel + Chrome 标签栏

```
┌──────────────────────────────────────────────────────────┐
│ AppShell                                                  │
│  ┌────────┐  ┌─────────────────────────┐  ┌───────────┐ │
│  │Sidebar │  │ Main Content            │  │ SidePanel │ │
│  │(nav)   │  │ DeviceWall / Groups / … │  │ ┌─┬─┬─┐ × │ │
│  │        │  │                         │  │ Tab Bar   │ │
│  │        │  │                         │  ├───────────┤ │
│  │        │  │                         │  │ Active    │ │
│  │        │  │                         │  │ Tab Page  │ │
│  └────────┘  └─────────────────────────┘  └───────────┘ │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ TerminalLogsPanel（底部全局 Logcat，职责不变）        │ │
│  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

**选择理由：**

- 用户可同时保持多个设备/会话（多标签），与 Chrome 心智模型一致
- `Mirror` 与 `AppLog` 共享容器、关闭/切换逻辑，避免 `mirroredDevice` 与未来的 `appLogSession` 互斥冲突
- `AppShell` 仅渲染一个 `SidePanel`，替代 `uiState.mirroredDevice?.let { MirrorDrawer(...) }`

### 2.2 AppLog 标签：精简右屏（方案 B）

- **无手机框** — 主区域全部留给日志流与操作栏
- 顶栏：设备名、serial、APK 文件名、包名（解析中显示占位符）
- 操作栏：`打开应用` | `监听日志`（toggle）| `清空`
- 日志区：LazyColumn，等宽字体，级别着色，自动滚动（可关）

### 2.3 安装成功触发侧边栏，包名异步解析

- **打开侧边栏的唯一安装条件**：`adb install` 输出判定为 Success（与现有 `JvmAdbRepository` 逻辑一致）
- 包名解析**不阻塞**侧边栏打开；解析完成后启用「打开应用」按钮
- 解析失败时：侧边栏仍打开，按钮 disabled + tooltip 说明

### 2.4 包名解析策略（非反编译）

| 优先级 | 方式 | 平台 | 说明 |
|--------|------|------|------|
| P0 | `aapt dump badging <apk>` | Desktop JVM | 与 adb 同属 Android SDK `build-tools`，输出 `package: name='com.xxx'` |
| P1 | `apkanalyzer manifest application-id <apk>` | Desktop JVM | Google 官方 CLI，输出更干净 |
| P2 | JVM 库解析 APK（如 `net.dongliu:apk-parser`） | Desktop JVM | 无 SDK 时的纯本地降级 |
| Mock | 文件名推导 | commonMain Mock | `com.mock.<sanitized_file_name>` |

`expect/actual suspend fun parseApkMetadata(apkPath: String): ApkMetadata?`

Settings 中已有 `adbPath`；解析器从 adb 路径推导 `build-tools` 下的 `aapt`/`apkanalyzer`，找不到则降级 P2。

### 2.5 全局 Logcat vs 应用 Logcat

| 面板 | 位置 | 数据来源 | 用途 |
|------|------|----------|------|
| `TerminalLogsPanel` | 底部 | `repository.logs` | 全局 ADB 操作、系统事件 |
| `AppLogTabContent` | SidePanel 标签内 | 每标签独立 `List<AdbLog>` | 单应用 logcat 流 |

### 2.6 标签会话生命周期：关闭标签必停监听（硬性约束）

**原则：** 每个标签页是独立会话（`tabId`）。标签内执行的**任何长驻/流式监听命令**（如 `adb logcat`、截图轮询、未来的 scrcpy 流等）必须登记到该 `tabId` 下；**关闭标签时统一 teardown**，不得遗留后台进程或 Flow 订阅。

```
标签创建 (tabId)
  → 用户触发监听类操作
  → TabSessionManager.start(tabId, ListenKind, …)   // 登记 + 启动
  → …
标签关闭 / 被驱逐 / 设备断开 / ADB 重启 / ViewModel 销毁
  → TabSessionManager.stopAll(tabId)                  // 必达路径
  → destroy 进程、cancel 协程、complete Flow
```

**适用关闭路径（均须触发 `stopAll`）：**

| 触发 | 说明 |
|------|------|
| 用户点击标签 `×` | `closeSidePanelTab(tabId)` |
| 标签数超限驱逐最旧标签 | §3.4 自动关闭非活跃标签 |
| 设备断开 `DISCONNECT` | 关闭该设备全部标签 |
| `killAdb` / `restartAdb` | 关闭全部标签 |
| `ViewModel.onCleared` | 应用退出时兜底清理 |

**实现约束：**

- 禁止在 Composable 内直接 `ProcessBuilder` 启动监听；须经 `TabSessionManager`（或 `AppLogSessionManager` 作为其 AppLog 子模块）统一入口
- `SidePanelTab` 的 `id` 即 `sessionId` 主键；一种标签可有多种 `ListenKind`，但均属同一 `tabId`
- `closeSidePanelTab` **先** `stopAll(tabId)`，**再**从 `tabs` 列表移除——顺序不可颠倒
- 切换活跃标签（`selectSidePanelTab`）**不**停止非活跃标签的监听（允许多标签并行监听）；仅关闭标签时停止

**ListenKind 枚举（可扩展）：**

```kotlin
enum class TabListenKind {
    APP_LOGCAT,       // AppLog：adb logcat 流
    MIRROR_FRAME,     // Mirror：截图轮询 / 模拟帧刷新（首版若有定时器须登记）
    // 预留：SCRCPY_STREAM, SHELL_STREAM, …
}
```

---

## 3. 数据模型

### 3.1 新增模型（`commonMain/model/`）

```kotlin
data class ApkMetadata(
    val packageName: String,
    val appLabel: String? = null,
    val versionName: String? = null,
)

data class ApkInstallResult(
    val success: Boolean,
    val message: String,
    val apkPath: String,
    val apkFileName: String,
    val metadata: ApkMetadata? = null,
)

enum class AppLogMonitorState {
    IDLE,           // 未监听
    MONITORING,     // logcat 流活跃
}

sealed class SidePanelTab {
    abstract val id: String
    abstract val title: String
    abstract val device: Device

    data class AppLog(
        override val id: String,
        override val title: String,
        override val device: Device,
        val apkPath: String,
        val apkFileName: String,
        val packageName: String?,
        val appLabel: String?,
        val monitorState: AppLogMonitorState,
        val logs: List<AdbLog>,
    ) : SidePanelTab()

    data class Mirror(
        override val id: String,
        override val title: String,
        override val device: Device,
    ) : SidePanelTab()
}

data class SidePanelState(
    val tabs: List<SidePanelTab> = emptyList(),
    val activeTabId: String? = null,
) {
    val isVisible: Boolean get() = tabs.isNotEmpty()
}
```

### 3.2 AppUiState 变更

```kotlin
// 移除
// val mirroredDevice: Device? = null

// 新增
val sidePanel: SidePanelState = SidePanelState()
```

### 3.3 标签标题规则

| 类型 | 标题格式 | 示例 |
|------|----------|------|
| AppLog | `{device.name} · {apkFileName}` | `Pixel 7 · demo.apk` |
| Mirror | `{device.name}` | `Pixel 7` |

标签栏过长时 truncate，hover 显示完整标题（Desktop tooltip）。

### 3.4 标签去重与上限

- **AppLog**：同 `deviceId + apkFileName` 已存在 → 聚焦已有标签，不重复创建
- **Mirror**：同 `deviceId` 已存在 Mirror 标签 → 聚焦已有标签
- 最大标签数：**8**；超出时关闭最旧（非当前活跃）标签，并调用 `TabSessionManager.stopAll(evictedTabId)` 停止该标签下**全部**监听命令（不仅 logcat）

---

## 4. 组件与目录结构

```
shared/src/commonMain/kotlin/fun/abbas/wps_adb/
├── model/
│   ├── SidePanelTab.kt
│   ├── SidePanelState.kt
│   ├── ApkMetadata.kt
│   └── ApkInstallResult.kt
├── data/
│   ├── ApkMetadataParser.kt            # expect
│   ├── TabSessionManager.kt            # 接口：按 tabId 登记/停止所有 ListenKind
│   └── AppLogSessionManager.kt         # TabSessionManager 的 AppLog 子实现（logcat 进程）
├── viewmodel/
│   ├── AppViewModel.kt                 # 扩展 SidePanel 操作
│   └── SidePanelController.kt          # 可选：从 ViewModel 拆出标签/会话生命周期逻辑
└── ui/sidepanel/
    ├── SidePanel.kt                    # 容器：标签栏 + 内容区
    ├── SidePanelTabBar.kt              # Chrome 风格标签
    ├── AppLogTabContent.kt             # 精简日志页
    └── MirrorTabContent.kt             # 自 MirrorDrawer 迁移（无手机框外的 Shell 等保留）

shared/src/jvmMain/kotlin/fun/abbas/wps_adb/
├── data/
│   ├── ApkMetadataParser.jvm.kt        # aapt / apkanalyzer / apk-parser
│   ├── JvmTabSessionManager.kt         # tabId → 活跃 Process / Job 映射
│   └── JvmAppLogSessionManager.kt      # logcat Process，由 TabSessionManager 调度
└── ...

shared/src/androidMain/kotlin/fun/abbas/wps_adb/
└── data/ApkMetadataParser.android.kt   # Mock 或 PackageManager.getPackageArchiveInfo
```

**废弃（迁移完成后删除）：**

- `ui/mirror/MirrorDrawer.kt` — 逻辑迁入 `MirrorTabContent.kt`

---

## 5. 交互与数据流

### 5.1 APK 拖放安装 → 打开 AppLog 标签

```
DeviceCard.onApkDropped
  → AppViewModel.installApkOnDevice(deviceId, apkPath)
  → AdbRepository.installApkOnDevice() → ApkInstallResult
  → if (result.success)
       SidePanelController.openAppLogTab(device, result)
       launch { metadata = parseApkMetadata(apkPath); updateTabPackageName(...) }
     else
       repository.addLog(E, "ApkInstaller", result.message)
```

### 5.2 设备卡片投屏 → 打开 Mirror 标签

```
DeviceCard.onMirror
  → AppViewModel.onMirrorDevice(device)
  → SidePanelController.openMirrorTab(device)   // 替代 setMirroredDevice
  → repository.addLog(I, "MirrorService", ...)
```

### 5.3 AppLog 标签内操作

**打开应用**

```bash
adb -s <serial> shell monkey -p <packageName> -c android.intent.category.LAUNCHER 1
```

- `packageName == null` → 按钮 disabled
- 执行结果写入全局 `repository.logs`

**监听日志**

- 首次点击：`TabSessionManager.start(tabId, APP_LOGCAT, …)` → 内部 `startAppLogcat` → `Flow<AdbLog>` 追加到该标签 `logs`
- 再次点击 / 点「停止」：`TabSessionManager.stop(tabId, APP_LOGCAT)`
- Desktop 过滤：`adb logcat --pid=$(adb shell pidof -s <package>)`；PID 为空时提示先「打开应用」
- Mock：点击后每 1–2s 生成模拟应用日志行；Mock 侧 Job 同样登记到 `tabId`

**关闭标签（见 §2.6）**

```
closeSidePanelTab(tabId):
  1. TabSessionManager.stopAll(tabId)     // 停止该标签全部 ListenKind，幂等
  2. tabs.remove(tabId)
  3. 若无剩余标签 → sidePanel 不可见
```

- **不依赖** `monitorState` 判断是否停止——以 `TabSessionManager` 登记的实际会话为准，避免状态不同步导致泄漏
- 用户手动「停止监听」仅停止 `APP_LOGCAT` 一类，不关标签；关标签则 `stopAll` 兜底

### 5.4 SidePanel UI 线框

```
┌─────────────────────────────────────┐
│ [Pixel·demo.apk ×] [Galaxy ×]       │  ← SidePanelTabBar
├─────────────────────────────────────┤
│ Pixel 7 Pro                         │
│ RF8M90ABCDE                         │
│ demo.apk                            │
│ com.example.demo                    │
├─────────────────────────────────────┤
│ [打开应用] [监听日志] [清空]          │
├─────────────────────────────────────┤
│ 10:14:23.001 I/MyApp: onCreate      │
│ 10:14:23.045 D/MyApp: init complete │
│ ...                                 │
└─────────────────────────────────────┘
```

宽度：**400.dp**（与现有 `MirrorDrawer` 一致），自右侧贴边，无 AnimatedVisibility 遮罩（主内容区 `padding(end = if (sidePanel.isVisible) 400.dp else 0.dp)`）。

---

## 6. Repository 接口变更

```kotlin
interface AdbRepository {
    // 变更：由 Unit 改为 ApkInstallResult
    suspend fun installApkOnDevice(deviceId: String, apkPath: String): ApkInstallResult

    // 新增
    suspend fun parseApkMetadata(apkPath: String): ApkMetadata?
    suspend fun launchApp(deviceId: String, packageName: String): Result<Unit>
    fun startAppLogcat(deviceId: String, packageName: String, tabId: String): Flow<AdbLog>
    fun stopAppLogcat(tabId: String)
    fun stopAllTabSessions(tabId: String)   // 关闭标签时调用；停止该 tab 下全部监听
    fun stopAllTabSessions()                // killAdb / ViewModel 销毁时调用
}
```

`TabSessionManager` 接口（`commonMain`）：

```kotlin
interface TabSessionManager {
    fun start(tabId: String, kind: TabListenKind, params: TabListenParams)
    fun stop(tabId: String, kind: TabListenKind)
    fun stopAll(tabId: String)              // 幂等；未登记则无操作
    fun stopAll()                           // 清空全部 tab 会话
    fun activeKinds(tabId: String): Set<TabListenKind>
}
```

`JvmAdbRunner` 新增：

- `launchApp(serial, packageName): AdbProcessResult`
- `startLogcat(serial, pid: Int?): Process` — `redirectErrorStream(true)`，stdout 按行解析为 `AdbLog`
- logcat 行解析：匹配 `MM-DD HH:MM:SS.mmm LEVEL/TAG( PID): message` 格式；解析失败则整行作为 message

---

## 7. ViewModel API（新增/变更）

| 方法 | 说明 |
|------|------|
| `installApkOnDevice` | 返回 `ApkInstallResult`；成功时 `openAppLogTab` |
| `onMirrorDevice` | 改为 `openMirrorTab` |
| `closeSidePanelTab(tabId)` | **`TabSessionManager.stopAll(tabId)` 后**移除标签（§2.6 硬性顺序） |
| `selectSidePanelTab(tabId)` | 切换活跃标签；**不**停止其他标签监听 |
| `launchAppInTab(tabId)` | AppLog 标签内「打开应用」（一次性命令，不登记长驻会话） |
| `toggleLogMonitorInTab(tabId)` | `start/stop(tabId, APP_LOGCAT)` |
| `clearAppLogInTab(tabId)` | 清空该标签日志列表（不影响监听状态） |
| `killAdb` / `restartAdb` | `TabSessionManager.stopAll()` + 关闭所有标签 |
| `onDeviceAction(DISCONNECT)` | 对该设备每个标签 `stopAll(tabId)` 后移除 |
| `onCleared()` | `TabSessionManager.stopAll()` 兜底 |

移除：`setMirroredDevice`

---

## 8. 错误处理

| 场景 | 行为 |
|------|------|
| 安装失败 | 不打开 SidePanel；错误写入全局 Logcat |
| 包名解析失败 | AppLog 标签仍显示；「打开应用」「监听日志」disabled + tooltip |
| 监听时 PID 为空 | Toast/内联提示：「请先打开应用」 |
| 设备离线 | 标签保留但操作按钮 disabled；顶栏显示离线状态 |
| logcat 进程异常退出 | `TabSessionManager` 注销该 `APP_LOGCAT` 会话；`monitorState → IDLE`；标签内追加系统提示 |
| 重复 `stopAll(tabId)` | 幂等，无报错 |
| 标签已关闭但 Flow 仍 emit | **禁止**——`stopAll` 须 `cancel` 收集协程并 `destroy` 进程 |
| ADB kill/restart | `TabSessionManager.stopAll()` 后清空所有 SidePanel 标签 |

---

## 9. 分阶段交付

| 阶段 | 内容 | 可验证产出 |
|------|------|-----------|
| **SP1** | `SidePanel` 骨架 + `SidePanelTabBar` + 状态模型；`MirrorTabContent` 从 `MirrorDrawer` 迁移；`onMirrorDevice` 开 Mirror 标签 | Desktop 点击投屏出现标签栏 |
| **SP2** | `ApkInstallResult` + 安装成功自动开 AppLog 标签 + `AppLogTabContent` UI（Mock 日志） | 拖放 APK 成功后右侧出现 AppLog 标签 |
| **SP3** | `parseApkMetadata`（aapt + JVM 降级）+ `launchApp` Mock/Desktop | 「打开应用」可用 |
| **SP4** | Desktop `startAppLogcat` / `stopAppLogcat` 真实流 + `TabSessionManager` 骨架 | 「监听日志」显示真实 logcat；关标签停进程 |
| **SP5** | 删除 `MirrorDrawer`；`AppShell` 主内容区 padding 适配；i18n；**关标签/驱逐/killAdb 全路径 teardown 测试** | `./gradlew :desktopApp:run` 全流程 |

---

## 10. 风险与缓解

| 风险 | 缓解 |
|------|------|
| aapt 路径难找 | 从 `settings.adbPath` 推导 SDK 根目录；Settings 可增加 `sdkPath`（可选，后续） |
| 监听命令进程/协程泄漏 | `TabSessionManager` 统一登记；关标签 `stopAll(tabId)` 为第一道闸；驱逐/killAdb/disconnect/`onCleared` 为兜底；**禁止 UI 层绕过 Manager 启动长驻命令** |
| 标签过多占内存 | 上限 8；每标签日志保留最近 2000 行 |
| Mirror 与 AppLog UI 差异大 | 共享 `SidePanel` 容器，内容区各自 Composable，不强行抽象 |

---

## 11. 验收标准

- [ ] 设备卡片「投屏」在 `SidePanel` 中打开 `Mirror` 标签，不再弹出独立 `MirrorDrawer`
- [ ] APK 拖放安装成功后自动打开 `AppLog` 标签；安装失败不打开
- [ ] `AppLog` 标签无手机框；含「打开应用」「监听日志」「清空」
- [ ] 「监听日志」仅在用户点击后开始；未启动应用时给出明确提示
- [ ] 多标签可切换、可关闭；同设备重复投屏/重复安装聚焦已有标签
- [ ] **关闭任意标签（含超限驱逐、设备断开、killAdb）后，该标签内所有监听命令均已停止**（无残留 adb 子进程 / 协程 Job）
- [ ] **切换标签不停止其他标签的监听**；仅关闭标签时 `TabSessionManager.stopAll(tabId)`
- [ ] 底部 `TerminalLogsPanel` 行为不变，与应用标签日志隔离
- [ ] `TabSessionManager.stopAll` 单元测试：重复调用幂等；关标签后 Flow 不再 emit
- [ ] `./gradlew :shared:jvmTest` 通过（含 `ApkInstallResult`、`parseApkMetadata` 单元测试）
- [ ] `./gradlew :desktopApp:run` 与 `:androidApp:assembleDebug` 编译通过

---

## 12. 与主迁移规格的关系

本规格为 `2026-06-09-web-to-kmp-design.md` 的**增量**，替换其中：

- §6 `MirrorDrawer.kt` → `ui/sidepanel/` 模块
- §7 `mirroredDevice` 状态 → `sidePanel: SidePanelState`
- §8 P5「PairingDialog + MirrorDrawer」→ P5 完成后接 SP1–SP5

主迁移规格中 `MirrorDrawer` 首版描述仍然有效（静态截图 + 模拟 Shell），仅容器形态改为标签页。
