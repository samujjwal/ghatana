#!/usr/bin/env bash
set -euo pipefail

# Simple installer for native messaging host manifests (user-level)
# Usage: install-native-host.sh --browser=chrome --extension-id=<EXTENSION_ID>

BROWSER=chrome
EXTENSION_ID=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --browser)
      BROWSER="$2"
      shift 2
      ;;
    --extension-id)
      EXTENSION_ID="$2"
      shift 2
      ;;
    --path)
      HOST_PATH="$2"
      shift 2
      ;;
    --help)
      echo "Usage: $0 --browser=chrome|firefox|edge --extension-id=<EXTENSION_ID> [--path=/absolute/path/to/native-host]"
      exit 0
      ;;
    *)
      echo "Unknown arg: $1"
      exit 1
      ;;
  esac
done

if [ -z "$EXTENSION_ID" ]; then
  echo "--extension-id is required"
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST_DIR="$ROOT_DIR/ops/native-messaging"

case "$BROWSER" in
  chrome|edge)
    TARGET_DIR="$HOME/.config/google-chrome/NativeMessagingHosts"
    TEMPLATE="$MANIFEST_DIR/com.dcmaar.desktop.chrome.json"
    ;;
  firefox)
    TARGET_DIR="$HOME/.mozilla/native-messaging-hosts"
    TEMPLATE="$MANIFEST_DIR/com.dcmaar.desktop.firefox.json"
    ;;
  windows)
    echo "Windows install requires manual steps. See ops/native-messaging/README.md"
    exit 0
    ;;
  *)
    echo "Unsupported browser: $BROWSER"
    exit 1
    ;;
esac

mkdir -p "$TARGET_DIR"

OUT_FILE="$TARGET_DIR/com.dcmaar.desktop.json"

echo "Installing native messaging host manifest to $OUT_FILE"

# Replace placeholders
sed \
  -e "s#__EXTENSION_ID__#${EXTENSION_ID}#g" \
  -e "s#__FIREFOX_EXTENSION_ID__#${EXTENSION_ID}#g" \
  "$TEMPLATE" > "$OUT_FILE"

echo "Installed. Please ensure the 'path' in the manifest points to your native host binary and is executable."
