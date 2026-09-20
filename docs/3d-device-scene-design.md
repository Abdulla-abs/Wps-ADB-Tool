# WpsAdbTool 3D Device Scene 设计方案

> Status: Final Design Baseline
> Target: WpsAdbTool Desktop
> Scope: Optional Three.js-based 3D Device Scene
> Principle: 3D is a visualization and interaction layer, not the owner of ADB business logic.

---

## 1. 背景

WpsAdbTool Desktop 当前已经具备传统设备墙及相关设备管理能力，包括：

* ADB 设备发现与周期刷新
* USB / WiFi 设备管理
* 同一物理设备的 USB / WiFi 去重
* 设备截图、电量、存储、Android 版本等信息展示
* 搜索、过滤、排序、自定义排序
* Shell
* Mirror / scrcpy
* Debug / Logcat
* APK 拖放安装
* Disconnect / Reconnect
* SidePanel、Terminal、日志等 Compose Desktop UI

现有 Device Wall 是完整且稳定的基础能力。

本方案在此基础上增加一个**可选的 3D Device Scene**。

用户可以导入自己的 3D 场景，将真实 ADB 设备绑定到场景中的 3D Object，并通过空间化方式观察和选择设备。

3D Device Scene 不替代传统 Device Wall，也不改变 ADB 作为应用核心能力的定位。

---

# 2. 产品目标

3D Device Scene 的核心目标是：

> 允许用户构造自己的 3D 工作空间，并把真实 ADB 设备映射到该空间中的对象。

例如：

```text
3D Lab

Desk_Phone_01  ↔  Pixel 8
Desk_Phone_02  ↔  Galaxy S24
Wall_TV        ↔  Android TV
Rack_Device_01 ↔  Test Device
```

用户点击场景中的设备对象后，WpsAdbTool 找到对应真实设备，并通过 Compose UI 提供设备信息和操作。

3D Scene 解决的是：

* 空间表达
* 场景组织
* 设备选择
* 设备状态可视化

而不是重新实现 ADB 工具链。

---

# 3. 非目标

WpsAdbTool 不成为 3D 建模软件。

3D 编辑能力严格限制在设备场景管理所必需的范围。

## 支持

* 导入 Scene
* 导入独立 3D Asset
* Object Selection
* Device Binding
* Unbind / Rebind
* Position
* Rotation
* Scale
* Camera
* Camera View 保存
* Scene 配置持久化

## 不支持

* Mesh 建模
* Vertex / Face 编辑
* UV 编辑
* Texture 编辑
* Material 编辑器
* Rigging
* 骨骼编辑
* 动画制作
* 完整灯光编辑器
* Blender 类完整 Scene Authoring

复杂场景通过 Blender 等专业软件制作。

WpsAdbTool 只消费最终场景。

---

# 4. 核心设计原则

## 4.1 Classic Device Wall 永久保留

现有 2D Device Wall 不因为 3D 功能而删除或重写。

用户可以：

* 完全关闭 3D
* 使用传统 Device Wall
* 启用 3D Device Scene
* 随时返回传统 Device Wall

3D 功能必须是 Optional Feature。

---

## 4.2 3D 不能成为 ADB 状态源

ADB Device Repository 继续作为设备状态的唯一事实来源。

正确的数据流：

```text
ADB
 ↓
JvmAdbRepository
 ↓
Device State
 ↓
Binding Resolver
 ↓
3D Scene
```

禁止：

```text
Three.js
 ↓
维护自己的 Device State
 ↓
反向决定 ADB 状态
```

Three.js 不拥有真实设备状态。

---

## 4.3 Three.js 负责空间，Compose 负责工具

Three.js 负责：

* Scene
* Camera
* Rendering
* GLB
* Object Picking
* Hover
* Selection
* Transform
* Highlight
* 简单设备状态视觉

Compose/Kotlin 负责：

* ADB
* Device State
* Device Inspector
* Shell
* Mirror
* Debug
* Logcat
* APK Install
* Disconnect
* Reconnect
* Dialog
* Toast
* Settings
* Error handling

核心原则：

> Three.js 只需要知道“用户选择了哪个 Object”。

它不需要知道 Mirror、Shell、Logcat 等业务如何实现。

---

# 5. 总体架构

```text
                    WpsAdbTool Desktop
                           │
                 ┌─────────┴─────────┐
                 │                   │
          Classic Device Wall    3D Device Scene
                 │                   │
             Compose UI           Three.js
                 │                   │
                 │              Scene Object
                 │                   │
                 └────────┬──────────┘
                          │
                          ▼
                       Device
                          │
                          ▼
                  Compose Inspector
                          │
             ┌────────────┼────────────┐
             │            │            │
           Shell        Mirror       Debug
           Logcat       APK          ...
```

Classic Device Wall 和 3D Device Scene 最终共享同一套：

* Device Repository
* Device Identity
* Device Actions
* Tool infrastructure

---

# 6. Scene

用户可以拥有多个 Scene。

例如：

```text
My Lab
Office
Device Rack
Living Room
Testing Room
```

应用保存：

```text
activeSceneId
```

用户可以切换当前 Scene。

第一阶段优先支持：

```text
.glb
```

GLB 单文件可以包含：

* Mesh
* Material
* Texture
* Node hierarchy

相比 `.gltf + .bin + textures`，GLB 更适合 Desktop 应用进行导入、复制、管理和持久化。

---

# 7. Scene 不要求特殊格式

第一版不要求用户按照 WpsAdbTool 专有规范制作 GLB。

任意合法 GLB：

```text
Import
 ↓
Load Scene Graph
 ↓
Select Object
 ↓
Bind Device
```

即可使用。

因此第一版不会因为缺少：

```text
wpsAdb metadata
特殊 object name
特殊 Blender plugin
```

而拒绝场景。

Scene Convention 可以后续增加。

---

# 8. Scene Object 来源

Scene 中可以存在两类 Object。

## 8.1 Environment 自带 Object

例如用户从 Blender 导出：

```text
Lab
├── Desk
├── Chair
├── Phone_A
├── Phone_B
├── Monitor
└── AndroidTV
```

用户可以直接：

```text
Phone_A
   ↓
Bind
   ↓
Pixel 8
```

WpsAdbTool 不要求 Phone_A 使用某种固定模型。

---

## 8.2 独立导入 Asset

用户也可以导入：

```text
phone.glb
tablet.glb
tv.glb
device-panel.glb
custom-device.glb
```

然后将 Asset 放入 Scene。

只提供必要编辑能力：

```text
Position
Rotation
Scale
Bind
Delete
```

不允许进一步编辑 Mesh、Material 等模型内容。

---

# 9. Device Asset 是可选的

WpsAdbTool 不要求用户使用固定 Phone / Tablet / TV 面板。

用户可以：

### 使用 Scene 自带 Object

```text
Phone_A ↔ Pixel
```

### 导入 Device Asset

```text
phone.glb
    ↓
place
    ↓
bind Pixel
```

### 使用任意 Object

```text
Cube_01 ↔ Emulator
```

因此 Binding 的核心定义是：

```text
Scene Object ↔ Device
```

而不是：

```text
Phone Model ↔ Device
```

这使 Scene 系统保持开放。

---

# 10. Device Binding

Device Binding 是 3D Device Scene 最重要的数据关系。

```text
Scene Object
     │
     ▼
SceneBinding
     │
     ▼
Device Identity
```

例如：

```text
Desk_Phone_01  ↔ physical-device-A
Desk_Phone_02  ↔ physical-device-B
Wall_TV        ↔ physical-device-C
```

Binding 属于 Scene。

因此同一个 Device 可以同时存在于：

```text
Scene A
Scene B
Scene C
```

不同 Scene 可以使用不同 Object 表示同一设备。

---

# 11. Stable Device Identity

Scene Binding 不应该直接绑定当前 ADB transport serial。

例如同一设备可能表现为：

```text
USB:
R3CN30ABC

WiFi:
192.168.1.20:5555
```

如果 Binding 保存 WiFi endpoint，那么网络变化可能导致绑定失效。

当前 WpsAdbTool 已经在 JVM Repository 内部执行：

```text
adb shell getprop ro.serialno
```

并维护：

```text
hardwareSerialByTransport
```

用于识别并去重同一物理设备的 USB / WiFi transport。

因此 3D Device Scene 应复用这一能力。

目标模型：

```text
DeviceIdentity
├── hardwareSerial
└── fallbackIdentity
```

SceneBinding 保存：

```text
deviceIdentity
```

而不是简单保存：

```text
transportSerial
```

---

# 12. Device Identity 架构整理

当前 stable hardware identity 主要属于 `JvmAdbRepository` 内部实现细节。

在实现 Scene Binding 前，应将其提升为明确的领域能力。

目标：

```text
ADB Transport
      │
      ▼
Device Identity Resolution
      │
      ▼
Stable Device Identity
      │
      ├── Classic Device Wall
      └── 3D Scene Binding
```

3D 模块禁止重新实现一套：

```text
ro.serialno
USB/WiFi matching
hardware deduplication
```

避免出现两个不同的设备身份系统。

---

# 13. Online / Offline

Binding 不随设备掉线消失。

例如：

```text
Phone_A ↔ Pixel 8
```

Pixel 8 离线后：

```text
Phone_A
   ↓
Binding remains
   ↓
Offline
```

3D Object 可以：

* 降低亮度
* 灰化
* 显示 Offline 状态

重新连接：

```text
Physical Device Identity
        ↓
Binding Resolver
        ↓
Phone_A
        ↓
Online
```

无需重新绑定。

---

# 14. 未绑定设备

Scene 不要求覆盖所有 ADB Device。

例如：

```text
ADB Devices:     8
Scene Bindings:  5
Unbound Devices: 3
```

Compose UI 可以提供：

```text
Unbound Devices (3)
```

帮助用户完成绑定。

Device 不存在 Scene Binding 是合法状态。

Scene Object 不绑定 Device 也是合法状态。

---

# 15. Binding Mode

3D Scene 至少具有两个主要工作模式。

## Normal Mode

```text
Click Object
     ↓
Resolve Binding
     ↓
Device
     ↓
Device Inspector
```

## Binding / Edit Mode

```text
Click Object
     ↓
Object Selected
     ↓
Choose Device
     ↓
Bind
```

例如：

```text
Selected Object

Name:
Desk_Phone_01

Binding:
None

Available Devices:

○ Pixel 8
○ Galaxy S24
○ Android TV

[Bind]
```

已经绑定时：

```text
Change Binding
Unbind
```

---

# 16. Object Transform

对于额外导入的 Asset，提供：

```text
Position
X / Y / Z

Rotation
X / Y / Z

Scale
X / Y / Z
```

可考虑使用 Three.js TransformControls。

Environment 原始 Object 是否允许 Transform 可以根据 Scene Edit Mode 控制。

第一阶段重点是满足：

> 将 Device Asset 放到合理位置。

而不是提供专业建模体验。

---

# 17. Camera

Camera 属于 Scene 配置。

至少保存：

```text
position
target
fov / zoom
```

用户可以：

* Orbit
* Pan
* Zoom
* Reset
* Save Current View

打开 Scene 时恢复上一次保存视角。

后续可以增加：

```text
Overview
Focus Device
Return to Overview
Camera Presets
```

Camera 不属于某个 Device。

---

# 18. Device Selection

Three.js 点击 Object 后只发送 Selection Event。

概念事件：

```text
OBJECT_SELECTED
objectId = "desk_phone_01"
```

Kotlin：

```text
objectId
   ↓
SceneBinding
   ↓
DeviceIdentity
   ↓
Current Device
```

然后 Compose 显示 Device Inspector。

Three.js 不直接调用 ADB。

---

# 19. Device Inspector

Device Inspector 使用 Compose Desktop 实现。

### 19.1 布局架构约束：Split Layout

鉴于 Desktop 平台（尤其是 Windows）下 JCEF / SwingPanel 采用原生原生表面（HWND / NSView），重量级组件会在窗口层叠时遮挡 Compose 的轻量级浮层（例如 DropdownMenu、浮动 Inspector）。

因此正式 3D 页面第一版主动采用**并列分栏布局（Split Layout）**，彻底避免浮动层遮挡风险：

```text
┌──────────────────────────────┬──────────────┐
│                              │              │
│        JCEF / Three.js       │   Compose    │
│                              │  Inspector   │
│                              │              │
│                              │  Actions     │
│                              │              │
└──────────────────────────────┴──────────────┘
```

而不是：

```text
Three.js
   ↑
Compose floating inspector
   ↑
DropdownMenu
```

Split Layout 是第一版消除 native HWND / Compose overlay 冲突的正式技术策略。

Inspector 是轻量设备入口，而不是另一个业务系统。

---

# 20. 与现有 SidePanel 的关系

当前 SidePanel 主要承载：

```text
AppLog
Mirror
```

因此不强制将 Device Inspector 本身做成 SidePanel Tab。

推荐关系：

```text
3D Scene
   ↓
Device Inspector
   ↓
User Action
   ├── Mirror → existing Mirror infrastructure
   ├── Debug  → existing SidePanel/AppLog
   ├── Shell  → existing Shell
   └── ...
```

即：

> Inspector 是入口，现有 Tool UI 是具体工作区。

以后增加 Device Action 时，只需要扩展 Compose/Kotlin，不需要同步扩展 Three.js。

---

# 21. 实时 Device State

当前 Device Repository 已经通过 StateFlow 提供动态设备状态。

3D Scene 不自行轮询 ADB。

数据流：

```text
StateFlow<List<Device>>
        ↓
Binding Resolution
        ↓
Scene Device State
        ↓
Renderer Bridge
        ↓
Three.js
```

Renderer 应尽量接收增量变化：

```text
ADD
UPDATE
REMOVE
ONLINE
OFFLINE
SELECT
```

而不是每次重新创建整个 Scene。

---

# 22. Screenshot

当前 Device 已经提供：

```text
screenshotUrl
```

未来可以实现：

```text
ADB Screenshot
      ↓
Kotlin
      ↓
Renderer Bridge
      ↓
Three.js Texture
      ↓
Device Screen
```

例如把真实手机截图映射到 3D 手机屏幕。

但 Screenshot Texture 是增强功能，不属于 MVP 的基础依赖。

场景必须在没有 Screenshot Texture 的情况下正常工作。

---

# 23. 状态视觉

第一阶段保持克制。

建议支持：

* Hover Highlight
* Selected Highlight
* Online / Offline
* Bound / Unbound
* Focus animation

后续再考虑：

* Battery indicator
* USB / WiFi indicator
* Screenshot texture
* Connection visualization

不建议第一阶段增加大量：

* Particle
* HUD
* Animated connection lines
* Sci-Fi effects

3D 的目标是提升空间认知，而不是制造视觉噪声。

---

# 24. Settings

Settings 增加：

```text
Advanced
└── 3D Device Scene

    Enable 3D Device Scene

    Active Scene
    [ My Lab ▼ ]

    Scenes
    My Lab
    Office
    Device Rack

    [ Import Scene ]
    [ Manage Scenes ]
```

第一阶段不要加入大量 Rendering 参数。

例如：

```text
shadow quality
anti-aliasing
texture quality
GPU profile
```

等实际需要出现后再添加。

---

# 25. AppSettings 边界

当前 `AppSettings` 使用 JVM `Properties` 持久化。

因此只保存轻量全局设置，例如：

```text
threeDSceneEnabled
activeSceneId
```

不要将：

```text
Scene
Bindings
Camera
Imported Assets
Transforms
```

全部塞入 `settings.properties`。

---

# 26. Scene 独立持久化

Scene 使用独立 Store。

概念：

```text
DeviceSceneRepository
├── scenes
├── activeScene
├── importScene()
├── deleteScene()
├── importAsset()
├── bindDevice()
├── unbindDevice()
├── updateTransform()
└── saveCamera()
```

Scene 数据属于独立领域数据，而不是普通应用设置。

---

# 27. Scene 数据模型

概念模型：

```text
DeviceScene
├── id
├── name
├── environment
├── camera
├── importedAssets[]
└── bindings[]
```

Camera：

```text
SceneCamera
├── position
├── target
└── projection settings
```

Asset：

```text
SceneAssetInstance
├── id
├── source
├── position
├── rotation
└── scale
```

Binding：

```text
SceneBinding
├── objectId
└── deviceIdentity
```

具体序列化字段在实现阶段确定。

---

# 28. Scene 文件布局

现有项目已经存在统一的 `AppDataPaths`。

Scene 数据应扩展这一体系。Scene 属于用户持久数据，存放于应用持久根目录（默认 `~/.wps-adb-tool/scenes/`），不跟随 `dataCacheDir`，避免被缓存清理机制清除。

概念目录：

```text
WpsAdbTool persistent root (~/.wps-adb-tool/)
└── scenes/
    ├── <scene-id>/
    │   ├── scene.json
    │   ├── environment.glb
    │   └── assets/
    │       ├── phone.glb
    │       └── custom.glb
    │
    └── <scene-id>/
        └── ...
```

不建议长期直接引用用户原始 GLB 路径。

Import：

```text
User GLB
   ↓
Validate
   ↓
Copy into WpsAdbTool data directory
   ↓
Register Scene
```

这样原始文件被移动或删除不会破坏 Scene。

---

# 29. Scene 删除

删除 Scene 时应明确：

```text
Remove registry
Remove bindings
Remove scene metadata
Remove managed GLB/assets
```

不能遗留大量 orphan resource。

如果未来支持多个 Scene 共用 Asset，则再引入 Asset Registry / reference counting。

第一阶段无需提前复杂化。

---

# 30. Three.js 前端模块

Three.js 应作为独立的小型 Web Module。

推荐结构：

```text
three-device-scene/
├── package.json
├── vite.config.ts
└── src/
    ├── main.ts
    │
    ├── scene/
    │   ├── DeviceScene.ts
    │   ├── SceneLoader.ts
    │   └── CameraController.ts
    │
    ├── object/
    │   ├── ObjectPicker.ts
    │   └── TransformController.ts
    │
    └── bridge/
        └── KotlinBridge.ts
```

Build：

```text
TypeScript
   ↓
Vite
   ↓
dist/
   ↓
JVM Resources
   ↓
Desktop Package
```

Three.js 模块禁止直接调用 ADB。

---

# 31. Kotlin ↔ JavaScript Bridge

Bridge 保持窄接口。

## Kotlin → Three.js

概念消息：

```text
LOAD_SCENE
SET_DEVICE_STATE
SET_BINDING_STATE
SELECT_OBJECT
FOCUS_OBJECT
SET_EDIT_MODE
```

## Three.js → Kotlin

概念消息：

```text
SCENE_READY
OBJECT_SELECTED
OBJECT_HOVERED
OBJECT_TRANSFORMED
CAMERA_CHANGED
SCENE_ERROR
```

禁止设计：

```text
RUN_ADB_COMMAND
START_SCRCPY
INSTALL_APK
START_LOGCAT
```

这类命令不属于 Renderer Bridge。

---

# 32. Renderer Host

经 Phase 0 Spike 验证，技术选型决策如下：

* **JavaFX WebView**：已明确排除。其底层 WebKit 引擎未实现 WebGL 支持，调用 `new THREE.WebGLRenderer()` 报错失败。
* **JCEF (Java Chromium Embedded Framework)**：**选定为第一候选宿主**。已完成 WebGL 2.0、Raycaster 拾取、双向 IPC、尺寸伸缩、重置销毁及发行打包的 100% 验证。

### 32.1 生产版 Renderer 安全与数据边界约束

Phase 0 Spike 验证完成后，正式实现不得原样继承实验代码，必须遵守以下架构底线：

1. **禁止宽松安全参数**：正式实现严禁开启 `--disable-web-security` 或 `--ignore-gpu-blocklist`，必须以标准现代 Chromium 安全沙箱与最小权限运行。
2. **统一数据根目录与生命周期边界**：严禁创建非标准的 `~/.wpsadb`；统一通过项目既有的 `AppDataPaths` 管理：JCEF runtime 缓存属于可清理缓存（`cache/jcef-bundle`，随 `dataCacheDir` / `cacheRoot`），而 3D Scene 持久化数据直接位于应用持久根目录（`~/.wps-adb-tool/scenes/`），不跟随 `dataCacheDir`，避免缓存清理时误删用户场景定义与模型资产。
3. **精准源资源加载**：本地内嵌服务不得对所有外部开放（禁止通用 `Access-Control-Allow-Origin: *`），只允许绑定到内部环回地址的本应用源访问。
4. **依赖规范化**：JCEF 及关联依赖统一收拢于项目 Version Catalog（`libs.versions.toml`），不采用临时写死的字符串依赖。
5. **Split Layout 技术策略**：因重量级原生 HWND/NSView 窗口无法安全被轻量 Compose 浮层（如 DropdownMenu）覆盖，第一版 3D 页面强制采用左右并列分栏布局。

---

# 33. Renderer Host 选择标准

至少验证：

## Runtime

* Three.js 正常启动
* WebGL 正常
* GLB 正常加载
* Mouse Picking
* Resize
* Focus
* Keyboard
* Renderer Dispose
* Renderer Recreate

## Bridge

* Kotlin → JavaScript
* JavaScript → Kotlin
* Structured message
* Error propagation

## Compose

* Compose Desktop embedding
* Native surface behavior
* Overlay / z-order
* SidePanel interaction
* Dialog interaction
* Drag & Drop

## Packaging

* Windows MSI
* macOS DMG
* packaged resource loading
* Browser native resources
* WebGL in packaged application

## Release

* Windows x64
* macOS arm64
* macOS x64
* signing
* notarization

## Cost

* Installer size
* Runtime memory
* Startup time
* Native dependency complexity

只有 PoC 完成后才能确定最终 Browser Host。

---

# 34. APK Drag & Drop

当前 Desktop APK Drag & Drop 由 Compose/AWT 处理。

3D Scene 不应重新实现文件系统 Drop Pipeline。

长期可以：

```text
Three.js
   ↓
Hovered Object
   ↓
Kotlin knows selected/bound Device

Compose/AWT
   ↓
APK path
   ↓
existing APK install
```

是否能在 Browser Surface 上稳定完成 Drag & Drop，需要 Renderer Host PoC 验证。

---

# 35. Failure Isolation

3D 是 Optional Feature，因此必须完全隔离失败。

可能失败：

```text
Browser unavailable
WebGL unavailable
Renderer init failure
GLB corrupted
Scene file missing
Bridge failure
Asset failure
```

处理：

```text
3D Device Scene unavailable

[Retry]
[Use Classic Device Wall]
```

失败不得：

* Crash 整个应用
* 破坏 ADB Repository
* 阻止传统 Device Wall
* 损坏 Scene Store

---

# 36. 资源生命周期

Renderer 必须明确支持：

```text
create
load
resize
pause
dispose
recreate
```

Scene 切换时：

* dispose old Three.js resources
* dispose textures
* dispose geometry/material where owned
* release event listeners
* release browser references where possible

防止长期使用后 GPU / native memory 泄漏。

---

# 37. AppSettings 保存注意事项

当前 Repository 的 `saveSettings()` 会：

```text
save settings
 ↓
restart device scan
 ↓
check ADB
 ↓
refresh devices
```

因此未来仅修改：

```text
3D enabled
activeSceneId
```

也可能触发不必要的 ADB refresh。

第一阶段可以暂时接受。

后续应考虑区分：

```text
Application Preference Change
```

与：

```text
ADB Runtime Settings Change
```

但该重构不属于 3D MVP 前置条件。

---

# 38. Phase 0 — Renderer Host Spike

第一步不是改造 Device Wall。

首先建立一个可丢弃的 Desktop PoC。

目标：

```text
Compose Desktop
      ↓
Browser Host
      ↓
Three.js
      ↓
Simple GLB
```

验证：

```text
GLB
WebGL
Object Picking
Kotlin → JS
JS → Kotlin
Resize
Dispose
Resource Loading
```

PoC 不连接真实 ADB。

PoC 不修改现有 Device Wall 业务。

---

# 39. Phase 0 验收标准

Development：

```text
✓ Three.js scene
✓ WebGL
✓ GLB
✓ mouse interaction
✓ object selection
✓ Kotlin → JS
✓ JS → Kotlin
✓ resize
✓ dispose/recreate
```

Packaging：

```text
✓ Windows MSI
✓ macOS DMG
✓ packaged assets load
✓ WebGL works after packaging
✓ required native browser resources included
```

Release：

```text
✓ Windows x64
✓ macOS arm64
✓ macOS x64
✓ signing strategy understood
✓ notarization not blocked
```

只有 Phase 0 通过后才正式引入 Renderer Host。

---

# 40. Phase 1 — Scene Domain

建立与 Renderer 无关的数据层：

```text
DeviceScene
SceneCamera
SceneBinding
SceneAssetInstance
DeviceSceneRepository
SceneStore
SceneImporter
```

同时整理：

```text
Stable Device Identity
```

这一阶段重点是：

> 即使没有 Three.js，Scene 数据模型也应该能够独立测试。

---

# 41. Phase 2 — 3D Scene MVP

连接：

```text
DeviceSceneRepository
        ↓
Renderer Bridge
        ↓
Three.js
```

实现：

* Enable / Disable
* Import GLB
* Active Scene
* Load Scene
* Camera
* Object Picking
* Binding Mode
* Device Binding
* Online / Offline
* Scene persistence

完成真正可用的最小 3D Device Scene。

---

# 42. Phase 3 — Device Interaction

增加 Compose Device Inspector。

连接现有：

* Shell
* Mirror
* Debug
* Logcat
* Reconnect
* Disconnect
* 其他现有 Device Actions

原则：

> 调用现有业务能力，不复制实现。

---

# 43. Phase 4 — Imported Assets

增加独立 Asset Import。

支持：

```text
Import Asset
Place
Position
Rotation
Scale
Bind
Delete
```

仍然不增加建模能力。

---

# 44. Phase 5 — Visual Enhancement

基础功能稳定后再考虑：

* Screenshot Texture
* Focus animation
* Camera presets
* Battery visualization
* Connection type visualization
* Scene thumbnails
* Scene preview
* 更好的 Offline 状态

这些不是 MVP 前置条件。

---

# 45. Scene Authoring — Future

“如何制作适合 WpsAdbTool 的 3D Scene”单独作为后续设计。

可能提供：

## Naming Convention

```text
adb_phone_01
adb_tv_01
```

但不强制。

## glTF Extras

未来可以定义：

```text
extras.wpsAdb.bindable
extras.wpsAdb.type
```

## Blender Template

例如：

```text
WpsAdbTool-Lab-Template.blend
```

## Blender Helper

未来可能提供非常小的 Blender 插件：

```text
Select Object
     ↓
Mark as Device Slot
     ↓
Export GLB
```

这些工具的目标是降低 Scene 制作门槛，而不是把 Blender 功能搬进 WpsAdbTool。

---

# 46. Optional Asset Ecosystem — Future

未来可以提供独立于核心程序的 Asset：

```text
Minimal Phone
Tablet
Android TV
Device Rack
Test Bench
Office Desk
Lab
```

用户也可以使用自己的 Asset。

因此核心程序不依赖官方 Asset Pack 才能工作。

未来如果形成社区生态，可以进一步设计 Scene / Asset Package 格式。

---

# 47. 测试策略

## Domain Test

测试：

* Scene serialization
* Scene migration
* Binding
* Unbind
* Stable Device Identity
* Offline resolution
* Asset transform
* Camera persistence
* Scene deletion

## Bridge Test

测试：

* message serialization
* invalid message
* unknown object
* renderer unavailable
* renderer reconnect

## Desktop Integration

测试：

* Scene load
* Scene switch
* Settings
* Device selection
* SidePanel
* Shell
* Mirror
* Drag & Drop

## Packaging

测试：

* Windows MSI
* macOS arm64
* macOS x64
* packaged Web assets
* packaged GLB
* Browser native dependencies

---

# 48. 性能原则

3D Scene 不应该影响未启用 3D 的用户。

因此：

```text
3D disabled
    ↓
Do not initialize Renderer
```

尽可能做到：

```text
No Browser startup
No Three.js runtime
No Scene loading
No GPU allocation
```

只有进入或启用 3D 功能后才初始化相关资源。

---

# 49. 安全原则

Imported Scene / Asset 被视为不可信用户输入。

至少需要：

* 文件类型检查
* 文件存在检查
* 文件大小限制
* GLB load error handling
* 防止任意路径覆盖
* Managed directory normalization
* Scene manifest validation

Three.js Web 内容不得获得任意本地文件系统访问能力。

Renderer Bridge 必须采用有限 API。

不能提供：

```text
execute(command)
readFile(path)
runAdb(args)
```

这种通用高权限 Bridge。

---

# 50. 架构底线

后续实现不得违反以下原则。

### 1. 3D 不替代 Classic Device Wall

Classic Device Wall 始终是完整 fallback。

### 2. ADB 是核心，3D 是 View

```text
ADB → Kotlin → Binding → Three.js
```

### 3. Three.js 不拥有业务状态

真实 Device State 始终来自 Kotlin Repository。

### 4. 业务操作属于 Kotlin / Compose

Three.js 不实现 Mirror、Shell、Debug、APK 等业务流程。

### 5. Scene 与 Device 解耦

Scene 可以没有 Device。

Device 可以没有 Scene。

### 6. Asset 与核心程序解耦

WpsAdbTool 不强制固定 3D Device Model。

### 7. Binding 使用稳定设备身份

不得把临时 WiFi endpoint 当作唯一长期 Binding Key。

### 8. Scene 独立持久化

复杂 Scene 数据不得塞进 `settings.properties`。

### 9. Renderer Host 必须先 PoC

不得在未经打包验证的情况下大规模修改 Device Wall。

### 10. 不演变为建模软件

只实现设备场景管理所必需的编辑能力。

---

# 51. 最终用户体验

普通用户：

```text
Launch WpsAdbTool
       ↓
Classic Device Wall
```

无需知道 Three.js 存在。

高级用户：

```text
Settings
   ↓
Advanced
   ↓
Enable 3D Device Scene
   ↓
Import MyLab.glb
   ↓
Open Scene
```

然后：

```text
Select Phone_A
      ↓
Bind
      ↓
Pixel 8

Select Phone_B
      ↓
Bind
      ↓
Galaxy S24

Save Camera
```

以后打开：

```text
                My Device Lab

       Pixel 8             Galaxy S24
          📱                   📱

                   Android TV
                       📺
```

点击 Pixel：

```text
3D Object
    ↓
Device Binding
    ↓
Pixel 8
    ↓
Compose Device Inspector
    ↓
Shell / Mirror / Debug / Logcat / ...
```

设备从 USB 切换到 WiFi：

```text
Transport changed
       ↓
Stable Device Identity
       ↓
Binding preserved
       ↓
Scene Object remains associated
```

设备离线：

```text
Object remains
     ↓
Offline visual state
```

设备重新上线：

```text
Identity resolved
      ↓
Online
```

---

# 52. 最终定位

3D Device Scene 的目标不是：

> “把当前设备卡片做成立体版本。”

而是：

> **为 WpsAdbTool 提供一个开放、可选、可持久化的 3D Device Scene 系统，使用户能够导入自己的空间和资产，并将真实 ADB 设备映射到这些空间对象中。**

最终职责边界：

```text
WpsAdbTool Core
│
├── ADB
├── Device Identity
├── Device Actions
├── Scene Domain
└── Compose UI
        │
        ▼
Renderer Bridge
        │
        ▼
Three.js
│
├── Scene
├── Camera
├── Objects
├── Selection
└── Visualization
```

这条边界应作为后续所有 3D Device Scene 开发工作的架构基线。
