# macOS 桌面端 CI 构建指南

本文档说明如何在 GitHub Actions 上构建 WpsAdbTool 的 macOS 安装包，包括签名、公证（Notarization）与 Apple Silicon 架构支持。

## 快速开始

1. 推送代码到 GitHub
2. 打开 **Actions → macOS Desktop → Run workflow**
3. 选择构建目标，下载 Artifacts 中的 `.dmg`

默认 **both-arch** 会并行产出两份安装包：

| 产物 | 适用设备 |
|------|----------|
| `WpsAdbTool-*-macos-arm64.dmg` | Apple Silicon（M1/M2/M3/M4） |
| `WpsAdbTool-*-macos-x64.dmg` | Intel Mac |

打 `v*` 标签（如 `v1.0.0`）也会自动触发构建，版本号取自标签名（去掉 `v` 前缀）。标签触发默认产出**未签名** DMG；需要签名与公证时，请在 Actions 中手动触发 workflow 并勾选 **notarize**（需事先配置全部 Secrets）。

## 签名与公证

### 自用 / 内测（可跳过）

不配置任何 Apple 密钥时，CI 产出**未签名** DMG。用户在 Mac 上首次打开会看到「无法验证开发者」，需在 **系统设置 → 隐私与安全性** 中点击 **仍要打开**。

Workflow 手动触发时，保持 **notarize = false** 即可。

### 正式发布（需要 Apple Developer 账号）

需要 [Apple Developer Program](https://developer.apple.com/programs/)（$99/年），并完成：

1. 创建 **Developer ID Application** 证书
2. 在 [Apple Developer](https://developer.apple.com/account/resources/identifiers/list) 注册 App ID：`fun.abbas.wpsadb`
3. 生成 [App 专用密码](https://appleid.apple.com/account/manage)（用于公证）
4. 导出 `.p12` 证书文件

#### GitHub Secrets

在仓库 **Settings → Secrets and variables → Actions** 中添加：

| Secret | 说明 |
|--------|------|
| `BUILD_CERTIFICATE_BASE64` | `.p12` 文件的 Base64 编码 |
| `P12_PASSWORD` | 导出 `.p12` 时设置的密码 |
| `KEYCHAIN_PASSWORD` | CI 临时钥匙串密码（任意随机字符串） |
| `NOTARIZATION_APPLE_ID` | Apple ID 邮箱 |
| `NOTARIZATION_PASSWORD` | App 专用密码 |
| `NOTARIZATION_TEAM_ID` | Team ID（10 位字符，可在开发者账号页面查看） |

生成 Base64 证书（在 Mac 上）：

```bash
base64 -i DeveloperID.p12 | pbcopy
```

配置完成后，手动触发 workflow 并勾选 **notarize = true**，或推送 `v*` 标签（需已配置 `BUILD_CERTIFICATE_BASE64`）。

Gradle 侧已接入 Compose Desktop 签名 DSL（见 `desktopApp/build.gradle.kts`），CI 通过环境变量驱动：

- `MACOS_SIGN=true`
- `MACOS_SIGNING_IDENTITY` — 由 `scripts/macos/install-signing-cert.sh` 自动解析
- `NOTARIZATION_*` — 触发 `notarizeReleaseDmg` 任务（含等待与 staple）

## 架构策略

Compose Desktop / jpackage **无法一次构建出真正的 Universal Binary**，因此项目采用双轨方案：

### 方案 A：分架构包（推荐，CI 默认）

分别在 arm64 与 x64 JDK 下调用 `packageReleaseDmg`，用户按机器架构选择对应 DMG。在 Apple Silicon 上可通过 Rosetta 运行 x64 版，但性能较差。

### 方案 B：Universal 包（高级）

Workflow 选择 **target = universal**，或本地执行：

```bash
export JDK_ARM64=/path/to/arm64-jdk
export JDK_X64=/path/to/x64-jdk
./scripts/macos/build-universal-dmg.sh
```

脚本会分别构建两个 `.app`，用 `lipo` 合并 Mach-O 二进制，重新签名后打 DMG。Universal 构建耗时更长，但用户只需下载一个文件。

## 本地命令参考

```bash
# 未签名 release DMG（当前系统架构）
./gradlew :desktopApp:packageReleaseDmg

# 指定版本
./gradlew :desktopApp:packageReleaseDmg -PwpsAdbTool.version=1.2.0

# 签名 + 公证（需已配置环境变量）
export MACOS_SIGN=true
export MACOS_SIGNING_IDENTITY="Developer ID Application: Your Name (TEAMID)"
export NOTARIZATION_APPLE_ID=...
export NOTARIZATION_PASSWORD=...
export NOTARIZATION_TEAM_ID=...
./gradlew :desktopApp:notarizeReleaseDmg
```

## 无 Mac 本地环境的调试限制

必须承认：**没有 Mac 时排查 macOS 专属问题成本很高**。典型场景包括：

- Compose UI 在 macOS 上的布局/字体差异
- `jvmMain` 中 `expect/actual` 平台实现（如 `ApkDropTarget`、`ApkFilePicker`）的行为差异
- 签名、公证、Gatekeeper 相关的一机性问题

当前可行的「盲盒式」链路：

```
修改代码 → push → 等待 GitHub Actions → 下载 Artifact → 在 Mac 上验证
```

建议降低迭代成本的做法：

1. **优先在 Windows 跑 `:shared:jvmTest` 和 `:desktopApp:run`**，覆盖大部分业务逻辑
2. **macOS CI 作为发布门禁**，不要每处 UI 微调都依赖云端打包
3. 若条件允许，借一台 Mac 或使用 macOS 云主机做最终验收
4. 对平台相关代码（`shared/src/jvmMain/.../platform/`）编写 JVM 单元测试，减少上机验证次数

## 相关文件

| 路径 | 用途 |
|------|------|
| `.github/workflows/macos-desktop.yml` | GitHub Actions 工作流 |
| `desktopApp/build.gradle.kts` | 打包、签名、公证 Gradle 配置 |
| `scripts/macos/install-signing-cert.sh` | CI 导入 .p12 证书 |
| `scripts/macos/build-arch-dmg.sh` | 单架构 DMG 构建 |
| `scripts/macos/build-universal-dmg.sh` | Universal DMG 构建 |

## 故障排查

**`No Developer ID Application identity found`**

- 确认 `.p12` 包含 **Developer ID Application** 类型证书（不是 Mac App Distribution）
- 检查 `P12_PASSWORD` 是否正确

**公证失败**

- 确认 `NOTARIZATION_TEAM_ID` 与证书 Team 一致
- 使用 App 专用密码，而非 Apple ID 登录密码
- 查看 Actions 日志中 `notarytool` 返回的错误详情

**Intel Mac 提示「不支持在此 Mac 上运行」**

- 说明下载了 arm64 包；请改用 `*-macos-x64.dmg`，或使用 Universal 包

**Universal 包体积异常大**

- 正常现象：内含双架构 JRE 与 native 库；若介意体积，优先分发分架构包
