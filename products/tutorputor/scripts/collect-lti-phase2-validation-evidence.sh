#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
OUT_FILE="$ROOT_DIR/docs/operations/LTI_PHASE2_EVIDENCE_LOCAL_2026-04-16.md"

{
  echo "# LTI Phase 2 Evidence - Local"
  echo
  echo "## Execution Context"
  echo "- Date: 2026-04-16"
  echo "- Captured At: $STAMP"
  echo "- Environment: local"
  echo
  echo "## Command"
  echo "- ./scripts/verify-lti-phase2-routes.sh"
  echo
  echo "## Notes"
  echo "- Attach command output and reviewer annotations."
} > "$OUT_FILE"

printf "Wrote %s\n" "$OUT_FILE"
