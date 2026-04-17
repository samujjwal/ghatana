# Content Proof Runbook

## Purpose
Operational runbook for TP-008 and TP-009 content route proof execution.

## Preconditions
- TutorPutor platform dependencies installed.
- Test database and required environment variables configured.
- Operator has role-based access to run local/staging/preprod/production checks.

## Steps
1. Run local content route verification script.
2. Capture test output and route response evidence.
3. Execute staging wrapper and collect results.
4. Execute preprod wrapper and collect results.
5. Execute production wrapper and collect results.
6. Update status matrix and execution log.
7. Complete signoff after evidence review.

## Artifacts
- CONTENT_EVIDENCE_LOCAL_2026-04-16.md
- CONTENT_EVIDENCE_STAGING_2026-04-16.md
- CONTENT_EVIDENCE_PREPROD_2026-04-16.md
- CONTENT_EVIDENCE_PRODUCTION_2026-04-16.md
- CONTENT_PROOF_EXECUTION_LOG_2026-04-16.md
