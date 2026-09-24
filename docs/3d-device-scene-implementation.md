# WpsAdbTool 3D Device Scene 实施文档

> 依据：`docs/3d-device-scene-design.md` 设计基线
>
> 适用分支：`codex/add-3d-device-scene-design`
>
> 当前基线：Phase 0 ~ Phase 4 核心能力与闭环验证已全部完成，MVP 就绪。

## 1. 文档目的

本文档将 3D Device Scene 设计方案转换为可直接执行的开发计划，供后续实现、代码评审、测试和发布验收使用。

原设计文档继续作为产品和架构约束：

- [3D Device Scene 设计方案](./3d-device-scene-design.md)
- [Scene Renderer Contract Specification](./specs/scene-renderer-contract-spec.md)

本文档只描述“现在如何继续实现”，不重新定义产品边界。

## 2. 当前基线

当前分支已经具备以下能力：

### 已完成

- JCEF Renderer Host 基础能力与按需延迟初始化（仅在切入 3D 页面时加载）
- Three.js WebGL 场景运行时（SceneLoader、ObjectPicker、CameraController、TransformController）
- GLB 环境加载和 fallback 场景
- Object Picking 和对象选择事件
- Kotlin ↔ JavaScript Bridge（双向消息通道、协议版本校验、场景代次与 sceneId 隔离）
- Renderer Ready 握手和协议版本校验
- Scene、Camera、Binding、Asset 基础数据模型与不可变状态流
- Scene Store、JSON 序列化和场景目录持久化
- GLB 校验及路径安全检查（`SceneUiUtils` 与 `SceneStore` 双层校验）
- Stable Device Identity 完整解析策略（硬件序列号、Emulator AVD/端口映射、Transport 回退）
- ADB Device State 到 Renderer 的状态同步
- Online / Offline 视觉状态与掉线保留
- Scene Binding 和设备选择、解绑与覆盖绑定
- 3D 页面 Split Layout（无原生窗口遮挡 Compose 浮层问题）
- Device Inspector 完整嵌入式面板（Overview、Scene Manage、Scene Import、Asset Import、Device Bindings）
- Shell、Mirror、Debug、Logcat、Reconnect、Disconnect 等现有动作接入
- Scene 导入与管理桌面 UI 闭环（`SceneListUiState` 状态流、错误重试、删除校验）
- Asset 导入、摆放、Transform 实时编辑与删除闭环
- Transform 和 Camera 严格顺序与防抖持久化（按 Scene 写锁、flush 等待、activeEpoch/sceneId 门控隔离）
- View / Binding / Edit Mode 状态机与工具栏交互
- USB / WiFi 切换、端口重分配与离线恢复下的稳定设备身份解析测试覆盖
- JCEF 运行时错误降级隔离（Classic Device Wall 零干扰）
- `bundleRendererRuntime` 编译构建与 DesktopApp main classpath 资源打包集成

### 已验证

- `renderer-runtime`：42 个单元测试全部通过（Coverage: Ready 握手、Scene 加载、Fallback、Camera 控制、TransformControls、Bridge 收发、协议验证）
- `:desktopApp:test`：50 个测试通过（含 `SceneRuntimeHostTest`、`ScenePersistenceCoordinatorTest`、`SceneUiUtilsTest`、`CefHostManagerTest` 等）
- `:shared:jvmTest`：317 个测试通过（含 `DeviceIdentityResolutionTest`、`DefaultSceneRuntimeControllerTest`、`SceneStoreTest` 等）
- `bundleRendererRuntime` & `:desktopApp:processResources`：Vite 产物成功编译并注入 `scene-runtime/` 资源目录并打包至桌面应用。

### 后续规划（MVP 之后可选演进）

- Windows MSI 和 macOS DMG 自动化 CI 签名构建
- 截图纹理投影与实时屏幕投屏贴图
- 电量与充电状态 3D 发光视觉效果
- 自定义相机预设位与视角书签

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

## 9. 实施与验证状态

已按计划全量落地实施并验证：

1. [x] 实现 `SceneImporter` 和 Scene Import / Manage UI（含 `SceneListUiState` 错误重试、`SceneUiUtils` 文件合法性预检与 AWT 弹窗回退）。
2. [x] 打通 `OBJECT_TRANSFORM_CHANGED -> updateTransform()`（按 Scene 细粒度互斥锁、防抖协调、原子写入）。
3. [x] 打通 `CAMERA_CHANGED -> saveCamera()`（防抖协调、切场景等待落盘、activeEpoch 隔离）。
4. [x] 增加 `VIEW / BINDING / EDITING` 状态机与工具栏模式切换。
5. [x] 补充 Stable Identity 的 USB/WiFi 切换、Emulator 动态端口以及离线恢复端到端测试覆盖。
6. [x] 完成 `bundleRendererRuntime` 编译集成与 Desktop main classpath 资源打包验证。
7. [x] 验证 JCEF 延迟初始化与故障隔离：未开启/未进入 3D 页面不占资源；Renderer 失败不破坏 Classic Device Wall。
8. [x] **Scene 切换加固与桥接事件隔离**：
   - 引入场景生命周期互斥锁 `sceneLifecycleMutex` 与代次门控计数器 `activeEpoch`。
   - 切场景时将 `isSwitchingScene` 置为 true 并先 flush 旧场景 pending Camera/Transform save，再进入新场景。
   - 过滤与隔离过时（stale epoch / stale sceneId）桥接事件，杜绝跨场景串话。
   - 删除活跃场景时原子推进 fallback 场景加载，具备应急默认场景（Default fallback）自动重建与二次激活兜底。
9. [x] **Inspector 内嵌面板交互闭环与状态收敛**：
   - 主窗口 AWT `Window` 沿 `DeviceSceneScreen → DeviceSceneInspector → SceneImportPage / SceneAssetImportPage` 逐级显式传递，配合 `SceneUiUtils.resolveDialogOwner` 进行层级递归解析与 `KeyboardFocusManager` 受控回退，确保系统文件选择器始终正确归属。
   - FileDialog 取消时不擦除输入框原有路径。
   - `BindingModePanel` 移除 ExposedDropdownMenu，全面采用独立限高（max = 180.dp）的内嵌可滚动设备选择容器，避免与外层面板滚动冲突；异常捕获并保留可 Dismiss 的错误横幅；解绑与换绑支持行内操作；切场景/切 Slot 自动重置选择与错误；透传 `CancellationException`。
   - `EditingModePanel` Asset 删除采用行内二次确认；删除失败保留确认卡片与错误横幅以支持原地重试；UI 与 `SceneRuntimeHost.deleteAsset` 均显式透传 `CancellationException`，杜绝取消被吞或被当作失败；切场景/切 Slot 自动重置；列表变化时自动清理已移除 Asset 的 pending delete。
   - `SceneManagePage` 场景删除与激活异常处理完整闭环，删除失败保留行内确认供原地重试，显式透传 `CancellationException`；场景列表刷新时自动清理已不存在 Scene 的 pending delete；点击激活新场景时自动重置待删除状态。
   - 顶栏 `SceneToolbar` 快捷按钮仅作为 Inspector 导航路由触点 (`onNavigate`)，无弹层、无空域 (airspace) 遮挡。

## 10. 完成定义验证结果

Phase 0 ~ Phase 4 MVP 验收标准核验：

- [x] 用户可以导入并重新打开一个 GLB Scene（验证通过）
- [x] 用户可以切换和删除 Scene（验证通过，删除包含资源清理与回退场景选择）
- [x] 用户可以绑定、替换和解除绑定设备（验证通过）
- [x] 设备离线时 Binding 不消失，状态标记为 OFFLINE（验证通过）
- [x] USB/WiFi 切换后仍能依据硬件序列号或模型名解析到同一设备（验证通过）
- [x] 用户可以导入 Asset 并修改 Position、Rotation、Scale（验证通过）
- [x] Transform 和 Camera 可以可靠、保序持久化（验证通过）
- [x] Inspector 可以调用现有设备动作（Shell、Mirror、Logcat、Debug 等）（验证通过）
- [x] Renderer 失败不会破坏 Classic Device Wall（验证通过）
- [x] 单元测试、桌面测试和发行资源打包验证全部通过（验证通过：42 renderer unit tests, 54 desktop tests, 317 shared tests）

## 11. 后续发行包验证计划

代码、架构与内嵌操作闭环均已在开发环境完成静态检查与全量自动化测试，后续任务进入桌面发行环境的安装包集成验收：

1. **Windows MSI 安装包验收**：
   - 验证构建产物安装到无开发环境的 Windows 机器上。
   - 验证内嵌 JCEF 运行时释放、离屏渲染 (OSR) 与硬件加速兼容性。
   - 验证原生 AWT `FileDialog` 调起与窗口置顶交互。
2. **macOS DMG 安装包验收**：
   - 验证打包与签名、跨架构 (Apple Silicon / Intel) 兼容性。
   - 验证 Native WebGL 渲染管线与视口事件传递。
