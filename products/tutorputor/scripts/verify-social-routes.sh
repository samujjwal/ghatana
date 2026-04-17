#!/usr/bin/env bash
set -euo pipefail

API_URL="${TUTORPUTOR_API_URL:-}"
TENANT_ID="${TUTORPUTOR_TEST_TENANT_ID:-}"
USER_ID="${TUTORPUTOR_TEST_USER_ID:-}"

if [[ -z "$API_URL" || -z "$TENANT_ID" || -z "$USER_ID" ]]; then
  echo "Set TUTORPUTOR_API_URL, TUTORPUTOR_TEST_TENANT_ID, and TUTORPUTOR_TEST_USER_ID." >&2
  exit 1
fi

curl -sS "$API_URL/engagement/social/feed?limit=20" \
  -H "x-tenant-id: $TENANT_ID" \
  -H "x-user-id: $USER_ID"
