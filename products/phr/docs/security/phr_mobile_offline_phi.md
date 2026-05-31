# PHR Mobile Offline PHI — Security Architecture

**Document type:** Security Architecture  
**Layer:** Product  
**Last updated:** 2026-05-31  

---

## 1. Overview

The PHR mobile app uses encrypted local storage plus the Kernel mobile privacy plugin contract to protect Protected Health Information (PHI) cached locally on the device. Current offline support is limited to approved mobile dashboard/record surfaces and must preserve scope, TTL, last-sync metadata, biometric requirements, and consent invalidation behavior.

---

## 2. Key Material Management

| Component | Technology | Details |
|---|---|---|
| Encryption key | `expo-secure-store` (iOS Keychain / Android Keystore) | 256-bit random key, generated on first launch |
| Ciphertext storage | `@react-native-async-storage/async-storage` | Base64-encoded IV + ciphertext, one entry per cached resource |
| Encryption algorithm | AES-256-GCM | 96-bit IV generated per write, 128-bit authentication tag |

### Key Generation and Storage

```
First launch:
  1. Generate 256-bit cryptographically random key using `crypto.getRandomValues()`
  2. Store key bytes in SecureStore under key `phr_phi_key`
     Access control: `AFTER_FIRST_UNLOCK` on iOS (device must have been unlocked at least once)

Subsequent launches:
  3. Read key from SecureStore → `getItemAsync('phr_phi_key')`
  4. If SecureStore returns null (device reset / re-install): generate new key, evict cached data
```

### Ciphertext Format

Each cached value is stored as: `<base64(iv)>.<base64(ciphertext+authTag)>`

The IV and ciphertext are concatenated before base64 encoding per the `phiEncryptedStorage.ts` implementation.

---

## 3. What Is Cached Offline

| Data | AsyncStorage key | Eviction policy |
|---|---|---|
| Mobile dashboard JSON | Dashboard offline store | On successful network refresh, TTL expiry, consent revocation, logout, or privacy clear |
| Restricted-field stripped PHI snapshots | PHI encrypted storage | On logout, consent revocation, biometric/session invalidation, TTL expiry, or privacy clear |
| (nothing else by default) | - | - |

PHI that is **never** cached offline:
- Full patient records with complete medical history
- Consent grants or revocations (always fetched live)
- Emergency access events
- Audit logs

---

## 4. Key Rotation Policy

| Trigger | Action |
|---|---|
| Device SecureStore cleared (re-install, wipe) | Generate new key; purge all cached ciphertext |
| User-initiated logout | Purge PHI cache and product-specific offline stores through the Kernel mobile privacy plugin |
| App detects key retrieval failure | Log structured error; fall back to network-only mode |
| Annual rotation (future) | Not yet implemented; will require re-encryption of cached data with new key |

---

## 5. Network Connectivity Handling

The `NetInfo` listener in `App.tsx` monitors connectivity state. When the device goes offline:

1. `isOnline` state is set to `false`.
2. New API calls are skipped; the app serves the most recent cached (decrypted) data.
3. A banner indicates offline status and last-sync/staleness state to the patient.
4. Expired PHI is not rendered from cache.

When connectivity is restored:
1. `isOnline` is set to `true`.
2. A background refresh is triggered; fetched data replaces the cache.

---

## 6. Threat Model

| Threat | Mitigation |
|---|---|
| Physical device theft | AES-256-GCM encryption; plaintext never touches AsyncStorage |
| AsyncStorage exfiltration via backup | Ciphertext only; key remains in Keychain/Keystore (not in backup by default on iOS; `AFTER_FIRST_UNLOCK` prevents backup) |
| Memory scraping | Key is loaded into memory only during encrypt/decrypt operations; not held persistently in React state |
| Brute-force against key | 256-bit key space makes brute-force computationally infeasible |
| IV reuse | IV is generated fresh per write using `crypto.getRandomValues()` |
| Compromised SecureStore | Key is lost; cached PHI cannot be decrypted; fall back to network-only mode |
| Push notification PHI leakage | `handleNotification` in `pushNotifications.ts` strips content-available PHI from banner text; original payload is not shown |

---

## 7. Implementation Files

| File | Purpose |
|---|---|
| `products/phr/apps/mobile/src/services/phiEncryptedStorage.ts` | Encrypted PHI storage implementation + `PhiStorageAdapter` interface |
| `products/phr/apps/mobile/src/services/offlineStore.ts` | Offline cache read/write using the `PhiStorageAdapter` |
| `products/phr/apps/mobile/src/services/mobilePrivacyPlugin.ts` | PHR adapter for the Kernel mobile privacy plugin clearing contract |
| `products/phr/apps/mobile/src/services/pushNotifications.ts` | Push notification handler with PHI redaction |
| `products/phr/apps/mobile/src/types/ambient.d.ts` | Type stubs for `expo-secure-store` |
| `products/phr/apps/mobile/src/App.tsx` | NetInfo connectivity listener; offline banner |

---

## 8. Compliance Notes

- PHI at rest on mobile devices must be encrypted per applicable healthcare privacy requirements.
- Emergency PHI is never rendered before biometric/device approval and server authorization.
- Consent revoke and logout paths must invoke the Kernel mobile privacy clearing contract for all PHI/offline stores.
- Offline access from cache does not emit a real-time server audit event. Future work: emit a local audit queue that is flushed on reconnect.
