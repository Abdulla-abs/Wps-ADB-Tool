# 反编译模块（Decompile Studio）未完成工作 Backlog

> **For agentic workers:** 实施单项任务前，用 `writing-plans` 拆分为独立计划；执行时用 `subagent-driven-development` 或 `executing-plans`。  
> **状态：** 待实施（基于 2026-06-15 代码审计）  
> **创建日期：** 2026-06-15  
> **关联代码：** `shared/src/**/decompile/**`、`DecompileService*.kt`、`AppViewModel.kt`（decompile 段）  
> **关联文档：** `README.md` §功能概览、`docs/superpowers/specs/2026-06-11-remaining-work-backlog.md` RW-F08  
> **设计原型：** `design-decompile/`（`DESIGN.md`、`code.html`、`screen.png`）

---

## 1. 背景与现状

### 1.1 模块定位

Desktop（JVM）上的 **Decompile Studio**（导航 `NavTab.DECOMPILE`）提供 APK 导入、资源浏览、按需 DEX 工具链。Android App 使用 `NoOpDecompileService`，当前不可用。

### 1.2 已实现能力（基线）

| 能力 | 实现位置 | 完整度 |
|------|---------|--------|
| APK 导入（解压 + 包名解析） | `JvmDecompileService.importApk` | ~85% |
| 文件树浏览 | `loadFileTree` + `ProjectExplorer` | ~90% |
| 二进制 XML 反编译查看 | JADX 缓存 + `readFileContent` | ~75% |
| XML/Smali 内存编辑 | `CodeWorkspace` + `CodeEditorBridge` | ~55%（无保存） |
| 常见文本文件打开 | 仅 `xml` / `smali` / `dex` 可交互 | ~15% |
| DEX → Smali | Baksmali + `DexEditorPlusScreen` 浏览 | ~85% |
| DEX 编辑器++（多 DEX） | 仅反编译**当前点击的单个** `.dex`，无多选 | ~30% |
| DEX → Java（仅写磁盘） | JADX `decompileDexToJava` | ~50%（UI 不可浏览） |
| DEX 字符串常量读取（Top 200） | `loadDexConstants` | ~40%（不可写回） |
| DEX 属性（日志） | `SHOW_PROPERTIES` action | ~70% |

### 1.3 综合完整度

| 使用场景 | 估算 |
|---------|------|
| 导入 APK → 浏览资源 → 查看 AXML / Smali | **~70%** |
| 编辑 Smali/XML 并保存 | **~10%** |
| 完整改码 → 重编译 → 重打包 APK | **~5%** |
| **模块整体** | **~45%–50%** |

### 1.4 已知占位 / 虚假实现（须优先消除）

| 位置 | 问题 |
|------|------|
| `AppViewModel.performDexSearch` | 返回硬编码假搜索结果 |
| `AppViewModel.executeDexAction` → `DEX_TO_JAR` / `DEX_REPAIR` | 仅写成功日志，无后端 |
| `DexActionDialog` → `REPLACE_CLASS_NAME` / `OBFUSCATE` | 按钮存在，`executeDexAction` 无分支 |
| `executeDexAction` → `DEX_EDITOR_PLUS` | 多 DEX APK 下**仅处理当前点击的 dex**，无多文件选择弹窗 |
| `JvmDecompileService.saveDexConstants` | 空实现 |
| `DexEditorPlusScreen` → smali转java / 导出 | UI 占位，`onClick` 无逻辑 |
| `DecompileDropZone` | 文案支持拖放，实际仅文件选择器；「最近项目」硬编码 |
| `DecompileStudioScreen` / `DecompileDropZone` | 未打开项目时为全屏居中简易 DropZone，**未按 `design-decompile` 双栏 IDE 布局还原** |
| `ProjectExplorer` | 打开项目后 **无退出/关闭项目入口**，无法返回未打开项目空态页 |
| `handleFileNodeClick` | 除 `xml` / `smali` / `dex` 外，**txt/json/properties/md 等常见文件点击无响应** |
| `README.md` | 仍将 APK 反编译标为「未实现」，与代码不符 |

---

## 2. 评估方法

### 2.1 优先级

| 级别 | 含义 |
|------|------|
| **P0** | 阻塞：功能虚假或严重误导用户 |
| **P1** | 高：编辑闭环或核心 DEX 工具缺失 |
| **P2** | 中：UX 完善、浏览增强 |
| **P3** | 低：高级逆向、Android 平台 |
| **P4** | 工程卫生：测试、文档、README |

### 2.2 工作量

| 标签 | 含义 |
|------|------|
| **S** | ≤ 0.5 天 |
| **M** | 1–2 天 |
| **L** | 3–5 天 |
| **XL** | > 1 周 |

### 2.3 任务 ID 前缀

所有任务 ID 使用 **`DC-`**（DeCompile）前缀，便于与 `RW-`（Remaining Work）区分。

---

## 3. 任务总览（追踪表）

> 实施完成后将 `- [ ]` 改为 `- [x]` 并填写「完成日期」。

| ID | 任务 | 优先级 | 工作量 | 状态 |
|----|------|--------|--------|------|
| DC-001 | 编辑器文件保存（XML/Smali） | P1 | M | - [x] |
| DC-002 | 消除虚假 DEX 操作（JAR/修复/替换/混淆） | P0 | S–M | - [x] |
| DC-003 | Smali 真实全文搜索 | P1 | M | - [x] |
| DC-004 | DEX→Java 结果 UI 浏览 | P1 | M | - [x] |
| DC-005 | DEX 字符串常量写回 | P2 | L | - [x] |
| DC-006 | 单文件 Smali→Java（DexEditor++） | P2 | M | - [x] |
| DC-007 | DexEditor++ 导出功能 | P2 | S | - [x] |
| DC-008 | Smali → DEX 重组（smali 库） | P1 | L | - [x] |
| DC-009 | APK 重打包（zip 回编） | P1 | M | - [x] |
| DC-010 | APK 签名（debug/release 可选） | P2 | M | - [x] |
| DC-011 | APK 拖放导入 | P2 | S | - [ ] |
| DC-012 | 最近工作区持久化 | P2 | M | - [ ] |
| DC-013 | 工作区状态/ JADX 缓存清理（配合 DC-024） | P2 | S | - [x] |
| DC-014 | 导入后刷新文件树 | P2 | S | - [x] |
| DC-015 | Smali 语法高亮 | P3 | S | - [x] |
| DC-016 | DEX→JAR 真实实现 | P3 | M | - [ ] |
| DC-017 | DEX 修复工具 | P3 | L | - [ ] |
| DC-018 | 包名/类名批量替换 | P3 | L | - [ ] |
| DC-019 | 控制流混淆 | P3 | XL | - [ ] |
| DC-020 | Android 平台策略（隐藏或实现） | P3 | L–XL | - [ ] |
| DC-021 | DecompileService 单元测试 | P4 | M | - [x] |
| DC-022 | README / Backlog 文档同步 | P4 | S | - [x] |
| DC-023 | 未打开项目时空态 UI 按设计原型还原 | P1 | M | - [x] |
| DC-024 | Project Explorer 标题行「退出项目」按钮 | P2 | S | - [x] |
| DC-025 | Dex 编辑器++ 多 DEX 选择弹窗 | P1 | M | - [x] |
| DC-026 | 常见文本文件类型打开与语法支持 | P2 | M | - [x] |

---

## 4. Backlog 明细

### P0 — 消除误导

#### DC-002 消除虚假 DEX 操作

| 字段 | 内容 |
|------|------|
| **问题** | `DEX_TO_JAR`、`DEX_REPAIR` 打假成功日志；`REPLACE_CLASS_NAME`、`OBFUSCATE` 按钮无 handler |
| **证据** | `AppViewModel.executeDexAction` L1109–1130；`DexActionDialog.kt` L105–114 |
| **影响** | 用户以为操作成功，实际无任何效果 |
| **工作量** | S（短期：禁用按钮 + 标注「即将推出」）或 M（接真实实现，见 DC-016–019） |
| **建议方案 A（快速）** | 未实现 action 按钮置灰 + Tooltip；ViewModel 分支改为 `LogLevel.W`「尚未实现」 |
| **建议方案 B（完整）** | 按 DC-016–019 逐项实现后移除此任务 |
| **涉及文件** | `DexActionDialog.kt`、`AppViewModel.kt` |
| **验收** | 点击任一 DEX 操作，行为与 UI 文案一致；无虚假成功日志 |
| **依赖** | 无（方案 A）；DC-016–019（方案 B） |

---

### P1 — 编辑与核心工具链

#### DC-023 未打开项目时空态 UI 按设计原型还原

| 字段 | 内容 |
|------|------|
| **问题** | `decompileWorkspace == null` 时，`DecompileStudioScreen` 仅渲染全屏居中的 `DecompileDropZone`（emoji + 实线边框卡片），与 `design-decompile` 设计原型差距大 |
| **设计参考** | `design-decompile/code.html`、`design-decompile/screen.png`、`design-decompile/DESIGN.md`（Carbon 色板、Glassmorphism、Drag-and-Drop Zones §158–159） |
| **证据** | 当前 `DecompileDropZone.kt` 单栏居中布局；设计为 **左 Project Explorer + 右 Code 占位** 双栏 IDE 结构 |
| **影响** | 反编译页首屏与产品设计不一致，缺少 IDE 式上下文与功能预期展示 |
| **工作量** | M |
| **布局要求（须还原）** | 见下方「设计对照清单」 |
| **建议** | 1) 重构 `DecompileDropZone` 或新建 `DecompileEmptyStateScreen`；2) `DecompileStudioScreen` 在未打开项目时采用 Row 双栏；3) 顶栏（模块内或 `DecompileStudioScreen` 内）增加标题「反编译 - 等待导入 APK」、Manifest / Smali View / Resources 禁用态 Tab、搜索框占位、「New Session」按钮；4) 左栏 320dp Project Explorer + 绿色虚线 Drop Zone + Recent Project 卡片；5) 右栏点阵背景 + `No project active` 空态 + 三枚 Glass 功能卡片；6) 导入进度 overlay 覆盖双栏而非替换整个布局 |
| **涉及文件** | `DecompileStudioScreen.kt`、`DecompileDropZone.kt`（拆分或重写）、可选新建 `DecompileEmptyEditorPane.kt`、`DecompileTopBar.kt`；i18n：`composeResources/values/strings.xml`、`values-zh/strings.xml` |
| **验收** | 与 `design-decompile/screen.png` 视觉结构一致（双栏、虚线 Drop Zone、右栏空态与三卡片）；点击 Drop Zone 仍可触发 `pickApkFile`；拖放/APK 导入行为见 DC-011 |
| **依赖** | 无（布局还原可先做）；Recent Project 真实数据见 **DC-012**；拖放交互见 **DC-011** |

**设计对照清单（`design-decompile/code.html` → Compose）**

| 区域 | 设计元素 | 当前实现 | 目标 |
|------|---------|---------|------|
| 顶栏 | 标题「反编译 - 等待导入 APK」 | 无 | ✅ 还原 |
| 顶栏 | Tab：Manifest / Smali View / Resources（空态禁用） | 无 | ✅ 还原（灰色不可点） |
| 顶栏 | 搜索框 `Search project symbols...` | 无 | ✅ 占位（空态 disabled） |
| 顶栏 | `+ New Session` 按钮 | 无 | ✅ 还原（可触发 `pickApkFile` 或等同导入） |
| 左栏 | `PROJECT EXPLORER` 标题 + 展开图标 | 无（全屏 DropZone） | ✅ 还原 |
| 左栏 | 绿色 **虚线** Drop Zone（hover 实线 + 浅绿底） | 实线圆角卡片 + 📥 emoji | ✅ 还原 |
| 左栏 | 圆形 upload 图标 +「拖拽 APK 文件」 | 简易文案 | ✅ 还原 |
| 左栏 | Recent Project 卡片 | 硬编码占位 | ⚠️ 布局先还原，数据接 DC-012 |
| 右栏 | 点阵 `code-grid` 背景 | 无 | ✅ 还原 |
| 右栏 | `code_off` 图标 + pulse 动画 | 无 | ✅ 还原（可用 Compose 动画） |
| 右栏 | `No project active` + 说明文案 | 无 | ✅ 还原（中英 i18n） |
| 右栏 | 三枚 Glass 卡片：JADX/Baksmali、Vulnerability Scan、Re-sign Tool | 无 | ✅ 还原（空态 informational，可 disabled） |
| 交互 | dragover 时边框实线 + `primary/10` 背景 | 无 | 与 DC-011 一并实现 |

> **范围说明：** 设计稿中的 Sidebar、底栏 ADB 状态栏、用户头像由全局 `AppShell` / `Sidebar` 负责，本任务仅覆盖 **Decompile 主内容区**（顶栏 + 双栏工作区）。底栏若 App 已有则不必重复。

---

#### DC-001 编辑器文件保存

| 字段 | 内容 |
|------|------|
| **问题** | `updateEditorContent` 仅更新内存；`EditorTab.isDirty` 无保存动作 |
| **证据** | `AppViewModel.updateEditorContent`；`CodeWorkspace` 无 Save 按钮/快捷键 |
| **影响** | 无法完成「查看 → 编辑 → 持久化」闭环 |
| **工作量** | M |
| **建议** | 1) `DecompileService` 新增 `saveFileContent(workspace, filePath, content)`；2) JVM 实现 `File.writeText`；3) UI 增加 Save（Ctrl+S）与 dirty 状态清除；4) 保存后更新 `initialContent` |
| **涉及文件** | `DecompileService.kt`、`DecompileService.jvm.kt`、`DecompileService.android.kt`（stub 抛 UnsupportedOperationException）、`AppViewModel.kt`、`CodeWorkspace.kt` 或 `DexEditorPlusScreen.kt` |
| **验收** | 编辑 XML/Smali → 保存 → 关闭重开标签内容一致；磁盘文件已更新 |
| **依赖** | 无 |

#### DC-003 Smali 真实全文搜索

| 字段 | 内容 |
|------|------|
| **问题** | `performDexSearch` 返回硬编码 `MainActivity.smali` / `Logger.smali` |
| **证据** | `AppViewModel.kt` L1156–1164 |
| **影响** | DexEditor++「搜索」标签完全不可用 |
| **工作量** | M |
| **建议** | 1) `DecompileService.searchSmaliFiles(rootDir, query, maxResults)`；2) JVM：递归扫描 `*.smali`，按行匹配（忽略大小写可选）；3) 返回 `List<DexSearchHit>`；4) 点击结果跳转浏览 Tab 并打开对应文件 |
| **涉及文件** | `DecompileService.kt`、`DecompileService.jvm.kt`、`AppViewModel.kt`、`DexEditorPlusScreen.kt` |
| **验收** | 在真实 Smali 树中搜索已知字符串，结果准确；点击结果打开正确文件与行号 |
| **依赖** | 无 |

#### DC-025 Dex 编辑器++ 多 DEX 选择弹窗

| 字段 | 内容 |
|------|------|
| **问题** | 解析 APK 后，工作区文件树常含多个 DEX（`classes.dex`、`classes2.dex` …）。用户点击某一 `.dex` 弹出 `DexActionDialog`，选择「dex编辑器++」后，当前实现**仅反编译被点击的那一个 DEX**，无法一次性选择多个 DEX 进入 DexEditor++ |
| **证据** | `AppViewModel.executeDexAction` → `DEX_EDITOR_PLUS` 仅对 `file.path` 调用 `disassembleDexToSmali` / `loadDexConstants`（L1070–1088）；无扫描 `fileTreeRoot` 中其他 `.dex` 的逻辑 |
| **影响** | MultiDex APK（主流应用）在 DexEditor++ 中只能查看部分 Smali，需反复退出再点其他 dex，体验割裂 |
| **工作量** | M |
| **交互流程** | 1) 用户点击文件树中某一 `.dex` → 弹出 `DexActionDialog`（现有）；2) 用户点击「dex编辑器++」；3) **若工作区存在 ≥2 个 `.dex` 文件** → **额外弹出「选择 DEX 文件」弹窗**（新建 `DexMultiSelectDialog`）；4) 弹窗以复选框列表展示全部 DEX（`classes.dex`、`classes2.dex` …，显示文件名 + 可选大小）；5) **默认勾选用户当前点击的那个 DEX**；6) 用户可勾选多个 → 确认后开始反编译；7) **若仅 1 个 DEX** → 跳过选择弹窗，行为与现有一致（直接反编译该文件） |
| **反编译行为** | 对用户勾选的每个 DEX：分别 `disassembleDexToSmali` → `{dexPath}_smali`；合并为统一浏览树（建议顶层 Folder 按 dex 文件名分子目录，如 `classes.dex/`、`classes2.dex/`）；`loadDexConstants` 对所选 DEX 分别加载后合并列表（去重或标注来源 dex） |
| **状态扩展** | `AppUiState` 新增：`showDexMultiSelectDialog: Boolean`、`dexMultiSelectCandidates: List<FileNode.File>`、`dexMultiSelectDefaultPath: String?`（触发时记录点击的 dex path）；确认后传入 `List<FileNode.File>` 给 `launchDexEditorPlus(selectedDexFiles)` |
| **建议实现** | 1) `AppViewModel` 新增 `collectDexFilesFromTree(root: FileNode.Folder): List<FileNode.File>` 递归收集 `extension == "dex"`；2) `executeDexAction` 中 `DEX_EDITOR_PLUS` 改为：多 dex 时 `showDexMultiSelectDialog = true` 而非立即反编译；3) 新建 `DexMultiSelectDialog.kt`（Carbon 风格，全选/取消全选、确定/取消）；4) `confirmDexEditorPlusSelection(selected: List<FileNode.File>)` 执行批量反编译 + 进入 `DexEditorPlusScreen`；5) `activeDexEditorProject` 可显示为 `packageName (N dex)` 或主 dex 名 |
| **涉及文件** | `DexMultiSelectDialog.kt`（新建）、`DexActionDialog.kt`、`DecompileStudioScreen.kt`、`AppViewModel.kt`、`AppUiState.kt`、`DecompileService.jvm.kt`（可选新增 `disassembleMultipleDexToSmali`）、`DexEditorPlusScreen.kt`（标题展示）；i18n：`decompile_dex_multi_select_title`、`decompile_dex_multi_select_confirm` 等 |
| **验收** | MultiDex APK：`classes.dex` + `classes2.dex` 均可见 → 点击 `classes2.dex` → dex编辑器++ → 弹出选择框且 **classes2.dex 默认勾选** → 可额外勾选 `classes.dex` → 确认后 DexEditor++ 浏览树含两个 dex 的 Smali；单 DEX APK：点击后直接进 DexEditor++，**不出现**选择弹窗 |
| **依赖** | 无 |

#### DC-004 DEX→Java 结果 UI 浏览

| 字段 | 内容 |
|------|------|
| **问题** | `decompileDexToJava` 输出到 `{dex}_java`，仅日志提示路径 |
| **证据** | `AppViewModel.executeDexAction` → `DEX_TO_JAVA` |
| **影响** | Java 反编译能力对用户不可见 |
| **工作量** | M |
| **建议** | 1) 反编译完成后 `loadFileTree` 挂载 `_java` 目录为子树或切换浏览根；2) `handleFileNodeClick` 支持 `.java` + `EditorType.JAVA`；3) 可选：自动打开入口 Activity 类 |
| **涉及文件** | `AppViewModel.kt`、`DecompileStudioScreen.kt` / `ProjectExplorer.kt` |
| **验收** | DEX→Java 完成后可在 UI 浏览并打开 `.java` 文件 |
| **依赖** | 无 |

#### DC-026 常见文本文件类型打开与语法支持

| 字段 | 内容 |
|------|------|
| **问题** | APK 解包后工作区含大量配置文件与文档（如 `assets/*.json`、`META-INF/*.properties`、`README.md`、`res/raw/*.txt` 等），但 `handleFileNodeClick` **仅处理** `dex`（弹窗）、`xml`、`smali`；点击其他常见文本文件**无任何反应** |
| **证据** | `AppViewModel.handleFileNodeClick` L1027–1057：`else if (node.extension == "xml" \|\| node.extension == "smali")` 硬编码白名单；`EditorType` 仅 `XML / SMALI / JAVA`（`DecompileModel.kt`） |
| **影响** | 无法在工作区内直接查看/编辑 JSON 配置、Properties、Markdown 说明等，逆向分析需依赖外部编辑器 |
| **工作量** | M |
| **首版支持扩展名** | 见下方「文件类型映射表」；未列入且可判定为文本的，降级为纯文本打开 |
| **Markdown 库候选（JVM）** | [flexmark-java](https://github.com/vsch/flexmark-java) 或 [commonmark-java](https://github.com/commonmark/commonmark-java) — 仅 Desktop 预览面板需要时在 `jvmMain` 引入；Android 可降级为纯文本编辑 |
| **二进制文件** | `png` `jpg` `webp` `so` `arsc` 等 **不在本任务范围**；点击时提示「二进制文件不支持文本打开」 |
| **建议实现** | 1) 新建 `DecompileOpenableFileTypes.kt`：`extension → EditorType + syntax` 集中映射；2) 扩展 `EditorType`；3) `handleFileNodeClick` 查表打开；4) `CodeEditorBridge.jvm.kt` 补充 syntax 分支；5) Markdown Phase A 纯文本编辑，Phase B 可选预览 Tab；6) `ProjectExplorer` 扩展名图标；7) 保存随 **DC-001** 覆盖新类型 |
| **涉及文件** | `DecompileModel.kt`、`DecompileOpenableFileTypes.kt`（新建）、`AppViewModel.kt`、`CodeWorkspace.kt`、`CodeEditorBridge.jvm.kt`、`CodeEditorBridge.android.kt`、`ProjectExplorer.kt`；可选 `MarkdownPreviewBridge.jvm.kt`、`shared/build.gradle.kts` |
| **验收** | 含 `config.json`、`*.properties`、`README.md`、`*.txt` 的 APK → 点击后在 `CodeWorkspace` 打开；JSON/Properties 有语法高亮；MD 至少可编辑，预览可选；二进制文件有明确提示 |
| **依赖** | DC-001（保存）；与 DC-004（`.java`）扩展名映射需合并 |

**文件类型映射表**

| 扩展名 | 语法 / 编辑器 | 依赖 |
|--------|--------------|------|
| `txt` `log` `cfg` `ini` `csv` | 纯文本 | `RSyntaxTextArea`（`SYNTAX_STYLE_NONE`） |
| `json` | JSON 高亮 | `RSyntaxTextArea` → `SYNTAX_STYLE_JSON` |
| `properties` `prop` | Properties 高亮 | `RSyntaxTextArea` → `SYNTAX_STYLE_PROPERTIES_FILE` |
| `md` `markdown` | Markdown 编辑 + 可选预览 | RSyntaxTextArea 编辑；flexmark/commonmark 预览（JVM） |
| `yml` `yaml` | YAML 高亮 | `RSyntaxTextArea` → `SYNTAX_STYLE_YAML` |
| `html` `htm` | HTML | `SYNTAX_STYLE_HTML` |
| `css` | CSS | `SYNTAX_STYLE_CSS` |

#### DC-008 Smali → DEX 重组

| 字段 | 内容 |
|------|------|
| **问题** | 依赖中已有 `org.smali:smali`，未使用；编辑 Smali 后无法回写 DEX |
| **证据** | `shared/build.gradle.kts` L39；无 `assembleDex` 类 API |
| **影响** | 无法完成 Smali 级修改闭环 |
| **工作量** | L |
| **建议** | 1) `DecompileService.assembleSmaliToDex(smaliRoot, outputDexPath)`；2) 使用 smali 库 `Smali.assemble`；3) 处理 multidex（按 smali 目录结构）；4) ViewModel 增加「重组 DEX」动作 |
| **涉及文件** | 新建 `SmaliAssembler.jvm.kt` 或扩 `DecompileService.jvm.kt`、`AppViewModel.kt` |
| **验收** | 修改 Smali → 重组 → 新 DEX 可被 `baksmali` 再次反汇编且变更可见 |
| **依赖** | DC-001（建议先能保存 Smali） |

#### DC-009 APK 重打包

| 字段 | 内容 |
|------|------|
| **问题** | 无将工作区重新 zip 为 APK 的能力 |
| **证据** | 全库无 `repack` / `rebuildApk` |
| **影响** | 即使 DEX 可重组，也无法产出可安装 APK |
| **工作量** | M |
| **建议** | 1) `DecompileService.repackApk(workspace, outputApkPath)`；2) 遍历工作区 zip（保留原 APK 未改文件如 `resources.arsc`）；3) 替换已修改的 `classes*.dex`；4) UI：「导出 APK」按钮 |
| **涉及文件** | `DecompileService.kt`、`DecompileService.jvm.kt`、`DecompileStudioScreen.kt` |
| **验收** | 未改动的 APK 重打包后 `adb install` 成功；替换 DEX 后行为符合修改 |
| **依赖** | DC-008（若需 DEX 级修改） |

---

### P2 — UX 与增强

#### DC-005 DEX 字符串常量写回

| 字段 | 内容 |
|------|------|
| **问题** | `loadDexConstants` 可读；`editDexConstant` 仅改 UI；`saveDexConstants` 空实现 |
| **证据** | `DecompileService.jvm.kt` L187–189；`AppViewModel.editDexConstant` |
| **影响** | 常量 Tab 编辑无效 |
| **工作量** | L |
| **建议** | 方案 A：写回 Smali 中 `const-string`（配合 DC-001/008）；方案 B：直接 patch DEX string pool（dexlib2 writer，风险高） |
| **涉及文件** | `DecompileService.jvm.kt`、`AppViewModel.kt`、`DexEditorPlusScreen.kt` |
| **验收** | 修改常量 → 保存 → 重新加载常量或反汇编验证字符串已变 |
| **依赖** | DC-001、DC-008（方案 A 推荐） |

#### DC-006 单文件 Smali→Java

| 字段 | 内容 |
|------|------|
| **问题** | DexEditor++「smali转java」仅显示假进度弹窗 |
| **证据** | `DexEditorPlusScreen.kt` L131–136、L316–381 |
| **影响** | 编辑 Smali 时无法对照 Java |
| **工作量** | M |
| **建议** | 1) 对当前 Smali 对应 class 调用 JADX 单类反编译或临时 dex；2) 在新 `EditorType.JAVA` 标签展示 |
| **涉及文件** | `DecompileService.jvm.kt`、`DexEditorPlusScreen.kt`、`AppViewModel.kt` |
| **验收** | 选中 Smali 文件 → smali转java → 显示可读的 Java 源码 |
| **依赖** | DC-001（可选） |

#### DC-007 DexEditor++ 导出

| 字段 | 内容 |
|------|------|
| **问题** | 「导出」按钮 `onClick` 为空 |
| **证据** | `DexEditorPlusScreen.kt` L142 |
| **工作量** | S |
| **建议** | 导出当前 Smali 目录或当前文件到用户选择路径（`JFileChooser`） |
| **涉及文件** | `DexEditorPlusScreen.kt`、新建 `ExportFilePicker.jvm.kt`（可选） |
| **验收** | 导出后磁盘文件与编辑器内容一致 |
| **依赖** | DC-001（若导出含未保存修改，应先提示保存） |

#### DC-010 APK 签名

| 字段 | 内容 |
|------|------|
| **问题** | 重打包 APK 默认未签名，无法直接安装 |
| **工作量** | M |
| **建议** | 集成 `apksigner` 或内置 debug keystore；Settings 可配置 keystore 路径 |
| **涉及文件** | `DecompileService.jvm.kt`、`AppSettings`（可选） |
| **验收** | 重打包 APK 经签名后 `adb install` 成功 |
| **依赖** | DC-009 |

#### DC-011 APK 拖放导入

| 字段 | 内容 |
|------|------|
| **问题** | `DecompileDropZone` 文案写拖放，仅 `clickable` + `pickApkFile` |
| **证据** | `DecompileDropZone.kt` L109 |
| **工作量** | S |
| **建议** | Desktop 复用现有 `ApkDropTarget` 模式（若已有 JVM actual）或 Compose drag-drop API |
| **涉及文件** | `DecompileDropZone.kt`、平台层 drop target |
| **验收** | 拖放 `.apk` 到 DropZone 触发 `importApkToWorkspace` |
| **依赖** | 无 |

#### DC-012 最近工作区持久化

| 字段 | 内容 |
|------|------|
| **问题** | 「最近导入项目」硬编码 `com.android.settings` |
| **证据** | `DecompileDropZone.kt` L133 |
| **工作量** | M |
| **建议** | `DecompileWorkspaceStore` 写入 `~/.wps_adb_tool/decompile/recent.json`；DropZone 列表可点击恢复 |
| **涉及文件** | 新建 `DecompileWorkspaceStore.jvm.kt`、`DecompileDropZone.kt`、`AppViewModel.kt` |
| **验收** | 导入 APK 后出现在最近列表；重启应用仍可打开 |
| **依赖** | 无 |

#### DC-013 关闭/清空工作区 UI

| 字段 | 内容 |
|------|------|
| **问题** | `clearDecompileWorkspace()` 存在但未接入 UI |
| **证据** | `AppViewModel.kt` L1195；无 Sidebar/Toolbar 调用 |
| **工作量** | S |
| **说明** | **UI 入口与交互规格见 DC-024**；本项涵盖同一能力的服务端清理（JADX 缓存释放等） |
| **建议** | 与 DC-024 一并实施：`clearDecompileWorkspace()` + `JvmDecompileService` 释放 JADX 缓存 |
| **涉及文件** | `AppViewModel.kt`、`JvmDecompileService`（新增 `close()` / cache clear） |
| **验收** | 退出后 ViewModel 状态清空、无 stale 缓存；UI 行为见 DC-024 |
| **依赖** | DC-024 |

#### DC-024 Project Explorer 标题行「退出项目」按钮

| 字段 | 内容 |
|------|------|
| **问题** | 反编译页在**已打开项目**（`decompileWorkspace != null`）时，缺少退出当前项目的入口；用户无法从工作区返回「未打开项目」空态页（DC-023） |
| **证据** | `DecompileStudioScreen.kt` 打开项目后直接展示 `ProjectExplorer` + `CodeWorkspace`，无关闭/退出控件；`ProjectExplorer.kt` 标题行右侧仅有占位 `↕`（Expand/collapse all，未实现） |
| **影响** | 导入 APK 后只能重启应用或切换导航 Tab 才能「离开」项目，体验不完整 |
| **工作量** | S |
| **UI 要求** | 在 **Project Explorer 标题行**（`PROJECT EXPLORER` 文案所在 Row）**右侧**增加 **「退出」icon 按钮**（Material 风格，如 `logout` / `close` / `exit_to_app`，与 Carbon 主题 `CarbonColors.Outline` hover → `Primary`） |
| **交互** | 1) 点击 → 调用 `viewModel.clearDecompileWorkspace()`（及 DC-013 的 JADX 缓存释放）；2) 界面回到 `decompileWorkspace == null` 分支，即 **未打开项目空态页**（DC-023 布局）；3) 同步清空 `openTabs`、`activeTabId`、`activeDexEditorProject` 等关联状态（`clearDecompileWorkspace` 已部分覆盖，需确认 DexEditor++ 状态一并重置） |
| **显示范围** | **仅**主工作区 `DecompileStudioScreen` 中的 `ProjectExplorer`（`fileTreeRoot` 场景）显示退出按钮；**DexEditor++** 内嵌的 `ProjectExplorer`（`dexBrowseTree`）不显示——该页已有「← 返回工作空间」 |
| **建议实现** | 1) `ProjectExplorer` 增加可选参数 `onExitProject: (() -> Unit)? = null`；2) 标题行右侧：`onExitProject != null` 时显示退出 IconButton，否则保留现有 `↕` 占位；3) `DecompileStudioScreen` 传入 `onExitProject = { viewModel.clearDecompileWorkspace() }`；4) 可选：存在 `openTabs` 且含 `isDirty` 时弹出确认对话框 |
| **涉及文件** | `ProjectExplorer.kt`、`DecompileStudioScreen.kt`、`AppViewModel.kt`（扩展 `clearDecompileWorkspace` 重置 DexEditor 相关字段）、`JvmDecompileService`（DC-013 缓存释放）；i18n：`decompile_exit_project` contentDescription |
| **验收** | 打开 APK 项目 → Project Explorer 标题行右侧可见退出 icon → 点击 → 回到未打开项目空态页；再次导入无残留标签页/文件树；DexEditor++ 内 Project Explorer 无退出按钮 |
| **依赖** | 无（空态页布局见 DC-023，可独立先做退出逻辑） |

#### DC-014 导入后刷新文件树

| 字段 | 内容 |
|------|------|
| **问题** | DEX→Smali/Java 输出到 `_smali` / `_java` 子目录，主文件树不自动刷新 |
| **工作量** | S |
| **建议** | DEX 操作完成后重新 `loadFileTree` 或局部插入新 Folder 节点 |
| **涉及文件** | `AppViewModel.kt` |
| **验收** | DEX→Smali 后主树可见 `{dex}_smali` 目录 |
| **依赖** | 无 |

---

### P3 — 高级 / 平台

#### DC-015 Smali 语法高亮

| 字段 | 内容 |
|------|------|
| **问题** | `CodeEditorBridge.jvm.kt` 中 Smali 使用 `SYNTAX_STYLE_NONE` |
| **证据** | `CodeEditorBridge.jvm.kt` L33 |
| **工作量** | S |
| **建议** | 移植 JADX `SmaliTokenMaker` + `text/smali` 注册 + Carbon 主题 |
| **状态** | ✅ 已移植 `SmaliTokenMaker.java`，`SmaliSyntaxSetup.jvm.kt` 注册，`SmaliEditorTheme.jvm.kt` Carbon 配色 |
| **依赖** | 无 |

#### DC-016 DEX→JAR 真实实现

| 字段 | 内容 |
|------|------|
| **问题** | `DEX_TO_JAR` 仅日志 |
| **工作量** | M |
| **建议** | dexlib2 转 jar 或调用 `d2j-dex2jar` 外部工具；输出到 `{dex}_jar` |
| **依赖** | DC-002 |

#### DC-017 DEX 修复工具

| 字段 | 内容 |
|------|------|
| **问题** | `DEX_REPAIR` 仅日志 |
| **工作量** | L |
| **建议** | 调研 dex 校验失败场景（checksum、header）；实现基础 repair 或集成现有工具 |
| **依赖** | DC-002 |

#### DC-018 包名/类名批量替换

| 字段 | 内容 |
|------|------|
| **问题** | `REPLACE_CLASS_NAME` 无实现 |
| **工作量** | L |
| **建议** | Smali 全文替换 + manifest 二进制/XML 包名更新；需 DC-001/008/009 闭环 |
| **依赖** | DC-001、DC-008、DC-009 |

#### DC-019 控制流混淆

| 字段 | 内容 |
|------|------|
| **问题** | `OBFUSCATE` 无实现 |
| **工作量** | XL |
| **建议** | 列为远期；或移除 UI 入口 |
| **依赖** | DC-008 |

#### DC-020 Android 平台策略

| 字段 | 内容 |
|------|------|
| **问题** | `NoOpDecompileService`；导航仍显示 Decompile |
| **工作量** | L（实现）或 S（隐藏 Tab） |
| **建议** | **短期**：Android 隐藏 `NavTab.DECOMPILE`；**长期**：服务端化或限制功能子集 |
| **涉及文件** | `DecompileService.android.kt`、`Sidebar.kt`、`AppShell.kt` |
| **依赖** | 无 |

---

### P4 — 工程卫生

#### DC-021 DecompileService 单元测试

| 字段 | 内容 |
|------|------|
| **问题** | 零测试覆盖 |
| **工作量** | M |
| **建议** | `shared/src/jvmTest/` 增加：`importApk`（fixture mini apk）、`disassembleDexToSmali`、`loadDexConstants`、`searchSmaliFiles`（DC-003 后） |
| **验收** | `./gradlew :shared:jvmTest` 通过 |
| **依赖** | 无（可随 DC-001/003 增量添加） |

#### DC-022 README / Backlog 文档同步

| 字段 | 内容 |
|------|------|
| **问题** | README 写「APK 反编译未实现」；RW-F08 仍为远期 |
| **工作量** | S |
| **建议** | README 新增「Decompile Studio（部分实现）」表格；更新 RW-F08 指向本文档 |
| **涉及文件** | `README.md`、`docs/superpowers/specs/2026-06-11-remaining-work-backlog.md` |
| **依赖** | 无 |

---

## 5. 推荐实施路线图

### Sprint D0 — 空态 UI 还原（约 2–3 天）

```
DC-023 未打开项目时空态 UI 按 design-decompile 还原
  → DC-011 拖放导入（与 Drop Zone 交互合并）
  → DC-012 最近工作区（填充 Recent Project 卡片）
```

**里程碑：** 反编译页首屏与设计原型视觉一致，可点击/拖放导入 APK。

### Sprint D1 — 诚实化 + 编辑闭环（约 1 周）

```
DC-002 消除虚假 DEX 操作（方案 A：置灰/警告）
  → DC-001 文件保存（含 DC-026 常见文本类型）
  → DC-026 常见文本文件打开（txt/json/properties/md 等）
  → DC-024 Project Explorer 退出项目（含 DC-013 状态/缓存清理）
  → DC-014 刷新文件树
  → DC-022 README 同步
```

**里程碑：** 用户可导入 → 编辑 → 保存 → 关闭，且无假成功日志。

### Sprint D2 — DexEditor++ 可用（约 1–2 周）

```
DC-025 Dex 编辑器++ 多 DEX 选择弹窗
  → DC-003 Smali 真实搜索
  → DC-004 DEX→Java UI 浏览
  → DC-007 导出
  → DC-021 基础单元测试
```

**里程碑：** DexEditor++ 三项子 Tab 均可真实使用。

### Sprint D3 — 改码回编（约 2–3 周）

```
DC-008 Smali → DEX
  → DC-009 APK 重打包
  → DC-010 APK 签名
  → DC-005 常量写回（Smali 路径）
  → DC-006 单文件 Smali→Java
```

**里程碑：** 修改 Smali → 产出可安装 APK。

### Sprint D4 — 高级工具（可选 / 远期）

```
DC-016 DEX→JAR
  → DC-017 DEX 修复
  → DC-018 包名/类名替换
  → DC-015 Smali 高亮
  → DC-020 Android 策略
  → DC-019 控制流混淆（或永久移出范围）
```

---

## 6. 文件影响矩阵

| 文件 | 主要任务 |
|------|---------|
| `DecompileService.kt` | DC-001, DC-003, DC-008, DC-009 |
| `DecompileService.jvm.kt` | DC-001, DC-003, DC-005, DC-008, DC-009, DC-010, DC-016 |
| `DecompileService.android.kt` | DC-020 |
| `AppViewModel.kt` | DC-001–004, DC-012–014, DC-002, **DC-024**, DC-013, **DC-025**, **DC-026** |
| `AppUiState.kt` | **DC-025** |
| `ProjectExplorer.kt` | **DC-024**, **DC-026** |
| `DecompileStudioScreen.kt` | DC-009 UI, **DC-023**, **DC-024**, **DC-025** |
| `DecompileDropZone.kt` | DC-011, DC-012, **DC-023** |
| `design-decompile/` | **DC-023**（设计参考，非代码修改） |
| `DexEditorPlusScreen.kt` | DC-006, DC-007, DC-003 导航, **DC-025** |
| `DexActionDialog.kt` | DC-002, **DC-025** |
| `DexMultiSelectDialog.kt`（新建） | **DC-025** |
| `CodeWorkspace.kt` | DC-001 Save UI, **DC-026** |
| `CodeEditorBridge.jvm.kt` | DC-015, **DC-026** |
| `CodeEditorBridge.android.kt` | **DC-026**（plain text 降级） |
| `DecompileOpenableFileTypes.kt`（新建） | **DC-026** |
| `MarkdownPreviewBridge.jvm.kt`（可选） | **DC-026** |
| `DecompileModel.kt` | DC-012（workspace 序列化）、**DC-026**；清理未用 `decompileResources` |
| `shared/build.gradle.kts` | **DC-026**（flexmark/commonmark，`jvmMain`） |
| `README.md` | DC-022 |

---

## 7. 验收清单（模块级）

模块达到 **「Beta 可用」** 须满足：

- [ ] 打开项目后可通过 Project Explorer 退出按钮返回空态页
- [ ] 未打开项目时空态 UI 与 `design-decompile/screen.png` 结构一致
- [ ] 无虚假成功日志或未实现却可点击的主操作
- [ ] XML / Smali / 常见文本（txt、json、properties、md 等）可在工作区打开
- [ ] XML / Smali 可保存到磁盘
- [ ] DEX→Smali / DEX→Java 结果可在 UI 浏览
- [ ] MultiDex APK 下 Dex 编辑器++ 可多选 DEX 并默认勾选触发项
- [ ] DexEditor++ 搜索返回真实结果
- [ ] `./gradlew :shared:jvmTest` 含 Decompile 相关测试
- [ ] README 准确描述已实现与未实现边界

模块达到 **「改码回编」** 须额外满足：

- [ ] Smali 修改 → 重组 DEX → 重打包 APK → 签名 → `adb install` 成功

---

## 8. 变更记录

| 日期 | 变更 |
|------|------|
| 2026-06-15 | 初版：基于代码审计创建 DC-001–DC-022 |
| 2026-06-15 | 新增 **DC-023**：未打开项目时空态 UI 按 `design-decompile` 设计原型还原 |
| 2026-06-15 | 新增 **DC-024**：Project Explorer 标题行退出项目 icon；DC-013 合并为后端清理子项 |
| 2026-06-15 | 新增 **DC-025**：Dex 编辑器++ 多 DEX 选择弹窗（MultiDex 默认勾选当前 dex） |
| 2026-06-15 | 新增 **DC-026**：常见文本文件类型打开（txt/json/properties/md 等）与 Markdown 开源库预览 |
