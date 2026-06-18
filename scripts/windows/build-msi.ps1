# Build a release MSI for Windows x64.
#
# Usage:
#   pwsh -File scripts/windows/build-msi.ps1
#
# Environment:
#   JAVA_HOME              JDK with jlink + jpackage (optional; Gradle JVM used when unset)
#   WPS_ADB_TOOL_VERSION   App version passed to Gradle (optional, default 1.0.0)

$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Version = if ($env:WPS_ADB_TOOL_VERSION) { $env:WPS_ADB_TOOL_VERSION } else { "1.0.0" }
$OutputDir = Join-Path $RootDir "desktopApp\build\ci-artifacts"

Set-Location $RootDir

if ($env:JAVA_HOME) {
    Write-Host "Using JAVA_HOME=$($env:JAVA_HOME)"
    & java -version
    $env:GRADLE_OPTS = "-Dorg.gradle.java.home=$($env:JAVA_HOME)"
    $env:PATH = "$($env:JAVA_HOME)\bin;$env:PATH"
} else {
    Write-Host "JAVA_HOME not set; using Gradle JVM from setup-java."
    & java -version
}

Write-Host "Building release MSI (version $Version)..."
$GradleArgs = @("-PwpsAdbTool.version=$Version", ":desktopApp:packageReleaseMsi")
if ($env:CI -eq "true") {
    $GradleArgs += "--no-configuration-cache"
}
& .\gradlew.bat @GradleArgs
if ($LASTEXITCODE -ne 0) {
    throw "Gradle packageReleaseMsi failed with exit code $LASTEXITCODE"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$SourceMsi = Get-ChildItem -Path (Join-Path $RootDir "desktopApp\build\compose\binaries") -Recurse -Filter "*.msi" -File |
    Select-Object -First 1

if (-not $SourceMsi) {
    throw "Could not locate built MSI under desktopApp/build/compose/binaries"
}

$DestMsi = Join-Path $OutputDir "WpsAdbTool-$Version-windows-x64.msi"
Copy-Item -Path $SourceMsi.FullName -Destination $DestMsi -Force
Write-Host "Artifact: $DestMsi"
