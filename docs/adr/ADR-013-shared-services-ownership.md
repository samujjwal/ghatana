# ADR-013: Shared-Services Ownership and Fate

**Status:** Accepted  
**Date:** 2026-03-21  
**Authors:** Platform Team  
**Phase:** BDY-3 (Boundary Implementation Plan)  
**Related:** `docs/BOUNDARY_IMPLEMENTATION_PLAN.md` Phase BDY-3

---

## Context

The `shared-services/` directory contains 5 thin service stubs (18 Java files total). The boundary audit raised the question: should these be real standalone services, moved to product ownership, or deleted?

### Current State (verified 2026-03-21)

| Service | Java Files | Build | Notes |
|---------|-----------|-------|-------|
| `auth-gateway` | 7 | ✅ | Has `AuthGatewayLauncher`, `TenantExtractor`, `RateLimiter`, `CredentialStore`, `JdbcCredentialStore`, `PasswordHasher`, `InMemoryCredentialStore` |
| `auth-service` | 1 | ✅ | Single stub file — appears to be a scaffold placeholder |
| `user-profile-service` | 5 | ✅ | Thin profile data model |
| `ai-registry` | 1 | ✅ | Single stub — AI model registry intent |
| `ai-inference-service` | 3 | ❌ | Build was commented out ("not stabilised") |
| `feature-store-ingest` | 1 | ✅ | Single file — ingest entrypoint scaffold |

---

## Decision

### 1. `auth-gateway` — KEEP + DEVELOP

**Decision:** Auth gateway remains in `shared-services/` as a real cross-product service.

**Rationale:**  
- Already has a `AuthGatewayLauncher` (ActiveJ application entry point), `TenantExtractor`, `RateLimiter`, and credential stores  
- Authentication/authorisation is genuinely cross-product — no single product owns it  
- All 7 products consume auth functionality via this gateway  
- Owner: **Security Team** (Platform sub-team)  
- Path to completion: full JWT validation, token refresh, multi-tenant session management

**Next steps:**  
- Add `OWNER.md` pointing to Security Team  
- Flesh out `JdbcCredentialStore` with Flyway-managed schema  
- Add to `scripts/check-cross-product-deps.sh` approved list

---

### 2. `auth-service` — CONSOLIDATE INTO `auth-gateway`

**Decision:** Delete `auth-service` stub. Its intent (authentication logic) belongs in `auth-gateway`.

**Rationale:**  
- 1-file stub with no real implementation  
- `auth-gateway` already covers the authentication concern  
- Having two "auth" services creates confusion and ownership ambiguity

**Action:** Remove `shared-services/auth-service` and delete its `include()` from `settings.gradle.kts` when the stub is the only content.

---

### 3. `user-profile-service` — KEEP as Cross-Product Service

**Decision:** Keep in `shared-services/` and develop into a proper cross-product service.

**Rationale:**  
- User profiles are used by multiple products (YAPPC, Finance, DCMAAR)  
- Centralised user profile storage avoids duplication  
- Owner: **Platform Team** (shared infra)

**Next steps:**  
- Define a `UserProfile` domain model with Protobuf contract in `contracts/`  
- Implement REST + gRPC endpoints  
- Add Flyway migration for persistence schema

---

### 4. `ai-registry` — CONSOLIDATE INTO `platform/java/ai-integration`

**Decision:** Delete `ai-registry` stub. AI model registry belongs in `platform/java/ai-integration`.

**Rationale:**  
- 1-file stub with no implementation  
- `platform/java/ai-integration` already has model registry functionality  
- `ai-registry` as a separate service creates unnecessary duplication

**Action:** Remove `shared-services/ai-registry` and its settings entry.

---

### 5. `ai-inference-service` — STABILISE or REMOVE

**Decision:** Fix the build, then evaluate.

**Rationale:**  
- Currently has 3 Java files and a broken build (commented out in settings)  
- If it implements a genuine inference routing/proxy service, it belongs here as cross-product infra  
- If it's just a thin wrapper around an external AI API, move the code into `platform/java/ai-integration`

**Decision timeline:** Platform Team evaluates by 2026-04-30. If no progress, delete.

---

### 6. `feature-store-ingest` — MOVE to `products/data-cloud`

**Decision:** Move to `products/data-cloud/services/feature-store-ingest/`.

**Rationale:**  
- Feature store is data-cloud's domain (ML feature engineering pipeline)  
- Data-Cloud team is the owner  
- No other product consumes feature store directly

---

## Consequences

### Immediate (by 2026-04-15)
- [ ] Add `OWNER.md` to `shared-services/auth-gateway` and `shared-services/user-profile-service`  
- [ ] Remove `shared-services/auth-service` stub + settings entry  
- [ ] Remove `shared-services/ai-registry` stub + settings entry  

### Near-term (by 2026-04-30)
- [ ] Move `shared-services/feature-store-ingest` to `products/data-cloud`  
- [ ] Decision on `ai-inference-service` — fix or delete

### Module count impact
- Removing 2 stubs (auth-service, ai-registry): -2 Gradle modules  
- Moving feature-store-ingest: net 0 (moves, not deleted)

---

## Rejected Alternatives

### Option: Move all shared-services to product ownership
**Rejected because:**  
- `auth-gateway` and `user-profile-service` are genuinely cross-product - no single product should own them  
- Product-ownership creates governance problems when multiple products need changes

### Option: Keep all as-is
**Rejected because:**  
- 1-file stubs add noise to the module graph  
- The build-broken `ai-inference-service` creates CI uncertainty  
- No progress without ownership clarity
