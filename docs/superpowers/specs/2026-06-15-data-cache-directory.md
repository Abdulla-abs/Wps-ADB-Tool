# 全局数据缓存目录 — 设计规格

**日期:** 2026-06-15  
**状态:** 已评审，待实现  
**关联计划:** `docs/superpowers/plans/2026-06-15-data-cache-directory.md`

---

## 1. 背景与目标

用户在反编译模块中会产生大量磁盘数据（APK 解压工作区、smali 树、签名 keystore 等）。当前路径硬编码在用户主目录下，且存在两套命名：

| 现状路径 | 用途 |
|----------|------|
| `~/.wps-adb-tool/` | 设置、无线设备、已移除设备 |
| `~/.wps_adb_tool/decompile/` | 反编译工作区、`recent.json`、`debug.keystore` |

**目标：** 在全局设置中新增「数据缓存目录」，让用户指定应用处理文件时的根目录；第一期优先打通反编译链路，并统一路径解析方式。

**非目标（第一期不做）：**

- 自动迁移旧目录中的全部历史数据（仅提供检测与提示）
- 修改 `~/Downloads/` 导出路径
- 修改 `~/.android/` ADB 密钥位置
- Android 端完整反编译缓存（保持 NoOp，路径 API 可预留）

---

## 2. 用户需求

1. 在 **设置** 页可查看/编辑「数据缓存目录」
2. 可通过 **浏览** 按钮选择文件夹（Desktop JVM）
3. 留空时使用 **默认目录**
4. 修改后对 **新导入/新创建** 的数据生效
5. 旧 `recent.json` 中记录的绝对路径 **不自动改写**；若工作区仍在旧位置，仍可打开；若用户希望彻底换盘，需手动迁移或重新导入

---

## 3. 目录布局（新）

固定引导配置（不可配置）：

```
~/.wps-adb-tool/settings.properties    # 始终在此，含 dataCacheDir 字段
```

用户可配置根目录 `{dataCacheDir}`，默认：

```
Windows:  %USERPROFILE%\.wps-adb-tool\cache
macOS/Linux: ~/.wps-adb-tool/cache
```

其下子目录：

```
{dataCacheDir}/
├── decompile/
│   ├── recent.json
│   ├── debug.keystore
│   └── {packageName}_{timestamp}/     # APK 工作区
├── devices/
│   ├── wireless-devices.txt           # Phase 2
│   └── removed-devices.txt            # Phase 2
└── temp/
    └── screenshots/                   # Phase 2（或由 temp 指向 java.io.tmpdir 子目录）
```

**命名统一：** 废弃 `~/.wps_adb_tool`（下划线）新写入；读取时兼容旧路径（见 §6）。

---

## 4. 数据模型

### 4.1 AppSettings 新增字段

```kotlin
data class AppSettings(
    // ... existing fields ...
    val dataCacheDir: String = "",  // 空 = 使用 AppDataPaths.defaultCacheRoot()
)
```

### 4.2 AppDataPaths（路径解析器）

**位置:** `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/AppDataPaths.kt`  
**模式:** `object`，构造时注入 `cacheRoot: String`；通过 `AppDataPaths.fromSettings(settings)` 创建。

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `defaultCacheRoot()` | String | 平台默认 cache 根 |
| `decompileRoot()` | String | `{root}/decompile` |
| `decompileWorkspacesRoot()` | String | 同 `decompileRoot()`（工作区直接在其下） |
| `recentProjectsFile()` | String | `{decompileRoot}/recent.json` |
| `debugKeystoreFile()` | String | `{decompileRoot}/debug.keystore` |
| `devicesRoot()` | String | `{root}/devices`（Phase 2） |
| `screenshotCacheDir()` | String | `{root}/temp/screenshots`（Phase 2） |

**规则：**

- 传入的 `dataCacheDir` 若非空，必须经 `canonical` 规范化并校验为可写目录
- 保存设置时若目录不存在，尝试 `mkdirs()`；失败则拒绝保存并提示

---

## 5. 受影响模块与改动点

### Phase 1（本计划实现）

| 模块 | 文件 | 改动 |
|------|------|------|
| 设置模型 | `AppSettings.kt` | 新增 `dataCacheDir` |
| 设置持久化 | `AppSettingsStore.kt` | 读写 `dataCacheDir` |
| 路径解析 | `AppDataPaths.kt` | **新建** |
| 反编译索引 | `DecompileWorkspaceStore.jvm.kt` | 构造函数/工厂接收 `AppDataPaths` 或动态 root |
| 反编译导入 | `AppViewModel.kt` | `importApkToWorkspace` 使用 `paths.decompileWorkspacesRoot()` |
| APK 签名 | `DecompileApkSigner.jvm.kt` | keystore 路径改用 `AppDataPaths` |
| 设置 UI | `SettingsScreen.kt` | 新卡片：路径输入 + 浏览 |
| 目录选择 | `pickDirectory.kt` | **新建** expect/actual |
| 仓库 | `JvmAdbRepository.kt` | 暴露 `settings` 供 ViewModel 解析路径；或构造 `AppDataPaths` |
| i18n | `strings.xml`, `values-zh/strings.xml` | 新字符串 |

### Phase 2（后续）

- `WirelessDeviceStore.kt`、`RemovedDeviceStore.kt` → `{dataCacheDir}/devices/`
- `JvmAdbRepository.screenshotDir` → `{dataCacheDir}/temp/screenshots`

### 不改动

- `settings.properties` 物理位置
- `~/Downloads/` 导出
- `DecompileService` 内 `createTempFile` 临时 dex（仍用系统 tmpdir）

---

## 6. 旧路径兼容

启动时 `DecompileWorkspaceStore.loadRecent()` 除读新 `recent.json` 外：

1. 若新文件为空且旧文件 `~/.wps_adb_tool/decompile/recent.json` 存在 → **读取旧文件**（只读，不写回旧路径）
2. 用户下次打开项目时 `rememberRecentDecompileProject` 写入新路径的 `recent.json`
3. 设置页展示 **legacy 提示**（可选 Phase 1.1）：检测到旧目录有数据时显示「检测到旧缓存目录 ~/.wps_adb_tool/decompile，可在文件管理器中手动迁移」

工作区目录本身不移动；`workspacePath` 仍为导入时的绝对路径。

---

## 7. UI 规格

### 设置页新增卡片「数据与缓存」

| 控件 | 行为 |
|------|------|
| 标签 | 数据缓存目录 |
| 只读/可编辑文本框 | 显示当前 `dataCacheDir`；空时显示默认路径占位说明 |
| 「浏览…」按钮 | 调用 `pickDirectory()`，选中后填入文本框 |
| 「恢复默认」按钮 | 清空 `dataCacheDir` |
| 说明文字 | 修改仅对新创建的反编译工作区生效；已有项目记录仍指向原路径 |

保存逻辑与现有 FAB 一致，写入 `AppSettingsStore`。

---

## 8. 错误处理

| 场景 | 行为 |
|------|------|
| 用户选择无写权限目录 | 保存失败，日志 + UI 提示 |
| 路径含非法字符 | 规范化失败则拒绝 |
| 删除/反编译进行中修改路径 | 不阻塞；新 import 用新路径，已打开工作区不受影响 |
| `pickDirectory` 取消 | 不修改文本框 |

---

## 9. 测试策略

| 测试 | 位置 |
|------|------|
| `AppDataPathsTest` | 默认 root、自定义 root、子路径拼接 |
| `AppSettingsStoreTest` | `dataCacheDir` 读写 roundtrip |
| `DecompileWorkspaceStoreTest` | 自定义 store 目录下 save/load/delete |
| 手动 | 设置改路径 → 导入 APK → 确认工作区在新目录；管理项目列表正常 |

---

## 10. 验收标准（Phase 1）

- [ ] 设置页可配置数据缓存目录并持久化
- [ ] Desktop 可通过文件夹选择器设置路径
- [ ] 新导入 APK 的工作区落在 `{dataCacheDir}/decompile/` 下
- [ ] `recent.json`、`debug.keystore` 落在新 decompile 目录
- [ ] 旧 `~/.wps_adb_tool/decompile/recent.json` 仍可加载项目列表
- [ ] `./gradlew :shared:jvmTest` 通过

---

## 11. 风险

| 风险 | 缓解 |
|------|------|
| 两套历史目录混淆 | 统一新写入；文档 + 可选 UI 提示 |
| Store 单例硬编码路径 | Phase 1 改为 `AppDataPaths.fromSettings(repository.settings.value)` 动态解析 |
| Windows 路径空格/中文 | 使用 `File.canonicalFile` + 集成测试 |
