#!/usr/bin/env bash
set -euo pipefail

API_URL="${TUTORPUTOR_API_URL:-}"
TENANT_ID="${TUTORPUTOR_TEST_TENANT_ID:-}"
USER_ID="${TUTORPUTOR_TEST_DELETE_USER_ID:-}"

if [[ -z "$API_URL" || -z "$TENANT_ID" || -z "$USER_ID" ]]; then
  echo "Set TUTORPUTOR_API_URL, TUTORPUTOR_TEST_TENANT_ID, and TUTORPUTOR_TEST_DELETE_USER_ID." >&2
  exit 1
fi

payload=$(cat <<JSON
{"userId":"$USER_ID","retentionDays":30}
JSON
)

echo "Submitting deletion request for user '$USER_ID'"
curl -sS -X POST "$API_URL/compliance/deletion/request" \
  -H "content-type: application/json" \
  -H "x-tenant-id: $TENANT_ID" \
  -H "x-user-role: admin" \
  -d "$payload"

echo
echo "Run scripts/verify-gdpr-delete-cascade.sql with matching tenant/user values to complete verification."
