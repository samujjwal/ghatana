# PostgreSQL Backup and Restore Strategy

> **Purpose:** Define backup and restore procedures with clear RPO/RTO targets
> **Last Updated:** 2026-04-19
> **Version:** 1.0

---

## Overview

This document defines the backup and restore strategy for YAPPC's PostgreSQL database, including recovery point objectives (RPO), recovery time objectives (RTO), implementation procedures, and disaster recovery plans.

---

## Recovery Objectives

### RPO (Recovery Point Objective)

**Target:** 5 minutes

**Definition:** Maximum acceptable amount of data loss measured in time. An RPO of 5 minutes means that in the event of a failure, up to 5 minutes of data may be lost.

### RTO (Recovery Time Objective)

**Target:** 1 hour

**Definition:** Maximum acceptable time to restore service after a failure. An RTO of 1 hour means the system must be fully operational within 1 hour of a failure being detected.

---

## Backup Strategy

### Backup Types

#### 1. Full Backups

- **Frequency:** Daily at 2:00 AM UTC
- **Retention:** 30 days
- **Format:** Custom PostgreSQL format (pg_dump -Fc)
- **Location:** Encrypted cloud storage (S3/GCS) + local backup server

#### 2. Incremental Backups

- **Frequency:** Hourly
- **Retention:** 24 hours
- **Method:** WAL (Write-Ahead Log) archiving
- **Location:** Encrypted cloud storage

#### 3. Point-in-Time Recovery (PITR)

- **Capability:** Restore to any point within 30 days
- **Granularity:** 1 second (via WAL replay)
- **Use Case:** Recovery from data corruption, accidental deletion

### Backup Schedule

```bash
# Hourly incremental (WAL archiving)
0 * * * * /path/to/scripts/db/wal-archive.sh

# Daily full backup
0 2 * * * /path/to/scripts/db/backup.sh

# Weekly backup verification
0 3 * * 0 /path/to/scripts/db/verify-backup-integrity.sh
```

### Backup Storage

#### Primary Storage

- **Location:** Encrypted S3 bucket (or equivalent)
- **Encryption:** AES-256 at rest
- **Access:** IAM role with least privilege
- **Cost:** Standard tier with lifecycle policy

#### Secondary Storage

- **Location:** On-premises backup server
- **Encryption:** LUKS full-disk encryption
- **Replication:** Async to primary storage
- **Purpose:** Fast local restore, disaster recovery

### Backup Encryption

All backups are encrypted using:

- **Algorithm:** AES-256-GCM
- **Key Management:** AWS KMS or HashiCorp Vault
- **Key Rotation:** Every 90 days
- **Key Access:** Role-based, audit logged

---

## Backup Implementation

### Full Backup Script

Location: `scripts/db/backup.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-yappc}"
DB_USER="${DB_USER:-ghatana}"
DB_PASSWORD="${DB_PASSWORD:-ghatana123}"
BACKUP_DIR="${BACKUP_DIR:-products/yappc/backups}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/yappc-${DB_NAME}-${TIMESTAMP}.dump"

mkdir -p "${BACKUP_DIR}"
export PGPASSWORD="${DB_PASSWORD}"

echo "Creating backup ${BACKUP_FILE}"
pg_dump -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -Fc -f "${BACKUP_FILE}"

# Encrypt backup
gpg --encrypt --recipient ops@ghatana.ai "${BACKUP_FILE}"
rm "${BACKUP_FILE}"

# Upload to cloud storage
aws s3 cp "${BACKUP_FILE}.gpg" s3://yappc-backups/postgres/

echo "Backup created and uploaded: ${BACKUP_FILE}.gpg"
```

### WAL Archiving

Configure in `postgresql.conf`:

```ini
# Enable WAL archiving
wal_level = replica
archive_mode = on
archive_command = 'aws s3 cp %p s3://yappc-backups/wal/%f'
max_wal_senders = 3
wal_keep_size = 1GB
```

### Backup Verification

Location: `scripts/db/verify-backup-integrity.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

BACKUP_FILE="$1"

# Download and decrypt backup
aws s3 cp "s3://yappc-backups/postgres/${BACKUP_FILE}" /tmp/
gpg --decrypt /tmp/${BACKUP_FILE} > /tmp/backup.dump

# Test restore to temporary database
createdb yappc_verify
pg_restore -d yappc_verify /tmp/backup.dump

# Verify data integrity
psql -d yappc_verify -c "SELECT COUNT(*) FROM projects"
psql -d yappc_verify -c "SELECT COUNT(*) FROM workspaces"

# Cleanup
dropdb yappc_verify
rm /tmp/backup.dump

echo "Backup verification successful"
```

---

## Restore Procedures

### Restore Types

#### 1. Full Restore

**Use Case:** Complete system failure, migration to new environment

**Procedure:**

```bash
cd products/yappc
./scripts/db/restore.sh /path/to/backup.dump yappc_prod
```

**Steps:**
1. Stop application services
2. Download and decrypt backup
3. Drop existing database (or create new)
4. Restore from backup
5. Run database migrations
6. Restart application services
7. Verify health endpoints
8. Run smoke tests

#### 2. Point-in-Time Recovery

**Use Case:** Data corruption, accidental deletion, revert to specific time

**Procedure:**

```bash
# Restore from base backup
pg_restore -d yappc_prod /path/to/base-backup.dump

# Replay WAL logs to specific point
pg_rewind -D /var/lib/postgresql/data --target-timeline=latest
```

**Steps:**
1. Identify target recovery point (timestamp)
2. Restore from most recent full backup
3. Replay WAL logs up to target point
4. Verify data integrity
5. Restart application

#### 3. Selective Restore

**Use Case:** Restore specific tables or schemas

**Procedure:**

```bash
# List contents of backup
pg_restore -l /path/to/backup.dump

# Restore specific table
pg_restore -d yappc_prod -t projects /path/to/backup.dump
```

### Restore Script

Location: `scripts/db/restore.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <backup-file> [target-db-name]" >&2
  exit 1
fi

BACKUP_FILE="$1"
TARGET_DB_NAME="${2:-${TARGET_DB_NAME:-yappc_restore}}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-ghatana}"
DB_PASSWORD="${DB_PASSWORD:-ghatana123}"

if [ ! -f "${BACKUP_FILE}" ]; then
  echo "Backup file not found: ${BACKUP_FILE}" >&2
  exit 1
fi

# Decrypt if encrypted
if [[ "${BACKUP_FILE}" == *.gpg ]]; then
  echo "Decrypting backup..."
  gpg --decrypt "${BACKUP_FILE}" > /tmp/restore.dump
  BACKUP_FILE="/tmp/restore.dump"
fi

export PGPASSWORD="${DB_PASSWORD}"

echo "Recreating target database ${TARGET_DB_NAME}"
psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "DROP DATABASE IF EXISTS ${TARGET_DB_NAME};"
psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "CREATE DATABASE ${TARGET_DB_NAME};"

echo "Restoring backup into ${TARGET_DB_NAME}"
pg_restore -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${TARGET_DB_NAME}" "${BACKUP_FILE}"

echo "Restore completed"
```

---

## Disaster Recovery

### Disaster Scenarios

#### Scenario 1: Database Server Failure

**Impact:** Complete system outage

**RTO:** 1 hour

**Procedure:**
1. Promote read replica to primary (if available)
2. If no replica, restore from latest backup to new server
3. Update DNS/application configuration
4. Verify service health
5. Monitor for issues

#### Scenario 2: Data Corruption

**Impact:** Data integrity issues, potential data loss

**RPO:** 5 minutes (via PITR)

**Procedure:**
1. Identify corruption time window
2. Restore from backup prior to corruption
3. Replay WAL logs up to point before corruption
4. Verify data integrity
5. Investigate root cause
6. Implement preventive measures

#### Scenario 3: Accidental Data Deletion

**Impact:** Loss of specific data

**RPO:** 5 minutes (via PITR)

**Procedure:**
1. Identify deletion time
2. Restore from backup prior to deletion
3. Replay WAL logs up to point before deletion
4. Export deleted data
5. Restore current state
6. Re-import deleted data
7. Verify data integrity

#### Scenario 4: Regional Outage

**Impact:** Complete system unavailability

**RTO:** 4 hours (cross-region restore)

**Procedure:**
1. Activate disaster recovery site
2. Restore from cross-region backup
3. Update DNS to point to DR site
4. Verify service health
5. Communicate with stakeholders
6. Plan return to primary region

### Disaster Recovery Site

**Location:** Separate AWS region (or equivalent cloud provider)

**Configuration:**
- Standby database with continuous replication
- Regular backup restores for verification
- Infrastructure as code for rapid deployment
- DNS failover capability

**Activation:**
- Manual trigger by operations team
- Automated failover for critical outages
- Regular DR drills (quarterly)

---

## Monitoring and Alerting

### Backup Monitoring

**Metrics to Monitor:**
- Backup success/failure rate
- Backup duration
- Backup size
- Storage utilization
- Backup age

**Alerts:**
- Backup failure (P2)
- Backup delayed >15 minutes (P3)
- Storage utilization >80% (P2)
- Backup verification failure (P1)

### Restore Monitoring

**Metrics to Monitor:**
- Restore success/failure rate
- Restore duration
- Data integrity check results
- Post-restore health checks

**Alerts:**
- Restore failure (P1)
- Restore duration >RTO (P1)
- Data integrity check failure (P1)

---

## Testing and Validation

### Backup Testing

**Frequency:** Weekly

**Procedure:**
1. Select random backup from past 30 days
2. Restore to test environment
3. Verify data integrity
4. Run application smoke tests
5. Document results

### Restore Testing

**Frequency:** Monthly

**Procedure:**
1. Simulate disaster scenario
2. Execute full restore procedure
3. Measure restore time (RTO)
4. Verify data integrity
5. Run application smoke tests
6. Document results and improvements

### DR Drills

**Frequency:** Quarterly

**Procedure:**
1. Activate disaster recovery site
4. Verify service health
5. Measure failover time
6. Document lessons learned
7. Update procedures

---

## Security Considerations

### Backup Security

- All backups encrypted at rest
- Encryption keys managed via KMS/Vault
- Access to backups requires IAM role
- Backup access logged and audited
- Backup integrity verified via checksums

### Restore Security

- Restore requires authorization
- Restore operations logged
- Post-restore data validation
- Sensitive data handling during restore

### Compliance

- Backups retained per data retention policy
- Data classification enforced
- GDPR/CCPA compliance for personal data
- Audit trail for all backup/restore operations

---

## Appendix

### Environment Variables

```bash
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=yappc_prod
DB_USER=ghatana
DB_PASSWORD=<secure-password>

# Backup Configuration
BACKUP_DIR=/path/to/backups
BACKUP_RETENTION_DAYS=30
BACKUP_ENCRYPTION_ENABLED=true

# Cloud Storage
AWS_S3_BUCKET=yappc-backups
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=<access-key>
AWS_SECRET_ACCESS_KEY=<secret-key>

# Encryption
GPG_RECIPIENT=ops@ghatana.ai
GPG_KEY_ID=<key-id>
```

### Useful Commands

```bash
# Create backup
./scripts/db/backup.sh

# Restore backup
./scripts/db/restore.sh /path/to/backup.dump yappc_prod

# Verify backup
./scripts/db/verify-backup-integrity.sh /path/to/backup.dump

# List backups
aws s3 ls s3://yappc-backups/postgres/

# Check WAL archive
ls -la /var/lib/postgresql/wal/archive/

# Monitor backup progress
pg_stat_progress_basebackup
```

### Contact Information

- **Database Administrator:** dba@ghatana.ai
- **Operations Team:** ops@ghatana.ai
- **On-Call:** +1-555-XXX-XXXX

### Related Documentation

- [YAPPC Operations Runbook](./OPERATIONS.md)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [AWS RDS Backup and Restore](https://docs.aws.amazon.com/AmazonRDS/)
