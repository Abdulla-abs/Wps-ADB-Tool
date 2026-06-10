# WpsAdbTool UI 原型迁移至 Kotlin Multiplatform 设计规格

> 状态：草案 — 待评审  
> 来源：`wpsAdbToolForWeb`（React 原型，仅作 UI 参考）→ `WpsAdbTool`（Compose Multiplatform，Android + Desktop）

## 1. 背景与目标

### 1.1 现状

| 项目 | 技术栈 | 状态 |
|------|--------|------|
| `wpsAdbToolForWeb` | React 19、Vite 6、Tailwind CSS 4 | UI 原型，Mock 数据，**仅作设计参考** |
| `WpsAdbTool` | KMP + Compose Multiplatform 1.11.1 | Android + Desktop，占位 UI |

Web 原型中**需要迁移**的功能模块（均为模拟逻辑）：

- **Device Wall** — 设备卡片网格、筛选/排序/搜索
- **Sidebar** — 导航、APK 拖拽安装
- **PairingModal** — 无线 ADB 三步配对向导
- **MirrorDrawer** — 投屏侧栏、Shell 终端、应用切换
- **TerminalLogs** — Logcat 控制台（级别过滤、自动滚动）
- **GroupManagement** — 分组批量操作
- **SettingsPanel** — ADB 全局配置

### 1.2 目标

1. 参照 Web 原型，用 **Compose Multiplatform 重写 UI**，Android / Desktop **双端共享** UI 与业务逻辑
2. 建立 **expect/actual 平台层**，Desktop/Android 接 Mock ADB（后续接真实 ADB）
3. 保留 Carbon 深色 Material3 主题视觉一致性

### 1.3 非目标（首版）

- **Web 端（wasmJs / webApp）** — 不新增 Web 目标，不实现浏览器版本
- **KmpHub 模块** — 不实现 KMP 代码浏览页；Sidebar 仅保留 Device Wall / Groups / Settings 三项导航
- iOS 目标
- Gemini AI 集成
- 生产级 scrcpy 视频流（首版用静态截图 + 模拟帧）

---

## 2. 架构决策

### 2.1 推荐方案：Compose Multiplatform 双端共享（方案 A）

```
┌─────────────────────────────────────────────────────────┐
│                    shared (commonMain)                   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐ │
│  │  Theme   │ │   UI     │ │ ViewModel│ │  Domain    │ │
│  │ Carbon   │ │ Composable│ │ StateFlow│ │  Models    │ │
│  └──────────┘ └──────────┘ └──────────┘ └────────────┘ │
│                         │                                │
│              AdbRepository (interface)                   │
└─────────────────────────┼───────────────────────────────┘
                          │
              ┌───────────┴───────────┐
              ▼                       ▼
         jvmMain               androidMain
         ProcessBuilder        Android Debug Bridge
         Mock → 真实 adb        Mock（后续扩展）
              │                       │
        desktopApp              androidApp
```

**选择理由：**

- Desktop 可直接调用本地 `adb`，是 ADB 管理工具的主战场
- Android 端共享同一套 UI，便于移动端调试场景
- 双端 UI 代码复用率预计 > 95%，无需维护 Web 目标复杂度

### 2.2 备选方案对比

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| **A. CMP 双端共享（推荐）** | commonMain UI + jvm/android actual | 代码复用高、ADB 无浏览器限制 | 无浏览器访问 |
| B. CMP 三端含 Web | 额外 wasmJs 目标 | 浏览器可访问 | 包体积大、ADB 需代理、Alpha 不稳定 |
| C. Desktop-only | 不做 Android | 最简单 | 丢失移动端共享 |

### 2.3 ADB 平台策略

| 平台 | 实现 | 首版 |
|------|------|------|
| Desktop (JVM) | `ProcessBuilder` 执行 adb 命令 | Mock → 真实 adb |
| Android | 受限（需 root 或无线 adb server） | Mock |

`AdbRepository` 接口统一抽象，UI 层不感知数据来源。

---

## 3. 模块与目录结构

```
WpsAdbTool/
├── shared/
│   └── src/
│       ├── commonMain/kotlin/fun/abbas/wps_adb/
│       │   ├── App.kt
│       │   ├── theme/
│       │   │   ├── CarbonTheme.kt
│       │   │   └── CarbonColors.kt
│       │   ├── model/
│       │   ├── data/
│       │   │   ├── MockData.kt
│       │   │   ├── MockAdbRepository.kt
│       │   │   └── AdbRepository.kt
│       │   ├── viewmodel/
│       │   └── ui/
│       │       ├── layout/          # AppShell, Sidebar, Header, Footer
│       │       ├── device/          # DeviceWall, DeviceGrid, DeviceCard
│       │       ├── pairing/         # PairingDialog
│       │       ├── mirror/          # MirrorDrawer
│       │       ├── logs/            # TerminalLogsPanel
│       │       ├── groups/          # GroupManagementScreen
│       │       └── settings/        # SettingsScreen
│       ├── jvmMain/kotlin/fun/abbas/wps_adb/
│       │   └── data/PlatformAdbRepository.kt   # actual → Mock / JvmAdb
│       └── androidMain/kotlin/fun/abbas/wps_adb/
│           └── data/PlatformAdbRepository.kt   # actual → Mock
├── desktopApp/
├── androidApp/
└── wpsAdbToolForWeb/                 # 保留作 UI 参考，不参与构建
```

---

## 4. 数据模型映射

Web `types.ts` → Kotlin `commonMain/model/`：

```kotlin
enum class ConnectionType { WIFI, USB, EMULATOR }
enum class DeviceStatus { ONLINE, OFFLINE, UNAUTHORIZED }
enum class LogLevel { V, D, I, W, E }
enum class NavTab { WALL, GROUPS, SETTINGS }   // 不含 KMP_CODE

data class Device(/* 字段同前 */)
data class AdbLog(/* 字段同前 */)
```

---

## 5. 主题映射

Web `index.css` @theme 变量 → `CarbonColors.kt`：

| CSS 变量 | Kotlin Color | 用途 |
|----------|-------------|------|
| `--color-primary` | `#60F99E` | Android Green，主操作 |
| `--color-background` | `#111316` | 深黑背景 |
| `--color-surface-container` | `#1E2023` | 卡片表面 |
| `--color-error` | `#FFB4AB` | 错误/离线 |
| `--color-secondary-container` | `#4B8EFF` | 交互蓝 |

字体：Inter（UI）、JetBrains Mono（日志/序列号），通过 `compose-resources` 打包。

---

## 6. UI 组件映射

| Web 原型组件 | Compose 组件 | 复杂度 |
|-------------|-------------|--------|
| `App.tsx` | `App.kt` + `AppShell.kt` | 中 |
| `Sidebar.tsx` | `Sidebar.kt`（3 项导航，无 KmpHub） | 中 |
| `DeviceGrid.tsx` | `DeviceGrid.kt` + `DeviceCard.kt` | 高 |
| `PairingModal.tsx` | `PairingDialog.kt` | 高 |
| `MirrorDrawer.tsx` | `MirrorDrawer.kt` | 高 |
| `TerminalLogs.tsx` | `TerminalLogsPanel.kt` | 中 |
| `GroupManagement.tsx` | `GroupManagementScreen.kt` | 中 |
| `SettingsPanel.tsx` | `SettingsScreen.kt` | 低 |
| `KmpHub.tsx` | **不实现** | — |

Lucide Icons → Compose Material Icons Extended 或自定义 Vector。

---

## 7. 状态管理

采用 **ViewModel + StateFlow**（项目已引入 `lifecycle-viewmodel-compose`）。

`AppShell` 持有顶层导航状态（`NavTab.WALL | GROUPS | SETTINGS`）；各 Screen 持有局部 ViewModel。

---

## 8. 分阶段交付

| 阶段 | 内容 | 可验证产出 |
|------|------|-----------|
| **P1** | Domain 模型 + Mock 数据 + AdbRepository 接口 | 单元测试通过 |
| **P2** | CarbonTheme | Desktop 显示主题色块 |
| **P3** | AppShell 布局（Sidebar + Header + Footer） | 双端导航切换 |
| **P4** | Device Wall 完整功能 | 筛选/排序/搜索/卡片 |
| **P5** | PairingDialog + MirrorDrawer | 模态交互 |
| **P6** | TerminalLogs + GroupManagement + Settings | 全部页面 |
| **P7** | Desktop 窗口配置 + 收尾 | `./gradlew :desktopApp:run` |
| **P8（后续）** | Desktop 真实 ADB 接入 | jvmMain ProcessBuilder |

---

## 9. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| MirrorDrawer 视频流 | 首版无真实 scrcpy | 静态截图 + 模拟刷新 |
| Coil 网络图片加载 | Desktop/Android 行为差异 | 使用 Coil 3 KMP 版本 |
| APK 拖拽 | Android 与 Desktop API 不同 | expect/actual 文件选择 |

---

## 10. 验收标准

- [ ] `./gradlew :desktopApp:run` 显示完整 UI，与 Web 原型视觉一致（±5% 像素偏差）
- [ ] `./gradlew :androidApp:assembleDebug` 显示相同 UI
- [ ] Sidebar 仅含 Device Wall / Groups / Settings，**无 KmpHub 入口**
- [ ] 所有 Mock 交互（配对、重启、批量操作、日志）行为与 Web 原型一致
- [ ] `./gradlew :shared:jvmTest` 通过
- [ ] 项目中**无** wasmJs / webApp 模块或配置
