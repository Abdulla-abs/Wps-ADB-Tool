# WpsAdbTool 3D Device Scene 实施文档

> 依据：`docs/3d-device-scene-design.md` 设计基线
>
> 适用分支：`codex/add-3d-device-scene-design`
>
> 当前基线：Phase 0/1 已基本完成，Phase 2 核心闭环已完成，下一步进入 MVP 产品闭环与 Phase 3/4 收尾。

## 1. 文档目的

本文档将 3D Device Scene 设计方案转换为可直接执行的开发计划，供后续实现、代码评审、测试和发布验收使用。

原设计文档继续作为产品和架构约束：

- [3D Device Scene 设计方案](./3d-device-scene-design.md)
- [Scene Renderer Contract Specification](./specs/scene-renderer-contract-spec.md)

本文档只描述“现在如何继续实现”，不重新定义产品边界。

## 2. 当前基线

当前分支已经具备以下能力：

### 已完成

- JCEF Renderer Host 基础能力
- Three.js WebGL 场景运行时
- GLB 环境加载和 fallback 场景
- Object Picking 和对象选择事件
- Kotlin ↔ JavaScript Bridge
- Renderer Ready 握手和协议版本校验
- Scene、Camera、Binding、Asset 基础数据模型
- Scene Store、JSON 序列化和场景目录持久化
- GLB 校验及路径安全检查
- Stable Device Identity 基础能力
- ADB Device State 到 Renderer 的状态同步
- Online / Offline 视觉状态
- Scene Binding 和设备选择
- 3D 页面 Split Layout
- Device Inspector 基础入口
- Shell、Mirror、Debug、Logcat、Reconnect、Disconnect 等现有动作接入

### 已验证

- `renderer-runtime`：29 个测试通过
- `:shared:jvmTest :desktopApp:test`：构建及测试通过

### 尚未完成

- Scene 导入的完整桌面 UI 流程
- Asset 导入、摆放、Transform 编辑和删除闭环
- Transform 和 Camera 变化的持久化闭环
- 明确的 View / Binding / Edit Mode 状态机
- USB / WiFi 切换后的稳定身份端到端验收
- Windows MSI 和 macOS DMG 的发行包验收
- 截图纹理、电量视觉、相机预设等增强能力

## 3. 实施原则

1. Classic Device Wall 不重写、不依赖 3D 模块。
2. ADB Repository 是设备状态唯一事实来源。
3. Scene Store 只保存场景定义、绑定、相机和资源引用。
4. Renderer 不执行 ADB、Shell、Mirror 或设备业务命令。
5. 3D 页面第一版保持 Split Layout，避免 native surface 覆盖 Compose 浮层。
6. 所有用户导入的 GLB 都视为不可信输入，必须校验和复制到应用管理目录。
7. 3D 功能关闭时，不初始化 JCEF、不加载 Scene、不创建 Renderer 资源。
8. 每个阶段先完成可测试的最小闭环，再扩展视觉效果。

## 4. 目标架构

```text
ADB Repository
    │ StateFlow<List<Device>>
    ▼
SceneBindingResolver
    │ ResolvedSceneState
    ▼
SceneRuntimeController
    │ SceneBridgeMessage
    ▼
SceneBridgeHostController
    │ JSON over JCEF
    ▼
RendererRuntime
    ├── SceneLoader
    ├── ObjectPicker
    ├── CameraController
    ├── TransformController
    └── DeviceVisualState
```

用户交互回流：

```text
Object click / transform / camera change
    ▼
RendererRuntime
    ▼
SceneBridgeHostController
    ▼
SceneRuntimeController
    ▼
DeviceSceneRepository
    ▼
SceneStore
```

## 5. 实施阶段

### Phase A：补齐 Scene 导入和管理闭环

目标：用户可以创建、导入、切换和删除一个 Scene。

#### Kotlin 任务

新增或完善：

```text
shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/scene/
├── SceneImporter.kt
├── SceneImportResult.kt
├── SceneValidationError.kt
└── SceneStore.kt
```

建议接口：

```kotlin
interface SceneImporter {
    fun importEnvironment(sourceFile: File, sceneName: String? = null): DeviceScene
    fun importAsset(sceneId: String, sourceFile: File, assetName: String? = null): DeviceScene
}
```

导入流程：

```text
FileChooser
  -> extension check
  -> GLB magic/header check
  -> size limit check
  -> copy into scenes/<scene-id>/
  -> write scene.json
  -> refresh scene list
  -> activate imported scene
```

#### Compose 任务

```text
desktopApp/src/main/kotlin/fun/abbas/wps_adb/scene/ui/
├── SceneImportDialog.kt
├── SceneManageDialog.kt
└── SceneToolbar.kt
```

必须处理：

- 空场景列表
- 当前场景被删除
- 文件选择取消
- 文件格式错误
- 文件过大
- GLB 损坏
- 导入成功提示
- 导入失败提示

#### 验收标准

- 可导入合法 GLB 并立即显示
- 原始文件移动或删除后，场景仍可加载
- 删除 Scene 后 metadata、环境 GLB 和 assets 均被删除
- 3D 关闭时不会触发导入或 JCEF 初始化

### Phase B：完成 Asset Transform 持久化

目标：用户可以导入独立 Asset，调整位置、旋转、缩放并重新打开后保持结果。

#### Domain / Store

在 `DeviceSceneRepository` 中确认并实现：

```kotlin
fun updateTransform(
    sceneId: String,
    objectId: String,
    transform: SceneTransform,
): DeviceScene
```

实现要求：

- `objectId` 同时支持 environment object 和 imported asset id
- 只更新目标对象，不重建其他场景数据
- 更新时间戳
- 原子写入 `scene.json`
- 非法数值、NaN、无穷大和异常缩放值必须拒绝

#### Bridge

下行和上行消息使用现有协议：

```text
OBJECT_TRANSFORM_CHANGED
```

处理链路：

```text
TransformControls
  -> RendererSceneBridge
  -> SceneBridgeHostController
  -> SceneRuntimeController
  -> repository.updateTransform()
```

拖拽过程不要每一帧写磁盘。建议 Renderer 侧发送实时预览，Kotlin 侧更新内存状态，在 `pointerup` 或 debounce 后持久化。

#### 验收标准

- Import Asset 后可在场景中显示
- 拖动、旋转、缩放均可回传 Kotlin
- 重新打开应用后 Transform 保持
- 删除 Asset 后不会留下孤立文件

### Phase C：完成 Camera 持久化

目标：保存并恢复 Scene 的用户视角。

#### Renderer

```text
OrbitControls change
  -> CAMERA_CHANGED
```

相机消息至少包含 position、target、fov。Kotlin 侧最终调用：

```kotlin
repository.saveCamera(sceneId, camera)
```

相机保存应使用 debounce，避免拖动时频繁同步磁盘。

#### 验收标准

- Orbit、Pan、Zoom 后退出场景
- 再次打开 Scene 时恢复视角
- 切换 Scene 时使用各自 Camera
- Camera 数据损坏时回退默认视角

### Phase D：明确交互模式

新增共享状态：

```kotlin
enum class SceneInteractionMode {
    VIEW,
    BINDING,
    EDITING,
}
```

行为定义：

```text
VIEW:
  点击对象 -> 解析 Binding -> 选中 Device -> 显示 Inspector

BINDING:
  点击对象 -> 选择设备 -> Bind / Replace / Unbind

EDITING:
  选择 Asset -> 显示 TransformControls -> 修改并保存 Transform
```

建议文件：

```text
shared/src/commonMain/kotlin/fun/abbas/wps_adb/model/scene/SceneInteractionMode.kt
desktopApp/src/main/kotlin/fun/abbas/wps_adb/scene/SceneRuntimeState.kt
desktopApp/src/main/kotlin/fun/abbas/wps_adb/scene/ui/SceneModeToolbar.kt
```

Renderer 只接收模式和选择状态，不自行决定业务模式。

### Phase E：稳定身份和离线恢复验收

Scene Binding 必须绑定稳定 identity value，而不是当前 transport serial。

重点场景：

```text
USB serial A -> ro.serialno HW-001
WiFi endpoint B -> ro.serialno HW-001
SceneBinding(HW-001) -> USB/WiFi 切换仍指向同一 Object
```

必须覆盖：设备掉线后 Binding 保留、设备恢复后自动回到 ONLINE、transport endpoint 改变不导致重复绑定、无法取得硬件序列号时使用明确的 fallback identity、Binding 到不存在设备时显示 OFFLINE 而不是删除绑定。

相关代码：

```text
shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/JvmDeviceIdentityResolver.kt
shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/scene/DefaultSceneBindingResolver.kt
shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/scene/runtime/DefaultSceneRuntimeController.kt
```

### Phase F：发行包和故障隔离验收

验证目标：

- Windows MSI 和 macOS DMG 能加载 bundled renderer
- JCEF runtime 和 `scene-runtime` 静态资源完整
- 用户 Scene 数据不落入 cache 清理目录
- Renderer 初始化失败不影响 Classic Device Wall
- 关闭 3D 后释放 Browser、Bridge、Coroutine 和临时资源

建议先执行：

```powershell
npm test --prefix renderer-runtime
./gradlew :shared:jvmTest :desktopApp:test --no-daemon
```

再根据当前 Compose Desktop 任务列表执行 MSI/DMG 打包和安装后验证。

## 6. 代码责任边界

### shared

负责 Scene domain、Scene Store、Binding Resolver、Runtime Controller、Bridge 模型和领域测试；不负责 JCEF、Compose UI、Three.js 或 ADB 命令执行。

### desktopApp

负责 JCEF Host、Kotlin ↔ JCEF transport、Compose Scene 页面、导入/管理 UI、Device Inspector 和现有 Device Action 适配；不重复实现 Stable Device Identity。

### renderer-runtime

负责 Three.js Scene/Camera、GLB 加载、Object Picking、TransformControls、视觉状态和 Bridge 收发；不负责 ADB、设备业务或文件持久化。

## 7. 推荐提交拆分

```text
1. feat(scene): add scene importer and validation result
2. feat(scene-ui): add environment import and scene management dialogs
3. feat(scene): persist object transforms from renderer events
4. feat(scene): persist camera changes with debounce
5. feat(scene-ui): add view binding editing modes
6. test(scene): cover transport switch and offline rebinding
7. test(desktop): verify packaged renderer resources
8. docs(scene): record release verification results
```

每个提交都应保持可编译、相关测试通过、不影响 Classic Device Wall，并且不引入 Renderer ↔ ADB 直接调用。

## 8. 测试矩阵

### Domain / Store

- Scene 创建、读取、更新、删除
- GLB 合法性和路径安全
- Asset 导入、删除和 Transform 更新
- Camera 保存和恢复
- JSON schema version
- 损坏数据回退

### Bridge

- Renderer Ready 握手
- 版本不兼容
- Scene 初始化
- State Sync
- Object Clicked
- Binding Update
- Transform Changed
- Camera Changed
- 未知消息和非法 payload 隔离

### UI / Runtime

- 3D 开关
- Scene 切换
- Inspector 设备选择
- Binding / Unbinding
- Renderer 初始化失败
- Renderer dispose / recreate
- Classic Device Wall 回归

### 发布

- Windows MSI
- macOS DMG
- 资源路径
- JCEF runtime
- GPU/WebGL
- 用户数据和缓存目录边界

## 9. 当前下一步

按优先级执行：

1. 实现 `SceneImporter` 和 Scene Import / Manage UI。
2. 打通 `OBJECT_TRANSFORM_CHANGED -> updateTransform()`。
3. 打通 `CAMERA_CHANGED -> saveCamera()`。
4. 增加 `VIEW / BINDING / EDITING` 状态机。
5. 补充 Stable Identity 的 USB/WiFi 切换和离线恢复测试。
6. 完成 Windows MSI / macOS DMG 发行验证。
7. MVP 稳定后，再开始截图纹理、电量状态和相机预设。

## 10. 完成定义

当以下条件全部满足时，Phase 2/3 MVP 才视为真正完成：

- 用户可以导入并重新打开一个 GLB Scene
- 用户可以切换和删除 Scene
- 用户可以绑定、替换和解除绑定设备
- 设备离线时 Binding 不消失
- USB/WiFi 切换后仍能解析到同一设备
- 用户可以导入 Asset 并修改 Position、Rotation、Scale
- Transform 和 Camera 可以持久化
- Inspector 可以调用现有设备动作
- Renderer 失败不会破坏 Classic Device Wall
- 单元测试、桌面测试和发行资源验证全部通过
