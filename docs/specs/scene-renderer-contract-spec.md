# Scene Bridge Renderer Contract Specification

> Status: Frozen Wire Contract (Phase 1C Step 4)  
> Protocol Version: 1  
> Scope: Host (Kotlin Desktop) ↔ Renderer (Web JS/TS Runtime) IPC Protocol  
> Boundary Principle: Protocol & Contract only; strictly zero WebGL / Three.js rendering dependencies.

---

## 1. 架构总览与传输边界

Scene Bridge 连接 Kotlin 宿主环境与嵌入式 Web 渲染端（基于 JCEF）。所有数据通过纯文本 JSON 帧以双向异步方式传递：

```text
┌────────────────────────────────────────────────────────┐
│                   Kotlin Host Layer                    │
│  (SceneRuntimeController -> SceneVisualProjector ->    │
│   SceneBridgeHostController -> SceneBridgeChannel)     │
└───────────────────────────┬────────────────────────────┘
                            │ (BridgeTransport / UTF-8 JSON)
                            ▼
┌────────────────────────────────────────────────────────┐
│                   CEF JS Bridge                        │
│      window.cefBridge.send(rawJsonString)              │
│      window.cefBridge.onMessage(callback)              │
└───────────────────────────┬────────────────────────────┘
                            │ (BridgeEnvelope<T>)
                            ▼
┌────────────────────────────────────────────────────────┐
│               JS/TS Renderer Contract                  │
│   (InitScene, SyncState, SelectionChange, Ready, etc.) │
└────────────────────────────────────────────────────────┘
```

### 职责边界原则
1. **纯净契约**：协议层不包含 Three.js、WebGL、Mesh、Material、灯光或镜头动画代码。
2. **单一事实来源**：设备真实状态始终由 Kotlin Repository 掌控；渲染端仅作为只读视觉展现与点击拾取输入源。
3. **安全隔离**：报文解析异常或未知字段绝不导致宿主或渲染循环崩溃。

---

## 2. 统一报文信封 (Wire Envelope)

所有通过 BridgeTransport 传递的报文必须封装在统一的外层信封中：

```json
{
  "type": "SCENE_INIT",
  "version": 1,
  "timestamp": 1726915200000,
  "payload": { ... }
}
```

### 字段规范与校验不变量
| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :---: | :--- |
| `type` | `string` | 是 | 冻结的 Wire Type 常量标识符 |
| `version` | `number` | 是 | 协议版本号（当前固定为 `1`） |
| `timestamp` | `number` | 是 | 毫秒时间戳（`timestamp >= 0`，不参与精确相等性判定） |
| `payload` | `object` | 是 | 具体报文结构体，必须为有效 JSON 对象 |

---

## 3. 版本协商与握手生命周期 (Handshake Lifecycle)

```text
Renderer (JS/TS)                                    Host (Kotlin)
       │                                                  │
  [Page Loaded]                                           │
       │                                                  │
       │─── 1. RENDERER_READY ───────────────────────────►│ (Channel moves to READY)
       │    { protocolVersion: 1, rendererVersion: "1.0" }│
       │                                                  │
       │◄── 2. SCENE_INIT ────────────────────────────────│ (InitScene)
       │    { sceneDescriptor: { ... } }                  │
       │                                                  │
       │◄── 3. STATE_SYNC ────────────────────────────────│ (SyncState latest)
       │    { snapshot: { ... } }                         │
       │                                                  │
       │─── 4. OBJECT_CLICKED ───────────────────────────►│ (Raycast hit)
       │    { objectId: "phone_1", screenX, screenY }     │
       │                                                  │
       │◄── 5. SELECTION_CHANGE ──────────────────────────│ (Selection resolved)
       │    { selectedObjectId: "phone_1" }               │
       │                                                  │
       │─── 6. RENDERER_ERROR (if error occurs) ─────────►│ (Graceful isolation)
       │    { code: "PROTOCOL_VERSION_MISMATCH" }         │
```

### 握手规则
1. **版本协商**：
   * Renderer 加载完成后发送 `RENDERER_READY`，携带其实现的 `protocolVersion`（当前为 `1`）。
   * Host 校验 `protocolVersion == CURRENT_BRIDGE_PROTOCOL_VERSION`。若一致，通道进入 `READY` 状态，触发初始下行数据同步。
   * 若版本不兼容，Host 触发错误处理，并可通过 `RENDERER_ERROR` 提示版本冲突。
2. **消息保序与防洪**：
   * 在通道达到 `READY` 状态之前，所有下行状态更新在 Host 端被缓冲，仅在握手完成后派发最新的完整 `SyncState` 快照。

---

## 4. 核心协议报文定义

### 4.0 报文状态分类 (Implemented vs Reserved)
为避免渲染端误解报文就绪状态，协议类型明确划分为当前已落地报文与预留报文：

* **已实现核心报文 (Implemented in Phase 1C)**：
  * `SCENE_INIT` (Downlink)：场景静态骨架与可用位点下发
  * `STATE_SYNC` (Downlink)：全量设备视觉状态快照与全局选中同步
  * `SELECTION_CHANGE` (Downlink)：选中目标更新广播
  * `RENDERER_READY` (Uplink)：运行时就绪握手
  * `OBJECT_CLICKED` (Uplink)：物体拾取点击上报
  * `RENDERER_ERROR` (Bidirectional)：错误隔离与上报
* **预留报文 (Reserved for Future Phases)**：
  * `BINDING_UPDATE` (Downlink)：单设备增量更新（优化大场景性能）
  * `CAMERA_COMMAND` (Downlink)：UI 驱动的视角切换指令
  * `OBJECT_HOVERED` (Uplink)：光标悬停拾取
  * `OBJECT_TRANSFORM_CHANGED` (Uplink)：Gizmo 视口位姿交互回传

---

### 4.1 下行：`InitScene` (`SCENE_INIT`)
描述场景静态元数据与位点定义，**不包含相机运行时动画与控制器逻辑**。

```json
{
  "type": "SCENE_INIT",
  "version": 1,
  "timestamp": 1726915200000,
  "payload": {
    "sceneDescriptor": {
      "id": "lab_01",
      "name": "Device Lab",
      "environmentFileName": "environment.glb",
      "camera": {
        "position": { "x": 0.0, "y": 2.5, "z": 5.0 },
        "target": { "x": 0.0, "y": 0.0, "z": 0.0 },
        "fov": 45.0
      },
      "assets": [
        {
          "id": "desk_01",
          "fileName": "desk.glb",
          "name": "Office Desk",
          "transform": {
            "position": { "x": 0.0, "y": 0.0, "z": 0.0 },
            "rotation": { "x": 0.0, "y": 0.0, "z": 0.0 },
            "scale": { "x": 1.0, "y": 1.0, "z": 1.0 }
          }
        }
      ],
      "bindableObjectIds": ["slot_phone_01", "slot_phone_02"]
    }
  }
}
```

---

### 4.2 下行：`SyncState` (`STATE_SYNC`)
同步所有已绑定设备的完整视觉外观与全局选中目标。

```json
{
  "type": "STATE_SYNC",
  "version": 1,
  "timestamp": 1726915200000,
  "payload": {
    "snapshot": {
      "sceneId": "lab_01",
      "selectedObjectId": "slot_phone_01",
      "devices": [
        {
          "objectId": "slot_phone_01",
          "deviceIdentity": "HW_PIXEL_8",
          "displayName": "Pixel 8",
          "status": "ONLINE",
          "connectionType": "USB",
          "visual": {
            "statusColorHex": "#10B981",
            "isEmissive": true,
            "emissiveIntensity": 0.8,
            "badgeText": "ONLINE",
            "isDimmed": false
          }
        }
      ]
    }
  }
}
```

---

### 4.3 选中状态流：`OBJECT_CLICKED` 与 `SELECTION_CHANGE`

#### 上行：`ObjectClicked` (`OBJECT_CLICKED`)
当用户在 3D 视口中点击某个物体时上报：
```json
{
  "type": "OBJECT_CLICKED",
  "version": 1,
  "timestamp": 1726915200000,
  "payload": {
    "objectId": "slot_phone_01",
    "screenX": 300.0,
    "screenY": 450.0,
    "isCtrlPressed": false,
    "isShiftPressed": false
  }
}
```

#### 下行：`SelectionChange` (`SELECTION_CHANGE`)
Host 裁决选中关系并完成设备绑定解析后向渲染端广播：
```json
{
  "type": "SELECTION_CHANGE",
  "version": 1,
  "timestamp": 1726915200000,
  "payload": {
    "selectedObjectId": "slot_phone_01",
    "focusCamera": false
  }
}
```

---

### 4.4 上行：`RendererReady` (`RENDERER_READY`)
Renderer 在 JS 运行时与 Bridge 通道建立完成时发送。解耦具体图形 API，仅包含协议与版本声明：
```json
{
  "type": "RENDERER_READY",
  "version": 1,
  "timestamp": 1726915200000,
  "payload": {
    "protocolVersion": 1,
    "rendererVersion": "1.0"
  }
}
```

---

### 4.5 双向/上行：`RendererError` (`RENDERER_ERROR`)
用于传输通信、协议和内部异常。错误码分为基础协议错误与可选扩展分类：

#### 基础错误码 (BridgeErrorCodes)
* `PROTOCOL_VERSION_MISMATCH`：协议版本不兼容
* `INVALID_MESSAGE`：报文信封格式非法或缺失必要字段
* `INVALID_PAYLOAD`：负载格式或类型错误
* `INTERNAL_ERROR`：渲染端内部未捕获异常

#### 报文示例
```json
{
  "type": "RENDERER_ERROR",
  "version": 1,
  "timestamp": 1726915200000,
  "payload": {
    "code": "PROTOCOL_VERSION_MISMATCH",
    "message": "Bridge protocol version 2 not supported, expected 1",
    "category": "HANDSHAKE"
  }
}
```

---

## 5. 兼容性保证 (Compatibility Invariants)

1. **未知字段忽略 (Tolerant Reader)**：
   * 接收方解析任意报文时，若 JSON 中包含契约定义以外的额外字段（如未来版本新增的扩展属性），必须安全保留或忽略，严禁抛出解析异常。
2. **未知消息类型回退 (Unknown Fallback)**：
   * 当接收到未知或未来定义的 `type` 时，Kotlin 端反序列化为 `SceneBridgeMessage.Unknown`，保留原始 `rawType` 与 `rawPayload`，保证前向兼容性。
3. **时间戳容错**：
   * 测试断言不应强绑定时间戳绝对相等，只需校验 `timestamp >= 0` 且属于数字类型。
