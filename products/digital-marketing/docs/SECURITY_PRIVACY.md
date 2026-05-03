# DMOS Security and Privacy Guide

## Security

### Authentication

- JWT-based authentication
- Session expiry: 30 minutes (configurable)
- Session refresh: every 5 minutes
- Runtime-only token storage (no localStorage for tokens)

### Authorization

- Role-based access control (RBAC)
- Roles: admin, approver, user
- Tenant and workspace scoping
- Authorization checks on all API endpoints

### Encryption

- AES-GCM encryption for sensitive data at rest
- Keys: `ENCRYPTION_KEY` environment variable (256-bit)
- Encrypted fields: connector tokens, API keys

### Hashing

- HMAC-SHA256 hashing for PII (email, phone)
- Keys: `HMAC_KEY` environment variable
- Used for: contact suppression, data subject requests

### API Keys

- SHA-256 hashed storage only
- Display secret only once during creation
- Key prefix for lookup (first 8 characters)
- Rotation and revocation support
- Last-used tracking
- Rate-limit plan per key

### Connector Credentials

- AES-GCM encryption at rest
- Revocation support
- Audit logging
- Token redaction from logs

## Privacy

### Data Subject Rights

DMOS supports GDPR/CCPA data subject rights:
- Data export
- Data deletion
- Data correction
- Processing restriction
- Consent withdrawal

### Data Retention

- Data subject requests: 90 days (configurable)
- Suppression entries: 365 days (configurable)
- Audit logs: 1 year

### PII Handling

- Contact normalization before hashing
- Hashed storage for email and phone
- Encrypted storage for raw data when needed
- Audit trail for all PII operations

### Compliance

- GDPR compliant
- CCPA compliant
- SOC 2 Type II compliant (in progress)

## Incident Response

### Security Incident

1. Contain the incident
2. Notify security team
3. Preserve evidence
4. Assess impact
5. Communicate with stakeholders
6. Remediate
7. Document lessons learned

### Data Breach

1. Identify affected data
2. Notify affected users (within 72 hours per GDPR)
3. Notify regulators
4. Implement remediation measures
5. Conduct post-mortem

## Best Practices

- Never commit secrets to repository
- Use environment variables for sensitive configuration
- Rotate encryption keys regularly
- Review access logs periodically
- Keep dependencies updated
- Run security scans in CI
