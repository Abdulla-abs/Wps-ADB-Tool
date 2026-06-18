# Windows 桌面端 CI 构建指南

本文档说明如何在 GitHub Actions 上构建 WpsAdbTool 的 Windows 安装包（MSI）。

## 快速开始

1. 推送代码到 GitHub
2. 打开 **Actions → Windows Desktop → Run workflow**
3. 下载 Artifacts 中的 `.msi`

| 产物 | 适用设备 |
|------|----------|
| `WpsAdbTool-*-windows-x64.msi` | Windows 10/11 x64 |

打 `v*` 标签（如 `v1.0.0`）会触发 **Release Desktop** workflow，自动构建 Windows / macOS 安装包并创建 [GitHub Release](https://github.com/Abdulla-abs/Wps-ADB-Tool/releases)。版本号取自标签名（去掉 `v` 前缀）。

仅构建 Windows MSI、不上传 Release 时，可手动运行本 workflow。

## 签名（可选）

当前 CI 产出**未签名** MSI。Windows SmartScreen 可能提示「未知发布者」，用户可点击 **更多信息 → 仍要运行** 继续安装。

正式发布如需 Authenticode 签名，需另行配置证书与 `signtool` 步骤（尚未接入 workflow）。

## 本地命令参考

```powershell
# 未签名 release MSI
.\gradlew.bat :desktopApp:packageReleaseMsi

# 指定版本
.\gradlew.bat :desktopApp:packageReleaseMsi -PwpsAdbTool.version=1.2.0

# 与 CI 相同的打包脚本
$env:WPS_ADB_TOOL_VERSION = "1.2.0"
pwsh -File scripts/windows/build-msi.ps1
```

产物目录：`desktopApp/build/compose/binaries/main-release/msi/`

CI 会将 MSI 复制到 `desktopApp/build/ci-artifacts/WpsAdbTool-<version>-windows-x64.msi` 后上传。

## 环境要求

- **JDK 17**（含 `jlink` 与 `jpackage`；CI 使用 Temurin 17）
- Gradle 通过 `gradlew.bat` 执行
- 打包任务：`:desktopApp:packageReleaseMsi`

## 相关文件

| 路径 | 用途 |
|------|------|
| `.github/workflows/windows-desktop.yml` | GitHub Actions 工作流 |
| `desktopApp/build.gradle.kts` | 打包 Gradle 配置 |
| `scripts/windows/build-msi.ps1` | MSI 构建与产物归档 |

## 故障排查

**`jpackage` 或 `jlink` 找不到**

- 确认使用的是完整 JDK，而非仅 JRE
- 检查 `JAVA_HOME` 是否指向 JDK 17 根目录

**Gradle 构建失败 / configuration cache 错误**

- 本地可尝试 `./gradlew.bat :desktopApp:packageReleaseMsi --no-configuration-cache`
- 查看 Actions 日志中的完整 stack trace

**SmartScreen 拦截**

- 未签名 MSI 的正常现象；签名接入后可缓解
