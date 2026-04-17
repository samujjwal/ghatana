#!/usr/bin/env bash
set -euo pipefail

API_URL="${TUTORPUTOR_API_URL:-}"
PLATFORM="${1:-canvas}"

if [[ -z "$API_URL" ]]; then
  echo "Set TUTORPUTOR_API_URL before running this script." >&2
  exit 1
fi

curl -sS "$API_URL/integration/lti/config/$PLATFORM"
