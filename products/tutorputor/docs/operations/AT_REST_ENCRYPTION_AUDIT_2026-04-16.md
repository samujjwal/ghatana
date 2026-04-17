# TutorPutor At-Rest Encryption Audit

Date: 2026-04-16
Owner: TutorPutor Platform
Status: In progress

## Scope

This audit covers persistence layers that may store learner PII, assessment responses, credentials, and institution metadata.

- Primary relational data: Postgres via Prisma (`libs/tutorputor-core/prisma/schema.prisma`)
- Object storage: S3-compatible bucket usage in platform services
- Backups/snapshots: DB and object-storage backup targets

## Threat Model Focus

- Unauthorized read access to raw database volumes
- Unauthorized read access to object storage objects
- Snapshot/backup exfiltration
- Misconfigured default encryption settings in cloud storage

## Current Evidence Collected

- Prisma schema contract tests are in place for GDPR deletion model integrity.
- Runtime services rely on external infrastructure for storage encryption policy.
- No single automated report previously existed for at-rest encryption verification.

## Gaps

- No committed, repeatable command set for posture checks.
- No central evidence template for security-review snapshots.
- No explicit per-environment checklist in TutorPutor docs.

## This Session Deliverables

- Added operations checklist: `docs/operations/AT_REST_ENCRYPTION_CHECKLIST.md`
- Added evidence capture template: `docs/operations/AT_REST_ENCRYPTION_EVIDENCE_TEMPLATE.md`
- Added verification scripts:
  - `scripts/verify-at-rest-encryption.ps1`
  - `scripts/verify-postgres-at-rest-encryption.sql`
  - `scripts/verify-object-storage-encryption.sh`

## Next Verification Steps

1. Run the PowerShell verification script against each deployed environment.
2. Store outputs using the evidence template with timestamps and operator identity.
3. Confirm object storage bucket default encryption is enforced with KMS or AES256.
4. Confirm database encryption posture with infra owners (disk-level/TDE and backup encryption).
5. Record unresolved items as remediation tasks with owners and due dates.
