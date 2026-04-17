# Database Backup Encryption Requirements

**Document Version:** 1.0
**Date:** 2026-04-17
**Scope:** TutorPutor Platform Database Backups

## Overview

This document specifies the encryption requirements for database backups to ensure PII (Personally Identifiable Information) is protected at rest.

## Requirements

### 1. Backup Encryption at Rest

All database backups MUST be encrypted at rest using one of the following methods:

#### Option A: PostgreSQL Native Encryption
- Use `pg_dump` with encryption at the filesystem level
- Store backups on encrypted volumes (AES-256 XTS)
- Ensure backup storage has encryption enabled (e.g., AWS EBS encryption, Azure Disk Encryption)

#### Option B: Application-Level Encryption
- Encrypt backup files using AES-256-GCM before storage
- Store encryption keys in AWS KMS or HashiCorp Vault
- Implement key rotation policy (90-day rotation recommended)

#### Option C: Cloud Provider Encryption
- Use AWS RDS automated backups with encryption enabled
- Enable KMS encryption for snapshot storage
- Ensure cross-region replication uses encrypted channels

### 2. Backup Storage Configuration

#### AWS S3 Storage
```bash
# Enable default encryption on bucket
aws s3api put-bucket-encryption \
  --bucket tutorputor-backups \
  --server-side-encryption-configuration '{
    "Rules": [
      {
        "ApplyServerSideEncryptionByDefault": {
          "SSEAlgorithm": "AES256"
        }
      }
    ]
  }'

# Or use KMS for enhanced security
aws s3api put-bucket-encryption \
  --bucket tutorputor-backups \
  --server-side-encryption-configuration '{
    "Rules": [
      {
        "ApplyServerSideEncryptionByDefault": {
          "SSEAlgorithm": "aws:kms",
          "KMSMasterKeyID": "arn:aws:kms:region:account-id:key/key-id"
        }
      }
    ]
  }'
```

#### Environment Variables
```bash
# Backup configuration
BACKUP_ENCRYPTION_ENABLED=true
BACKUP_ENCRYPTION_METHOD=AES256
BACKUP_KMS_KEY_ID=arn:aws:kms:region:account-id:key/key-id
BACKUP_STORAGE_ENCRYPTED=true
```

### 3. Backup Rotation and Retention

- **Daily backups:** Retain for 30 days
- **Weekly backups:** Retain for 90 days
- **Monthly backups:** Retain for 365 days
- **Encryption key rotation:** Every 90 days

### 4. Backup Access Control

- Backup storage must have strict IAM policies
- Only authorized backup service accounts can access backups
- Implement MFA for backup restoration operations
- Log all backup access and restoration attempts

### 5. Backup Verification

Regularly verify backup integrity and encryption:

```bash
# Verify backup can be decrypted and restored
pg_restore --verbose --clean --if-exists \
  --dbname=verification_db \
  --no-owner --no-acl \
  encrypted_backup.dump

# Verify encryption on S3 objects
aws s3api head-object \
  --bucket tutorputor-backups \
  --key backup-2026-04-17.dump
```

### 6. Incident Response

In case of suspected backup compromise:
1. Immediately revoke access to backup storage
2. Rotate all encryption keys
2. Verify integrity of recent backups
3. Restore from known-good backup if needed
4. Document incident and lessons learned

## Implementation Checklist

- [ ] Enable encryption on backup storage (S3/EBS/Azure Disk)
- [ ] Configure backup encryption in backup script
- [ ] Set up KMS key for backup encryption
- [ ] Implement key rotation schedule
- [ ] Add backup encryption verification to CI/CD
- [ ] Document backup restoration procedure
- [ ] Train operations team on backup encryption
- [ ] Add backup encryption to security audit checklist

## Compliance Mapping

- **GDPR Article 32:** Technical and organizational measures for security
- **SOC 2 Type II:** Encryption of data at rest
- **PCI DSS:** Requirement 3 (protect stored cardholder data) - if applicable

## References

- [AWS S3 Server-Side Encryption](https://docs.aws.amazon.com/AmazonS3/latest/userguide/serv-side-encryption.html)
- [PostgreSQL Backup Documentation](https://www.postgresql.org/docs/current/backup-dump.html)
- [NIST SP 800-57: Key Management](https://csrc.nist.gov/publications/detail/sp/800-57-part-1-rev-5/final)
