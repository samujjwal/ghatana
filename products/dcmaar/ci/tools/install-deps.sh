#!/usr/bin/env bash
set -euo pipefail

# If --strict is passed, the installer will fail loudly instead of attempting fallbacks silently
STRICT=0
for arg in "$@"; do
  case "$arg" in
    --strict) STRICT=1 ;;
    *) ;;
  esac
done

START_DIR=$(cd "$(dirname "$0")/.." && pwd)
ROOT_DIR=""
CUR="$START_DIR"
while [ "$CUR" != "/" ] && [ -n "$CUR" ]; do
  if [ -f "$CUR/package.json" ] || [ -f "$CUR/pnpm-workspace.yaml" ]; then
    ROOT_DIR="$CUR"
    break
  fi
  CUR=$(dirname "$CUR")
done

if [ -z "$ROOT_DIR" ]; then
  echo "Could not find repository root (no package.json or pnpm-workspace.yaml found starting from $START_DIR)."
  echo "Please run the installer from inside the repository or install dependencies manually."
  exit 2
fi

echo "Installing dependencies at repo root: $ROOT_DIR"
cd "$ROOT_DIR"
if command -v pnpm >/dev/null 2>&1; then
  if pnpm install --frozen-lockfile; then
    true
  else
    echo "pnpm --frozen-lockfile failed, retrying without frozen lockfile"
    if pnpm install --no-frozen-lockfile; then
      true
    else
      echo "pnpm install failed at workspace root."
      if [ "$STRICT" -eq 1 ]; then
        echo "STRICT mode enabled; aborting with non-zero exit"
        exit 4
      fi
      echo "Attempting to install only in services/desktop as a fallback."
        DESKTOP_DIR="$START_DIR/services/desktop"
        if [ -d "$DESKTOP_DIR" ]; then
          cd "$DESKTOP_DIR"
          if command -v pnpm >/dev/null 2>&1; then
            if ! pnpm install --no-frozen-lockfile; then
              if [ "$STRICT" -eq 1 ]; then
                echo "per-package pnpm install failed and STRICT mode enabled; aborting"
                exit 5
              else
                true
              fi
            fi
          elif command -v npm >/dev/null 2>&1; then
            if ! npm install; then
              if [ "$STRICT" -eq 1 ]; then
                echo "per-package npm install failed and STRICT mode enabled; aborting"
                exit 6
              else
                true
              fi
            fi
          else
            echo "No package manager available in fallback. Please install pnpm or npm.";
            exit 3
          fi
          echo "Fallback install in services/desktop completed (or attempted). Return to repo root."
          cd "$ROOT_DIR"
        else
          echo "services/desktop not found; cannot fallback. Please resolve pnpm/npm issues manually.";
          exit 3
        fi
      fi
  fi
elif command -v npm >/dev/null 2>&1; then
  npm install
else
  echo "No pnpm or npm found; please install one of them to proceed."
  exit 2
fi
