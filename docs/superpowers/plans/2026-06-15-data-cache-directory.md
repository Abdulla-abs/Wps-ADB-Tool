# 全局数据缓存目录 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在全局设置中新增可配置的「数据缓存目录」，统一反编译模块（工作区、recent.json、debug.keystore）的磁盘根路径，并兼容旧 `~/.wps_adb_tool/decompile` 数据。

**Architecture:** `AppSettings.dataCacheDir` 持久化于固定 `~/.wps-adb-tool/settings.properties`；运行时通过 `AppDataPaths.fromSettings()` 解析子路径；各 Store/Service 不再硬编码 `user.home` 子目录。

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Java `File`, JVM Swing 目录选择器

**Spec:** `docs/superpowers/specs/2026-06-15-data-cache-directory.md`

---

## 文件结构预览

| 文件 | 职责 |
|------|------|
| `model/AppSettings.kt` | 新增 `dataCacheDir` |
| `data/AppDataPaths.kt` | 缓存根目录与子路径解析 |
| `data/AppSettingsStore.kt` | 读写 `dataCacheDir` |
| `platform/pickDirectory.kt` | expect 目录选择 |
| `jvmMain/.../pickDirectory.jvm.kt` | JFileChooser 选目录 |
| `androidMain/.../pickDirectory.android.kt` | NoOp / SAF 占位 |
| `data/DecompileWorkspaceStore.jvm.kt` | 动态 recent.json 路径 + 旧路径 fallback |
| `data/DecompileApkSigner.jvm.kt` | keystore 路径改 AppDataPaths |
| `viewmodel/AppViewModel.kt` | import 使用 AppDataPaths |
| `ui/settings/SettingsScreen.kt` | 缓存目录 UI |
| `composeResources/values/strings.xml` | 英文 |
| `composeResources/values-zh/strings.xml` | 中文 |
| `jvmTest/.../AppDataPathsTest.kt` | 单元测试 |
| `jvmTest/.../AppSettingsStoreTest.kt` | 扩展测试 |
| `jvmTest/.../DecompileWorkspaceStoreTest.kt` | 新建 |

---

## Phase 1 — 路径抽象 + 设置 UI

### Task 1: AppSettings 与 AppSettingsStore

**Files:**
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/model/AppSettings.kt`
- Modify: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/AppSettingsStore.kt`
- Test: `shared/src/jvmTest/kotlin/fun/abbas/wps_adb/AppSettingsStoreTest.kt`

- [ ] **Step 1: 扩展 AppSettings**

```kotlin
data class AppSettings(
    val adbPath: String = "adb",
    val scrcpyPath: String = "scrcpy",
    val scrcpyConnection: ScrcpyConnectionOptions = ScrcpyConnectionOptions(),
    val minPort: Int = 5555,
    val maxPort: Int = 5585,
    val scanIntervalSec: Int = 15,
    val parallelThreads: Int = 4,
    val logRetention: Int = 2500,
    val autoApproveKey: Boolean = true,
    val diagnosticTelemetry: Boolean = false,
    val dataCacheDir: String = "",
)
```

- [ ] **Step 2: AppSettingsStore 增加 KEY 与读写**

在 `AppSettingsStore.kt` 中：

```kotlin
private const val KEY_DATA_CACHE_DIR = "dataCacheDir"

// load() 内：
dataCacheDir = props.getProperty(KEY_DATA_CACHE_DIR, ""),

// save() 内：
setProperty(KEY_DATA_CACHE_DIR, settings.dataCacheDir)
```

- [ ] **Step 3: 写失败测试**

在 `AppSettingsStoreTest.kt` 追加：

```kotlin
@Test
fun saveAndLoad_persistsDataCacheDir() {
    val file = File.createTempFile("wps-adb-settings", ".properties")
    file.deleteOnExit()
    val store = AppSettingsStore(file)
    store.save(AppSettings(dataCacheDir = "D:\\WpsCache"))
    assertEquals("D:\\WpsCache", AppSettingsStore(file).load().dataCacheDir)
}
```

- [ ] **Step 4: 运行测试**

```bash
./gradlew :shared:jvmTest --tests "fun.abbas.wps_adb.AppSettingsStoreTest"
```

Expected: PASS

---

### Task 2: AppDataPaths 路径解析器

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/AppDataPaths.kt`
- Test: `shared/src/jvmTest/kotlin/fun/abbas/wps_adb/AppDataPathsTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.AppDataPaths
import `fun`.abbas.wps_adb.model.AppSettings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppDataPathsTest {
    @Test
    fun defaultCacheRoot_usesWpsAdbToolCacheUnderHome() {
        val paths = AppDataPaths.fromSettings(AppSettings())
        val root = File(paths.cacheRoot())
        assertTrue(root.path.replace('\\', '/').endsWith(".wps-adb-tool/cache"))
    }

    @Test
    fun customCacheRoot_overridesDefault() {
        val custom = File(System.getProperty("java.io.tmpdir"), "wps-cache-test").absolutePath
        val paths = AppDataPaths.fromSettings(AppSettings(dataCacheDir = custom))
        assertEquals(File(custom).canonicalFile.path, File(paths.cacheRoot()).path)
        assertEquals(
            File(custom, "decompile/recent.json").path,
            paths.recentProjectsFile(),
        )
    }
}
```

- [ ] **Step 2: 实现 AppDataPaths.kt**

```kotlin
package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.AppSettings
import java.io.File

class AppDataPaths private constructor(
    private val root: File,
) {
    fun cacheRoot(): String = root.absolutePath
    fun decompileRoot(): String = File(root, "decompile").absolutePath
    fun decompileWorkspacesRoot(): String = decompileRoot()
    fun recentProjectsFile(): String = File(decompileRoot(), "recent.json").absolutePath
    fun debugKeystoreFile(): String = File(decompileRoot(), "debug.keystore").absolutePath

    fun ensureDirectoriesExist() {
        File(decompileRoot()).mkdirs()
    }

    companion object {
        fun defaultCacheRoot(): File {
            val home = System.getProperty("user.home")
            return File(home, ".wps-adb-tool/cache")
        }

        /** Legacy decompile dir (underscore) — read-only fallback */
        fun legacyDecompileRoot(): File =
            File(System.getProperty("user.home"), ".wps_adb_tool/decompile")

        fun fromSettings(settings: AppSettings): AppDataPaths {
            val root = if (settings.dataCacheDir.isBlank()) {
                defaultCacheRoot()
            } else {
                File(settings.dataCacheDir)
            }
            return AppDataPaths(root.canonicalFile)
        }
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
./gradlew :shared:jvmTest --tests "fun.abbas.wps_adb.AppDataPathsTest"
```

Expected: PASS

---

### Task 3: pickDirectory 平台 API

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/platform/pickDirectory.kt`
- Create: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/platform/pickDirectory.jvm.kt`
- Create: `shared/src/androidMain/kotlin/fun/abbas/wps_adb/platform/pickDirectory.android.kt`

- [ ] **Step 1: expect 声明**

```kotlin
package `fun`.abbas.wps_adb.platform

expect suspend fun pickDirectory(initialPath: String? = null): String?
```

- [ ] **Step 2: JVM — JFileChooser**

```kotlin
package `fun`.abbas.wps_adb.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView

actual suspend fun pickDirectory(initialPath: String?): String? = withContext(Dispatchers.Swing) {
    val chooser = JFileChooser(initialPath ?: FileSystemView.getFileSystemView().homeDirectory.path).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Select data cache directory"
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
    chooser.selectedFile?.absolutePath
}
```

- [ ] **Step 3: Android 占位**

```kotlin
actual suspend fun pickDirectory(initialPath: String?): String? = null
```

- [ ] **Step 4: 编译**

```bash
./gradlew :shared:compileKotlinJvm :shared:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL

---

### Task 4: DecompileWorkspaceStore 动态路径 + 旧路径 fallback

**Files:**
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/DecompileWorkspaceStore.kt`
- Modify: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/DecompileWorkspaceStore.jvm.kt`
- Create: `shared/src/jvmTest/kotlin/fun/abbas/wps_adb/DecompileWorkspaceStoreTest.kt`

- [ ] **Step 1: expect 接口增加路径参数**

```kotlin
expect object DecompileWorkspaceStore {
    fun loadRecent(recentFile: String): List<RecentDecompileProject>
    fun saveRecent(recentFile: String, project: RecentDecompileProject)
    fun removeRecent(recentFile: String, workspacePath: String)
    fun deleteProject(recentFile: String, project: RecentDecompileProject)
}
```

或保持 object 但增加：

```kotlin
expect object DecompileWorkspaceStore {
    fun loadRecent(paths: AppDataPaths): List<RecentDecompileProject>
    // ...
}
```

**推荐：** 方法签名增加 `recentFile: String`，由调用方传入 `paths.recentProjectsFile()`，Store 不再内置 hardcoded dir。

- [ ] **Step 2: JVM 实现 — 去掉硬编码 storeDir**

```kotlin
actual fun loadRecent(recentFile: String): List<RecentDecompileProject> = runCatching {
    val file = File(recentFile)
    if (file.exists()) return parseRecentJson(file.readText())
    val legacy = File(AppDataPaths.legacyDecompileRoot(), "recent.json")
    if (legacy.exists()) return parseRecentJson(legacy.readText())
    emptyList()
}.getOrElse { emptyList() }

actual fun saveRecent(recentFile: String, project: RecentDecompileProject) {
    File(recentFile).parentFile?.mkdirs()
    // ... same merge logic, write to recentFile
}
```

- [ ] **Step 3: 更新所有调用点**

| 调用方 | 改法 |
|--------|------|
| `AppViewModel.init` | `DecompileWorkspaceStore.loadRecent(paths.recentProjectsFile())` |
| `AppViewModel.openDecompileProjectManager` | 同上 |
| `AppViewModel.deleteDecompileProject` | `deleteProject(paths.recentProjectsFile(), project)` |
| `AppViewModel.rememberRecentDecompileProject` | `saveRecent(paths.recentProjectsFile(), ...)` |
| `AppViewModel.openRecentDecompileProject` catch | `removeRecent(paths.recentProjectsFile(), ...)` |

`paths` 来自 `AppDataPaths.fromSettings(repository.settings.value)`。

- [ ] **Step 4: DecompileWorkspaceStoreTest**

```kotlin
@Test
fun saveRecent_writesToCustomRecentFile() = runBlocking {
    val tempDir = createTempDir("ws-store")
    val recentFile = File(tempDir, "decompile/recent.json").absolutePath
    val project = RecentDecompileProject(/* ... */)
    DecompileWorkspaceStore.saveRecent(recentFile, project)
    assertEquals(1, DecompileWorkspaceStore.loadRecent(recentFile).size)
}
```

- [ ] **Step 5: 运行测试**

```bash
./gradlew :shared:jvmTest --tests "fun.abbas.wps_adb.DecompileWorkspaceStoreTest"
```

---

### Task 5: 反编译导入与签名 keystore

**Files:**
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/viewmodel/AppViewModel.kt`
- Modify: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/DecompileApkSigner.jvm.kt`

- [ ] **Step 1: AppViewModel.importApkToWorkspace**

替换：

```kotlin
val userHome = System.getProperty("user.home")
val workspaceRoot = "$userHome/.wps_adb_tool/decompile"
```

为：

```kotlin
val paths = AppDataPaths.fromSettings(repository.settings.value)
paths.ensureDirectoriesExist()
val workspaceRoot = paths.decompileWorkspacesRoot()
```

- [ ] **Step 2: DecompileApkSigner.ensureDebugKeystore**

```kotlin
fun ensureDebugKeystore(keystoreFile: File): File {
    keystoreFile.parentFile?.mkdirs()
    if (!keystoreFile.isFile) generateDebugKeystore(keystoreFile)
    return keystoreFile
}

fun sign(unsignedApk: File, signedApk: File, adbPath: String, keystoreFile: File) {
    // use keystoreFile instead of hardcoded path
}
```

`DecompileService.signApk` 调用链传入 `AppDataPaths.fromSettings(...).debugKeystoreFile()`（需在 `DecompileService.jvm.kt` 的 `signApk` 或上层 ViewModel 解析）。

- [ ] **Step 3: 手动冒烟**

Desktop → 设置改缓存目录 → 导入 APK → 检查新目录下出现 `{package}_{ts}/` 与 `recent.json`

---

### Task 6: 设置页 UI

**Files:**
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/settings/SettingsScreen.kt`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`
- Modify: `shared/src/commonMain/composeResources/values-zh/strings.xml`

- [ ] **Step 1: 添加 i18n**

`values/strings.xml`:

```xml
<string name="settings_data_cache">Data Cache Directory</string>
<string name="settings_data_cache_hint">Decompile workspaces and app cache files. Changes apply to newly created data only.</string>
<string name="settings_data_cache_browse">Browse…</string>
<string name="settings_data_cache_reset">Use Default</string>
<string name="settings_data_cache_default">Default: %1$s</string>
```

`values-zh/strings.xml`:

```xml
<string name="settings_data_cache">数据缓存目录</string>
<string name="settings_data_cache_hint">反编译工作区与应用缓存文件存放位置。修改后仅对新创建的数据生效。</string>
<string name="settings_data_cache_browse">浏览…</string>
<string name="settings_data_cache_reset">恢复默认</string>
<string name="settings_data_cache_default">默认：%1$s</string>
```

- [ ] **Step 2: SettingsScreen 新卡片**

在 `SettingsScreen` 中增加状态：

```kotlin
var dataCacheDir by remember(settings) { mutableStateOf(settings.dataCacheDir) }
val scope = rememberCoroutineScope()
```

卡片内容：OutlinedTextField + Row(浏览, 恢复默认) +  hint Text

浏览按钮：

```kotlin
scope.launch {
    pickDirectory(dataCacheDir.ifBlank { null })?.let { dataCacheDir = it }
}
```

`saveSettings` 传入 `dataCacheDir = dataCacheDir.trim()`。

- [ ] **Step 3: 编译运行**

```bash
./gradlew :desktopApp:run
```

Expected: 设置页显示新字段；浏览可选目录；保存后重启仍保留。

---

## Phase 2 — 设备与截图缓存（后续迭代）

- [ ] `WirelessDeviceStore` / `RemovedDeviceStore` 改用 `AppDataPaths.devicesRoot()`
- [ ] `JvmAdbRepository.screenshotDir` 改用 `AppDataPaths.screenshotCacheDir()`
- [ ] 设置保存时若路径变更，重建 store 或 lazy 读取 paths

**不在 Phase 1 范围。**

---

## 验证命令（Phase 1 完成时）

```bash
./gradlew :shared:jvmTest
./gradlew :shared:compileKotlinJvm
./gradlew :desktopApp:run
```

### 手动验收清单

- [ ] 默认路径下导入 APK，工作区在 `~/.wps-adb-tool/cache/decompile/`
- [ ] 自定义路径（如 `D:\WpsAdbCache`）导入 APK，工作区在新位置
- [ ] 管理项目列表正常；删除项目不卡顿
- [ ] 旧 `~/.wps_adb_tool/decompile/recent.json` 中项目仍可打开
- [ ] 导出签名 APK 仍成功（keystore 在新 decompile 目录）

---

## Self-Review（计划自检）

| 规格条目 | 对应 Task |
|----------|-----------|
| AppSettings.dataCacheDir | Task 1 |
| AppDataPaths 子目录 | Task 2 |
| 设置 UI + 浏览 | Task 3, 6 |
| 反编译工作区/recent/keystore | Task 4, 5 |
| 旧路径 fallback | Task 4 |
| 测试 | Task 1–4 |
| Phase 2 设备/截图 | Phase 2 节 |

**Placeholder 扫描:** 无 TBD；Phase 2 明确标注后续。

---

## 执行方式

Plan 已保存至 `docs/superpowers/plans/2026-06-15-data-cache-directory.md`，规格见 `docs/superpowers/specs/2026-06-15-data-cache-directory.md`。

**两种执行方式：**

1. **Subagent-Driven（推荐）** — 按 Task 1→6 逐任务派发子 agent，每步 review  
2. **Inline Execution** — 本会话按 executing-plans 批量实现，checkpoint Review

如需开始写代码，告诉我选哪种方式即可。
