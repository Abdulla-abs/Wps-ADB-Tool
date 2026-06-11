# WpsAdbTool 未完成工作 Backlog 与优先级评估

> 状态：已评审（§7 决策已确认）；Sprint A/B/C 已于 2026-06-11 实施完成  
> 创建日期：2026-06-11  
> 评审日期：2026-06-11  
> 关联：`2026-06-09-web-to-kmp-design.md`、`2026-06-10-side-panel-tabs-design.md`、`2026-06-11-scrcpy-bridge-design.md`、`2026-06-11-qr-wireless-pairing-design.md`

## 1. 背景

截至 2026-06-11，Desktop 端核心能力已基本落地：

| 已完成规格 | 计划状态 |
|-----------|---------|
| Web → KMP 主迁移（P1–P7） | ✅ |
| Desktop 真实 ADB（原 P8） | ✅ |
| SidePanel 标签栏（Mirror + AppLog，SP1–SP5） | ✅ |
| scrcpy 外部窗口投屏（M1–M5） | ✅ |
| QR 无线扫码配对（QR1–QR5） | ✅ |

本文档汇总**尚未完成或仅部分完成**的工作项，并给出优先级与建议实施顺序，供后续 `writing-plans` 拆分为独立规格与实施计划。

---

## 2. 评估方法

### 2.1 优先级定义

| 级别 | 含义 | 典型特征 |
|------|------|---------|
| **P0** | 阻塞 | 影响日常 Desktop 使用、数据丢失、或构建/运行失败 |
| **P1** | 高 | Desktop 核心功能缺口，用户可感知且无合理降级 |
| **P2** | 中 | 部分实现、UX 偏差、或 Settings 有 UI 无逻辑 |
| **P3** | 低 | 平台扩展（Android 真实能力）、增强型非首版目标 |
| **P4** | 后续 | 各规格「非目标」中明确列出的独立 Phase |
| **P5** | 工程卫生 | 构建警告、文档状态、测试/验收闭环 |

### 2.2 工作量估算

| 标签 | 含义 |
|------|------|
| **S** | ≤ 0.5 天 |
| **M** | 1–2 天 |
| **L** | 3–5 天 |
| **XL** | > 1 周 |

### 2.3 评分维度

每项从 **用户影响**（1–5）、**技术风险若延期**（1–5）、**实现成本**（S/M/L/XL）综合得出推荐优先级。分数越高越应优先处理。

---

## 3. Backlog 明细

### P0 — 阻塞级

#### RW-001 Settings 持久化缺失

| 字段 | 内容 |
|------|------|
| **问题** | `AppSettings`（含 `adbPath`、`scrcpyPath` 等）仅保存在内存 `_settings` StateFlow 中，应用重启后恢复默认值 |
| **证据** | `JvmAdbRepository.saveSettings()` 仅 `_settings.value = settings`；对比 `WirelessDeviceStore` 已有磁盘持久化 |
| **影响** | 每次启动需重新配置 adb/scrcpy 路径，投屏与真实 ADB 体验断裂 |
| **工作量** | M |
| **建议** | 新增 `AppSettingsStore`（JVM：`~/.wps-adb/settings.properties` 或 JSON），启动时 load、保存时 write；Mock 可内存实现 |
| **依赖** | 无 |

#### RW-002 Desktop 构建/运行环境一致性

| 字段 | 内容 |
|------|------|
| **问题** | （a）Gradle 守护进程 JDK 21 vs `run` 任务 JDK 17 曾导致 `UnsupportedClassVersionError`；（b）`:desktopApp:run` 与 configuration cache 不兼容 |
| **证据** | `gradle/gradle-daemon-jvm.properties` → `toolchainVersion=21`；`desktopApp/build.gradle.kts` 中 `compose.desktop.javaHome` provider 触发 config cache 序列化错误 |
| **影响** | IDEA 执行 `run` 失败；开启 `org.gradle.configuration-cache=true` 时构建报错 |
| **工作量** | S（Java 对齐已部分修复：`jvmToolchain(17)`）；config cache 修复约 S |
| **建议** | 1) 确认全模块 JVM 17 编译目标已生效；2) 将 `javaHome` 改为 toolchain `.map {}` 而非 `providers.provider {}`；3) 验证 `./gradlew :desktopApp:run` + configuration cache |
| **依赖** | 无 |

---

### P1 — 高优先级（Desktop 功能缺口）

#### RW-003 QR 配对验收、Step 3 UX 与文档闭环

| 字段 | 内容 |
|------|------|
| **问题** | QR 无线配对代码已实现，但（a）QR5 验收未闭环；（b）成功 UX 未对齐设计 Step 3 |
| **证据** | `docs/superpowers/plans/2026-06-11-qr-wireless-pairing.md` 全部 `- [ ]`；`AppViewModel.startQrPairing()` 在 `QrPairingEvent.Success` 时调用 `closePairingDialog()`，跳过 `PairingDialog` Step 3 |
| **影响** | 用户看不到配对成功确认页；验收与文档滞后 |
| **工作量** | S（验收 + 文档）+ S（Step 3 UX） |
| **建议** | **已确认**：Success 时进入 Step 3 成功态（填充设备名/IP），由用户点「完成」再关闭；移除 ViewModel 内自动 `closePairingDialog()` |
| **依赖** | RW-002（需稳定 run） |

#### RW-015 USB + WiFi 双 transport 设备重复显示（Bug）

| 字段 | 内容 |
|------|------|
| **问题** | QR 无线配对成功后，设备墙出现**两台相同设备**：一条 `ConnectionType.USB`，一条 `ConnectionType.WIFI` |
| **根因** | **与 Legacy TCP/IP 假扫描（RW-010）无关**。USB 仍连接时 `adb devices -l` 会同时列出 USB serial（如 `ABC123`）与无线 serial（如 `192.168.x.x:xxxxx`）；`JvmAdbDeviceParser` 按 serial 是否含 `:` 区分连接类型，`refreshDevicesInternal()` 逐行 1:1 映射为 `Device`，**无硬件级去重** |
| **证据** | `JvmAdbDeviceParser.toDevice()` L52–55；`JvmAdbRepository.refreshDevicesInternal()` L114–117；`mergeWithSavedWirelessDevices()` 仅合并离线已保存无线设备，不处理在线双 transport |
| **影响** | 设备墙误导、批量操作/投屏/AppLog 可能针对「同一台手机的两个条目」 |
| **工作量** | M |
| **建议** | 在 `scheduleDeviceEnrichment` 阶段为每台在线设备查询 `getprop ro.serialno`（或解析 `adb devices -l` 的 `device:` 字段），按硬件 ID 分组；同组多条 transport 时**保留一条**，优先 WiFi serial，USB 作为次要或合并展示 |
| **依赖** | 无；应在 RW-003 验收前修复，避免验收结论被 duplicate 干扰 |

#### RW-004 批量侧载（install-package）真实实现

| 字段 | 内容 |
|------|------|
| **问题** | `GroupManagementScreen` 的「批量侧载」在 JVM 层走 `shell echo batch:install-package` 桩 |
| **证据** | `JvmAdbRepository.runBatchAction()` → `else` 分支 |
| **影响** | 分组页核心批量能力之一不可用 |
| **工作量** | M |
| **建议** | 弹窗选 APK + 对选中设备并行 `adb install`；复用 `parallelThreads`（见 RW-006） |
| **依赖** | RW-001（若侧载需记住最近路径可顺带）、RW-006 |

#### RW-005 批量清除缓存目标包名可配置

| 字段 | 内容 |
|------|------|
| **问题** | `pm clear` 批量操作写死 `com.android.settings` |
| **证据** | `JvmAdbRepository.runBatchAction()` `actionKey.contains("pm clear")` |
| **影响** | 「清除缓存」名称与行为不符，易误导 |
| **工作量** | S |
| **建议** | 弹窗输入包名，或从分组上下文选择应用；短期可在 UI 标注「仅 Settings」 |
| **依赖** | 无 |

---

### P1 — Settings 字段接线（已确认优先「接线」）

> 评审决策：Settings 未接线字段**优先实现接线**，不从 UI 隐藏。以下项由 P2 提升至 P1，纳入 Sprint B。

#### RW-006 Settings 字段接线：parallelThreads

| 字段 | 内容 |
|------|------|
| **问题** | 设置页有线程数滑块，批量操作与安装均为串行 |
| **证据** | `AppSettings.parallelThreads` 仅 `SettingsScreen` 读写 |
| **影响** | 多设备批量操作慢；配置项失去意义 |
| **工作量** | M |
| **建议** | `runBatchAction`、`installApk` 多设备等路径使用 `Semaphore(parallelThreads)` |
| **依赖** | RW-004 可一并受益 |

#### RW-007 Settings 字段接线：scanIntervalSec

| 字段 | 内容 |
|------|------|
| **问题** | 扫描间隔滑块无对应后台定时 `refreshDevices()` |
| **证据** | 全库仅 `SettingsScreen` 引用 `scanIntervalSec` |
| **影响** | 设备墙不会自动刷新；无线设备断连感知延迟 |
| **工作量** | M |
| **建议** | `JvmAdbRepository` 或 `AppViewModel` 启动 `while(isActive) { delay(interval); refreshDevices() }`，设置变更时重启 Job |
| **依赖** | RW-001（间隔持久化才有意义） |

#### RW-008 Settings 字段接线：logRetention

| 字段 | 内容 |
|------|------|
| **问题** | 全局 Logcat 硬编码 `takeLast(200)`，与 Settings 默认 2500 不一致 |
| **证据** | `TerminalLogsPanel.kt`；`AppLog` 标签独立上限 `MAX_APP_LOG_LINES` |
| **影响** | 用户调高保留条数无效 |
| **工作量** | S |
| **建议** | `addLog` 或 UI 层使用 `settings.logRetention`；AppLog 标签可单独常量或共用 |
| **依赖** | RW-001 |

#### RW-009 Settings 字段：minPort / maxPort

| 字段 | 内容 |
|------|------|
| **问题** | 端口范围配置未用于配对或局域网扫描 |
| **证据** | 仅 `SettingsScreen` + `AppSettings` 模型 |
| **影响** | 非标准 ADB 端口环境需手动输入 |
| **工作量** | M |
| **建议** | Legacy 扫描（若实现 RW-010）或手动配对默认端口校验使用该范围 |
| **依赖** | RW-010 可选 |

#### RW-010 Legacy 配对局域网扫描（真实实现）— 搁置

| 字段 | 内容 |
|------|------|
| **问题** | 「扫描局域网」为 `delay(1800)` + 硬编码 `192.168.1.105` |
| **证据** | `PairingDialog.kt` `StepConnectManual.onScan` |
| **影响** | Legacy 手动 IP 路径体验一般；QR 已为主路径 |
| **工作量** | L |
| **状态** | **搁置** — 保留手动 IP 输入；不投入真实子网扫描。用户反馈的「配对后出现两台设备」属 **RW-015**，非本项 |
| **依赖** | 无 |

#### RW-011 Settings 字段：autoApproveKey

| 字段 | 内容 |
|------|------|
| **问题** | 「自动批准 RSA 密钥」勾选无后端 |
| **证据** | 无 `adb` 相关调用读取该标志 |
| **影响** | 首次 USB 调试仍需手动点手机确认 |
| **工作量** | M–L（需调研 `adb keygen` / `ADB_VENDOR_KEYS` 等机制） |
| **建议** | 调研后单独出小规格；短期可在 UI 加「尚未实现」提示 |
| **依赖** | RW-001 |

#### RW-012 Settings 字段：diagnosticTelemetry

| 字段 | 内容 |
|------|------|
| **问题** | 诊断遥测开关无实现 |
| **证据** | 仅 Settings UI |
| **影响** | 无功能影响（占位） |
| **工作量** | S（移除或标注） / L（若真做遥测） |
| **建议** | P2 末：UI 标注「即将推出」或从首版隐藏 |
| **依赖** | 无 |

---

### P3 — 低优先级 / 已搁置（平台扩展）

#### RW-013 Android 端真实 ADB 能力 — 搁置

| 字段 | 内容 |
|------|------|
| **问题** | `androidMain` `PlatformAdbRepository` 固定 `MockAdbRepository()` |
| **证据** | `PlatformAdbRepository.android.kt` |
| **状态** | **已确认搁置** — Android App 暂不考虑真实实现，维持 Mock 演示 |
| **工作量** | XL（若未来重启） |

#### RW-014 Android 端 logcat / 投屏 / QR — 搁置

| 字段 | 内容 |
|------|------|
| **状态** | 随 RW-013 一并搁置；当前 `NoOpScrcpyMirrorService` + Mock QR 降级符合产品定位 |

---

### P4 — 后续 Phase（规格已明确为非目标）

以下项在 `2026-06-11-scrcpy-bridge-design.md` 或 `2026-06-10-side-panel-tabs-design.md` 中列为首版非目标，**不纳入近期 Sprint**，仅作路线图参考：

| ID | 功能 | 来源规格 | 工作量 |
|----|------|---------|--------|
| RW-F01 | SidePanel 内嵌 scrcpy 视频流 | scrcpy-bridge §1.3 | XL |
| RW-F02 | `adb screencap` 截图轮询降级 | scrcpy-bridge §1.3 | M |
| RW-F03 | Mirror 标签真实 `adb shell` 终端 | scrcpy-bridge §1.3 | L |
| RW-F04 | scrcpy 高级参数 UI（码率、录屏） | scrcpy-bridge §1.3 | M |
| RW-F05 | Settings 可选 `sdkPath` | side-panel §10 | S |
| RW-F06 | `TabListenKind.MIRROR_FRAME` 截图帧会话 | side-panel + scrcpy | M |
| RW-F07 | PC 扫描手机展示的 QR（反向流程） | qr-pairing §1.3 | L |
| RW-F08 | 完整 APK 反编译（apktool/jadx） | side-panel §1.3 | L |

---

### P5 — 工程卫生与文档

| ID | 项 | 工作量 | 说明 |
|----|-----|--------|------|
| RW-D01 | 更新各 spec 状态（草案 → 已实现） | S | side-panel、qr-pairing、web-to-kmp 验收 checkbox |
| RW-D02 | QR / scrcpy 计划文档 checkbox 同步 | S | 与代码现状对齐 |
| RW-D03 | Web 原型 README 归档说明 | S | migration plan Task 7.3 |
| RW-D04 | Gradle 升级至 ≥ 8.14.4 | S | 消除 KGP 弃用警告 |
| RW-D05 | Skiko 版本对齐（coil vs compose） | S | `checkJvmMainComposeLibrariesCompatibility` 警告 |
| RW-D06 | SidePanel / scrcpy 设计 §1.3 交叉引用更新 | S | scrcpy-bridge §14 关联文档变更 |

---

## 4. 推荐实施路线图

### Sprint A — 稳定可用 + 配对修复（建议 1 周内）

```
RW-002 构建/运行修复
  → RW-001 Settings 持久化
  → RW-015 USB+WiFi 设备去重 Bug     ← 评审新增，优先于 QR 验收
  → RW-003 QR Step 3 UX + 验收闭环
  → RW-008 logRetention 接线
```

**目标**：Desktop「配一次、一直用」；QR 配对后设备墙只显示一台设备。

### Sprint B — Settings 接线 + 批量（建议第 2 周）

```
RW-007 scanIntervalSec 定时刷新
  → RW-006 parallelThreads
  → RW-009 minPort/maxPort
  → RW-004 批量侧载
  → RW-005 批量 pm clear 可配置
  → RW-011 autoApproveKey
```

**目标**：Settings 各项配置真实生效；分组批量能力可信。

### Sprint C — 体验打磨（按需）

```
RW-012 diagnosticTelemetry（接线或标注）
RW-010 Legacy 假扫描（可选：改为诚实文案，移除假 delay）
```

### 已搁置 / 远期

```
RW-013/014 Android 真实能力（产品已确认不做）
RW-F01 ~ RW-F08 各独立规格
```

---

## 5. 优先级总览矩阵

| ID | 标题 | 优先级 | 工作量 | 用户影响 | 建议顺序 |
|----|------|--------|--------|----------|----------|
| RW-001 | Settings 持久化 | P0 | M | 5 | 1 ✅ |
| RW-002 | 构建/运行/Config Cache | P0 | S | 5 | 2 ✅ |
| RW-015 | USB+WiFi 设备去重 Bug | P1 | M | 5 | 3 ✅ |
| RW-003 | QR Step 3 UX + 验收 | P1 | S | 4 | 4 ✅ |
| RW-008 | logRetention 接线 | P1 | S | 3 | 5 ✅ |
| RW-007 | scanIntervalSec 接线 | P1 | M | 4 | 6 ✅ |
| RW-006 | parallelThreads 接线 | P1 | M | 3 | 7 ✅ |
| RW-009 | minPort/maxPort 接线 | P1 | M | 2 | 8 ✅ |
| RW-004 | 批量侧载真实实现 | P1 | M | 4 | 9 ✅ |
| RW-005 | 批量 pm clear 可配置 | P1 | S | 3 | 10 ✅ |
| RW-011 | autoApproveKey 接线 | P1 | M–L | 2 | 11 ✅ |
| RW-012 | diagnosticTelemetry | P2 | S | 1 | 12 ✅ |
| RW-010 | Legacy 局域网扫描 | 搁置 | L | 1 | — ✅ 诚实文案 |
| RW-013/014 | Android 真实能力 | 搁置 | XL | — | — |
| RW-D01~D06 | 文档与工程卫生 | P5 | S | 1 | 并行 ✅ |

---

## 6. 不在本 Backlog 内（已视为完成）

- SidePanel Mirror / AppLog 全链路（含 logcat teardown）
- scrcpy 外部窗口投屏桥接
- Desktop `JvmAdbRepository` 真实 adb 命令
- APK 包名解析（aapt → apkanalyzer → 文件名降级）
- AppLog：打开应用、监听日志、卸载、过滤
- 无线设备 `WirelessDeviceStore` 持久化与重连

---

## 7. 评审决策（2026-06-11 已确认）

| # | 问题 | 决策 | 对 Backlog 的影响 |
|---|------|------|------------------|
| 1 | Android App 定位 | **暂不考虑 Android 端真实实现** | RW-013/014 标记为「搁置」 |
| 2 | Legacy TCP/IP / 配对后双设备 | **非 Legacy 扫描问题**；QR 配对后 USB 仍连接时 `adb devices` 返回双 serial，应用未去重 → 新增 **RW-015**（P1 Bug） | RW-010 搁置；Legacy 保留手动 IP |
| 3 | Settings 未接线字段 | **优先「接线」** | RW-006/007/008/009/011 提升至 P1，纳入 Sprint B |
| 4 | QR 成功 UX | **对齐设计 Step 3** | RW-003 必须改 `AppViewModel`，Success 后不自动关对话框 |

### RW-015 技术说明（供实施参考）

QR 配对时手机通常仍通过 USB 连接（或无线调试与 USB 并存）。此时 `adb devices -l` 典型输出：

```
2201117PG              device product:xxx model:Phone ...
192.168.1.105:41235    device product:xxx model:Phone ...
```

当前逻辑对每行独立建 `Device`，`id`/`serial` 不同即视为两台设备。修复方向：enrichment 阶段用 `ro.serialno` 或 `device:` 字段识别同一硬件，合并为单条记录并优先展示 WiFi transport。

---

## 8. 下一步

1. ~~评审本文档~~ ✅ 已完成  
2. ~~Sprint A/B/C~~ ✅ 已于 2026-06-11 完成  
3. 远期：RW-F01~F08、RW-013/014（已搁置）

---

## 9. 修订记录

| 日期 | 变更 |
|------|------|
| 2026-06-11 | 初稿：基于代码审查与既有规格交叉比对 |
| 2026-06-11 | 评审：确认 §7 四项决策；新增 RW-015（USB+WiFi 去重）；Settings 接线提升至 P1；Android/Legacy 扫描搁置 |
| 2026-06-11 | 实施：Sprint A/B/C + P5 工程卫生全部完成；§5 矩阵标 ✅ |
