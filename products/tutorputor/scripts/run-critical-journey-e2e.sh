#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${TUTORPUTOR_BASE_URL:-}"
API_URL="${TUTORPUTOR_API_URL:-}"

if [[ -z "$BASE_URL" || -z "$API_URL" ]]; then
  echo "Set TUTORPUTOR_BASE_URL and TUTORPUTOR_API_URL before running this script." >&2
  exit 1
fi

echo "Running critical journey Playwright suite against $BASE_URL"
corepack pnpm playwright test tests/e2e --reporter=line

echo "Running GDPR deletion flow verification helper"
./scripts/verify-gdpr-delete-flow.sh

echo "Critical journey run completed"
