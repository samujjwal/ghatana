# At-Rest Encryption Checklist

Use this checklist per environment (`dev`, `staging`, `prod`).

## Database (Postgres)

- [ ] `ssl` is enabled for transport-layer protection.
- [ ] Database volume encryption is enabled (cloud disk encryption or equivalent).
- [ ] Backup encryption is enabled.
- [ ] Access to snapshots/backups is restricted via least-privilege IAM.
- [ ] `pgcrypto` availability is verified when column-level encryption is required.

## Object Storage

- [ ] Bucket default encryption is enabled.
- [ ] Encryption algorithm is approved (`AES256` or `aws:kms`).
- [ ] KMS key policy enforces least privilege.
- [ ] Bucket public access is blocked.
- [ ] Lifecycle policies preserve encrypted state in transitions.

## Application and Secrets

- [ ] No storage credentials are hardcoded in source.
- [ ] Encryption-related configuration is environment-managed.
- [ ] Secret rotation policy exists and has an owner.

## Evidence Capture

- [ ] Script output archived with date and environment.
- [ ] Command history included in change review or ticket.
- [ ] Exceptions documented with remediation owner and target date.
