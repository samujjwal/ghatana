#!/bin/bash
set -euo pipefail

# Check if buf is installed
if ! command -v buf >/dev/null; then
  echo "Error: buf not found. Please install buf first"
  echo "See https://buf.build/docs/installation"
  exit 1
fi

# Get the directory of this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "${SCRIPT_DIR}")"

# Generate protobuf code
buf generate

# Delegate to the main generate_protos.sh script
echo "[proto-gen] Delegating to ${ROOT_DIR}/generate_protos.sh"
"${ROOT_DIR}/generate_protos.sh"
