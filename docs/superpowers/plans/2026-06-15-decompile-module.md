# 反编译模块 Implementation Plan（追踪用）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.  
> **Spec / Backlog:** `docs/superpowers/specs/2026-06-15-decompile-module-backlog.md`

**Goal:** 将 Decompile Studio 从「~45% 可演示原型」推进到可编辑、可搜索、可回编 APK 的 Desktop 逆向工作台。

**Architecture:** 延续现有 `DecompileService` expect/actual + `AppViewModel` 状态机 + Compose UI；JVM 层补齐 smali/jadx/dexlib2 能力；Android 短期隐藏入口。

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, JADX 1.5.0, Baksmali/dexlib2, smali, RSyntaxTextArea

**Design:** `design-decompile/`（空态 UI 原型）

---

## Phase D0 — 空态 UI 还原

- [x] **DC-023** 未打开项目时空态 UI 按 `design-decompile` 设计原型还原（双栏 IDE 布局）
- [x] **DC-011** APK 拖放导入（与 Drop Zone 交互合并）
- [x] **DC-012** 最近工作区持久化（Recent Project 卡片）

**验收：** 首屏与 `design-decompile/screen.png` 一致；可导入 APK。

---

## Phase D1 — 诚实化 + 编辑闭环

- [x] **DC-002** 消除虚假 DEX 操作（置灰未实现项 / 移除假成功日志）
- [x] **DC-001** 编辑器文件保存（XML/Smali/常见文本 + Ctrl+S）
- [x] **DC-026** 常见文本文件打开（txt/json/properties/md 等，Markdown 可选预览库）
- [x] **DC-024** Project Explorer 标题行「退出项目」icon（含 DC-013 状态/缓存清理）
- [x] **DC-014** DEX 操作后刷新主文件树
- [x] **DC-022** README / RW-F08 文档同步

**验收：** 导入 → 编辑 → 保存 → 关闭；无误导性成功提示。

---

## Phase D2 — DexEditor++ 可用

- [x] **DC-025** Dex 编辑器++ 多 DEX 选择弹窗（≥2 个 dex 时弹出，默认勾选当前点击项）
- [x] **DC-003** Smali 真实全文搜索 + 结果跳转
- [x] **DC-004** DEX→Java 结果 UI 浏览（`.java` 标签页）
- [x] **DC-007** DexEditor++ 导出
- [x] **DC-021** DecompileService 单元测试（基础）

**验收：** DexEditor++ 浏览 / 搜索 / 常量 三项均可真实使用（常量写回除外）。

---

## Phase D3 — 改码回编

- [x] **DC-008** Smali → DEX 重组
- [x] **DC-009** APK 重打包
- [x] **DC-010** APK 签名
- [x] **DC-005** DEX 字符串常量写回（Smali 路径）
- [x] **DC-006** 单文件 Smali→Java

**验收：** 修改 Smali → 导出可 `adb install` 的 APK。

---

## Phase D4 — 高级 / 可选

- [x] **DC-015** Smali 语法高亮
- [ ] **DC-016** DEX→JAR
- [ ] **DC-017** DEX 修复
- [ ] **DC-018** 包名/类名批量替换
- [ ] **DC-020** Android 平台策略
- [ ] **DC-019** 控制流混淆（或永久移出范围）

---

## 验证命令

```bash
./gradlew :shared:jvmTest
./gradlew :desktopApp:compileKotlinJvm
./gradlew :desktopApp:run
```

手动验收：Desktop → Decompile 导航 → 导入测试 APK → 按 Phase 验收清单操作。

---

## 任务详情

每项任务的证据、涉及文件、依赖与验收标准见：

**`docs/superpowers/specs/2026-06-15-decompile-module-backlog.md`**
