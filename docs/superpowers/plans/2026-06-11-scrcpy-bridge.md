# scrcpy 外部窗口投屏桥接 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Desktop 端通过用户自备 scrcpy 实现真实投屏，WpsAdbTool 负责路径配置、进程启动与生命周期管理。

**Architecture:** `JvmScrcpyMirrorService` 用 `ProcessBuilder` 启动 scrcpy 独立窗口；`AppViewModel` 在 Mirror 标签开关时编排启停；`TabSessionManager` 登记 `SCRCPY_PROCESS`；Mock/Android 使用 `NoOpScrcpyMirrorService`。

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, ProcessBuilder, scrcpy CLI

**Spec:** `docs/superpowers/specs/2026-06-11-scrcpy-bridge-design.md`

---

## Phase M1 — Settings + 模型 ✅

- [x] `AppSettings.scrcpyPath`
- [x] `MirrorSessionState` 枚举
- [x] `TabListenKind.SCRCPY_PROCESS`
- [x] `SidePanelTab.Mirror` 扩展 `sessionState` / `errorMessage`
- [x] Settings UI + i18n（中英）

## Phase M2 — ScrcpyMirrorService ✅

- [x] `ScrcpyMirrorService` 接口 + `NoOpScrcpyMirrorService`
- [x] `JvmScrcpyMirrorService`（命令构造、ADB 环境变量、进程管理）
- [x] `createScrcpyMirrorService()` expect/actual
- [x] `JvmScrcpyMirrorServiceTest`

## Phase M3 — MirrorTabContent UI ✅

- [x] 状态面板（设备信息、状态徽章、开始/停止、下载指引）
- [x] 移除模拟手机框与假 Shell

## Phase M4 — ViewModel 集成 ✅

- [x] `AppViewModel` 投屏启停、进程退出同步、teardown
- [x] `App.kt` 注入 `createScrcpyMirrorService`
- [x] `SidePanel` / `AppShell` 回调接线
- [x] `AppViewModelMirrorTeardownTest`

## Phase M5 — 验收 ✅

- [x] `./gradlew :shared:jvmTest`
- [x] `./gradlew :desktopApp:compileKotlin`
