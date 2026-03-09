# Module Migration Mapping

## Analysis of Original libs/java (44 modules, 1,200+ Java files)

### Classification

#### 🟢 TRUE PLATFORM (Migrate to platform/)

These are genuinely shared across multiple products:

| Original Module   | Files | New Location                | Status                              |
| ----------------- | ----- | --------------------------- | ----------------------------------- |
| common-utils      | 65    | platform/java/core          | ✅ Migrated (core utilities)        |
| types             | 36    | platform/java/core          | ✅ Migrated (Id, Timestamp)         |
| domain-models     | 95    | platform/java/core          | ⏳ Partial (generic types only)     |
| database          | 46    | platform/java/database      | ✅ Migrated (ConnectionPool, Cache) |
| redis-cache       | 4     | platform/java/database      | ✅ Migrated (InMemoryCache)         |
| http-server       | 21    | platform/java/http          | ✅ Migrated (JsonServlet)           |
| http-client       | 3     | platform/java/http          | ⏳ Pending                          |
| activej-runtime   | 13    | platform/java/http          | ⏳ Pending                          |
| auth              | 22    | platform/java/auth          | ✅ Migrated (JWT, Password)         |
| security          | 104   | platform/java/auth          | ⏳ Partial (RBAC pending)           |
| observability     | 89    | platform/java/observability | ✅ Migrated (Metrics, Health)       |
| audit             | 4     | platform/java/observability | ⏳ Pending                          |
| testing           | 59    | platform/java/testing       | ✅ Migrated (TestFixture)           |
| validation        | 18    | platform/java/core          | ✅ Migrated (ValidationResult)      |
| validation-api    | 5     | platform/java/core          | ✅ Consolidated                     |
| validation-common | 2     | platform/java/core          | ✅ Consolidated                     |
| validation-spi    | 1     | platform/java/core          | ✅ Consolidated                     |

#### 🟡 PRODUCT-SPECIFIC (Move to products/\*/platform/)

These are used by specific products and should NOT be in global platform:

| Original Module      | Files | Product Owner    | New Location                                      | Status                                |
| -------------------- | ----- | ---------------- | ------------------------------------------------- | ------------------------------------- |
| operator             | 112   | AEP              | products/aep/platform/java/operators              | ✅ Core migrated                      |
| operator-catalog     | 8     | AEP              | products/aep/platform/java/operators              | ⏳ Pending                            |
| event-runtime        | 53    | AEP/Data-Cloud   | products/aep/platform/java/events                 | ✅ Core migrated                      |
| event-cloud          | 13    | AEP/Data-Cloud   | products/aep/platform/java/events                 | ⏳ Pending                            |
| event-cloud-contract | 12    | AEP/Data-Cloud   | products/aep/platform/java/events                 | ⏳ Pending                            |
| event-cloud-factory  | 3     | AEP/Data-Cloud   | products/aep/platform/java/events                 | ⏳ Pending                            |
| event-spi            | 4     | AEP/Data-Cloud   | products/aep/platform/java/events                 | ⏳ Pending                            |
| agent-api            | 14    | AEP              | products/aep/platform/java/agents                 | Truly shared agent abstractions       |
| agent-framework      | 40    | AEP              | products/aep/platform/java/agents                 | Agent lifecycle management            |
| agent-runtime        | 22    | AEP              | products/aep/platform/java/agents                 | Agent execution engine                |
| ai-integration       | 33    | Shared Services  | products/shared-services/platform/java/ai         | Combined with ai-platform             |
| ai-platform          | 41    | Shared Services  | products/shared-services/platform/java/ai         | Combined into shared AI module        |
| auth-platform        | 86    | Security-Gateway | products/security-gateway/platform/java/auth      |                                       |
| context-policy       | 11    | Flashit          | products/flashit/platform/java/context            |                                       |
| governance           | 18    | Data-Cloud       | products/data-cloud/platform/java/governance      |                                       |
| ingestion            | 7     | Data-Cloud       | products/data-cloud/platform/java/ingestion       |                                       |
| connectors           | 10    | Shared Services  | products/shared-services/platform/java/connectors | Shared pattern for data/event sources |
| plugin-framework     | 27    | Multiple         | platform/java/plugin (if truly shared)            |                                       |
| workflow-api         | 6     | AEP              | products/aep/platform/java/workflow               | Pipeline/workflow orchestration       |
| config-runtime       | 16    | Multiple         | platform/java/config                              |                                       |
| state                | 5     | Data-Cloud       | products/data-cloud/platform/java/storage         | Storage state management              |

#### 🔴 CONSOLIDATE/REMOVE (Redundant or over-abstracted)

| Original Module             | Files | Action                                 |
| --------------------------- | ----- | -------------------------------------- |
| observability-http          | 18    | Merge into platform/java/observability |
| observability-clickhouse    | 4     | Merge into platform/java/observability |
| activej-websocket           | 6     | Merge into platform/java/http          |
| architecture-tests          | 2     | Merge into platform/java/testing       |
| platform-architecture-tests | 1     | Merge into platform/java/testing       |

## Feature Parity Checklist

### Platform Core Features ✅ COMPLETE

- [x] Preconditions/Validation utilities - **TESTED**
- [x] Result type (Success/Failure) - **TESTED**
- [x] Id types (StringId, UuidId) - **TESTED**
- [x] Timestamp utilities - **TESTED**
- [x] ValidationResult aggregation - **TESTED**
- [x] Feature flags (FeatureService) - **TESTED**
- [x] JSON utilities (JsonUtils) - **MIGRATED + 20 TESTS**
- [x] String utilities (StringUtils) - **MIGRATED + 39 TESTS**
- [x] Exception framework (PlatformException, ErrorCode, CommonErrorCode) - **MIGRATED + TESTED**
- [x] DateTime utilities (DateTimeUtils) - **MIGRATED + 30 TESTS**
- [x] Pair utility (Pair) - **MIGRATED + 10 TESTS**

**Status**: 11/11 features complete with **119 tests**

### Platform Database Features ✅ COMPLETE

- [x] Connection pooling (HikariCP) - **TESTED**
- [x] DataSource configuration - **TESTED**
- [x] In-memory cache with TTL - **MIGRATED + 13 TESTS**
- [x] Cache interface - **TESTED**
- [x] Redis cache configuration (RedisCacheConfig) - **MIGRATED + 16 TESTS**

**Status**: 5/5 features complete with **29 tests**

### Platform HTTP Features ✅ COMPLETE

- [x] JSON response wrapper - **TESTED**
- [x] JSON servlet base class - **TESTED**
- [x] HTTP client configuration (HttpClientConfig) - **MIGRATED + 13 TESTS**

**Status**: 3/3 features complete with **13 tests**

- [ ] CORS support

### Platform Auth Features

- [x] JWT token creation/validation
- [x] Password hashing (BCrypt)
- [ ] RBAC utilities
- [ ] Permission checking
- [ ] Session management

### Platform Observability Features

- [x] Metrics registry (Micrometer)
- [x] Health check types
- [ ] Distributed tracing
- [ ] Structured logging
- [ ] Audit logging

### Platform Testing Features

- [x] Test fixture interface
- [ ] Test containers utilities
- [ ] Mock builders
- [ ] Assertion helpers

## Migration Priority

### Phase 2A: Complete Platform Core (HIGH PRIORITY)

1. Migrate remaining common-utils (JSON, String, Collection utilities)
2. Migrate generic domain-models (not product-specific)
3. Add config-runtime to platform

### Phase 2B: Complete Platform Modules (MEDIUM PRIORITY)

1. Complete platform/java/database (Redis, transactions)
2. Complete platform/java/http (client, interceptors)
3. Complete platform/java/auth (RBAC, permissions)
4. Complete platform/java/observability (tracing, audit)

### Phase 2C: Product Platforms (MEDIUM PRIORITY)

1. Create products/aep/platform with operators, events, ai
2. Create products/data-cloud/platform with governance, ingestion
3. Create products/virtual-org/platform with agents, workflow
4. Create products/security-gateway/platform with auth-platform

### Phase 2D: Cleanup (LOW PRIORITY)

1. Remove redundant modules
2. Update all consumers
3. Delete old libs/java structure

## Files NOT to Migrate (Unnecessary)

- Build artifacts (_/build/_)
- IDE files (\*.iml, .idea/)
- Empty modules
- Deprecated code marked for removal
- Test fixtures that duplicate platform/testing
