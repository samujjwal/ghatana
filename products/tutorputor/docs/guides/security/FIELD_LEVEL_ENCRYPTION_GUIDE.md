# Field-Level Encryption Implementation Guide

**Date:** 2026-04-17
**Status:** Implementation Complete

## Overview

Field-level encryption has been implemented for PII (Personally Identifiable Information) using AES-256-GCM encryption. This provides an additional layer of security for sensitive data at rest, complementing database-level encryption.

## Implementation Details

### Encryption Service

**Location:** `services/tutorputor-platform/src/core/encryption/field-encryption.ts`

The `FieldEncryptionService` provides:
- AES-256-GCM encryption for sensitive fields
- Random IV (initialization vector) for each encryption operation
- Authentication tag for integrity verification
- Key management integration points (currently using environment variables, ready for KMS integration)

### Encrypted Field Format

```typescript
interface EncryptedField {
  encryptedData: string;  // Hex-encoded encrypted data
  iv: string;             // Hex-encoded initialization vector
  authTag: string;        // Hex-encoded authentication tag
  keyId: string;          // Key identifier for key rotation
  algorithm: string;      // Encryption algorithm used
}
```

### Usage Example

```typescript
import { fieldEncryption } from "@tutorputor/platform/core/encryption";

// Encrypt a field
const plaintext = "user@example.com";
const encrypted = fieldEncryption.encrypt(plaintext);

// Decrypt a field
const decrypted = fieldEncryption.decrypt(encrypted);

// Encrypt a field in an object
const user = { email: "user@example.com", name: "John Doe" };
fieldEncryption.encryptField(user, "email");

// Decrypt a field from an object
const decryptedEmail = fieldEncryption.decryptField(user, "email");
```

## Configuration

### Environment Variables

```bash
# Encryption key (hex-encoded, 64 characters for AES-256)
FIELD_ENCRYPTION_KEY=your-64-character-hex-key-here

# Key identifier (for key rotation tracking)
FIELD_ENCRYPTION_KEY_ID=default
```

### Generating an Encryption Key

```bash
# Generate a 256-bit (32 byte) key and encode as hex
openssl rand -hex 32
```

## Integration Points

### Recommended Fields for Encryption

Based on the PII audit, the following fields should be encrypted:

1. **User.email** - Email addresses
2. **User.phoneNumber** - Phone numbers (if present)
3. **AssessmentResponse.responses** - Assessment responses (may contain personal information)
4. **PreferenceChange.oldValue** - Old preference values (may contain PII)
5. **PreferenceChange.newValue** - New preference values (may contain PII)

### Integration Steps

1. **Add encrypted field to Prisma schema:**

```prisma
model User {
  id                String    @id @default(cuid())
  email             String
  emailEncrypted    String?   // Store encrypted email here
  // ...
}
```

2. **Encrypt on write:** Use `fieldEncryption.encryptField()` before saving
3. **Decrypt on read:** Use `fieldEncryption.decryptField()` after retrieving
4. **Update queries:** Handle both encrypted and unencrypted data during migration

## Key Management

### Current Implementation

- Uses environment variable `FIELD_ENCRYPTION_KEY`
- Deterministic fallback for development (NOT SECURE for production)
- Key rotation interface available but not yet implemented

### Production Requirements

For production deployment, integrate with a proper KMS:

1. **AWS KMS:** Use AWS SDK to generate data keys
2. **HashiCorp Vault:** Use Vault transit engine
3. **Cloud KMS:** Google Cloud KMS or Azure Key Vault

### Key Rotation

The `rotateKey()` method is available but requires implementation:

```typescript
await fieldEncryption.rotateKey("new-key-id");
```

Key rotation involves:
1. Retrieving all records with encrypted fields
2. Decrypting with old key
3. Encrypting with new key
4. Updating records atomically
5. Updating key identifier

## Security Considerations

### Strengths

- AES-256-GCM is a strong, widely-accepted encryption algorithm
- Random IV prevents pattern analysis
- Authentication tag prevents tampering
- Key rotation support for long-term security

### Limitations

- Encryption keys must be stored securely (KMS integration required for production)
- Encrypted fields cannot be queried directly (must decrypt first)
- Performance impact on read/write operations
- Key rotation requires downtime or careful migration strategy

### Best Practices

1. **Never log encrypted values** - Log only metadata
2. **Use different keys** for different data types if possible
3. **Rotate keys regularly** - At least annually, or upon compromise
4. **Monitor decryption failures** - May indicate key issues or data corruption
5. **Backup encryption keys** - Securely backup keys in multiple locations

## Testing

Unit tests are available at:
`services/tutorputor-platform/src/core/encryption/__tests__/field-encryption.test.ts`

Run tests:
```bash
pnpm test -- field-encryption.test.ts
```

## Migration Strategy

### Phase 1: Add Encryption Support

1. Deploy encryption service without enabling encryption
2. Add encrypted fields to schema
3. Update application code to support both encrypted and unencrypted data

### Phase 2: Enable Encryption for New Data

1. Enable encryption for new writes
2. Existing data remains unencrypted
3. Application handles both formats transparently

### Phase 3: Migrate Existing Data

1. Background job to encrypt existing records
2. Process in batches to avoid performance impact
3. Verify encryption/decryption works correctly
4. Remove unencrypted fields after migration complete

### Phase 4: Remove Unencrypted Fields

1. Once all data is encrypted, remove unencrypted fields
2. Update queries to only use encrypted fields
3. Remove legacy decryption logic

## Compliance Mapping

- **GDPR Article 32:** Technical measures for data security
- **SOC 2 Type II:** Encryption of sensitive data at rest
- **PCI DSS:** Protection of stored cardholder data (if applicable)

## Next Steps

1. Integrate with AWS KMS or HashiCorp Vault for production key management
2. Add encrypted fields to Prisma schema
3. Implement encryption in user service for email/phone fields
4. Implement encryption in assessment service for response fields
5. Create migration job to encrypt existing data
6. Add monitoring for encryption/decryption operations
7. Document key rotation procedures
