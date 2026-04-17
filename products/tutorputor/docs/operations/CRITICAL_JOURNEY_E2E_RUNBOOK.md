# Critical Journey E2E Runbook

## Commands

### Windows

1. Set required environment variables:
   - TUTORPUTOR_BASE_URL
   - TUTORPUTOR_API_URL
   - TUTORPUTOR_TEST_TENANT_ID
2. Run: .\\scripts\\run-critical-journey-e2e.ps1

### Linux/macOS

1. Export required environment variables.
2. Run: ./scripts/run-critical-journey-e2e.sh

## Outputs

- Playwright report directory
- Trace archives for each test
- Raw API response bundle
- GDPR SQL verification output

## Failure Handling

- If auth fails, stop and rotate test credentials.
- If payments fail, capture provider response body and correlation ID.
- If deletion verification fails, rerun verify-gdpr-delete-flow script and save outputs.
