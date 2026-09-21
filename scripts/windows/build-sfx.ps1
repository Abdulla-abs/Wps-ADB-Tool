# Build a self-extracting EXE from the release app-image (createReleaseDistributable output).
#
# Requires 7-Zip (7z.exe + 7z.sfx). Install: winget install 7zip.7zip
#
# Usage:
#   pwsh -File scripts/windows/build-sfx.ps1
#   pwsh -File scripts/windows/build-sfx.ps1 -SkipBuild
#
# Environment:
#   JAVA_HOME              JDK with jlink + jpackage (optional)
#   WPS_ADB_TOOL_VERSION   App version (optional, default 1.0.0)
#   SEVEN_ZIP_HOME         7-Zip install dir (optional, auto-detected)

param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Version = if ($env:WPS_ADB_TOOL_VERSION) { $env:WPS_ADB_TOOL_VERSION } else { "1.0.0" }
$OutputDir = Join-Path $RootDir "desktopApp\build\ci-artifacts"
$SfxConfig = Join-Path $PSScriptRoot "sfx-config.txt"

function Resolve-SevenZip {
    if ($env:SEVEN_ZIP_HOME) {
        $candidate = Join-Path $env:SEVEN_ZIP_HOME "7z.exe"
        if (Test-Path $candidate) {
            return @{
                Zip = $candidate
                Sfx = Join-Path $env:SEVEN_ZIP_HOME "7z.sfx"
            }
        }
    }

    $sevenZip = Get-Command 7z -ErrorAction SilentlyContinue
    if ($sevenZip) {
        $dir = Split-Path $sevenZip.Source -Parent
        return @{
            Zip = $sevenZip.Source
            Sfx = Join-Path $dir "7z.sfx"
        }
    }

    foreach ($base in @(
        "${env:ProgramFiles}\7-Zip"
        "${env:ProgramFiles(x86)}\7-Zip"
    )) {
        $zip = Join-Path $base "7z.exe"
        $sfx = Join-Path $base "7z.sfx"
        if ((Test-Path $zip) -and (Test-Path $sfx)) {
            return @{ Zip = $zip; Sfx = $sfx }
        }
    }

    return $null
}

function Install-SevenZip {
    Write-Host "7-Zip not found. Attempting install via winget..."
    & winget install --id 7zip.7zip -e --accept-package-agreements --accept-source-agreements
    if ($LASTEXITCODE -ne 0) {
        throw @"
7-Zip is required to build the self-extracting EXE.

Install manually:
  winget install 7zip.7zip
  - or download from https://www.7-zip.org/

Then re-run: pwsh -File scripts/windows/build-sfx.ps1
"@
    }
}

Set-Location $RootDir

if ($env:JAVA_HOME) {
    Write-Host "Using JAVA_HOME=$($env:JAVA_HOME)"
    $env:GRADLE_OPTS = "-Dorg.gradle.java.home=$($env:JAVA_HOME)"
    $env:PATH = "$($env:JAVA_HOME)\bin;$env:PATH"
}

if (-not $SkipBuild) {
    Write-Host "Building release app-image (version $Version)..."
    $GradleArgs = @("-PwpsAdbTool.version=$Version", ":desktopApp:createReleaseDistributable")
    if ($env:CI -eq "true") {
        $GradleArgs += "--no-configuration-cache"
    }
    & .\gradlew.bat @GradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle createReleaseDistributable failed with exit code $LASTEXITCODE"
    }
}

$AppDir = Join-Path $RootDir "desktopApp\build\compose\binaries\main-release\app\WpsAdbTool"
if (-not (Test-Path (Join-Path $AppDir "WpsAdbTool.exe"))) {
    throw "App image not found: $AppDir`nRun createReleaseDistributable first, or omit -SkipBuild."
}

$sevenZip = Resolve-SevenZip
if (-not $sevenZip) {
    Install-SevenZip
    $sevenZip = Resolve-SevenZip
}
if (-not $sevenZip -or -not (Test-Path $sevenZip.Sfx)) {
    throw "7z.sfx not found next to 7z.exe. Reinstall 7-Zip from https://www.7-zip.org/"
}

if (-not (Test-Path $SfxConfig)) {
    throw "Missing SFX config: $SfxConfig"
}

$WorkDir = Join-Path $RootDir "desktopApp\build\compose\tmp\sfx"
New-Item -ItemType Directory -Force -Path $WorkDir, $OutputDir | Out-Null

$Archive = Join-Path $WorkDir "WpsAdbTool.7z"
if (Test-Path $Archive) {
    Remove-Item $Archive -Force
}

Write-Host "Creating 7z archive..."
$AppParent = Split-Path $AppDir -Parent
$AppFolder = Split-Path $AppDir -Leaf
& $sevenZip.Zip a -t7z -mx=9 $Archive (Join-Path $AppParent $AppFolder)
if ($LASTEXITCODE -ne 0) {
    throw "7z archive failed with exit code $LASTEXITCODE"
}

$DestExe = Join-Path $OutputDir "WpsAdbTool-$Version-windows-x64.exe"
Write-Host "Building self-extracting EXE..."

# 7-Zip SFX = 7z.sfx + config + archive (binary concat)
$sfxStream = [System.IO.File]::OpenRead($sevenZip.Sfx)
$configStream = [System.IO.File]::OpenRead($SfxConfig)
$archiveStream = [System.IO.File]::OpenRead($Archive)
$outStream = [System.IO.File]::Create($DestExe)

try {
    $sfxStream.CopyTo($outStream)
    $configStream.CopyTo($outStream)
    $archiveStream.CopyTo($outStream)
} finally {
    $sfxStream.Close()
    $configStream.Close()
    $archiveStream.Close()
    $outStream.Close()
}

$sizeMb = [math]::Round((Get-Item $DestExe).Length / 1MB, 1)
Write-Host "Artifact: $DestExe ($sizeMb MB)"
Write-Host "Double-click to extract and launch WpsAdbTool.exe."
