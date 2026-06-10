#!/usr/bin/env bash
# Combine arm64 and x64 .app bundles into a universal binary, then package a DMG.
#
# Compose Desktop / jpackage cannot emit a true universal binary in one pass.
# This script builds both architectures separately, merges Mach-O files with lipo,
# re-signs, and creates a DMG with hdiutil.
#
# Prerequisites on the build machine:
#   - Two JDKs: one arm64, one x64 (Rosetta x64 JDK works on Apple Silicon)
#   - lipo, codesign, hdiutil (Xcode command line tools)
#
# Usage:
#   export JDK_ARM64=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
#   export JDK_X64=/Library/Java/JavaVirtualMachines/temurin-21-x64.jdk/Contents/Home
#   ./scripts/macos/build-universal-dmg.sh
#
# Optional signing (same env vars as build-arch-dmg.sh):
#   MACOS_SIGN, MACOS_SIGNING_IDENTITY, MACOS_KEYCHAIN_PATH, NOTARIZATION_*

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
APP_NAME="WpsAdbTool"
VERSION="${WPS_ADB_TOOL_VERSION:-1.0.0}"
JDK_ARM64="${JDK_ARM64:-${JAVA_HOME:-}}"
JDK_X64="${JDK_X64:-}"

if [[ -z "$JDK_ARM64" || -z "$JDK_X64" ]]; then
  echo "Set JDK_ARM64 and JDK_X64 to arm64 and x64 JDK homes." >&2
  exit 1
fi

WORK_DIR="$ROOT_DIR/desktopApp/build/universal-work"
ARM64_APP="$WORK_DIR/${APP_NAME}-arm64.app"
X64_APP="$WORK_DIR/${APP_NAME}-x64.app"
UNIVERSAL_APP="$WORK_DIR/${APP_NAME}-universal.app"
OUTPUT_DIR="$ROOT_DIR/desktopApp/build/ci-artifacts"
DMG_PATH="$OUTPUT_DIR/${APP_NAME}-${VERSION}-macos-universal.dmg"

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR" "$OUTPUT_DIR"

build_distributable() {
  local jdk_home="$1"
  local dest_app="$2"
  echo "Building distributable for $(basename "$dest_app") with JDK: $jdk_home"
  (cd "$ROOT_DIR" && ./gradlew --stop >/dev/null 2>&1 || true)
  (cd "$ROOT_DIR" && ./gradlew -Dorg.gradle.java.home="$jdk_home" \
    "-PwpsAdbTool.version=$VERSION" \
    :desktopApp:createReleaseDistributable)
  local built_app
  built_app="$(find "$ROOT_DIR/desktopApp/build/compose/binaries" -path "*/app/${APP_NAME}.app" -type d | head -n 1)"
  if [[ -z "$built_app" ]]; then
    echo "Failed to locate ${APP_NAME}.app for $(basename "$dest_app")" >&2
    exit 1
  fi
  cp -R "$built_app" "$dest_app"
}

build_distributable "$JDK_X64" "$X64_APP"
build_distributable "$JDK_ARM64" "$ARM64_APP"

echo "Creating universal app bundle..."
rm -rf "$UNIVERSAL_APP"
cp -R "$ARM64_APP" "$UNIVERSAL_APP"

combine_mach_o() {
  local arm64_file="$1"
  local rel_path="${arm64_file#$ARM64_APP/}"
  local x64_file="$X64_APP/$rel_path"
  local universal_file="$UNIVERSAL_APP/$rel_path"

  if file "$arm64_file" | grep -q 'Mach-O'; then
    if [[ -f "$x64_file" ]] && file "$x64_file" | grep -q 'Mach-O'; then
      lipo -create "$arm64_file" "$x64_file" -output "$universal_file"
      echo "  lipo: $rel_path"
    fi
  fi
}

echo "Merging Mach-O binaries with lipo..."
while IFS= read -r -d '' arm64_file; do
  combine_mach_o "$arm64_file"
done < <(find "$ARM64_APP" -type f \( -name '*.dylib' -o -perm -111 \) -print0)

sign_if_needed() {
  if [[ "${MACOS_SIGN:-false}" != "true" ]]; then
    return 0
  fi
  if [[ -z "${MACOS_SIGNING_IDENTITY:-}" ]]; then
    echo "MACOS_SIGNING_IDENTITY is required when MACOS_SIGN=true" >&2
    exit 1
  fi

  local keychain_args=()
  if [[ -n "${MACOS_KEYCHAIN_PATH:-}" ]]; then
    keychain_args=(--keychain "$MACOS_KEYCHAIN_PATH")
  fi

  echo "Re-signing universal app bundle..."
  /usr/bin/codesign --force --deep --options runtime \
    --sign "$MACOS_SIGNING_IDENTITY" \
    "${keychain_args[@]}" \
    "$UNIVERSAL_APP"
  /usr/bin/codesign --verify --deep --strict --verbose=2 "$UNIVERSAL_APP"
}

sign_if_needed

echo "Creating DMG..."
rm -f "$DMG_PATH"
hdiutil create -volname "$APP_NAME" -srcfolder "$UNIVERSAL_APP" -ov -format UDZO "$DMG_PATH"

if [[ "${MACOS_SIGN:-false}" == "true" \
  && -n "${NOTARIZATION_APPLE_ID:-}" \
  && -n "${NOTARIZATION_PASSWORD:-}" \
  && -n "${NOTARIZATION_TEAM_ID:-}" ]]; then
  echo "Submitting universal DMG for notarization..."
  xcrun notarytool submit "$DMG_PATH" --wait --apple-id "$NOTARIZATION_APPLE_ID" \
    --password "$NOTARIZATION_PASSWORD" \
    --team-id "$NOTARIZATION_TEAM_ID"
  xcrun stapler staple "$DMG_PATH"
fi

echo "Universal artifact: $DMG_PATH"
