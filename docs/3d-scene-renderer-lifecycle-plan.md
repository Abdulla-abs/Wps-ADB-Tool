# Scene / Renderer 生命周期可观测性与故障恢复实施方案

> 状态：本轮缺陷修复完成；完整桌面测试与导航实机回归通过。生命周期专项的更多故障注入用例待补。
> 适用模块：`desktopApp` Scene Runtime、JCEF Host、Bridge Host
> 前置基线：3D Device Scene MVP 与发行包真机测试已完成

2026-09-24 进展：新增可控 Bridge Ready 超时、fake CEF manager 生命周期测试、重复 dispose 测试，并修复超时被当作普通协程取消而使 Runtime 停留在 `WAITING_BRIDGE` 的问题。`close()` 现在等待初始化任务结束，并与 Retry 串行化后释放资源。旧 Host 测试已改为等待异步场景恢复和 Bridge 点击事件，并使用 `close()` 验证异步清理完成；完整 `:desktopApp:test` 通过（71 项）。

2026-09-24 导航回归修复：离开 3D 页会卸载 `SwingPanel`，再次进入时不能复用已脱离原生窗口的 JCEF Browser 组件。`SceneView` 在重复挂载时先调用 Host 的 Renderer Retry，待旧组件失效并创建新 Browser 后再挂载；新增 fake manager 回归测试验证 Browser 实例已更换。用户在当前 Windows 桌面应用连续切换导航多次，确认每次正常显示 3D；运行日志显示各次 Browser 重建后握手与 GLB 加载成功。

## 1. 目标与边界

本轮让用户和开发者能判断 3D Runtime 正处于哪个生命周期阶段、失败发生在哪里，以及重试或退出后资源是否被正确释放。失败时仍能返回 Classic Device Wall，Scene/ADB 业务数据不因此丢失。

只处理桌面端 Renderer Host 生命周期与诊断：

- Scene Runtime 状态表达、错误归类与日志上下文。
- CEF 初始化、EDT Browser 创建、Bridge Ready、Scene 加载、dispose 的阶段协调。
- 失败后回到可用 UI，并通过明确的重试/重建路径恢复。
- 测试初始化、重试、重复进入和关闭竞态。

当前已落地阶段状态、失败分类、诊断编号、CEF page load error 上报、Bridge Ready 超时、显式 Retry、SwingPanel Browser 组件替换和独立 cleanup scope。用户已确认本轮代码真机测试正常。自动化生命周期回归和 Retry/close 并发覆盖仍待补充；具体真机平台、构建版本和逐项结果未提供，本文不推定这些信息。

本轮不改变 Scene JSON、Stable Device Identity、Bridge wire contract 或 ADB 业务；不新增截图贴图、电量动画、相机书签等产品功能。普通运行状态通过现有 Host/Compose 状态流表达；如确需用户可见诊断面板，再单独决定是否扩展 Inspector。

## 2. 当前代码基线与需要解决的缺口

当前实现已经将 `ensureCefAppInitialized()` 放在 IO dispatcher，将 `createBrowserOnEdt()` 切到 Compose Desktop 主 dispatcher；关闭通过异步 `dispose()` 避免 EDT 上 `runBlocking`。Scene 数据加载也在 IO dispatcher。Bridge Ready 后由 `SceneBridgeHostController` 下发 Scene 初始化与状态。

仍需收敛以下边界：

1. `SceneRuntimeState` 只有 `isInitializing` 和 `initError`，区分不了 CEF 初始化、Browser 创建、Bridge 握手、Scene 加载和关闭。
2. Browser 创建成功后当前就清除初始化遮罩，但 Renderer `READY` 握手和 Scene 实际加载还可能未完成。
3. JCEF 页加载错误目前主要写 `System.err`，没有统一映射为 Runtime 阶段或可操作错误。
4. 初始化异常路径需要明确定义部分创建资源的回收；取消、窗口关闭和失败不能把 UI 留在永久 loading 状态。
5. `dispose()` 是 fire-and-forget。它必须使用不依赖已取消 `hostScope` 的清理 scope，否则 parent scope 先取消时清理任务可能无法启动。
6. 日志目前有原始 URL、JS query、消息或异常直接输出的做法；生命周期日志应统一、可筛查，并避免泄露用户场景路径、设备身份和消息 payload。

以上是实施核对项；开始编码时先核查最新实现，若已被其他提交解决则保留正确现状，不重复造轮子。

## 3. 生命周期模型

### 3.1 单一阶段状态

在 `SceneRuntimeState` 增加明确的阶段字段，避免用多个相互矛盾的布尔值表示进度：

```kotlin
enum class SceneRuntimePhase {
    DISABLED,          // 尚未请求创建 Renderer
    INITIALIZING_CEF,  // CEF app/native runtime 初始化，后台线程
    CREATING_BROWSER,  // 创建 JCEF Browser，AWT EDT
    WAITING_BRIDGE,    // 页面加载，等待 Renderer Ready 握手
    LOADING_SCENE,     // Bridge Ready，发送 Scene 初始化并等待加载确认
    READY,             // Scene 可交互
    FAILED,            // 可恢复失败
    DISPOSING,         // 资源正在异步释放
    DISPOSED,          // Host 已关闭
}

data class SceneRuntimeFailure(
    val stage: SceneRuntimePhase,
    val category: SceneRuntimeFailureCategory,
    val userMessage: String,
    val diagnosticId: String,
)

enum class SceneRuntimeFailureCategory {
    CEF_INITIALIZATION,
    BROWSER_CREATION,
    PAGE_LOAD,
    BRIDGE_HANDSHAKE,
    SCENE_LOAD,
    RESOURCE_CLEANUP,
    UNKNOWN,
}
```

建议 `SceneRuntimeState` 演进为：

```kotlin
data class SceneRuntimeState(
    val phase: SceneRuntimePhase = SceneRuntimePhase.DISABLED,
    val connectionState: BridgeConnectionState = BridgeConnectionState.DISCONNECTED,
    val failure: SceneRuntimeFailure? = null,
    val activeSceneId: String? = null,
    val selectedObjectId: String? = null,
    val interactionMode: SceneInteractionMode = SceneInteractionMode.VIEW,
) {
    val isBusy: Boolean
        get() = phase in setOf(
            SceneRuntimePhase.INITIALIZING_CEF,
            SceneRuntimePhase.CREATING_BROWSER,
            SceneRuntimePhase.WAITING_BRIDGE,
            SceneRuntimePhase.LOADING_SCENE,
            SceneRuntimePhase.DISPOSING,
        )
}
```

迁移时检索所有 `isInitializing` / `initError` 用点并统一替换。若兼容性要求短期保留旧属性，应提供只读派生属性，不允许双份可变状态。

### 3.2 阶段迁移

```text
DISABLED
  -> INITIALIZING_CEF
  -> CREATING_BROWSER
  -> WAITING_BRIDGE
  -> LOADING_SCENE
  -> READY

任一运行阶段 -> FAILED -> 用户重试 -> INITIALIZING_CEF 或 CREATING_BROWSER
任一阶段 -> DISPOSING -> DISPOSED
```

状态变更由 `SceneRuntimeHost` 作为唯一 Runtime 生命周期所有者发布。Bridge Host 通过窄回调报告 `RENDERER_READY` / `SCENE_READY`，不得直接修改 Compose StateFlow。连接状态继续由 `channel.state` 独立维护，用于诊断传输层连接；它不能替代 Runtime 阶段。

若当前 Bridge Contract 只有 Renderer Ready，没有 Scene 加载确认，则首轮将 `LOADING_SCENE` 定义为 `InitScene` 已成功发送，发送失败进入 `FAILED`；`READY` 表示 Bridge 可通信且初始 Scene 命令已接受/发出。只有确实需要确认 Three.js 完成 GLB 加载时，才新增 `SCENE_LOADED` 消息，并同步更新 `docs/specs/scene-renderer-contract-spec.md` 及双方校验测试。

## 4. 错误和日志设计

### 4.1 分层错误

- **用户状态**：简短说明失败位置与可用操作，例如“3D 渲染器启动失败。可以重试，或返回设备墙。”
- **结构化诊断**：`diagnosticId`、phase、category、异常类名、经过脱敏的根因摘要、平台/JVM/CEF 版本（可获得时）、sceneId 是否存在（不记录完整路径）。
- **原始堆栈**：仅走开发日志级别；不得把 GLB 文件内容、JS query/payload、完整本机路径或设备 serial 写入普通日志。

建立统一日志入口（可先用轻量 helper，遵循仓库当前日志设施）：

```kotlin
internal object SceneRuntimeLog {
    fun transition(from: SceneRuntimePhase, to: SceneRuntimePhase, attempt: Long) { /* structured */ }
    fun failure(failure: SceneRuntimeFailure, cause: Throwable) { /* structured + sanitized */ }
    fun cleanupFailure(resource: String, cause: Throwable) { /* continue cleanup */ }
}
```

日志事件建议包含 `component=scene-runtime`、`event=phase-transition|failure|cleanup-failure`、`attempt`、`phase`、`category`。不要在异常消息未脱敏时直接串接用户路径。

### 4.2 错误映射

每个异步阶段显式包边界并映射对应错误分类；不要用一个宽泛 catch 将所有失败当成 CEF 初始化失败。`CancellationException` 始终重新抛出，不映射成 `FAILED`；但如果取消源于 Host 关闭，phase 应由关闭流程进入 `DISPOSING`，不得遗留 busy 状态。

JCEF load handler 的 `onLoadError` 需要通过线程安全回调上报到 Host。忽略主动关闭产生的 `ERR_ABORTED`，对其他加载错误只报告阶段/错误码/资源类型，URL 应剥离 query 和本地路径后再记录。错误出现后防止晚到的 Ready 事件把 `FAILED` 覆盖为 `READY`，用 `attemptId` 或生命周期代次进行门控。

## 5. 生命周期所有权和线程规则

### 5.1 线程边界

| 操作 | 执行位置 | 规则 |
|---|---|---|
| Scene 文件读写、GLB 服务准备、CEF native 初始化 | IO dispatcher | 不阻塞 Compose / AWT EDT |
| 创建 Swing/JCEF Browser 组件、修改 AWT 组件层级 | AWT EDT（项目 `mainDispatcher`） | 不在该段执行磁盘 IO 或等待阻塞任务 |
| Compose 状态发布 | Compose 主 dispatcher | 仅在 Host 未关闭且 attempt 仍有效时发布 |
| JCEF/HTTP/Bridge 清理 | 独立 cleanup scope + 适用的 JCEF/EDT 约束 | 不依赖已取消的 UI parent scope；各资源清理失败互不阻断 |

不要在 EDT 调用 `runBlocking`、`join`、同步等待 IO 或等异步关闭完成。不要假定 `Dispatchers.Main` 在每个测试 dispatcher 中自动等同 EDT；对 EDT 约束使用可注入 dispatcher/调度器，测试时提供 fake。

### 5.2 Host 生命周期

- `SceneRuntimeHost` 拥有一次运行期中的 CEF manager、Browser component、bridge adapter 和 controller binding。
- Compose 页面离开时是否销毁 Host 由当前页面/容器所有权决定；页面重组不应创建第二份 Host。
- 一个 Host 的 `close()` 幂等；`dispose()` 只负责安排关闭，不能因 `hostScope` 已取消而漏掉清理。
- 清理顺序为：阻止新事件/推进 generation → 取消 Ready/页面监听和 Bridge controller binding → flush 或按既有规则关闭 persistence → 断开 channel → 释放 Browser/Client/CEF 及 HTTP server → 清空 Swing component 引用 → 标记 DISPOSED。
- 每项清理单独 try/finally，单项异常不能阻止后续资源释放；清理异常记录为 `RESOURCE_CLEANUP`，不覆盖更早的主失败原因。
- 若清理涉及 AWT component detach，应在 EDT 执行；不要将所有 JCEF dispose 调用一概搬到 EDT，需依据 JCEF API 要求逐项确认并在代码注释中标明线程契约。

建议把“停止接收事件”与“清理任务调度”分开。清理协程使用独立于 `hostJob` 的 scope，例如注入 `CoroutineScope(SupervisorJob() + ioDispatcher)` 的 app 级生命周期 scope；由应用窗口/容器拥有并最终等待/关闭。避免无 owner 的永久 GlobalScope。

## 6. 重试和故障恢复

### 6.1 重试策略

第一版只提供显式 `Retry`，不自动无限重试：

1. 失败 UI 保留 Scene/用户数据，提供 `Retry` 和返回传统设备墙的可用入口。
2. Retry 创建新的 `attemptId`，清除旧失败，先使旧 Browser/Bridge 会话失效，再创建新 Browser。
3. 若 JCEF API 和真机结果确认 `CefApp` 可复用，则复用 manager 内 CefApp、重建 Browser/Client 所需资源；否则 dispose 旧 manager 并重新创建。不要凭经验重建整个 native CefApp。
4. 旧 attempt 的迟到回调通过 `attemptId` 丢弃，不能污染新状态。
5. 重试失败时保留新的 diagnosticId 与阶段错误；不得将错误吞掉后停留在 loading。

建议 Host 暴露挂起操作：

```kotlin
suspend fun retryRenderer(): Boolean
```

内部用 `Mutex` 或 operation token 串行化 retry/close，禁止两个 Browser 并发创建。若创建中收到 close，close 应使 attempt 失效；创建流程在下一阶段边界检查 token，必要时立即 dispose 新建资源。

### 6.2 降级策略

- Renderer 未 Ready 或已 Failed：3D 视图显示阶段、简洁原因、Retry；应用其余区域与 Classic Device Wall 正常工作。
- Renderer 运行时崩溃/Bridge 断开：状态从 READY 进入 `FAILED`（或明确的可恢复 `WAITING_BRIDGE`，二者择一并定义超时），用户可 Retry / 离开 3D。
- Scene GLB 加载失败：错误类别为 `SCENE_LOAD`，保留 Scene 和 Binding；不要删除或覆盖场景数据。可提供重试 renderer；Scene 是否可切换由现有 Inspector 功能决定。
- 不在本轮自动切换到空白 fallback Scene 掩盖用户 Scene 加载错误；fallback 只用于 Store 中没有可用 Scene 的既有逻辑。

## 7. 代码改动清单

### 7.1 `SceneRuntimeState.kt`

- 新增 `SceneRuntimePhase`、`SceneRuntimeFailureCategory`、`SceneRuntimeFailure`。
- 将 `isInitializing/initError` 替换为 `phase/failure`，或先提供基于 phase 的只读兼容属性。
- 为状态迁移定义 Host 私有 helper，统一校验有效 attempt，避免散落 `_state.update`。

### 7.2 `SceneRuntimeHost.kt`

- 保存当前初始化 `Job`、`attemptId` 和 cleanup scope/provider。
- 将 `initializeJcefBrowser()` 拆成阶段明确的挂起流程：创建 manager → CEF 初始化(IO) → Browser 创建(EDT) → 等待 Bridge Ready → 发起 Scene 初始化 → READY。
- 在每个挂起边界后校验 `isDisposed` 与 attemptId。
- 捕获阶段异常，生成分类后的 `SceneRuntimeFailure`；CancellationException 原样传播。
- 实现串行化 `retryRenderer()`；替换现有不可重试的 init error 状态。
- `close()` 保持幂等，并 ensure 所有清理步骤执行；`dispose()` 调度到有明确 owner 的 cleanup scope。
- 检查构造期间 Scene store 初始化、JCEF 初始化同时运行时，错误是否正确上报且 READY 不早于 Renderer 握手。

伪代码轮廓（不是最终签名）：

```kotlin
private suspend fun startRenderer(attempt: Long) {
    transition(attempt, SceneRuntimePhase.INITIALIZING_CEF)
    val manager = createAndPublishManager(attempt)
    manager.ensureCefAppInitialized()
    checkAttemptActive(attempt)

    transition(attempt, SceneRuntimePhase.CREATING_BROWSER)
    val component = withContext(mainDispatcher) { manager.createBrowserOnEdt() }
    checkAttemptActive(attempt)
    publishBrowserComponent(attempt, component)

    transition(attempt, SceneRuntimePhase.WAITING_BRIDGE)
    awaitRendererReady(attempt, timeout = bridgeReadyTimeout)
    transition(attempt, SceneRuntimePhase.LOADING_SCENE)
    awaitInitialSceneAccepted(attempt) // 依现有协议语义决定是否可确认
    transition(attempt, SceneRuntimePhase.READY)
}
```

Ready 等待应使用可取消的 `CompletableDeferred`/事件流，并绑定 attempt；不能用 `delay` 轮询 StateFlow，也不能因 READY 在 collector 注册前到达而丢信号。先订阅再触发 Browser，或使用 replay/current state 检查消除竞态。

### 7.3 `CefHostManager.kt`

- 为 CEF 初始化、Browser 创建、page load end/error、dispose 添加阶段回调或 listener 接口；避免直接依赖 Compose 状态。
- listener 回调线程不确定时，统一通过 Host dispatcher 安全投递并进行 attempt 门控。
- 清理 `cefBrowser`、`cefClient`、HTTP server 时逐项隔离错误；dispose 幂等。
- 标明 `createBrowserOnEdt()` 的 EDT 前置条件；可用 `check(SwingUtilities.isEventDispatchThread())` 防止误调用。
- 检查 `initializeBrowser()` / `recreateBrowser()` 兼容入口的调用线程，避免包装方法绕过 EDT 约束；生产 Host 使用明确分阶段 API。
- 日志不输出带 query/path 的原始用户资源 URL、原始 JS payload；HTTP 服务访问日志限制为 method + 安全的资源类别 + status。

### 7.4 `SceneBridgeHostController.kt` 与 Bridge 状态

- 将 Renderer Ready/Scene init 完成信号以窄回调送至 Host，或由 Host 监听带 replay 的 Bridge 状态流；不把 JCEF 类型引入 shared/domain 层。
- `READY` 时下发初始化失败必须能回报给 RuntimeHost，而非仅在协程中打印异常。
- dispose 时取消 Ready collector 和 runtime binding job；重复 READY 不重复绑定 collector 或发送多次初始初始化。
- 运行时 channel ERROR/DISCONNECTED 的状态策略要明确，需区分应用正在关闭和意外断连。

### 7.5 `SceneView.kt`

- 用 `phase` 显示阶段化文案（例如“正在启动 Chromium”“正在建立渲染连接”“正在加载场景”）。
- `FAILED` 显示安全的 `failure.userMessage`、Retry 和返回入口（若导航回调由上层控制则通过回调提供）。
- `DISPOSING/DISPOSED` 不继续显示 loading 或错误覆盖层；不展示堆栈、完整本机路径或原始异常给普通用户。
- Bridge HUD 保留连接状态，但不把 `CONNECTED` 误显示成 Runtime `READY`。

## 8. 测试指导

使用 fake manager/browser factory 和可控 deferred/barrier，不启动真实 JCEF 做单元测试。线程调度器应可注入，测试能断言创建 Browser 的 executor/dispatcher。项目规则若要求用户指定测试才运行，则本方案只列出测试要求；实施时按当轮指令执行。

### Host 状态和重试

- 状态严格按 `INITIALIZING_CEF → CREATING_BROWSER → WAITING_BRIDGE → LOADING_SCENE → READY` 前进，Ready handshake 前不能 READY。
- CEF 初始化失败映射 `CEF_INITIALIZATION`；Browser 创建失败映射 `BROWSER_CREATION`；Bridge 超时/错误映射 `BRIDGE_HANDSHAKE`；Scene init 失败映射 `SCENE_LOAD`。
- Retry 清除旧 failure、增加 attemptId；旧 attempt 晚到 Ready/load callback 不得改变新 attempt 状态。
- Retry 与 close 并发时不创建泄漏的 Browser，close 最终到 DISPOSED。
- 初始化 CancellationException 不显示为普通错误；关闭后不能留下 busy 状态。
- 多次 retry / close 只关闭一次每个拥有的资源，不重复注册 listener。

### CEF Manager

- Browser 创建线程约束可验证（EDT 或注入的测试 dispatcher）。
- page load error 转为安全错误事件；正常关闭产生的 abort 不污染失败状态。
- 某一资源 dispose 抛异常时，其余 Browser/Client/HTTP server 仍尝试释放。
- HTTP server 启停和重复 dispose 幂等。

### UI / 手工回归

- 启动 Classic Device Wall，不进入 3D：CEF 不初始化。
- 首次进入 3D：展示阶段状态，Bridge 和 Scene 成功后进入 READY。
- 模拟/触发 Renderer 启动失败：显示 Retry 和退出入口，Classic 页面仍可用。
- Retry 成功，重复进出 3D；无重复 Browser、listener 或 HTTP server。
- 初始化任一阶段关闭应用：关闭不阻塞 EDT，最终资源释放且 UI 不被迟到回调更新。
- Scene GLB 加载失败后原 Scene/Binding 数据仍保留，切换其他 Scene 或返回 Classic 不受影响。

## 9. 验收标准

1. UI 能区分 CEF 初始化、Browser 创建、Bridge 握手和 Scene 加载阶段；Bridge READY 前不报告 Runtime READY。
2. 每种启动失败都有稳定错误类别、diagnosticId、对用户可理解的消息和开发可筛查的日志。
3. 任一阶段失败都能返回 Classic Device Wall；显式 Retry 不需重启应用。
4. 关闭与初始化、Ready 回调、Retry 并发时无 EDT 阻塞、重复 Browser 或晚到状态污染。
5. 清理一个资源失败不会跳过其他资源清理；dispose/close 幂等。
6. 日志不包含未经脱敏的 GLB 本地路径、设备 serial 或原始 JS payload。
7. Scene 文件、Binding、Camera、Transform 数据在 Renderer 失败和 Retry 后保持不变。
8. Scene/Bridge 的领域职责边界维持不变；若新增 wire message，契约文档和双端测试同提交更新。

## 10. 推荐提交拆分

1. `test(scene): cover renderer lifecycle transitions and close races`
2. `feat(scene): expose renderer startup phases and typed failures`
3. `feat(scene): add explicit renderer retry and generation gating`
4. `fix(scene): make asynchronous cleanup independent of UI scope`
5. `feat(scene-ui): show runtime phase and retry action`
6. `docs(scene): record lifecycle diagnostics and device verification`

如果状态字段、UI 和恢复路径改动很小，也可合并为 2–3 个提交，但每个提交保持可编译，且不把可选视觉功能混入故障恢复范围。
