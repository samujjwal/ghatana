# Migration Gap Analysis & Implementation Plan
## ghatana → ghatana-new
### Date: February 5, 2026

---

## 📊 Executive Summary

| Metric | Legacy (ghatana) | Migrated (ghatana-new) | Gap |
|--------|------------------|------------------------|-----|
| **libs/java modules** | 46 modules | 14 modules | 32 modules |
| **libs/java Java files** | ~1,277 files | ~611 files | ~666 files (52%) |
| **AEP launcher files** | 6 files | 1 file | 5 files |
| **Data Cloud launcher files** | 9 files | 1 file | 8 files |
| **AEP modules** | 868 Java files | 537 files | ~331 files (38%) |
| **Data Cloud core** | 542 Java files | 476 files | ~66 files (12%) |
| **Test files** | ~435 tests | ~114 tests | ~321 tests (74%) |

**Overall Migration Coverage**: ~60%

---

## 🔴 PHASE 1: Critical Launcher Infrastructure (HIGH PRIORITY)

### 1.1 AEP Launcher HTTP Server

These files enable the AEP product to actually start and serve HTTP requests.

| # | Source File | Source Path | Target Path | Lines | Description |
|---|-------------|-------------|-------------|-------|-------------|
| 1 | `AepHttpServer.java` | `ghatana/products/aep/launcher/src/main/java/com/ghatana/aep/launcher/http/` | `ghatana-new/products/aep/launcher/src/main/java/com/ghatana/aep/launcher/http/` | ~354 | Main HTTP server with health, event, pattern, analytics endpoints |
| 2 | `TlsConfig.java` | `ghatana/products/aep/launcher/src/main/java/com/ghatana/aep/launcher/http/` | `ghatana-new/products/aep/launcher/src/main/java/com/ghatana/aep/launcher/http/` | ~80 | TLS/SSL configuration for HTTPS |
| 3 | `RateLimiter.java` | `ghatana/products/aep/launcher/src/main/java/com/ghatana/aep/launcher/http/` | `ghatana-new/products/aep/launcher/src/main/java/com/ghatana/aep/launcher/http/` | ~60 | Request rate limiting |
| 4 | `CorsConfig.java` | `ghatana/products/aep/launcher/src/main/java/com/ghatana/aep/launcher/http/` | `ghatana-new/products/aep/launcher/src/main/java/com/ghatana/aep/launcher/http/` | ~50 | CORS headers configuration |
| 5 | `AuthenticationFilter.java` | `ghatana/products/aep/launcher/src/main/java/com/ghatana/aep/launcher/http/` | `ghatana-new/products/aep/launcher/src/main/java/com/ghatana/aep/launcher/http/` | ~120 | Authentication middleware |

**Dependencies to create first**:
```java
// Create in: ghatana-new/products/aep/platform/src/main/java/com/ghatana/aep/
Aep.java              // Main facade class with AepConfig inner class/record
AepEngine.java        // Engine interface wrapping orchestrator/detection/workflow
```

**Implementation Steps**:
1. Create `Aep.java` facade with `AepConfig` record and `create()` factory method
2. Create `AepEngine.java` interface with `EventCloud`, `Event`, `DataPoint`, `PatternType`, `Pattern` inner types
3. Copy HTTP server files
4. Update imports to reference new platform packages
5. Update `AepLauncher.java` to use the new facade

---

### 1.2 Data Cloud Launcher HTTP Server

These files enable Data Cloud to serve HTTP and GraphQL requests.

| # | Source File | Source Path | Target Path | Lines | Description |
|---|-------------|-------------|-------------|-------|-------------|
| 1 | `DataCloudHttpServer.java` | `ghatana/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/` | `ghatana-new/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/` | ~315 | Main HTTP server with entity/event/query endpoints |
| 2 | `TlsConfig.java` | Same path | `ghatana-new/products/data-cloud/launcher/.../http/` | ~80 | TLS configuration |
| 3 | `RateLimiter.java` | Same path | `ghatana-new/products/data-cloud/launcher/.../http/` | ~60 | Rate limiting |
| 4 | `CorsConfig.java` | Same path | `ghatana-new/products/data-cloud/launcher/.../http/` | ~50 | CORS configuration |
| 5 | `AuthenticationFilter.java` | Same path | `ghatana-new/products/data-cloud/launcher/.../http/` | ~120 | Authentication |
| 6 | `SimpleGraphQLHandler.java` | `ghatana/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/` | `ghatana-new/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/graphql/` | ~400 | GraphQL endpoint handler |
| 7 | `UnifiedLauncher.java` | Same path | `ghatana-new/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/` | ~600 | Alternative unified launcher |
| 8 | `Main.java` | Same path | `ghatana-new/products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/` | ~200 | Alternative main entry point |

**Dependencies to create first**:
```java
// Create in: ghatana-new/products/data-cloud/platform/src/main/java/com/ghatana/datacloud/
DataCloud.java        // Main facade class with DataCloudConfig
DataCloudClient.java  // Client interface wrapping stores
```

---

## 🔴 PHASE 2: Core Libraries Migration (HIGH PRIORITY)

### 2.1 Operator Library (112 files) → `platform/java/operator/`

This is a critical library for pipeline and stream processing. **Must be migrated** for AEP to function.

| Package | Files | Description |
|---------|-------|-------------|
| `com.ghatana.libs.operator.aggregation` | 15 | Aggregation operators (sum, avg, count, etc.) |
| `com.ghatana.libs.operator.anomaly` | 12 | Anomaly detection operators |
| `com.ghatana.libs.operator.correlation` | 8 | Event correlation operators |
| `com.ghatana.libs.operator.filter` | 10 | Filter operators |
| `com.ghatana.libs.operator.join` | 6 | Join/merge operators |
| `com.ghatana.libs.operator.pattern` | 14 | Pattern matching operators |
| `com.ghatana.libs.operator.stream` | 18 | Stream processing operators |
| `com.ghatana.libs.operator.transform` | 12 | Transform operators |
| `com.ghatana.libs.operator.window` | 10 | Window operators |
| `com.ghatana.libs.operator.core` | 7 | Core operator infrastructure |

**Migration**:
```
Source: ghatana/libs/java/operator/src/main/java/com/ghatana/libs/operator/
Target: ghatana-new/platform/java/operator/src/main/java/com/ghatana/platform/operator/
```

**Steps**:
1. Create `platform/java/operator/build.gradle.kts`
2. Add to settings.gradle.kts: `includeIfExists(":platform:java:operator")`
3. Copy all Java files preserving structure
4. Update package from `com.ghatana.libs.operator` → `com.ghatana.platform.operator`
5. Update imports in dependent modules

---

### 2.2 Security Library (104 files) → `platform/java/security/`

Critical security infrastructure including encryption, JWT, RBAC.

| Package | Files | Description |
|---------|-------|-------------|
| `com.ghatana.libs.security.jwt` | 18 | JWT token handling, validation |
| `com.ghatana.libs.security.rbac` | 22 | Role-based access control |
| `com.ghatana.libs.security.encryption` | 14 | Encryption utilities |
| `com.ghatana.libs.security.audit` | 8 | Security audit logging |
| `com.ghatana.libs.security.event` | 12 | Event security processing |
| `com.ghatana.libs.security.policy` | 15 | Security policy engine |
| `com.ghatana.libs.security.session` | 8 | Session management |
| `com.ghatana.libs.security.core` | 7 | Core security infrastructure |

**Migration**:
```
Source: ghatana/libs/java/security/src/main/java/com/ghatana/libs/security/
Target: ghatana-new/platform/java/security/src/main/java/com/ghatana/platform/security/
```

**Note**: Some security code may already be in `platform/java/auth/`. Merge carefully.

---

### 2.3 Auth Platform Completion (+61 files)

Currently migrated: 25 files. Remaining: 61 files.

| Package | Migrated | Remaining | Description |
|---------|----------|-----------|-------------|
| `auth.identity` | 5 | 12 | Identity management |
| `auth.provider` | 8 | 18 | Auth providers (OAuth, SAML, OIDC) |
| `auth.token` | 6 | 14 | Token management |
| `auth.session` | 4 | 10 | Session handling |
| `auth.permission` | 2 | 7 | Permission models |

**Migration**:
```
Source: ghatana/libs/java/auth-platform/src/main/java/
Target: ghatana-new/platform/java/auth/src/main/java/ (merge with existing)
```

---

### 2.4 Domain Models Completion (+24 files)

Currently migrated: 71 files. Remaining: 24 files.

| Package | Migrated | Remaining | Description |
|---------|----------|-----------|-------------|
| `domain.event` | 15 | 6 | Event domain models |
| `domain.pattern` | 12 | 5 | Pattern domain models |
| `domain.agent` | 8 | 4 | Agent domain models |
| `domain.pipeline` | 10 | 3 | Pipeline domain models |
| `domain.query` | 8 | 2 | Query domain models |
| `domain.metadata` | 18 | 4 | Metadata domain models |

**Migration**:
```
Source: ghatana/libs/java/domain-models/src/main/java/
Target: ghatana-new/platform/java/domain/src/main/java/ (merge with existing)
```

---

## 🟠 PHASE 3: Runtime Infrastructure (MEDIUM PRIORITY)

### 3.1 Event Runtime (53 files) → `platform/java/event-cloud/` or new module

| Package | Files | Description |
|---------|-------|-------------|
| `event.runtime.processor` | 15 | Event processors |
| `event.runtime.dispatcher` | 8 | Event dispatchers |
| `event.runtime.handler` | 12 | Event handlers |
| `event.runtime.pipeline` | 10 | Event pipeline runtime |
| `event.runtime.store` | 8 | Event store integration |

**Migration**:
```
Source: ghatana/libs/java/event-runtime/src/main/java/
Target: ghatana-new/platform/java/event-cloud/src/main/java/ (merge)
   OR: ghatana-new/platform/java/event-runtime/src/main/java/ (new module)
```

---

### 3.2 Agent Framework (40 files) → `platform/java/domain/` or new module

| Package | Files | Description |
|---------|-------|-------------|
| `agent.framework.core` | 10 | Core agent framework |
| `agent.framework.lifecycle` | 8 | Agent lifecycle management |
| `agent.framework.registry` | 6 | Agent registry |
| `agent.framework.execution` | 10 | Agent execution engine |
| `agent.framework.communication` | 6 | Agent communication |

**Migration**:
```
Source: ghatana/libs/java/agent-framework/src/main/java/
Target: ghatana-new/platform/java/domain/src/main/java/ (merge)
   OR: ghatana-new/platform/java/agent/src/main/java/ (new module)
```

---

### 3.3 AI Platform Completion (+11 files)

Currently migrated: 30 files. Remaining: 11 files.

| Package | Migrated | Remaining | Description |
|---------|----------|-----------|-------------|
| `ai.embedding` | 8 | 3 | Text/vector embeddings |
| `ai.llm` | 10 | 4 | LLM integration |
| `ai.prompt` | 6 | 2 | Prompt engineering |
| `ai.rag` | 6 | 2 | RAG (Retrieval Augmented Generation) |

**Migration**:
```
Source: ghatana/libs/java/ai-platform/src/main/java/
Target: ghatana-new/platform/java/ai-integration/src/main/java/ (merge)
```

---

### 3.4 Common Utils Completion (+~40 files)

Currently migrated: ~25 files. Remaining: ~40 files.

| Package | Migrated | Remaining | Description |
|---------|----------|-----------|-------------|
| `common.encryption` | 2 | 8 | Encryption utilities |
| `common.pagination` | 3 | 4 | Pagination support |
| `common.json` | 4 | 6 | JSON utilities |
| `common.date` | 2 | 4 | Date/time utilities |
| `common.string` | 3 | 5 | String utilities |
| `common.collection` | 4 | 6 | Collection utilities |
| `common.io` | 2 | 4 | I/O utilities |
| `common.reflection` | 2 | 3 | Reflection utilities |

**Migration**:
```
Source: ghatana/libs/java/common-utils/src/main/java/
Target: ghatana-new/platform/java/core/src/main/java/ (merge)
```

---

## 🟠 PHASE 4: Data Cloud Extensions (MEDIUM PRIORITY)

### 4.1 HTTP API Handlers (13 files)

| # | File | Source Path | Target Path | Description |
|---|------|-------------|-------------|-------------|
| 1 | `SpotlightHandler.java` | `ghatana/products/data-cloud/http-api/` | `ghatana-new/products/data-cloud/platform/src/.../http/` | Spotlight search API |
| 2 | `FeedbackHandler.java` | Same | Same | User feedback API |
| 3 | `LineageHandler.java` | Same | Same | Data lineage API |
| 4 | `QualityHandler.java` | Same | Same | Data quality API |
| 5 | `CostHandler.java` | Same | Same | Cost tracking API |
| 6 | `PatternHandler.java` | Same | Same | Pattern management API |
| 7 | `GovernanceHandler.java` | Same | Same | Governance API |
| 8 | `PIIHandler.java` | Same | Same | PII detection API |
| 9 | `AutonomyHandler.java` | Same | Same | Autonomy API |
| 10 | `MemoryHandler.java` | Same | Same | Memory/cache API |
| 11 | `MetricsHandler.java` | Same | Same | Metrics API |
| 12 | `HealthHandler.java` | Same | Same | Health check API |
| 13 | `CommonHandler.java` | Same | Same | Shared handler utilities |

---

### 4.2 Distributed Module (8 files)

| # | File | Target Path | Description |
|---|------|-------------|-------------|
| 1 | `ClusterCoordinator.java` | `platform/src/.../distributed/` | Cluster coordination |
| 2 | `PartitionManager.java` | Same | Partition management |
| 3 | `ReplicationManager.java` | Same | Data replication |
| 4 | `MultiRegionCoordinator.java` | Same | Multi-region support |
| 5 | `ConsistencyManager.java` | Same | Consistency guarantees |
| 6 | `FailoverManager.java` | Same | Failover handling |
| 7 | `DistributedConfig.java` | Same | Configuration |
| 8 | `DistributedUtils.java` | Same | Utilities |

---

## 🟡 PHASE 5: Supporting Libraries (LOW PRIORITY)

### 5.1 Remaining Libraries to Migrate

| Library | Files | Target | Priority | Notes |
|---------|-------|--------|----------|-------|
| `activej-runtime` | 13 | `platform/java/runtime/` | LOW | Merge with existing |
| `activej-websocket` | 6 | `platform/java/http/` | LOW | WebSocket support |
| `event-cloud-contract` | 12 | `platform/java/event-cloud/` | LOW | Contracts |
| `event-cloud-factory` | 3 | `platform/java/event-cloud/` | LOW | Factory |
| `event-spi` | 4 | `platform/java/event-cloud/` | LOW | SPI |
| `connectors` | 10 | `platform/java/database/` | LOW | DB connectors |
| `context-policy` | 11 | `platform/java/governance/` | LOW | Policy engine |
| `ingestion` | 7 | `platform/java/runtime/` | LOW | Ingestion |
| `http-client` | 3 | `platform/java/http/` | LOW | HTTP client |
| `observability-clickhouse` | 4 | `platform/java/observability/` | LOW | ClickHouse |
| `observability-http` | 18 | `platform/java/observability/` | LOW | HTTP metrics |
| `operator-catalog` | 8 | `platform/java/workflow/` | LOW | Operator catalog |
| `redis-cache` | 4 | `platform/java/database/` | LOW | Redis |
| `state` | 5 | `platform/java/core/` | LOW | State mgmt |
| `audit` | 4 | `platform/java/observability/` | LOW | Audit |
| `validation-api/spi/common` | 8 | `platform/java/core/` | LOW | Validation |
| `workflow-api` | 6 | `platform/java/workflow/` | LOW | Workflow |
| `agent-api` | 14 | `platform/java/domain/` | LOW | Agent API |
| `agent-runtime` | 22 | `platform/java/runtime/` | LOW | Agent runtime |
| `types` | 36 | `platform/java/core/` | LOW | Types |

---

## 🟡 PHASE 6: Test Migration (MEDIUM PRIORITY)

### 6.1 Test Coverage Gap

| Module | Legacy | Migrated | Gap | Priority |
|--------|--------|----------|-----|----------|
| AEP platform | 123 | 8 | 115 (93%) | HIGH |
| AEP modules | 84 | 3 | 81 (96%) | MEDIUM |
| Data Cloud | 16 | 11 | 5 (31%) | LOW |
| libs/java | 212 | 92 | 120 (57%) | MEDIUM |

### 6.2 Critical Tests to Migrate

| Test Class | Source | Priority | Description |
|------------|--------|----------|-------------|
| `OrchestratorTest.java` | AEP | HIGH | Core orchestration tests |
| `DetectionEngineTest.java` | AEP | HIGH | Detection tests |
| `PatternMatcherTest.java` | AEP | HIGH | Pattern matching tests |
| `EventCloudTest.java` | libs | HIGH | Event cloud tests |
| `SecurityServiceTest.java` | libs | HIGH | Security tests |
| `OperatorTest.java` | libs | HIGH | Operator tests |

---

## 📋 File-by-File Implementation Checklist

### Phase 1 Checklist (Launchers)

```
[ ] 1.1 Create AEP Facade Classes
    [ ] products/aep/platform/src/main/java/com/ghatana/aep/Aep.java
    [ ] products/aep/platform/src/main/java/com/ghatana/aep/AepEngine.java

[ ] 1.2 Migrate AEP HTTP Server
    [ ] products/aep/launcher/.../http/AepHttpServer.java
    [ ] products/aep/launcher/.../http/TlsConfig.java
    [ ] products/aep/launcher/.../http/RateLimiter.java
    [ ] products/aep/launcher/.../http/CorsConfig.java
    [ ] products/aep/launcher/.../http/AuthenticationFilter.java

[ ] 1.3 Update AEP Launcher
    [ ] Update AepLauncher.java to use Aep facade

[ ] 1.4 Create Data Cloud Facade Classes
    [ ] products/data-cloud/platform/src/main/java/com/ghatana/datacloud/DataCloud.java
    [ ] products/data-cloud/platform/src/main/java/com/ghatana/datacloud/DataCloudClient.java

[ ] 1.5 Migrate Data Cloud HTTP Server
    [ ] products/data-cloud/launcher/.../http/DataCloudHttpServer.java
    [ ] products/data-cloud/launcher/.../http/TlsConfig.java
    [ ] products/data-cloud/launcher/.../http/RateLimiter.java
    [ ] products/data-cloud/launcher/.../http/CorsConfig.java
    [ ] products/data-cloud/launcher/.../http/AuthenticationFilter.java

[ ] 1.6 Migrate Data Cloud Additional
    [ ] products/data-cloud/launcher/.../graphql/SimpleGraphQLHandler.java
    [ ] products/data-cloud/launcher/.../UnifiedLauncher.java
    [ ] products/data-cloud/launcher/.../Main.java

[ ] 1.7 Update Data Cloud Launcher
    [ ] Update DataCloudLauncher.java to use DataCloud facade
```

### Phase 2 Checklist (Core Libraries)

```
[ ] 2.1 Create Operator Module
    [ ] platform/java/operator/build.gradle.kts
    [ ] Add to settings.gradle.kts
    [ ] Copy 112 files from libs/java/operator
    [ ] Update package names

[ ] 2.2 Create Security Module
    [ ] platform/java/security/build.gradle.kts
    [ ] Add to settings.gradle.kts
    [ ] Copy 104 files from libs/java/security
    [ ] Update package names
    [ ] Merge with auth if overlap

[ ] 2.3 Complete Auth Platform
    [ ] Merge 61 remaining files into platform/java/auth
    [ ] Update imports

[ ] 2.4 Complete Domain Models
    [ ] Merge 24 remaining files into platform/java/domain
    [ ] Update imports
```

### Phase 3 Checklist (Runtime)

```
[ ] 3.1 Migrate Event Runtime
    [ ] Merge 53 files into platform/java/event-cloud (or create event-runtime)

[ ] 3.2 Migrate Agent Framework
    [ ] Merge 40 files into platform/java/domain (or create agent module)

[ ] 3.3 Complete AI Platform
    [ ] Merge 11 remaining files into platform/java/ai-integration

[ ] 3.4 Complete Common Utils
    [ ] Merge ~40 files into platform/java/core
```

---

## 🎯 Recommended Execution Order

### Week 1: Enable Launchers
1. Day 1-2: Create Aep/AepEngine facade classes
2. Day 2-3: Migrate AEP HTTP server (5 files)
3. Day 3-4: Create DataCloud/DataCloudClient facade classes
4. Day 4-5: Migrate Data Cloud HTTP server (8 files)
5. Day 5: Integration testing

### Week 2: Core Libraries
1. Day 1-2: Create and populate operator module (112 files)
2. Day 3-4: Create and populate security module (104 files)
3. Day 5: Update dependencies and test

### Week 3: Complete Platform
1. Day 1: Complete auth platform (+61 files)
2. Day 2: Complete domain models (+24 files)
3. Day 3: Migrate event runtime (53 files)
4. Day 4: Migrate agent framework (40 files)
5. Day 5: Integration testing

### Week 4: Extensions & Tests
1. Day 1-2: Data Cloud extensions
2. Day 3-4: Critical test migration
3. Day 5: Final verification

---

## 📊 Migration Commands Reference

### Copy files preserving structure:
```bash
# Example for operator library
mkdir -p ghatana-new/platform/java/operator/src/main/java
cp -r ghatana/libs/java/operator/src/main/java/* \
      ghatana-new/platform/java/operator/src/main/java/
```

### Find and replace package names:
```bash
# Update package declarations
find ghatana-new/platform/java/operator -name "*.java" -exec \
    sed -i '' 's/package com.ghatana.libs.operator/package com.ghatana.platform.operator/g' {} \;

# Update imports
find ghatana-new -name "*.java" -exec \
    sed -i '' 's/import com.ghatana.libs.operator/import com.ghatana.platform.operator/g' {} \;
```

### Verify compilation:
```bash
cd ghatana-new
./gradlew :platform:java:operator:compileJava
./gradlew :products:aep:launcher:compileJava
./gradlew :products:data-cloud:launcher:compileJava
```

---

## ✅ Success Criteria

1. **Launchers Functional**: Both AEP and Data Cloud launchers can start HTTP servers
2. **All Platform Modules Compile**: All 14+ platform modules compile without errors
3. **All Product Modules Compile**: AEP and Data Cloud products build successfully
4. **Test Coverage**: Critical tests migrated and passing
5. **No libs/ Dependency**: ghatana-new has no libs/ directory

---

## 📝 Notes

1. **Package Naming Convention**:
   - Legacy: `com.ghatana.libs.{module}` or `com.ghatana.{product}.{module}`
   - New: `com.ghatana.platform.{module}` or `com.ghatana.products.{product}.{module}`

2. **Merge vs New Module**:
   - Prefer merging into existing modules when logical
   - Create new modules only for large, distinct functionality (e.g., operator, security)

3. **Dependency Direction**:
   - Platform modules should not depend on product modules
   - Product modules can depend on platform modules
   - Launchers depend on both platform and product platform modules

4. **Testing Strategy**:
   - Migrate unit tests alongside code
   - Integration tests may need adjustment for new structure

---

**Document Version**: 1.0
**Created**: February 5, 2026
**Author**: GitHub Copilot
