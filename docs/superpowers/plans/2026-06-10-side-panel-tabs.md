# SidePanel 标签化侧边栏 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将右侧浮层统一为 Chrome 风格多标签 `SidePanel`，支持 Mirror / AppLog 标签，APK 安装成功后自动打开应用日志页，并在关闭标签时停止所有监听会话。

**Architecture:** `SidePanelState` + `SidePanelController` 管理标签；`TabSessionManager` 登记/停止长驻监听；`AppViewModel` 编排安装结果 → 开标签；UI 在 `ui/sidepanel/`。

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, StateFlow, adb (Desktop JVM)

**Spec:** `docs/superpowers/specs/2026-06-10-side-panel-tabs-design.md`

---

## Phase SP1 — SidePanel 骨架 + Mirror 迁移 ✅

- [x] 模型：`SidePanelTab`, `SidePanelState`, `TabListenKind`
- [x] `TabSessionManager` + `NoOpTabSessionManager`
- [x] `SidePanelController`（开/关/驱逐 Mirror 标签）
- [x] UI：`SidePanel`, `SidePanelTabBar`, `MirrorTabContent`
- [x] `AppUiState.sidePanel` 替代 `mirroredDevice`
- [x] `AppShell` Row 布局集成 SidePanel
- [x] 单元测试 `SidePanelControllerTest`

## Phase SP2 — AppLog 标签 + 安装成功触发 ✅

- [x] `ApkMetadata`, `ApkInstallResult`
- [x] `AdbRepository.installApkOnDevice` → `ApkInstallResult`
- [x] Mock/Jvm Repository 返回安装结果
- [x] `SidePanelController.openAppLogTab`
- [x] `AppLogTabContent` UI + i18n
- [x] 安装成功自动开标签；Mock 监听日志

## Phase SP3 — 包名解析 + 打开应用 ✅

- [x] `expect/actual ApkMetadataParser`（aapt → apkanalyzer → 文件名降级）
- [x] 安装后异步补全 `packageName`（Jvm 安装时同步解析；缺失时 ViewModel 异步补全）
- [x] `AdbRepository.parseApkMetadata` + `launchApp` Mock/Desktop
- [x] `JvmAdbRunner.launchApp`（`monkey`）
- [x] `launchAppInTab` 调用 `repository.launchApp`
- [x] 单元测试 `ApkBadgingParserTest`

## Phase SP4 — 真实 logcat + TabSessionManager ✅

- [x] `startAppLogcat` / `stopAppLogcat` / `stopAllAppLogcatSessions`（Mock + Jvm）
- [x] `JvmAdbRunner.resolveProcessId` + `startLogcat`
- [x] `LogcatLineParser` 行解析
- [x] ViewModel 改为收集 Repository Flow，统一 `teardownTabListening`
- [x] 单元测试 `LogcatLineParserTest`、`MockAppLogcatSessionTest`

## Phase SP5 — 收尾 ✅

- [x] 删除 `MirrorDrawer.kt`（逻辑已迁入 `MirrorTabContent.kt`）
- [x] `AppViewModelTeardownTest`：关标签 / disconnect / killAdb / 标签驱逐 teardown
- [x] `./gradlew :shared:jvmTest` + `:desktopApp:compileKotlin` 验收
