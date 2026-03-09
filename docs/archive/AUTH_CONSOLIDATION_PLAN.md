# Auth Module Consolidation Plan

## Overview

**Status:** BLOCKED - Pending Domain Models Migration  
**Priority:** HIGH (after domain migration)  
**Estimated Effort:** 1 week  

---

## Problem Statement

The auth modules (`auth/` and `auth-platform/`) contain significant duplication but cannot be consolidated yet due to tight coupling with domain model classes that reside in `domain-models/` module.

### Current State
- **auth/**: 22 files - Basic auth interfaces and implementations
- **auth-platform/**: 92 files - Full auth platform with JPA, Redis adapters
- **Duplicates:** AuthenticationService, JwtTokenProvider, SecurityContext, etc.

---

## Blockers

### Domain Model Dependencies

The `auth-platform` module depends on these domain types from `domain-models/`:

```java
// From auth-platform/core/src/main/java/com/ghatana/auth/adapter/jpa/JpaTokenRepository.java
import com.ghatana.domain.auth.ClientId;
import com.ghatana.domain.auth.TenantId;
import com.ghatana.domain.auth.Token;
import com.ghatana.domain.auth.TokenId;
import com.ghatana.domain.auth.UserId;

// From auth-platform/core/.../jpa/TokenEntity.java
import com.ghatana.domain.auth.Token;
import com.ghatana.domain.auth.TokenType;
```

### Files Affected by Domain Dependencies

| File | Domain Dependencies |
|------|-------------------|
| JpaTokenRepository.java | Token, TokenId, ClientId, TenantId, UserId |
| JpaUserRepository.java | User, UserId, TenantId |
| TokenEntity.java | Token (field types) |
| UserEntity.java | User (field types) |
| RedisTokenStore.java | Token, TokenId, ClientId, TenantId |
| RedisSessionStore.java | Session, SessionId |

---

## Consolidation Strategy

### Phase 1: Domain Models Migration (PREREQUISITE)

**Must complete first:**
1. Migrate `domain-models/` to `products/aep/platform/domain/` or `platform/java/domain/`
2. Update all imports in auth-platform to use new package paths
3. Verify domain models are accessible to both auth modules

### Phase 2: Auth Consolidation (After Domain Migration)

**Target Structure:**
```
platform/java/auth/
├── core/
│   ├── port/                    # Interfaces (from auth-platform)
│   │   ├── AuthorizationService.java
│   │   ├── JwtTokenProvider.java
│   │   ├── SessionStore.java
│   │   ├── TokenStore.java
│   │   └── UserRepository.java
│   ├── service/                 # Implementations
│   │   └── AuthenticationServiceImpl.java
│   ├── security/                # Security context
│   │   ├── SecurityContext.java
│   │   └── SecurityContextHolder.java
│   └── exception/               # Auth exceptions
├── adapter/
│   ├── jpa/                     # JPA implementations
│   │   ├── JpaTokenRepository.java
│   │   ├── JpaUserRepository.java
│   │   ├── TokenEntity.java
│   │   └── UserEntity.java
│   ├── redis/                   # Redis implementations
│   │   ├── RedisTokenStore.java
│   │   └── RedisSessionStore.java
│   └── memory/                  # In-memory (for testing)
│       ├── InMemoryTokenStore.java
│       └── InMemoryUserRepository.java
└── http/
    └── AuthHttpHandler.java
```

### Phase 3: Merge Strategy

**From auth/ (22 files):**
- Keep: ABAC/RBAC policy engines, OIDC support, audit
- Discard: Duplicates of interfaces (use auth-platform versions)

**From auth-platform/ (92 files):**
- Keep: All adapter implementations, HTTP handlers, comprehensive interfaces
- Discard: None - this is the more complete implementation

**Merge Actions:**
1. Move auth-platform files to consolidated location
2. Update package declarations: `com.ghatana.auth.*` → `com.ghatana.platform.auth.*`
3. Merge unique features from auth/ into consolidated module
4. Deprecate original auth/ module

---

## Implementation Checklist

### Pre-Migration (Domain Models)
- [ ] Migrate domain-models module
- [ ] Update auth-platform imports
- [ ] Verify no circular dependencies

### Consolidation
- [ ] Create `platform/java/auth/` structure
- [ ] Copy auth-platform files (92 files)
- [ ] Update package declarations
- [ ] Merge unique auth/ features:
  - [ ] AbacPolicyEngine.java
  - [ ] SecurityAuditService.java
  - [ ] OidcConfiguration.java
  - [ ] OidcTokenVerifier.java
- [ ] Add ActiveJ Promise to async methods
- [ ] Update build.gradle.kts dependencies
- [ ] Run compilation check
- [ ] Run tests

### Cleanup
- [ ] Mark original auth/ as deprecated
- [ ] Mark original auth-platform/ as deprecated
- [ ] Update migration inventory

---

## Dependencies to Add

```kotlin
// platform/java/auth/build.gradle.kts
dependencies {
    api(project(":platform:java:core"))
    
    // Domain models (after migration)
    api(project(":platform:java:domain")) // or products/aep/platform/domain
    
    // ActiveJ
    api("io.activej:activej-promise:6.0-beta2")
    api("io.activej:activej-http:6.0-beta2")
    
    // JPA
    api("jakarta.persistence:jakarta.persistence-api:3.1.0")
    
    // Redis
    api("redis.clients:jedis:5.0.0")
    
    // JWT
    api("io.jsonwebtoken:jjwt-api:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")
    
    // Logging
    api("org.slf4j:slf4j-api:2.0.9")
}
```

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Domain model migration breaks auth | Medium | High | Comprehensive tests before consolidation |
| Circular dependencies | Low | Medium | Clear module boundaries |
| JWT library version conflicts | Medium | Medium | Standardize on jjwt 0.12.3 |
| Redis client version conflicts | Low | Low | Use jedis 5.0.0 consistently |

---

## Expected Outcome

**Before:**
- auth/: 22 files
- auth-platform/: 92 files
- **Total: 114 files with duplication**

**After:**
- platform/java/auth/: ~70 files
- **Reduction: 39% (44 files eliminated)**

---

## Timeline

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Domain models migration | 3-4 days | None |
| Auth consolidation | 2-3 days | Domain migration complete |
| Testing & verification | 1 day | Consolidation complete |
| **Total** | **6-8 days** | - |

---

## Next Steps

1. **Immediate:** Complete domain-models migration
2. **Then:** Execute this consolidation plan
3. **Finally:** Migrate dependent services (api-gateway, event-service)

---

## Related Documents

- `/home/samujjwal/Developments/ghatana-new/MIGRATION_INVENTORY.md`
- `/home/samujjwal/Developments/ghatana-new/CONSOLIDATION_PROGRESS_REPORT.md`
- `/home/samujjwal/Developments/ghatana-new/CODEBASE_ANALYSIS_DUPLICATES.md`
