#!/usr/bin/env bash
set -euo pipefail

API_URL="${TUTORPUTOR_API_URL:-}"
TENANT_ID="${TUTORPUTOR_TEST_TENANT_ID:-}"
USER_ID="${1:-lti-user-1}"

if [[ -z "$API_URL" || -z "$TENANT_ID" ]]; then
  echo "Set TUTORPUTOR_API_URL and TUTORPUTOR_TEST_TENANT_ID." >&2
  exit 1
fi

curl -sS -X POST "$API_URL/integration/lti/grade-passback" \
  -H "content-type: application/json" \
  -H "x-tenant-id: $TENANT_ID" \
  -d "{\"sessionId\":\"session-test\",\"userId\":\"$USER_ID\",\"score\":90,\"maxScore\":100,\"lineItemId\":\"line-item-test\"}"
