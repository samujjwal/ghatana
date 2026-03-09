#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "${ROOT_DIR}"

PATTERN='@ghatana/tutorputor-content-service|@ghatana/tutorputor-learning-service|@ghatana/tutorputor-assessment-service|@ghatana/tutorputor-cms-service|@ghatana/tutorputor-analytics-service|@ghatana/tutorputor-marketplace-service|services/tutorputor-content-studio'

echo "Checking Tutorputor source and CI for legacy service/package references..."
if rg -n "${PATTERN}" \
  products/tutorputor/apps \
  products/tutorputor/services \
  products/tutorputor/libs \
  products/tutorputor/contracts \
  products/tutorputor/run-dev.sh \
  .github/workflows/tutorputor-ci.yml \
  --glob '!**/node_modules/**' \
  --glob '!**/dist/**' \
  --glob '!**/build/**' \
  --glob '!**/*.md'; then
  echo "FAIL: legacy references detected. Remove superseded paths/packages before merge."
  exit 1
fi

echo "PASS: no legacy references detected in active Tutorputor code/CI paths."
