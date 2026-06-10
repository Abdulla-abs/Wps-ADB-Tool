#!/usr/bin/env bash
# Build a release DMG for a single macOS architecture.
#
# Usage:
#   ./scripts/macos/build-arch-dmg.sh arm64
#   ./scripts/macos/build-arch-dmg.sh x64
#
# Environment:
#   JAVA_HOME                   JDK for the target architecture (required)
#   MACOS_SIGN=true             Enable code signing (optional)
#   MACOS_SIGNING_IDENTITY      Signing identity (required when MACOS_SIGN=true)
#   MACOS_KEYCHAIN_PATH         Keychain path (optional)
#   NOTARIZATION_*              Apple notarization credentials (optional)
#   WPS_ADB_TOOL_VERSION        App version passed to Gradle (optional, default 1.0.0)

set -euo pipefail

ARCH="${1:-}"
if [[ "$ARCH" != "arm64" && "$ARCH" != "x64" ]]; then
  echo "Usage: $0 <arm64|x64>" >&2
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME must point to a $ARCH JDK" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
VERSION="${WPS_ADB_TOOL_VERSION:-1.0.0}"
OUTPUT_DIR="$ROOT_DIR/desktopApp/build/ci-artifacts"
GRADLE=(bash gradlew -Dorg.gradle.java.home="$JAVA_HOME" "-PwpsAdbTool.version=$VERSION")

cd "$ROOT_DIR"

export GRADLE_OPTS="-Dorg.gradle.java.home=${JAVA_HOME}"
export PATH="${JAVA_HOME}/bin:${PATH}"

echo "Using JAVA_HOME=${JAVA_HOME}"
java -version

if [[ "${MACOS_SIGN:-false}" == "true" ]]; then
  if [[ -n "${NOTARIZATION_APPLE_ID:-}" && -n "${NOTARIZATION_PASSWORD:-}" && -n "${NOTARIZATION_TEAM_ID:-}" ]]; then
    echo "Building signed and notarized release DMG ($ARCH)..."
    "${GRADLE[@]}" :desktopApp:notarizeReleaseDmg
  else
    echo "Building signed release DMG ($ARCH)..."
    "${GRADLE[@]}" :desktopApp:packageReleaseDmg
  fi
else
  echo "Building unsigned release DMG ($ARCH)..."
  "${GRADLE[@]}" :desktopApp:packageReleaseDmg
fi

mkdir -p "$OUTPUT_DIR"

SOURCE_DMG="$(find "$ROOT_DIR/desktopApp/build/compose/binaries" -path '*/dmg/*.dmg' -type f | head -n 1)"
if [[ -z "$SOURCE_DMG" || ! -f "$SOURCE_DMG" ]]; then
  echo "Could not locate built DMG under desktopApp/build/compose/binaries" >&2
  exit 1
fi

DEST_DMG="$OUTPUT_DIR/WpsAdbTool-${VERSION}-macos-${ARCH}.dmg"
cp "$SOURCE_DMG" "$DEST_DMG"
echo "Artifact: $DEST_DMG"
