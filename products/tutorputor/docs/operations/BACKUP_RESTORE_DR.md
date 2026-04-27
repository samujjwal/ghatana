# TutorPutor Backup, Restore, and DR Verification

Date: 2026-04-27

## Scope

- PostgreSQL primary data store
- Redis queue/cache state
- Generated artifact object storage (if enabled)
- Prisma migration metadata

## Backup Policy

### PostgreSQL
- Full nightly backup (retention: 14 days)
- WAL/point-in-time recovery stream (retention: 7 days)
- Weekly restore rehearsal to isolated verification environment

### Redis
- Snapshot backup every 30 minutes for queue durability
- AOF enabled in production where supported
- Restore validation includes queue replay sanity checks

### Object Storage
- Versioning enabled for generated artifacts and provenance payloads
- Cross-region replication for production buckets

## Restore Drill Procedure

1. Select latest backup set and recovery target timestamp.
2. Provision isolated restore environment (`restore-verify`).
3. Restore Postgres backup and replay WAL to target timestamp.
4. Restore Redis snapshot/AOF and validate queue metadata.
5. Restore object storage sample set and verify checksums.
6. Run smoke checks:
   - `/health` and `/ready`
   - learner dashboard query
   - content generation enqueue + status poll
   - simulation manifest load
7. Capture `RTO` and `RPO` metrics in drill report.

## DR Targets

- RTO (critical learner path): <= 60 minutes
- RPO (learning and assessment state): <= 15 minutes

## Failover Verification Checklist

- [ ] Platform service starts with restored DB
- [ ] Auth and tenant boundary checks pass
- [ ] Queue worker resumes processing
- [ ] LTI launch succeeds
- [ ] Stripe webhook processing recovers
- [ ] SLO alerts return to expected baseline

## Drill Evidence Template

- Drill date/time:
- Trigger type:
- Backup set ID:
- Restore completion time:
- Measured RTO:
- Measured RPO:
- Failed checks:
- Corrective actions:

## Cadence

- Monthly restore drill in non-prod
- Quarterly DR failover simulation
- Annual chaos exercise for full regional outage scenario
