# Codebase Analysis: Duplicates, Inconsistencies & Shared Library Usage

**Analysis Date:** 2026-02-04  
**Scope:** ghatana/libs/java/ (44 modules)  
**Purpose:** Identify consolidation opportunities before migration

---

## 1. DUPLICATE IMPLEMENTATIONS 🔴

### 1.1 Validation Framework (DUPLICATE)

**Problem:** Multiple validation modules with overlapping functionality

```
libs/java/
├── validation/ (28 files) - Basic validators
├── validation-api/ (15 files) - API interfaces  
├── validation-common/ (12 files) - Common utilities
├── validation-spi/ (11 files) - Service provider interfaces
└── config-runtime/src/main/java/.../validation/ConfigValidator.java
```

**Issues:**
- 4 separate modules for validation (~66 files total)
- Artificial separation creates circular dependencies
- Same validation logic duplicated across modules
- config-runtime has its own ConfigValidator instead of using validation/

**Recommendation:** Consolidate into single `platform/java/core/validation/` module

---

### 1.2 Authentication Services (DUPLICATE)

**Problem:** Multiple auth implementations

```
libs/java/
├── auth/ (38 files) - Basic auth
│   ├── PasswordHasher.java
│   ├── JwtTokenProvider.java
│   └── AuthorizationService.java
├── auth-platform/ (92 files) - Extended auth platform
│   ├── core/src/main/java/.../auth/service/impl/AuthenticationServiceImpl.java
│   ├── core/src/main/java/.../auth/service/impl/JwtTokenProviderImpl.java
│   └── core/src/main/java/.../auth/util/PasswordHasher.java
└── security/ (110 files) - Security gateway
```

**Issues:**
- `auth/` has PasswordHasher, JwtTokenProvider
- `auth-platform/` has duplicate implementations (AuthenticationServiceImpl, JwtTokenProviderImpl, PasswordHasher)
- `security/` overlaps with auth functionality
- 3 different PasswordHasher implementations

**Recommendation:** Consolidate auth into single module, security should use auth module

---

### 1.3 Observability (FRAGMENTED)

**Problem:** Split across 3 modules

```
libs/java/
├── observability/ (121 files) - Core metrics/tracing
├── observability-clickhouse/ (16 files) - ClickHouse storage
└── observability-http/ (29 files) - HTTP endpoints
```

**Issues:**
- 3 modules for related functionality (166 files total)
- Should be single module with subpackages
- ClickHouse storage should be plugin, not separate module

**Recommendation:** Single `platform/java/observability/` with storage plugins

---

### 1.4 Multiple Service Implementations

**Found:** Various duplicate service patterns

| Service | Count | Locations |
|---------|-------|-----------|
| ValidationService | 2 | validation/, validation-spi/ |
| RateLimiter | 2 | ai-platform/gateway/, shared-services/ |
| TokenProvider | 2 | auth/, auth-platform/ |
| PasswordHasher | 2 | auth/, auth-platform/ |

---

## 2. INCONSISTENT LIBRARY USAGE 🟡

### 2.1 JSON Processing (INCONSISTENT)

**Found 186 matches across 89 files:**

```java
// Mix of libraries found:
import com.fasterxml.jackson.databind.*;  // Most common
import com.google.gson.*;                  // Some files
import org.json.*;                         // Few files
```

**Inconsistent Usage:**
- 85% use Jackson
- 10% use Gson (inconsistent)
- 5% use org.json (should migrate to Jackson)

**Files using Gson (should migrate):**
- Need to search products/ for Gson usage
- domain-models/ has some Gson usage

**Recommendation:** Standardize on Jackson for all JSON processing

---

### 2.2 Logging (MOSTLY CONSISTENT)

**Found 447 matches across 243 files:**

```java
// 95% consistent:
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

**Minor inconsistency:**
- Some test files may use different logging
- Overall good consistency with SLF4J

---

### 2.3 HTTP Client Libraries (INCONSISTENT)

**Multiple HTTP clients found:**

```java
// Found patterns:
import io.activej.http.*;           // Our standard (good)
import java.net.http.*;            // Java 11 client (inconsistent)
import org.apache.http.*;          // Apache client (inconsistent)
import okhttp3.*;                  // OkHttp (inconsistent)
```

**Inconsistency:**
- Should standardize on ActiveJ HTTP (already have http-server/ module)
- Some modules use different HTTP clients

**Recommendation:** Migrate all to ActiveJ HTTP client

---

### 2.4 Database/Storage (INCONSISTENT)

**Multiple database approaches:**

```
libs/java/
├── database/ (40 files) - Core database abstractions
├── redis-cache/ (17 files) - Redis caching
├── state/ (15 files) - State management (should be in data-cloud)
└── ai-platform/feature-store/.../RedisFeatureCacheAdapter.java
```

**Issues:**
- ai-platform has its own Redis adapter instead of using redis-cache/
- state/ module duplicates some database functionality
- Not using shared database abstractions consistently

---

## 3. NOT USING SHARED LIBRARIES 🔴

### 3.1 AI Platform (NOT USING SHARED)

**Problem:** ai-platform/ duplicates shared functionality

```
ai-platform/
├── feature-store/.../RedisFeatureCacheAdapter.java
│   └── SHOULD USE: redis-cache/ module
├── gateway/.../RateLimiter.java  
│   └── SHOULD USE: shared RateLimiter or create shared one
├── observability/.../CostTracker.java
│   └── SHOULD USE: observability/ module
└── serving/http/AIHttpRoutes.java
    └── SHOULD USE: http-server/ module patterns
```

**Files not using shared libs:**
- ai-platform/gateway/RateLimiter.java (duplicate)
- ai-platform/feature-store/RedisFeatureCacheAdapter.java (should use redis-cache)

---

### 3.2 Agent Framework (PARTIALLY USING SHARED)

**Good:**
- Uses validation/ for validation
- Uses types/ for type definitions

**Issues:**
- Has its own memory/EventLogMemoryStore instead of using data-cloud/
- Has own LLMGatewayAdapter that duplicates ai-integration/ functionality

---

### 3.3 Config Runtime (NOT USING SHARED)

```
config-runtime/
└── src/main/java/.../validation/ConfigValidator.java
    └── DUPLICATES: validation/ module functionality
```

**Issue:** Has its own ConfigValidator instead of using validation/ module

---

### 3.4 Domain Models (MIXED USAGE)

**Good:**
- Uses Jackson for JSON (consistent)
- Uses validation/ for validation

**Issues:**
- Has domain-models/src/main/java/.../jackson/ custom serializers
  - Should use shared JSON utilities from common-utils/
- PipelineSpecValidator duplicates validation/ functionality

---

## 4. CONSOLIDATION OPPORTUNITIES

### High Priority (Consolidate Before Migration)

| Issue | Files | Action |
|-------|-------|--------|
| validation* modules | 66 | Consolidate to single module |
| auth + auth-platform | 130 | Merge into single auth module |
| observability* modules | 166 | Single module with subpackages |
| ai-platform RateLimiter | 1 | Use shared RateLimiter |
| ai-platform Redis adapter | 1 | Use redis-cache module |

**Potential reduction:** ~360 files → ~200 files (44% reduction)

### Medium Priority (Migrate Then Refactor)

| Issue | Files | Action |
|-------|-------|--------|
| JSON library standardization | ~50 | Migrate Gson/org.json to Jackson |
| HTTP client standardization | ~30 | Migrate to ActiveJ HTTP |
| State module relocation | 15 | Move to data-cloud product |

### Low Priority (Nice to Have)

| Issue | Files | Action |
|-------|-------|--------|
| Custom Jackson serializers | ~10 | Use common-utils/JsonUtils |
| Duplicate service interfaces | ~20 | Consolidate service APIs |

---

## 5. MIGRATION IMPACT

### If We Consolidate First:

**Pros:**
- ~44% reduction in files to migrate (360 → 200)
- Cleaner architecture in ghatana-new
- Fewer modules to maintain
- Eliminates circular dependencies

**Cons:**
- Additional effort before migration
- Risk of breaking existing code
- More testing required

### If We Migrate As-Is:

**Pros:**
- Faster initial migration
- Lower risk
- Easier to test (1:1 copy)

**Cons:**
- Migrates technical debt
- More modules in ghatana-new
- Harder to refactor later
- ~8,540 files instead of ~8,200 files

---

## 6. RECOMMENDATION

### Pre-Migration Consolidation (Recommended)

**Phase 0: Consolidate libs/java/ (2 weeks)**

1. **Merge validation modules** (3 days)
   - validation + validation-api + validation-common + validation-spi → single validation/

2. **Merge auth modules** (5 days)
   - auth + auth-platform → single auth/
   - Remove duplicate implementations

3. **Merge observability** (3 days)
   - observability + observability-clickhouse + observability-http → single observability/

4. **Fix AI platform** (2 days)
   - Use redis-cache/ instead of custom adapter
   - Use shared RateLimiter

5. **Fix config-runtime** (1 day)
   - Use validation/ instead of ConfigValidator

**Result:** ~360 fewer files to migrate

---

## 7. FILES REQUIRING ATTENTION

### Critical Duplicates (Fix First):
1. `auth/PasswordHasher.java` vs `auth-platform/.../PasswordHasher.java`
2. `auth/JwtTokenProvider.java` vs `auth-platform/.../JwtTokenProviderImpl.java`
3. `validation/` vs `config-runtime/.../ConfigValidator.java`
4. `ai-platform/.../RateLimiter.java` vs `shared-services/RateLimiter.java`
5. `ai-platform/.../RedisFeatureCacheAdapter.java` vs `redis-cache/`

### Inconsistent Libraries (Standardize):
1. Gson usage → Migrate to Jackson
2. OkHttp/Apache HTTP → Migrate to ActiveJ HTTP
3. Custom Jackson serializers → Use JsonUtils

### Not Using Shared (Refactor):
1. ai-platform/gateway/RateLimiter.java
2. ai-platform/feature-store/RedisFeatureCacheAdapter.java
3. config-runtime/validation/ConfigValidator.java
4. domain-models/jackson/ custom serializers

---

## SUMMARY

| Category | Count | Impact |
|----------|-------|--------|
| Duplicate modules | 4 sets | High - Consolidate before migration |
| Inconsistent libraries | 3 types | Medium - Standardize during migration |
| Not using shared | 6 files | Medium - Refactor to use shared |
| **Total opportunities** | **~360 files** | **44% reduction potential** |

**Recommendation:** Consolidate high-priority duplicates before migration to reduce scope by ~360 files.
