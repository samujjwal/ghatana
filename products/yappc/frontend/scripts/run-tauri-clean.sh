#!/usr/bin/env bash
# Wrapper to run `pnpm --filter desktop tauri dev` with a cleaned environment to avoid
# snap/VS Code interfering with system libraries (glibc symbol mismatches).
# Usage:
#   ./scripts/run-tauri-clean.sh        # runs tauri dev
#   ./scripts/run-tauri-clean.sh --dry-run  # prints the command without executing

set -euo pipefail
DRY_RUN=0
if [[ ${1:-} == "--dry-run" ]]; then
  DRY_RUN=1
fi

CMD=(env -i PATH="$PATH" HOME="$HOME" DISPLAY="$DISPLAY" XDG_RUNTIME_DIR="$XDG_RUNTIME_DIR" XDG_DATA_DIRS="/usr/share:/usr/local/share" pnpm --filter desktop tauri dev)

if [[ $DRY_RUN -eq 1 ]]; then
  echo "DRY RUN: ${CMD[*]}"
  exit 0
fi

exec "${CMD[@]}"
