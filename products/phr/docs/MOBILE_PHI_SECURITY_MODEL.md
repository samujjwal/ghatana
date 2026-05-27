# Mobile PHI Security Model

## Overview

The PHR mobile app uses AES-256-GCM authenticated encryption to protect Protected Health Information (PHI) at rest on user devices. This document describes the security architecture, threat model, and operational procedures.

## Architecture

### Encryption Layer

- **Algorithm**: AES-256-GCM (Galois/Counter Mode)
- **Key Storage**: OS keychain/keystore via `expo-secure-store`
- **Ciphertext Storage**: `@react-native-async-storage/async-storage`
- **Key Length**: 256 bits
- **IV Length**: 12 bytes (fresh IV per write)
- **Crypto API**: WebCrypto API (`crypto.subtle`)

### Key Lifecycle

1. **Key Generation**: Per-installation AES-256 symmetric key generated on first launch
2. **Key Storage**: Key stored in OS keychain with `AFTER_FIRST_UNLOCK` accessibility
3. **Key Rotation**: Automatic rotation after 90 days
4. **Key Versioning**: Version counter tracks key generations
5. **Tamper Detection**: Check value stored to detect unauthorized key access

### Data Flow

```
PHI Data → AES-256-GCM Encrypt → Ciphertext (IV + tag + payload) → AsyncStorage
Key → SecureStore (OS keychain)
```

## Threat Model

### Device Risks

| Risk | Mitigation | Residual Risk |
|------|------------|---------------|
| **Physical theft** | Key stored in hardware-backed keychain; requires device unlock | High if device unlocked when stolen |
| **App reinstall** | New key generated; old ciphertext inaccessible | PHI lost (acceptable - requires re-sync) |
| **OS compromise** | Key non-extractable; ciphertext authenticated | Low on modern mobile OS |
| **Backup extraction** | Key not included in app backups (keychain separate) | Low on iOS/Android with proper backup policies |
| **Side-channel attacks** | Use of WebCrypto API (constant-time operations) | Low |
| **Tampering** | Tamper detection check; clear on failure | Medium - detection only, not prevention |

### Operational Risks

| Risk | Mitigation | Residual Risk |
|------|------------|---------------|
| **Key loss** | No recovery mechanism; requires re-sync | High - data loss on key loss |
| **Corrupted ciphertext** | Decryption failure removes item | Low - single item loss |
| **Backup/restore** | Key not backed up; PHI inaccessible after restore | High - requires re-sync from server |
| **Device transfer** | Key device-specific; manual data export required | High - requires explicit migration |

## Security Properties

### Guaranteed

- **Authenticated Encryption**: Any tampering with ciphertext causes decryption failure
- **Fresh IV**: Every write uses a new random IV
- **Key Isolation**: Key never leaves secure keychain
- **Ciphertext Only**: AsyncStorage contains only encrypted data
- **Automatic Rotation**: Keys rotate every 90 days

### Not Guaranteed

- **Key Recovery**: Lost keys cannot be recovered
- **Backup Compatibility**: Keys do not survive app reinstall or device transfer
- **Cross-Device Sync**: Keys are device-specific
- **Offline Access**: PHI requires server sync after key rotation

## Operational Procedures

### Key Rotation

- **Trigger**: 90 days after key creation
- **Process**:
  1. Generate new AES-256 key
  2. Re-encrypt all existing PHI with new key
  3. Replace old key in SecureStore
  4. Update version counter and timestamp
  5. Re-initialize tamper detection
- **Failure Handling**: If re-encryption fails for an item, the item is deleted

### Tamper Detection

- **Check Value**: Timestamp + random value stored in SecureStore
- **Verification**: On key retrieval, verify check value exists and is properly formatted
- **Failure Response**: Clear all PHI and regenerate key
- **Limitation**: Currently client-side only; production should use server attestation

### Cache Clearing

PHI cache is cleared on:
- Consent revocation
- User logout
- Session expiry
- Role/persona change
- Tamper detection failure
- Key rotation

## Device-Specific Considerations

### iOS

- **Keychain**: Hardware-backed Secure Enclave on supported devices
- **Backup**: Keychain not included in iTunes/iCloud backups
- **Accessibility**: `AFTER_FIRST_UNLOCK` requires device unlock after restart

### Android

- **Keystore**: Hardware-backed keystore on supported devices
- **Backup**: Keystore not included in ADB backups
- **Accessibility**: `AFTER_FIRST_UNLOCK` requires device unlock after restart

### Unsupported Devices

- Devices without hardware-backed key storage use software keystore
- Security level reduced on older Android versions (< 6.0)
- Fallback to standard keychain on iOS < 9.0

## Compliance Notes

### HIPAA

- **Encryption at Rest**: AES-256-GCM meets NIST standards
- **Access Control**: Device unlock required for key access
- **Audit Trail**: PHI access logged on server side
- **Breach Notification**: Key loss considered data breach (notify users)

### Nepal Context

- **Data Localization**: PHI stored on device; server in Nepal
- **Cross-Border**: No cross-border data transfer for encrypted PHI
- **Retention**: Server-side retention policy applies; device cache ephemeral

## Recommendations

### For Production

1. **Server Attestation**: Implement server-side tamper detection verification
2. **Biometric Gate**: Require biometric confirmation for key access
3. **Key Export**: Provide secure key export for device migration
4. **Backup Policy**: Document backup/restore behavior for users
5. **Monitoring**: Alert on frequent key regeneration (tampering indicator)

### For Users

1. **Device Security**: Enable device passcode/biometrics
2. **OS Updates**: Keep mobile OS updated for security patches
3. **Backup**: Regular server sync to mitigate key loss
4. **Report**: Report lost/stolen devices immediately

## References

- NIST SP 800-38D: Recommendation for Block Cipher Modes of Operation
- HIPAA Security Rule: 45 CFR § 164.312(a)(1)
- WebCrypto API Specification: https://www.w3.org/TR/WebCryptoAPI/
- expo-secure-store: https://docs.expo.dev/versions/latest/sdk/secure-store/
