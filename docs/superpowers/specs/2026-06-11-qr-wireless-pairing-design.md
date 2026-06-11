# 无线设备 QR 扫码配对 设计规格

> 状态：已实现（2026-06-11）  
> 关联：`2026-06-09-web-to-kmp-design.md`（PairingDialog 增量扩展）  
> 决策日期：2026-06-11

## 1. 背景与目标

### 1.1 需求来源

用户在「添加无线设备」向导第二步「连接」中，希望新增**二维码**，让 Android 设备扫描 PC 端二维码完成无线 ADB 配对，替代手动输入 IP/端口。

### 1.2 目标

1. 在 `PairingDialog` 第二步提供 **「手动输入 IP」** 与 **「扫码配对」** 两种连接方式（Tab 切换）
2. Desktop（JVM）真实实现 Android 11+ 无线调试 QR 配对协议（与 Android Studio「通过 Wi‑Fi 配对设备」一致）
3. 扫码路径**无需 USB + `adb tcpip 5555`**，仅需手机开启「无线调试」
4. Mock 模式可演示完整 UI 流程（模拟扫码成功）
5. 保留现有 Legacy TCP/IP 手动连接路径，不破坏已有行为

### 1.3 非目标（首版）

- PC 扫描**手机**展示的二维码（反向流程）
- 将 `IP:5555` 编码进 QR 让设备识别（Android 系统不支持）
- Android App 端真实 mDNS / `adb pair`（首版 Desktop 真实 + 双端 Mock UI）
- 局域网子网自动扫描的真实实现（现有模拟扫描保持不动）
- `wpsAdbToolForWeb` 原型同步（KMP 优先）

---

## 2. 协议与约束

### 2.1 Android 11+ QR 配对协议

QR 内容采用 WPA3 扩展格式（与 AOSP / Android Studio 一致）：

```
WIFI:T:ADB;S:<service_instance>;P:<password>;;
```

| 字段 | 说明 |
|------|------|
| `T:ADB` | 标识 ADB 无线配对 |
| `S:` | mDNS 服务实例名，前缀 `studio-`，后缀随机，用于匹配 `_adb-tls-pairing._tcp` |
| `P:` | 10 位随机密码，用于 `adb pair host:port password` |

**设备侧操作路径：** 设置 → 开发者选项 → **无线调试**（点文字进入子菜单）→ **使用二维码配对设备**

> 不可用系统相机直接扫描；必须从「无线调试」子菜单进入扫码界面。

### 2.2 PC 端配对时序

```
PairingDialog Step 2 [扫码 Tab]
  → 生成 serviceName + password + QR payload
  → 渲染 QR 码
  → 启动 mDNS 监听 (_adb-tls-pairing._tcp)
  → 用户手机扫码
  → 设备广播 pairing 服务
  → adb pair <pairing_host>:<pairing_port> <password>
  → 监听 _adb-tls-connect._tcp
  → adb connect <connect_host>:<connect_port>
  → refreshDevices() → 进入 Step 3 成功态
```

### 2.3 与 Legacy 路径对比

| | Legacy TCP/IP | QR 无线调试 |
|--|---------------|-------------|
| Android 版本 | 任意（需 root/调试权限） | **Android 11+** |
| Step 1 | USB + 开启 TCP/IP | 仅确认已开启「无线调试」 |
| Step 2 | 输入 IP:5555 | 展示 QR，等待扫码 |
| ADB 命令 | `adb connect` | `adb pair` → `adb connect` |
| 网络要求 | 同子网 | 同子网 + mDNS（UDP 5353）可用 |

---

## 3. 架构决策

### 3.1 向导流程改造

Step 1 增加**配对方式选择**（影响后续步骤文案与校验）：

```
┌─ Step 1: 准备 ─────────────────────────────────────┐
│  选择连接方式:                                       │
│  ○ Legacy — USB 连接并启用 TCP/IP（现有流程）         │
│  ● QR 配对 — 开启「无线调试」（Android 11+）          │
│  [对应 checklist]                                    │
│  [下一步 →]                                          │
└────────────────────────────────────────────────────┘

┌─ Step 2: 连接 ─────────────────────────────────────┐
│  Legacy 模式:  IP / Port 表单 + 自动扫描（现有）      │
│  QR 模式:      QR 码 + 状态文案 + 取消/刷新 QR        │
└────────────────────────────────────────────────────┘

┌─ Step 3: 完成 ─────────────────────────────────────┐
│  两种模式共用（loading / success / failure）        │
└────────────────────────────────────────────────────┘
```

对话框尺寸由 `640×480` 调整为 **`720×520`**，Step 2 QR 区域 QR 码 `160dp`。

### 3.2 分层与职责

```
┌─────────────────────────────────────────────────────────┐
│ PairingDialog (UI)                                       │
│   ├─ PairingModeSelector (Step 1)                       │
│   ├─ StepConnectManual (Step 2 Legacy)                  │
│   └─ StepConnectQr (Step 2 QR)                          │
├─────────────────────────────────────────────────────────┤
│ AppViewModel                                             │
│   └─ qrPairingSession: StateFlow<QrPairingUiState>      │
├─────────────────────────────────────────────────────────┤
│ AdbRepository                                            │
│   ├─ pairWirelessDevice(ip, port)     // 现有 Legacy    │
│   └─ pairWirelessViaQr(): Flow<QrPairingEvent>  // 新增 │
├─────────────────────────────────────────────────────────┤
│ commonMain                                               │
│   ├─ AdbQrPayloadBuilder        // 凭证 + payload 纯函数 │
│   └─ QrPairingEvent / QrPairingState  // 模型           │
├─────────────────────────────────────────────────────────┤
│ jvmMain                                                  │
│   ├─ AdbMdnsDiscovery           // jmdns 监听/解析       │
│   ├─ JvmWirelessQrPairingService // pair+connect 编排    │
│   └─ JvmAdbRunner.pair()        // adb pair 封装         │
└─────────────────────────────────────────────────────────┘
```

**选择理由：**

- QR payload 生成是纯函数，可单元测试，与 mDNS 解耦
- mDNS / `adb pair` 仅 Desktop 需要，放 `jvmMain`
- `Flow<QrPairingEvent>` 让 UI 订阅状态变化，对话框关闭时 `cancel` 即可清理

### 3.3 依赖选型

| 库 | 用途 | 作用域 |
|----|------|--------|
| `io.github.g0dkar:qrcode-kotlin` | QR 矩阵 → Compose `ImageBitmap` | commonMain |
| `org.jmdns:jmdns` | `_adb-tls-pairing._tcp` / `_adb-tls-connect._tcp` | jvmMain |

### 3.4 状态模型

```kotlin
enum class PairingMethod { LEGACY_TCP, QR_WIRELESS }

sealed class QrPairingEvent {
    data class QrReady(val payload: String, val serviceName: String) : QrPairingEvent()
    data object WaitingForScan : QrPairingEvent()
    data class Pairing(val endpoint: String) : QrPairingEvent()
    data class Connecting(val endpoint: String) : QrPairingEvent()
    data class Success(val device: Device) : QrPairingEvent()
    data class Failure(val message: String) : QrPairingEvent()
    data object Cancelled : QrPairingEvent()
}
```

### 3.5 生命周期与清理

| 触发 | 动作 |
|------|------|
| 切换 Step 2 Tab（QR → Manual） | `cancelQrPairing()` |
| 关闭 PairingDialog | `cancelQrPairing()` |
| QR 配对超时（120s） | 发 `Failure`，停止 mDNS |
| Step 3 成功 | 停止 mDNS，保留设备 |

---

## 4. UI 规格

### 4.1 Step 2 — QR Tab

```
┌──────────────────────────────────────────┐
│ 配置目标主机          [手动输入|扫码配对] │
├──────────────────────────────────────────┤
│  ┌────────┐   1. 手机与电脑在同一 Wi‑Fi    │
│  │ QR 160 │   2. 开发者选项 → 无线调试     │
│  │        │   3. 使用二维码配对设备        │
│  └────────┘   4. 扫描左侧二维码           │
│                                          │
│  ● 等待扫码… / 配对中… / 连接中…          │
│  [刷新二维码]                             │
└──────────────────────────────────────────┘
```

- QR Tab 进入时自动 `startQrPairing()`；无需点「连接」
- 配对成功后自动跳转 Step 3（与 Manual 点连接行为一致）
- 「刷新二维码」= cancel + 重新 start（新凭证）

### 4.2 错误态文案

| 场景 | 中文提示 |
|------|----------|
| mDNS 超时 | 未检测到设备，请确认同一 Wi‑Fi 且已从「无线调试」进入扫码 |
| `adb pair` 失败 | 配对失败：{adb 输出} |
| connect 超时 | 配对成功但连接超时，请在手机「无线调试」页查看 IP 后手动连接 |
| mDNS 被阻断 | 无法发现设备（mDNS 被阻断），请检查路由器 AP 隔离或防火墙 UDP 5353 |

### 4.3 i18n

新增 `pairing_*` 字符串（中英各一套），见实现计划 Task 6。

---

## 5. 测试策略

| 层级 | 内容 |
|------|------|
| 单元 | `AdbQrPayloadBuilderTest` — payload 格式、字符集、serviceName 前缀 |
| 单元 | `JvmAdbRunnerPairTest` — mock ProcessBuilder 验证 `adb pair` 命令行 |
| 单元 | `MockAdbRepositoryQrPairingTest` — Flow 事件序列 |
| 单元 | `SidePanelControllerTest` 模式 — 不涉及；QR 独立 |
| 集成 | 手动：Android 11+ 真机 + Desktop App 扫码 |

mDNS 集成测试不做自动化（依赖局域网环境）；`AdbMdnsDiscovery` 提供可注入的 `ServiceListener` 便于单测解析逻辑。

---

## 6. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 路由器 AP 隔离导致 mDNS 不通 | 错误文案明确提示；Manual 路径作降级 |
| 部分 OEM 延迟广播 connect 服务 | pair 成功后优先同 IP 的 connect 端点；超时提示手动 `adb connect` |
| 对话框关闭 mDNS 泄漏 | `awaitClose` + `finally { jmdns.close() }` |
| QR 库在 Compose Desktop 渲染问题 | 转 `ImageBitmap` 用 `Image()` 展示，首 Task 做 spike |

---

## 7. 验收标准

- [x] Step 1 可选择 Legacy / QR 两种方式，checklist 随模式变化
- [x] Step 2 QR Tab 展示可扫描 QR，状态流转正确
- [x] Step 2 Success 进入 Step 3 成功页（Sprint A RW-003）
- [x] Desktop 真机扫码后可 `adb devices` 看到无线设备
- [x] Legacy 手动连接路径行为不变（子网扫描已改为诚实文案，见 RW-010）
- [x] Mock 模式可演示 QR 全流程
- [x] 关闭对话框 / 切换 Tab 无 mDNS 残留
- [x] `./gradlew :shared:jvmTest :desktopApp:compileKotlin` 通过
