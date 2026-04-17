# At-Rest Encryption Audit for PII

**Audit Date:** 2026-04-16
**Auditor:** AI Remediation Agent
**Scope:** TutorPutor Platform - PII Data at Rest

## Executive Summary

This audit evaluates the at-rest encryption posture for Personally Identifiable Information (PII) in the TutorPutor platform. The assessment covers database encryption, field-level encryption, configuration management, and compliance with security best practices.

**Overall Risk Level:** MEDIUM
**Compliance Status:** PARTIALLY COMPLIANT

---

## Findings

### 1. Database-Level Encryption

#### Current State
- **Database Provider:** PostgreSQL (via Prisma ORM)
- **Database URL Configuration:** Environment variable `DATABASE_URL`
- **SSL/TLS Enforcement:** NOT EXPLICITLY CONFIGURED

#### Analysis
The Prisma schema (`libs/tutorputor-core/prisma/schema.prisma`) specifies PostgreSQL as the database provider but does not include SSL/TLS connection parameters in the datasource configuration.

```prisma
datasource db {
  provider = "postgresql"
}
```

The `DATABASE_URL` environment variable is required but SSL mode is not enforced in the configuration schema (`services/tutorputor-platform/src/config/config.ts`).

**Risk:** If SSL is not enforced, database connections may transmit data in plaintext, exposing PII to network interception.

#### Recommendation
1. **Add SSL mode enforcement** to `DATABASE_URL` validation in `config.ts`:
   ```typescript
   DATABASE_URL: z.string()
     .min(1, 'DATABASE_URL is required')
     .refine(
       (url) => url.includes('sslmode=require') || url.includes('sslmode=verify-full'),
       'DATABASE_URL must enforce SSL/TLS (sslmode=require or sslmode=verify-full)'
     ),
   ```

2. **Update Prisma datasource** to include SSL configuration:
   ```prisma
   datasource db {
     provider = "postgresql"
     url      = env("DATABASE_URL")
   }
   ```

3. **Document SSL requirements** in deployment documentation.

---

### 2. Field-Level Encryption

#### Current State
- **Application-Level Encryption:** NOT IMPLEMENTED
- **Sensitive Fields Stored:** Plaintext

#### Analysis
The following PII fields are stored in plaintext in the database:

**User Model:**
- `email` - User email addresses
- `displayName` - User display names
- `phoneNumber` (if present) - Contact information

**Learner Profile:**
- `learningGoals` - Personal learning preferences
- `gradeLevel` - Educational information

**Preference Changes:**
- `key`, `oldValue`, `newValue` - User preference history

**Assessment Results:**
- `responses` - User assessment responses (may contain personal information)

No field-level encryption is applied to sensitive data at rest. All encryption relies on database-level encryption (if configured) or filesystem encryption.

**Risk:** If database backups are compromised or database access is obtained, PII is exposed in plaintext.

#### Recommendation
1. **Implement field-level encryption** for highly sensitive fields (email, phone, assessment responses):
   - Use AES-256-GCM encryption
   - Store encryption keys in a secure key management service (AWS KMS, HashiCorp Vault)
   - Encrypt data before storage, decrypt on read

2. **Add encrypted field types** to Prisma schema:
   ```prisma
   model User {
     id                String    @id @default(cuid())
     email             String
     emailEncrypted    String?   // AES-256 encrypted email
     // ...
   }
   ```

3. **Create encryption utility** with key rotation support.

---

### 3. Configuration Security

#### Current State
- **Secret Management:** Environment variables
- **Secret Validation:** Partial (JWT_SECRET, SESSION_SECRET)
- **Production Security Checks:** Implemented

#### Analysis
The configuration system (`services/tutorputor-platform/src/config/config.ts`) includes:

**Strengths:**
- Zod schema validation for all configuration
- Production security checks that validate:
  - JWT_SECRET length (minimum 64 characters in production)
  - SESSION_SECRET length (minimum 64 characters in production)
  - SENTRY_DSN required in production
  - S3 credentials required in production
  - CORS_ORIGIN explicitly set
  - DATABASE_URL not using development defaults

**Weaknesses:**
- No encryption key management for field-level encryption
- No validation for SSL/TLS in DATABASE_URL
- Secrets stored as environment variables (not ideal for production)

**Risk:** Environment variable leakage could expose database credentials and other secrets.

#### Recommendation
1. **Use a secret management service** in production (AWS Secrets Manager, HashiCorp Vault)
2. **Add DATABASE_URL SSL validation** (see Finding #1)
3. **Implement secret rotation** policies
4. **Use sealed secrets** or **external secrets operator** for Kubernetes deployments

---

### 4. Database Backup Encryption

#### Current State
- **Backup Encryption:** NOT DOCUMENTED
- **Backup Storage:** UNKNOWN

#### Analysis
No documentation or configuration was found regarding database backup encryption. PostgreSQL backups typically use `pg_dump` which may or may not encrypt output.

**Risk:** Unencrypted backups are a significant security risk if storage is compromised.

#### Recommendation
1. **Document backup encryption requirements**
2. **Use encrypted backup storage** (S3 with SSE-KMS, encrypted volumes)
3. **Implement backup encryption at rest** using PostgreSQL's encryption options or external tools
4. **Test backup restore procedures** regularly

---

### 5. File Storage Encryption

#### Current State
- **Object Storage:** AWS S3 (configured via S3_ENDPOINT, S3_ACCESS_KEY, S3_SECRET_KEY)
- **S3 Encryption:** NOT EXPLICITLY CONFIGURED

#### Analysis
S3 configuration exists but server-side encryption is not explicitly enabled. S3 supports:
- SSE-S3 (AWS-managed keys)
- SSE-KMS (AWS KMS-managed keys)
- SSE-C (customer-provided keys)

**Risk:** S3 objects stored without encryption could be exposed if AWS account is compromised.

#### Recommendation
1. **Enable default S3 encryption** using SSE-KMS
2. **Update S3 client configuration** to enforce encryption:
   ```typescript
   const s3 = new S3Client({
     credentials: {
       accessKeyId: config.S3_ACCESS_KEY,
       secretAccessKey: config.S3_SECRET_KEY,
     },
     endpoint: config.S3_ENDPOINT,
     region: 'us-east-1', // Configure appropriately
   });
   // Use SSE-KMS for all uploads
   ```

3. **Add bucket policy** requiring encryption for all objects

---

### 6. Transit Encryption

#### Current State
- **HTTP:** HTTPS enforced via Helmet middleware
- **Database:** SSL not explicitly enforced
- **Redis:** SSL not explicitly enforced

#### Analysis
The application uses:
- Fastify with Helmet for security headers
- CORS configuration
- Rate limiting

However, database and Redis connections do not explicitly enforce SSL/TLS.

**Risk:** Database and Redis traffic could be intercepted in transit.

#### Recommendation
1. **Enable SSL for Redis** (use `rediss://` protocol or TLS options)
2. **Enforce SSL for PostgreSQL** (see Finding #1)
3. **Document all external service connection security requirements**

---

## Compliance Assessment

### GDPR Compliance
- **Data at Rest:** PARTIALLY COMPLIANT (missing field-level encryption)
- **Data in Transit:** PARTIALLY COMPLIANT (missing database SSL enforcement)
- **Data Portability:** COMPLIANT (GDPR export functionality exists)
- **Right to Erasure:** COMPLIANT (cascade delete implemented and tested)

### SOC 2 Type II
- **Access Control:** PARTIALLY COMPLIANT (secret management needs improvement)
- **Encryption:** NOT COMPLIANT (missing field-level encryption, SSL enforcement)
- **Change Management:** COMPLIANT (configuration validation exists)

### PCI DSS
- **Not Applicable** (TutorPutor does not process payment cards directly - uses Stripe)

---

## Prioritized Remediation Plan

### High Priority (Critical)
1. **Enforce SSL/TLS for DATABASE_URL** - Add validation to config schema
2. **Enable S3 server-side encryption** - Configure SSE-KMS for object storage
3. **Document backup encryption** - Ensure backups are encrypted at rest

### Medium Priority (Important)
4. **Implement field-level encryption** for email and phone numbers
5. **Enable Redis SSL/TLS** - Use encrypted Redis connections
6. **Migrate to secret management service** - Replace environment variables

### Low Priority (Enhancement)
7. **Implement key rotation** for encryption keys
8. **Add encryption audit logging** - Track encryption/decryption operations
9. **Regular security scans** - Automated vulnerability scanning

---

## Conclusion

The TutorPutor platform has a solid foundation for security with configuration validation and some security controls. However, critical gaps exist in at-rest encryption:

1. **Database SSL is not enforced** - This is a critical security gap
2. **No field-level encryption** - PII stored in plaintext
3. **Backup encryption undocumented** - Unknown if backups are encrypted
4. **S3 encryption not configured** - Objects may be stored unencrypted

Implementing the high-priority recommendations will significantly improve the security posture and bring the platform closer to compliance with GDPR and SOC 2 requirements.

---

**Next Steps:**
1. Review and approve this audit report
2. Create implementation tickets for high-priority items
3. Schedule security review for field-level encryption design
4. Update deployment documentation with encryption requirements
