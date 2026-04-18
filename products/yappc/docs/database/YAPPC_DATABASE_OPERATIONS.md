# YAPPC Database Operations Standard

## Scope
This standard defines migration, seeding, backup, and restore procedures for YAPPC runtime databases.

## Migration Strategy
- Selected tool: Flyway.
- Authoritative migration path: products/yappc/platform/src/main/resources/db/migration.
- Migration naming: V<MAJOR>_<MINOR>_<PATCH>__<DESCRIPTION>.sql.
- Validation gate: .github/workflows/yappc-db-migration-validation.yml.

## Migration Workflow
1. Add migration SQL in canonical migration path.
2. Run local validation:
   - scripts/db/migrate.sh
3. Open PR with migration rationale and rollback notes.
4. CI validates Flyway scripts and migration execution against ephemeral PostgreSQL.

## Performance Optimization Strategy
- Performance index migration: products/yappc/platform/src/main/resources/db/migration/V7_0_0__YAPPC_PERFORMANCE_INDEX_OPTIMIZATION.sql.
- Baseline query families targeted:
   - Tenant + status + recency filters for workflow_executions, alerts, incidents, vulnerabilities, and security_scans.
   - Tenant + project scoped board queries for stories and tasks.
   - Time-window analytics for metrics, traces, and log_entries.
- JSONB metadata filtering acceleration:
   - GIN indexes on projects.metadata and stories.metadata.
- Validation requirements for new indexing migrations:
   - Include representative query examples in migration PR notes.
   - Run EXPLAIN (ANALYZE, BUFFERS) against staging-sized dataset before production promotion.
   - Re-check index hit ratio and sequential-scan trends after rollout via database monitoring.

## Development Seeding
- Seed path: products/yappc/platform/src/main/resources/db/seed/V1_0_0__YAPPC_DEV_SEED.sql.
- Seed script: products/yappc/scripts/db/seed.sh.
- Seed must be idempotent and safe for repeated execution.

## Backup and Restore Strategy
- Backup script: products/yappc/scripts/db/backup.sh.
- Restore script: products/yappc/scripts/db/restore.sh.
- Validation drill script: products/yappc/scripts/db/verify-backup-restore.sh.
- Weekly restore drill CI: .github/workflows/yappc-db-backup-restore-drill.yml.

## Retention Policy
- Daily backups retained for 14 days.
- Weekly backups retained for 8 weeks.
- Monthly backups retained for 12 months.
- Encrypt backup artifacts at rest in production storage.

## Rollback Policy
- Preferred rollback path: restore from last known-good backup.
- Migration-level rollback requires explicit reverse migration and approval.
- Any rollback event must include incident ticket and post-incident review.

## Testing Requirements
- Every migration PR must pass migration validation workflow.
- Backup/restore drill must confirm seeded tenant data is restorable.
- Critical schema changes require staging migration rehearsal before production rollout.
