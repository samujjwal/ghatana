#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AI_VOICE_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${AI_VOICE_ROOT}/../../.." && pwd)"
DESKTOP_DIR="${AI_VOICE_ROOT}/apps/desktop"
PY_DIR="${DESKTOP_DIR}/src-tauri/python"
VENV_PATH="${PY_DIR}/.venv"

log() {
  printf '\033[1;34m[ai-voice-setup]\033[0m %s\n' "$*"
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf '\033[1;31m[ai-voice-setup]\033[0m Missing required command: %s\n' "$1" >&2
    exit 1
  fi
}

require_cmd pnpm
require_cmd uv

log "Installing workspace JavaScript dependencies via pnpm…"
(cd "${REPO_ROOT}" && pnpm install)

log "Creating Python virtual environment with uv at ${VENV_PATH}"
uv venv "${VENV_PATH}"

log "Activating virtual environment"
# shellcheck source=/dev/null
source "${VENV_PATH}/bin/activate"

log "Upgrading core build tooling inside the virtual environment"
uv pip install --upgrade pip setuptools wheel 'cython>=3.0'

log "Installing Python requirements (Demucs, RVC, etc.)"
(cd "${PY_DIR}" && uv pip install -r requirements.txt)

log "Environment ready!"
cat <<'EOF'
Next steps:
  1. Activate the Python environment when running standalone scripts:
       source apps/desktop/src-tauri/python/.venv/bin/activate
  2. Start the desktop app:
       cd products/audio-video/ai-voice/apps/desktop
       pnpm tauri dev
EOF
