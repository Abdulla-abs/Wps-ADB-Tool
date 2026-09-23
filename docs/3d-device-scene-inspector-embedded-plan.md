# 3D Scene Inspector 内嵌操作面板实施方案

## 目标

解决 JCEF 3D 视图遮挡 Compose 浮层的问题。Scene 导入、Scene 管理、Asset 导入/删除、设备绑定与 Transform 编辑都在右侧 Inspector 的内容区内完成，不创建 Compose `Dialog`、`AlertDialog`、`DropdownMenu` 或其他覆盖 3D 视口的浮层。

系统文件选择器可以继续使用原生文件对话框，因为它属于独立系统窗口，不是 Compose 轻量浮层。

页面继续采用当前左右 Split Layout：

```text
┌───────────────────────────────┬─────────────────────┐
│                               │ Scene Inspector     │
│         JCEF / Three.js       │                      │
│                               │ [场景 / 设备 / 资产] │
│                               │ 内嵌操作页面         │
└───────────────────────────────┴─────────────────────┘
```

## 当前 UI 中需要替换的浮层

| 当前入口 | 当前实现 | 改造后 |
|---|---|---|
| 顶栏 Import Scene | `SceneImportDialog` | Inspector 的 Scene Import 页面 |
| 顶栏 Manage Scenes | `SceneManageDialog` | Inspector 的 Scene Manage 页面 |
| EDITING 的 Import Asset | `SceneAssetImportDialog` | Inspector 的 Asset Import 页面 |
| 删除 Asset | `AlertDialog` | Inspector 内联删除确认状态 |
| 设备 Bind / Rebind 选择 | `ExposedDropdownMenu` | Inspector 内嵌设备列表选择器 |
| Scene 切换 | `ExposedDropdownMenu` | 顶栏有限数量可留用；若仍被遮挡则迁入 Scene 页面列表 |
| 模式切换 | `SceneModeToolbar` | 顶栏当前控件不覆盖 3D，可保留 |

## 交互设计

### Inspector 页面状态

新增一个局部页面状态，不改变现有 `SceneInteractionMode`（VIEW / BINDING / EDITING）：

```kotlin
sealed interface InspectorPage {
    data object Overview : InspectorPage
    data object ImportScene : InspectorPage
    data object ManageScenes : InspectorPage
    data object ImportAsset : InspectorPage
}
```

初始页面为 `Overview`。导入和管理入口在 Overview 的 Scene 区域中；进入子页面时 Inspector 标题显示返回按钮和页面名称。Scene/Asset 完成导入或用户取消后返回 Overview。

不要把 InspectorPage 持久化到 `SceneRuntimeState`，它只是当前 UI 的临时导航状态。

### Inspector 公共导航

Inspector 顶部：

```text
← 返回（子页面时）  页面标题                 VIEW/BINDING/EDITING
当前 Scene 名称 / ID
```

Overview 页面增加 Scene 管理卡片：

```text
Scene
  [Import Scene] [Manage Scenes]
  当前场景名称
```

所有控件都限制在 Inspector 的滚动容器内，不使用 overlay 或弹窗。

### Scene Import 页面

页面字段：

- GLB 文件路径只读文本或路径摘要
- `Browse…` 按钮打开原生 `FileDialog`
- Scene 名称输入框
- 校验错误内联显示
- 导入进度
- `Import` / `Cancel` 按钮

提交后调用现有 `runtimeHost.importScene(file, sceneName)`。成功时切换到新 Scene 并返回 Overview；失败时留在当前页显示错误。

实现时将 `SceneImportDialog` 的业务和表单逻辑搬成普通 `@Composable` 页面，例如：

```kotlin
@Composable
private fun SceneImportPage(
    runtimeHost: SceneRuntimeHost,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
)
```

不得保留 `Dialog` 容器。

### Scene Manage 页面

使用 `LazyColumn` 展示场景。每个场景行显示：

- 名称和 Active 标记
- Scene ID
- Asset 数和 Binding 数
- `Activate` 按钮
- `Delete` 按钮

删除不弹确认窗。点击 Delete 后，将该行替换为内联确认区：

```text
删除「Lab Scene」？该场景中的模型文件也会删除。
[Confirm Delete] [Cancel]
```

删除当前 Scene 时，使用现有 `SceneRuntimeHost.deleteScene()` fallback 逻辑；删除成功后刷新列表并回到 Overview。若删除失败，保留行并显示错误。

### Asset Import 页面

把 `SceneAssetImportDialog` 改为普通 Inspector 页面，保留原生 `FileDialog`、名称输入、导入状态和校验错误。成功后刷新 Scene Runtime、选中新 Asset（若 Renderer selection API 可用），返回 Overview 的 EDITING 面板。

### Asset 删除确认

在 `EditingModePanel` 当前 Asset 行中，将待删除 asset id 存为 `assetIdPendingDelete`。确认状态在对应资产卡片内展开，不调用 `AlertDialog`。

用户确认后调用 `runtimeHost.deleteAsset(sceneId, assetId)`；成功后清空 pending 状态，失败时保留确认区并显示错误。

### Binding 设备选择

将 Bind 和 Rebind 的 `ExposedDropdownMenuBox` 替换为 Inspector 中可见的内嵌选择列表：

- 展示在线设备名称、型号、Serial 和连接类型
- 当前选择项使用选中边框/单选状态
- 设备较多时使用固定高度的 `LazyColumn`
- 选择设备后点击 `Bind Device` 完成绑定
- 已绑定时提供 `Rebind` 和 `Unbind`，Unbind 使用行内确认
- 空设备列表显示明确空状态

这样避免 DropdownMenu 这种 popup 也被 JCEF 表面压住。

## 代码改动大纲

### `DeviceSceneInspector.kt`

- 删除 `showAssetImportDialog` 和 `assetToDelete` 弹层状态。
- 新增 `InspectorPage` 状态。
- 依据页面状态在 Inspector 中切换 Overview、ImportScene、ManageScenes、ImportAsset。
- 将现有 VIEW / BINDING / EDITING 内容作为 Overview 的模式面板继续复用。
- 顶部增加内嵌页面导航和返回逻辑。
- 删除 `Dialog` / `AlertDialog` 调用。

### `ScenePicker.kt`

- 顶栏 `Import` 和 `Manage` 按钮改为调用 `runtimeHost.openInspectorPage(...)`，或通过 `DeviceSceneScreen` 中提升的回调切换 InspectorPage。
- 不再直接构造 `SceneImportDialog` / `SceneManageDialog`。
- Scene 下拉如实测仍被遮挡，也应替换为 Inspector 的 Manage Scenes 页入口；当前先检查 JCEF 实际覆盖范围，避免保留不可用 popup。

推荐避免让 `SceneRuntimeHost` 持有 Compose 页面状态。页面导航应由 `DeviceSceneScreen` 持有并通过回调传给顶部 SceneToolbar 与右侧 Inspector：

```kotlin
var inspectorPage by remember { mutableStateOf<InspectorPage>(InspectorPage.Overview) }

SceneToolbar(onImport = { inspectorPage = InspectorPage.ImportScene }, ...)
DeviceSceneInspector(page = inspectorPage, onPageChange = { inspectorPage = it }, ...)
```

### `SceneImportDialog.kt` / `SceneAssetImportDialog.kt` / `SceneManageDialog.kt`

- 将文件中的表单与列表内容提取/改造成普通页面 Composable。
- 移除 `Dialog` 容器及 Dialog 专用命名。
- 可以删除旧文件，或将其重命名为 `SceneImportPage.kt`、`SceneManagePage.kt`、`SceneAssetImportPage.kt`。
- 不再从其他位置调用 Dialog 版本。

### `DeviceSceneScreen.kt`

- 持有 `InspectorPage` 临时状态。
- 将页面状态下发给顶栏和 Inspector。
- Inspector 仍固定在右侧 360dp 区域，页面内容滚动。

### `DeviceSceneInspector` 中的 Binding 面板

- 移除 `ExposedDropdownMenuBox`、`ExposedDropdownMenu`。
- 使用 `LazyColumn` 或 selectable `Column` 呈现设备。
- 移除 popup 相关 `ExperimentalMaterial3Api` 标注（若无其他 API 需要）。

## 文件选择器实现注意事项

沿用现有 AWT `FileDialog`，但设置当前 Compose Desktop 窗口为 owner，避免 `null Frame` 导致文件选择器跑到其他窗口后面或失焦：

```kotlin
val owner = LocalWindow.current
val dialog = FileDialog(owner, "Select GLB", FileDialog.LOAD)
```

如当前 Compose 版本没有 `LocalWindow`，通过 Composable 参数向下传递 owner Frame，或使用 `Local AWT Window` 的实际可用 API；不要退回 Compose Dialog。

## 状态和副作用处理

- Import 操作放到 IO dispatcher，结果回到 UI dispatcher更新状态。
- 页面切换不应重复创建 SceneRuntimeHost 或重建 JCEF。
- Scene 切换前继续由 `SceneRuntimeHost` flush 旧 Scene 的 Camera/Transform pending save。
- Scene 删除的 inline confirm 状态以 Scene ID 为 key；列表刷新后清理已不存在的 ID。
- Asset 删除确认状态以 Asset ID 为 key；切换 Scene 后清空。
- `FileDialog` 取消时不清空已经输入的路径，除非用户显式清除。

## 验收标准

1. 点击顶栏 Import Scene 后，右侧 Inspector 切换到 Import Scene 页面，字段和错误提示完整可见。
2. 点击 Manage 后，Inspector 展示场景列表；激活、删除和删除确认都不创建 popup。
3. EDITING 模式导入 Asset 后，Asset 出现在列表中，可选择、调整 Transform 和删除。
4. 删除 Asset 的确认操作显示在 Asset 卡片内。
5. BINDING 模式的设备列表、Bind、Rebind、Unbind 全部在 Inspector 内可见可操作。
6. 全部场景操作过程中，JCEF 3D 视口无需隐藏，Compose 内容没有被其覆盖。
7. Renderer Transform gizmo 仍可在 3D 视口正常交互；Inspector 数值编辑仍通过现有 `SET_OBJECT_TRANSFORM` 更新 Renderer。
8. 页面切换不重建 Renderer，导入和绑定后 Scene 状态实时刷新。

## 实施顺序

1. 在 `DeviceSceneScreen` 建立 InspectorPage 状态与顶部/Inspector 回调。
2. 将 Scene Import、Manage Dialog 转成 Inspector 普通页面。
3. 将 Asset Import Dialog 转成 Inspector 普通页面。
4. 将 Asset 删除 AlertDialog 改成行内确认。
5. 将 Binding 的两个 dropdown 改成可见设备列表。
6. 搜索并确认 Scene UI 内没有遗留 Dialog、AlertDialog、DropdownMenu popup。
7. 编译并手工验收上述 8 项流程。
