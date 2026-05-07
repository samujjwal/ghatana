# AEP Disaster Recovery Automation

**Version:** 1.0.0  
**Last Updated:** 2026-05-02  
**Maintainer:** AEP Platform Team

---

## Overview

This directory contains automated disaster recovery (DR) scripts for the AEP platform. These scripts provide automated backup, restore, and drill capabilities to ensure business continuity and meet recovery time objectives (RTO).

---

## Scripts

### backup-aep.sh

Performs automated backup of AEP critical data.

**Usage:**
```bash
./backup-aep.sh --tenant-id <id> [options]
```

**Options:**
- `--tenant-id <id>`: Tenant ID to backup (required)
- `--output-dir <dir>`: Output directory for backups (default: `/backups/aep`)
- `--retention <days>`: Retention period in days (default: 30)
- `--full`: Perform full backup (default: incremental)
- `--no-compress`: Disable compression (default: compress)

**Backed Up Components:**
- Pipeline configurations
- Agent registry
- Learning policies
- Patterns
- Governance state (kill switch, degradation mode)
- Audit log summary
- Compliance summary
- Checkpoint data (full backup only)

**Example:**
```bash
./backup-aep.sh --tenant-id tenant-prod --full --retention 90
```

---

### restore-aep.sh

Restores AEP critical data from a backup archive.

**Usage:**
```bash
./restore-aep.sh --tenant-id <id> --backup-file <file> [options]
```

**Options:**
- `--tenant-id <id>`: Tenant ID to restore (required)
- `--backup-file <file>`: Backup file to restore (required)
- `--validate-only`: Validate backup without restoring
- `--dry-run`: Show what would be restored without actually restoring

**Restore Process:**
1. Validate backup checksum
2. Validate backup manifest
3. Validate required components
4. Stop AEP services
5. Restore components via API
6. Restore checkpoint data (if present)
7. Start AEP services
8. Validate restore

**Example:**
```bash
./restore-aep.sh --tenant-id tenant-prod --backup-file /backups/aep/tenant-prod/20260502_120000.tar.gz
```

**Validate Only:**
```bash
./restore-aep.sh --tenant-id tenant-prod --backup-file /backups/aep/tenant-prod/20260502_120000.tar.gz --validate-only
```

---

### dr-drill.sh

Performs automated disaster recovery drills to validate DR procedures.

**Usage:**
```bash
./dr-drill.sh --tenant-id <id> [options]
```

**Options:**
- `--tenant-id <id>`: Tenant ID for drill (required)
- `--scenario <type>`: Failure scenario: `backup` | `restore` | `full` (default: full)
- `--rto-target <mins>`: RTO target in minutes (default: 30)
- `--no-cleanup`: Don't clean up after drill

**Scenarios:**

**backup:** Validates backup procedures
- Creates full backup
- Validates backup integrity
- Measures backup duration

**restore:** Validates restore procedures (dry-run)
- Validates backup file
- Performs dry-run restore
- Measures restore validation duration

**full:** Complete DR drill
- Pre-drill health check
- Backup drill
- Service failure simulation
- Service restore
- Post-restore health check
- Restore drill
- Validates RTO target

**Example:**
```bash
./dr-drill.sh --tenant-id tenant-prod --scenario full --rto-target 30
```

---

## Setup and Configuration

### Prerequisites

1. **AEP Server Access:** Scripts communicate with AEP server on `localhost:8080`
2. **Database Access:** For checkpoint backup/restore (PostgreSQL)
3. **Backup Storage:** Sufficient disk space in `/backups/aep`
4. **Permissions:** Execute permissions on scripts, write access to backup directory

### Environment Variables

```bash
# Database configuration (for checkpoint backup/restore)
export DB_HOST=localhost
export DB_USER=aep
export DB_PASSWORD=your_password
export DB_NAME=aep

# Backup configuration
export BACKUP_DIR=/backups/aep
export RETENTION_DAYS=30
```

### Cron Scheduling

**Daily Backup:**
```cron
0 2 * * * /path/to/backup-aep.sh --tenant-id tenant-prod >> /var/log/aep-backup.log 2>&1
```

**Weekly Full Backup:**
```cron
0 3 * * 0 /path/to/backup-aep.sh --tenant-id tenant-prod --full >> /var/log/aep-backup.log 2>&1
```

**Monthly DR Drill:**
```cron
0 4 1 * * /path/to/dr-drill.sh --tenant-id tenant-prod --scenario full >> /var/log/dr-drill.log 2>&1
```

---

## Recovery Time Objectives (RTO)

| Scenario | Target RTO | Actual RTO | Status |
|----------|-------------|------------|--------|
| Backup Creation | 5 minutes | TBD | To be measured |
| Restore Validation | 10 minutes | TBD | To be measured |
| Full DR Drill | 30 minutes | TBD | To be measured |

---

## Monitoring and Alerts

### Metrics to Monitor

- **Backup Duration:** Time to complete backup
- **Backup Size:** Size of backup files
- **Backup Success Rate:** Percentage of successful backups
- **Restore Duration:** Time to complete restore
- **RTO Compliance:** Whether RTO targets are met

### Alerting

Configure alerts for:
- Backup failures
- Restore failures
- RTO target misses
- Backup storage capacity (>80%)

---

## Testing

### Manual Testing

**Test Backup:**
```bash
./backup-aep.sh --tenant-id test-tenant --full
```

**Test Restore (Dry Run):**
```bash
./restore-aep.sh --tenant-id test-tenant --backup-file /backups/aep/test-tenant/latest.tar.gz --dry-run
```

**Test DR Drill:**
```bash
./dr-drill.sh --tenant-id test-tenant --scenario backup
```

### Automated Testing

Run DR drill in non-production environment:
```bash
./dr-drill.sh --tenant-id test-tenant --scenario full --rto-target 30
```

---

## Troubleshooting

### Backup Fails

**Symptom:** Backup script exits with error

**Solutions:**
1. Check AEP server is running: `curl http://localhost:8080/health`
2. Verify backup directory exists and is writable
3. Check disk space: `df -h /backups`
4. Review backup log: `/backups/aep/backup_*.log`

### Restore Fails

**Symptom:** Restore script exits with error

**Solutions:**
1. Validate backup file first: `./restore-aep.sh --validate-only`
2. Check backup file integrity: `sha256sum -c backup.tar.gz.sha256`
3. Verify tenant ID matches backup
4. Stop AEP services before restore
5. Review restore log

### DR Drill Fails

**Symptom:** DR drill reports failure

**Solutions:**
1. Check drill log: `/tmp/dr-drill-*.log`
2. Verify AEP server health
3. Check backup file exists and is valid
4. Verify RTO target is achievable
5. Review individual scenario results

---

## Security Considerations

1. **Backup Encryption:** Backups should be encrypted at rest
2. **Access Control:** Restrict access to backup directory and scripts
3. **Secure Credentials:** Store database credentials in secure vault
4. **Audit Logging:** All backup/restore operations are logged
5. **Backup Retention:** Follow data retention policies

---

## Disaster Recovery Plan

### RPO/RTO Targets

| Component | RPO | RTO |
|-----------|-----|-----|
| Pipeline Configs | 1 hour | 15 minutes |
| Agent Registry | 24 hours | 30 minutes |
| Learning Policies | 24 hours | 30 minutes |
| Checkpoints | 5 minutes | 10 minutes |
| Governance State | 1 hour | 15 minutes |

### Recovery Procedures

1. **Minor Incident:** Restore from latest backup
2. **Major Incident:** Restore from last known good backup
3. **Catastrophic:** Restore from off-site backup

### Communication

- **Incident Response Team:** Notify within 15 minutes
- **Stakeholders:** Notify within 1 hour
- **Customers:** Notify per SLA

---

## Maintenance

### Regular Maintenance Tasks

- **Weekly:** Review backup logs for errors
- **Monthly:** Test restore procedure in non-production
- **Quarterly:** Run full DR drill
- **Annually:** Review and update RPO/RTO targets

### Backup Verification

- **Daily:** Automated backup verification via checksum
- **Weekly:** Manual backup spot-check
- **Monthly:** Full restore test in non-production

---

## Support

For issues or questions:
- Documentation: [AEP Operations Documentation](../../../docs/)
- Issues: [GitHub Issues](https://github.com/ghatana/aep/issues)
- Contact: aep-team@ghatana.com
- On-call: AEP Platform On-Call

---

**Documentation Version:** 1.0.0  
**Last Updated:** 2026-05-02  
**Maintained By:** AEP Platform Team
