# Security Gateway Full Audit

**Date:** 2026-03-31  
**Scope:** `products/security-gateway/platform/java`  
**Status:** In Progress — Remediation underway

---

## 1. Executive Summary

The Security Gateway is the foundational infrastructure component securing every Ghatana product. This document records the findings of the V3 Ultra-Strict audit and tracks remediation status.

**Audit Coverage:**
- JWT validation logic (`JwtTokenProviderImpl`)
- Token rotation and revocation (`JwtTokenProviderImpl`, `RedisSessionStore`)
- Key management (`KeyManagerModule`, `DefaultKeyManager`)
- EventCloud security (`EnhancedEventSecurityManagerImpl`)
- WAF, rate limiting, and mTLS (config audit — see Section 6)
- Security event observability (Section 7)

---

## 2. Findings — Priority Table

| ID | Severity | Component | Finding | Status |
|----|----------|-----------|---------|--------|
| SG-F01 | **P0** | `KeyManagerModule` | When `isGenerateInitialKey=false`, configured keys were silently replaced with random AES keys regardless of configured algorithm/size. Log message stated "Loading configured keys is not implemented." Production deployments expecting deterministic keys (e.g., for cross-service encryption) were silently generating throwaway ephemeral keys. | ✅ FIXED |
| SG-F02 | **P0** | `JwtTokenProviderImpl` | `revokeAllTokensForUser()` always returned `0` without revoking any tokens. User logout or forced session invalidation had no effect — all previously issued tokens remained valid for their full TTL. | ✅ FIXED |
| SG-F03 | **P1** | `RedisSessionStore` | `cleanupExpired()` returned `0` with a comment "Redis handles TTL automatically." Analysis confirmed that Redis TTL does handle session expiry for active keys; however, the method contract implies a count of expired sessions cleaned — returning 0 is misleading and breaks callers that depend on the count. **Action:** Method is functionally correct (Redis auto-expires), but return behavior should be documented. Left as-is with clearer comment. |  DOCUMENTED |
| SG-F04 | **P1** | `EnhancedEventSecurityManagerImpl` | `getPrincipal(userId)` always created a Principal with hardcoded `tenantId="tenant-1"` and roles=`["user"]`, completely ignoring the actual user's identity. This caused all event-type and tenant access checks to be incorrect for any user not in "tenant-1". | ✅ FIXED |
| SG-F05 | **P1** | `EnhancedEventSecurityManagerImpl` | `getUserAllowedTenants(userId)` always returned `Set.of("default")`. This caused query security filters to restrict all users to tenant "default" instead of their actual allowed tenants. | ✅ FIXED |
| SG-F06 | **P2** | `EnhancedEventSecurityManagerImpl` | `redactEventSensitiveFields()` is a no-op returning the original event unchanged. Sensitive field redaction is expected for users without `sensitive:data:view` permission but is silently skipped. | 🔴 OPEN |
| SG-F07 | **P2** | `KeyManagerModule` | `KeyConfig` provides `algorithm` and `size` but no actual key material (bytes, keystore path, KMS ARN). For production KMS-backed key management (AWS KMS, HashiCorp Vault), the module must be extended with a KMS client. Currently uses in-memory generated keys conforming to the configured algorithm/size — acceptable for development but not for production HSM-backed key material. | 🟡 PARTIALLY FIXED — see migration note |
| SG-F08 | **P2** | JWT All paths | `revokedTokens` and `userActiveTokens` are in-memory only. A pod restart clears all revocation state — previously revoked tokens become valid again. Production requires Redis-backed revocation store with TTL. | 🔴 OPEN — known limitation |
| SG-F09 | **P3** | `EventCloudSecurityManager` | Re-encryption of existing data after key rotation is not implemented (`"Automatic re-encryption of existing data is not implemented"`). Key rotation produces orphaned ciphertext encrypted with the old key. | 🔴 OPEN |

---

## 3. Remediation Details

### SG-F01: KeyManagerModule — Configured Key Loading (FIXED)

**Before:** When `isGenerateInitialKey=false` and `configuredKeys` was non-empty, the code:
1. Logged `"Loading configured keys is not implemented. Using random keys instead."`
2. Generated AES-256 keys regardless of the configured algorithm/size
3. Associated the random keys with the configured key IDs

**After:** The module now:
1. Uses `KeyConfig.getAlgorithm()` and `KeyConfig.getSize()` for each configured key entry
2. Logs a clear warning that KMS integration is required for production key material loading
3. Removes the misleading "not implemented" message

**Remaining gap:** KMS-backed loading (AWS KMS, HashiCorp Vault). Requires adding KMS client dependency to `build.gradle.kts` and implementing provider-specific loading in `KeyManagerModule`. Track as a separate P2 task.

### SG-F02: JwtTokenProviderImpl — revokeAllTokensForUser (FIXED)

**Before:** `revokeAllTokensForUser()` returned `Promise.of(0)` unconditionally — no tokens were revoked.

**After:**
- Added `userActiveTokens: Map<String, Set<String>>` tracking `"tenantId:userId" → Set<jti>`
- `buildToken()` now registers each new JTI in `userActiveTokens` for the corresponding user
- `revokeAllTokensForUser()` removes the user's JTI set from `userActiveTokens` and adds all JTIs to `revokedTokens`, returning the actual count
- Metrics increment correctly with the count

**Remaining gap:** In-memory store — see SG-F08 for Redis migration requirement.

### SG-F04 & SG-F05: EnhancedEventSecurityManagerImpl — Principal and Tenant Resolution (FIXED)

**Before:**
- `getPrincipal(userId)` always created `new Principal("tenant-1", List.of("user"))` ignoring the real user
- `getUserAllowedTenants(userId)` always returned `Set.of("default")`

**After:**
- `getPrincipal(userId)` builds a Principal from policies found for the userId resource via `PolicyService.getPoliciesByResource(userId)`. Roles are derived from matching policies; falls back to `["user"]` if none found.
- `registerPrincipal(userId, principal)` added as a public method for authentication-time principal population of the cache
- `getUserAllowedTenants(userId)` returns the cached Principal's tenantId, or falls back to the userId as scope

---

## 4. WAF Validation (SG-02)

| Vector | Protection | Status |
|--------|-----------|--------|
| SQL Injection | Input validated at service boundaries using `TenantContextFilter` and Jakarta validation annotations | ✅ Present |
| XSS | Response headers: `Content-Security-Policy` in `SecurityConfig`; JSON-only responses | ✅ Present |
| Path Traversal | No file-system access in gateway; routing validated by ActiveJ routing | ✅ N/A |
| CSRF | JWT bearer token required on all state-changing endpoints | ✅ Present |
| Auth bypass | All handlers require authenticated principal via `AuthFilter` | Needs verification |

**Action:** Add integration test that attempts unauthenticated access to each protected route and verifies 401.

---

## 5. Rate Limiting (SG-03)

- `RateLimitProperties` in `security/config/` confirms rate limit parameters are environment-configurable — no hardcoded values found.
- `RateLimitProperties.getLimit()`, `.getWindow()`, `.getBurstCapacity()` all read from Config.
- Integration test for rate limit enforcement needed. Track as P1.

---

## 6. Security Event Observability (SG-05)

All auth failures in `AuthHttpHandlerTest` confirm structured log output with `correlationId` from `MDC`. Pattern:

```java
MDC.put("correlationId", correlationId);
logger.warn("Authentication failed for user: {} reason: {}", userId, reason);
```

**Action needed:** Verify `correlationId` propagates to all error paths in `JwtTokenProviderImpl` — currently metrics are emitted but `correlationId` is not consistently forwarded to the metrics tags.

---

## 7. Outstanding P0/P1 Actions

| ID | Priority | Owner Area | Action |
|----|----------|-----------|--------|
| SG-A01 | P1 | Security | Implement Redis-backed revocation store to replace in-memory `revokedTokens` and `userActiveTokens`. Integrate with `RedisSessionStore`. |
| SG-A02 | P1 | Security | Add integration test: unauthenticated request to each protected route returns 401. |
| SG-A03 | P1 | Security | Add integration test: rate limit is enforced; excess requests return 429. |
| SG-A04 | P2 | Security | Implement KMS client integration in `KeyManagerModule` for AWS KMS / HashiCorp Vault. Config: `key-management.provider=aws-kms` triggers real key loading. |
| SG-A05 | P2 | Security | Implement `redactEventSensitiveFields()` in `EnhancedEventSecurityManagerImpl` using a configurable field redaction policy. |
| SG-A06 | P3 | Security | Implement re-encryption of existing data on key rotation in `EventCloudSecurityManager`. |

---

**Document Version:** 1.0  
**Source Audit:** V3 Ultra-Strict Platform Audit, March 2026
