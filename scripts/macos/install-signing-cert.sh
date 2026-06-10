#!/usr/bin/env bash
# Import a Developer ID .p12 into a temporary keychain for CI signing.
#
# Required environment variables:
#   BUILD_CERTIFICATE_BASE64  Base64-encoded .p12 export
#   P12_PASSWORD                Password for the .p12 file
#   KEYCHAIN_PASSWORD           Password for the temporary keychain
#
# Optional:
#   KEYCHAIN_PATH               Defaults to $RUNNER_TEMP/app-signing.keychain-db
#
# Exports:
#   MACOS_KEYCHAIN_PATH
#   MACOS_SIGNING_IDENTITY      First "Developer ID Application" identity found

set -euo pipefail

if [[ -z "${BUILD_CERTIFICATE_BASE64:-}" ]]; then
  echo "BUILD_CERTIFICATE_BASE64 is not set" >&2
  exit 1
fi
if [[ -z "${P12_PASSWORD:-}" ]]; then
  echo "P12_PASSWORD is not set" >&2
  exit 1
fi
if [[ -z "${KEYCHAIN_PASSWORD:-}" ]]; then
  echo "KEYCHAIN_PASSWORD is not set" >&2
  exit 1
fi

KEYCHAIN_PATH="${KEYCHAIN_PATH:-${RUNNER_TEMP:-/tmp}/app-signing.keychain-db}"
CERTIFICATE_PATH="${RUNNER_TEMP:-/tmp}/build_certificate.p12"

echo "Importing signing certificate into temporary keychain..."
echo -n "$BUILD_CERTIFICATE_BASE64" | base64 --decode -o "$CERTIFICATE_PATH"

security create-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"
security set-keychain-settings -lut 21600 "$KEYCHAIN_PATH"
security unlock-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"
security import "$CERTIFICATE_PATH" -P "$P12_PASSWORD" -A -t cert -f pkcs12 -k "$KEYCHAIN_PATH"
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"
security list-keychain -d user -s "$KEYCHAIN_PATH"

IDENTITY="$(security find-identity -v -p codesigning "$KEYCHAIN_PATH" \
  | grep 'Developer ID Application' \
  | head -n 1 \
  | sed -E 's/^[[:space:]]*[0-9]+)[[:space:]]+"([^"]+)".*/\1/')"

if [[ -z "$IDENTITY" ]]; then
  echo "No Developer ID Application identity found in keychain." >&2
  security find-identity -v -p codesigning "$KEYCHAIN_PATH" >&2 || true
  exit 1
fi

echo "Using signing identity: $IDENTITY"

{
  echo "MACOS_KEYCHAIN_PATH=$KEYCHAIN_PATH"
  echo "MACOS_SIGNING_IDENTITY=$IDENTITY"
} >> "${GITHUB_ENV:-/dev/null}"

export MACOS_KEYCHAIN_PATH="$KEYCHAIN_PATH"
export MACOS_SIGNING_IDENTITY="$IDENTITY"
