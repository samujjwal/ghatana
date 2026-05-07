#!/usr/bin/env bash
set -euo pipefail

API_URL="${TUTORPUTOR_API_URL:-}"
TENANT_ID="${TUTORPUTOR_TEST_TENANT_ID:-}"
USER_ID="${1:-lti-user-1}"
ASSESSMENT_ATTEMPT_ID="${TUTORPUTOR_TEST_ASSESSMENT_ATTEMPT_ID:-}"
MODULE_ID="${TUTORPUTOR_TEST_MODULE_ID:-}"

if [[ -z "$API_URL" || -z "$TENANT_ID" ]]; then
  echo "Set TUTORPUTOR_API_URL and TUTORPUTOR_TEST_TENANT_ID." >&2
  exit 1
fi

if [[ -n "$ASSESSMENT_ATTEMPT_ID" ]]; then
  PAYLOAD="{\"sessionId\":\"session-test\",\"userId\":\"$USER_ID\",\"assessmentAttemptId\":\"$ASSESSMENT_ATTEMPT_ID\",\"lineItemId\":\"line-item-test\"}"
elif [[ -n "$MODULE_ID" ]]; then
  PAYLOAD="{\"sessionId\":\"session-test\",\"userId\":\"$USER_ID\",\"moduleId\":\"$MODULE_ID\",\"lineItemId\":\"line-item-test\"}"
else
  echo "Set TUTORPUTOR_TEST_ASSESSMENT_ATTEMPT_ID or TUTORPUTOR_TEST_MODULE_ID for evidence-backed grade passback verification." >&2
  exit 1
fi

curl -sS -X POST "$API_URL/integration/lti/grade-passback" \
  -H "content-type: application/json" \
  -H "x-tenant-id: $TENANT_ID" \
  -d "$PAYLOAD"
