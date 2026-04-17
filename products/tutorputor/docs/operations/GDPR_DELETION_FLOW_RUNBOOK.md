# GDPR Deletion Flow Runbook

## Objective

Provide repeatable proof that user deletion requests complete with expected retention metadata and data cascade guarantees.

## Steps

1. Trigger deletion request through compliance endpoint.
2. Capture verification token issuance event.
3. Confirm scheduledDeletionAt and retentionDays values.
4. Approve and execute deletion flow.
5. Run SQL verification script: scripts/verify-gdpr-delete-cascade.sql.
6. Save command output and correlation IDs.

## Required Evidence

- Request/response payloads for create and verify operations.
- SQL output for DataDeletionRequest and DeletionVerification rows.
- SQL output proving user-linked rows are removed or anonymized.

## Validation Scripts

- scripts/verify-gdpr-delete-flow.ps1
- scripts/verify-gdpr-delete-flow.sh
- scripts/verify-gdpr-delete-cascade.sql
