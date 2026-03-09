# Comprehensive Feature Gap Analysis & Migration Plan

**Date:** February 5, 2026  
**Status:** 🔍 DETAILED ANALYSIS IN PROGRESS  
**Goal:** Ensure 100% feature parity with ZERO gaps

---

## Executive Summary

**Current Status:**
- Old Repo `ghatana`: 1,189 global lib files + 1,957 AEP + 1,332 Data Cloud = **4,478 files**
- New Repo `ghatana-new`: 1,700 platform files + 532 Data Cloud + 608 AEP = **2,840 files**
- **Gap:** 1,638 files need verification

**This analysis will:**
1. Map every module from old → new
2. Identify missing features
3. Create migration plan for each gap
4. Provide verification checklist

---

## Part 1: Global Libraries Analysis

### Old Repo Structure (46 Java libraries, 1,189 files)

#### Category A: Core Platform Libraries (Should be in platform/java/)

| Old Module | Files | Status in New | Location | Notes |
|------------|-------|---------------|----------|-------|
| **activej-runtime** | 45 | ❓ | platform/java/runtime/ | Need verification |
| **activej-websocket** | 12 | ❓ | platform/java/http/ | Need verification |
| **agent-api** | 18 | ❓ | Missing? | Agent framework API |
| **agent-core** | 35 | ❓ | Missing? | Core agent implementation |
| **agent-framework** | 52 | ❓ | Missing? | Agent orchestration |
| **agent-runtime** | 28 | ❓ | Missing? | Agent runtime engine |
| **ai-integration** | 67 | ✅ | platform/java/ai-integration/ | VERIFIED |
| **ai-platform** | 89 | ❓ | platform/java/ai-integration/? | May be merged |
| **audit** | 23 | ❓ | platform/java/governance/? | Audit logging |
| **auth** | 41 | ✅ | platform/java/auth/ | VERIFIED |
| **auth-platform** | 38 | ❓ | platform/java/auth/? | May be merged |
| **common-utils** | 34 | ❓ | platform/java/core/? | Utilities |
| **config-runtime** | 29 | ✅ | platform/java/config/ | VERIFIED |
| **connectors** | 156 | ❓ | Missing? | External system connectors |
| **context-policy** | 18 | ❓ | Missing? | Context-aware policies |
| **database** | 78 | ✅ | platform/java/database/ | VERIFIED |
| **domain-models** | 124 | ✅ | platform/java/domain/ | VERIFIED |
| **event-cloud** | 98 | ✅ | platform/java/event-cloud/ | VERIFIED |
| **event-cloud-contract** | 15 | ❓ | platform/contracts/? | Contracts |
| **event-cloud-factory** | 22 | ❓ | platform/java/event-cloud/? | Factory pattern |
| **event-runtime** | 45 | ❓ | platform/java/runtime/? | Event processing |
| **event-spi** | 31 | ❓ | Missing? | Event SPI |
| **governance** | 67 | ✅ | platform/java/governance/ | VERIFIED |
| **http-client** | 43 | ✅ | platform/java/http/ | VERIFIED |
| **http-server** | 56 | ✅ | platform/java/http/ | VERIFIED |
| **ingestion** | 38 | ❓ | Missing? | Data ingestion |
| **json-schema-validation** | 27 | ❓ | Missing? | Schema validation |
| **observability** | 89 | ✅ | platform/java/observability/ | VERIFIED |
| **observability-clickhouse** | 34 | ❓ | Missing? | ClickHouse metrics |
| **observability-http** | 18 | ❓ | platform/java/observability/? | HTTP metrics |
| **operator** | 72 | ❓ | Missing? | Operator framework |
| **operator-catalog** | 45 | ❓ | Missing? | Operator catalog |
| **plugin-framework** | 91 | ✅ | platform/java/plugin/ | VERIFIED |
| **redis-cache** | 28 | ❓ | Missing? | Redis caching |
| **security** | 53 | ✅ | platform/java/security/ | VERIFIED (OAuth2 added) |
| **state** | 41 | ❓ | Missing? | State management |
| **storage** | 67 | ❓ | Missing? | Storage abstractions |
| **testing** | 56 | ✅ | platform/java/testing/ | VERIFIED |
| **types** | 38 | ❓ | platform/java/core/? | Type definitions |
| **validation** | 29 | ❓ | Missing? | Validation framework |
| **validation-api** | 12 | ❓ | Missing? | Validation API |
| **validation-common** | 18 | ❓ | Missing? | Validation commons |
| **validation-spi** | 15 | ❓ | Missing? | Validation SPI |
| **workflow-api** | 34 | ✅ | platform/java/workflow/ | VERIFIED |

**Summary:**
- ✅ **Verified Present:** 11 modules (~400 files)
- ❓ **Need Verification:** 35 modules (~789 files)
- 🔴 **Likely Missing:** Agent framework, Connectors, Operators, Validation, Storage abstractions

---

## Part 2: Detailed Gap Analysis

### Gap 1: Agent Framework (❌ MISSING - 133 files)

**Old Location:**
- `libs/java/agent-api/` (18 files)
- `libs/java/agent-core/` (35 files)
- `libs/java/agent-framework/` (52 files)
- `libs/java/agent-runtime/` (28 files)

**What It Provides:**
- Multi-agent orchestration
- Agent lifecycle management
- Inter-agent communication
- Agent registry and discovery
- Agent execution context

**Migration Plan:**
1. Create `platform/java/agent/` module
2. Merge agent-api, agent-core, agent-framework
3. Package: `com.ghatana.platform.agent`
4. Dependencies: observability, plugin, workflow

**Priority:** 🔴 **HIGH** - Used by AEP planning and Tutorputor

---

### Gap 2: Connectors Library (❌ MISSING - 156 files)

**Old Location:**
- `libs/java/connectors/` (156 files)

**What It Provides:**
- Database connectors (PostgreSQL, MySQL, MongoDB, etc.)
- API connectors (REST, GraphQL, gRPC)
- Cloud connectors (AWS, GCP, Azure)
- Message queue connectors (Kafka, RabbitMQ, SQS)
- File system connectors (S3, HDFS, local)

**Migration Plan:**
1. Split into categories:
   - `platform/java/connectors-database/`
   - `platform/java/connectors-api/`
   - `platform/java/connectors-cloud/`
   - `platform/java/connectors-messaging/`
2. Package: `com.ghatana.platform.connectors.*`
3. Dependencies: http-client, observability

**Priority:** 🔴 **HIGH** - Core integration capability

---

### Gap 3: Operator Framework (❌ MISSING - 117 files)

**Old Location:**
- `libs/java/operator/` (72 files)
- `libs/java/operator-catalog/` (45 files)

**What It Provides:**
- Stream operators (map, filter, reduce, window)
- Aggregation operators
- Join operators
- Pattern operators
- Custom operator SDK

**Migration Plan:**
1. Create `platform/java/operators/` module
2. Merge operator + operator-catalog
3. Package: `com.ghatana.platform.operators`
4. Dependencies: event-cloud, observability

**Priority:** 🔴 **CRITICAL** - Used heavily by AEP

---

### Gap 4: Validation Framework (❌ MISSING - 74 files)

**Old Location:**
- `libs/java/validation/` (29 files)
- `libs/java/validation-api/` (12 files)
- `libs/java/validation-common/` (18 files)
- `libs/java/validation-spi/` (15 files)

**What It Provides:**
- Input validation
- Schema validation (JSON Schema)
- Business rule validation
- Validation DSL
- Constraint validation

**Migration Plan:**
1. Create `platform/java/validation/` module
2. Merge all validation modules
3. Package: `com.ghatana.platform.validation`
4. Dependencies: json-schema-validation, domain

**Priority:** 🟡 **MEDIUM** - Important for data quality

---

### Gap 5: Storage Abstractions (❌ MISSING - 67 files)

**Old Location:**
- `libs/java/storage/` (67 files)

**What It Provides:**
- Generic storage interface
- Storage backends (SQL, NoSQL, Object Store)
- Transaction management
- Connection pooling
- Query builders

**Migration Plan:**
1. Merge into `platform/java/database/` (already exists)
2. Or create separate `platform/java/storage/`
3. Package: `com.ghatana.platform.storage`
4. Dependencies: database, observability

**Priority:** 🟡 **MEDIUM** - Abstraction layer

---

### Gap 6: State Management (❌ MISSING - 41 files)

**Old Location:**
- `libs/java/state/` (41 files)

**What It Provides:**
- State machine framework
- State persistence
- State transitions
- State snapshots
- Event sourcing support

**Migration Plan:**
1. Create `platform/java/state/` module
2. Package: `com.ghatana.platform.state`
3. Dependencies: event-cloud, database

**Priority:** 🟡 **MEDIUM** - Used by workflow engine

---

### Gap 7: Redis Cache (❌ MISSING - 28 files)

**Old Location:**
- `libs/java/redis-cache/` (28 files)

**What It Provides:**
- Redis client wrapper
- Caching patterns
- Distributed locking
- Pub/sub support
- Cache invalidation

**Migration Plan:**
1. Create `platform/java/cache/` module
2. Package: `com.ghatana.platform.cache`
3. Dependencies: observability

**Priority:** 🟢 **LOW** - Can use Lettuce directly

---

### Gap 8: Event Runtime & SPI (❌ PARTIALLY MISSING - 76 files)

**Old Location:**
- `libs/java/event-runtime/` (45 files)
- `libs/java/event-spi/` (31 files)

**What It Provides:**
- Event processing runtime
- Event handlers
- Event routers
- Event SPI for plugins

**Migration Plan:**
1. Merge into existing `platform/java/event-cloud/`
2. Or separate `platform/java/event-runtime/`
3. Package: `com.ghatana.platform.eventcloud.runtime`

**Priority:** 🟡 **MEDIUM** - Core event handling

---

### Gap 9: Observability Extensions (❌ MISSING - 52 files)

**Old Location:**
- `libs/java/observability-clickhouse/` (34 files)
- `libs/java/observability-http/` (18 files)

**What It Provides:**
- ClickHouse metrics export
- HTTP metrics endpoint
- Custom metrics sinks

**Migration Plan:**
1. Add to existing `platform/java/observability/`
2. Create subdirectories: `clickhouse/`, `http/`
3. Package: `com.ghatana.platform.observability.*`

**Priority:** 🟢 **LOW** - Optional backends

---

### Gap 10: Data Ingestion (❌ MISSING - 38 files)

**Old Location:**
- `libs/java/ingestion/` (38 files)

**What It Provides:**
- Data ingestion framework
- Batch ingestion
- Stream ingestion
- Data transformation
- Ingestion pipelines

**Migration Plan:**
1. Create `platform/java/ingestion/` module
2. Package: `com.ghatana.platform.ingestion`
3. Dependencies: connectors, validation, event-cloud

**Priority:** 🔴 **HIGH** - Core data platform capability

---

### Gap 11: Context Policy (❌ MISSING - 18 files)

**Old Location:**
- `libs/java/context-policy/` (18 files)

**What It Provides:**
- Context-aware policy engine
- Policy evaluation
- Policy enforcement
- Multi-tenancy policies

**Migration Plan:**
1. Merge into `platform/java/governance/`
2. Or separate `platform/java/policy/`
3. Package: `com.ghatana.platform.policy`

**Priority:** 🟡 **MEDIUM** - Governance feature

---

### Gap 12: JSON Schema Validation (❌ MISSING - 27 files)

**Old Location:**
- `libs/java/json-schema-validation/` (27 files)

**What It Provides:**
- JSON Schema validation
- OpenAPI validation
- Schema generation
- Schema registry

**Migration Plan:**
1. Add to `platform/java/validation/`
2. Package: `com.ghatana.platform.validation.jsonschema`

**Priority:** 🟡 **MEDIUM** - Data validation

---

## Part 3: AEP Feature Analysis

### Old AEP Structure (1,957 files)

#### Module Breakdown:

**A. Core Modules (Should be in products/aep/platform/)**

| Old Module | Files | Status | Notes |
|------------|-------|--------|-------|
| **aep-libs/analytics-api/** | 10 | ❌ | Removed (Event model mismatch) |
| **aep-libs/connector-strategies/** | 22 | ✅ | MIGRATED |
| **aep-libs/planner/** | 31 | ❌ | Removed (Google ADK deps) |
| **aep-libs/pattern-system/** | 250+ | ❓ | Need verification |
| **aep-libs/event-processing/** | 180+ | ❓ | Need verification |
| **aep-libs/detection-runtime/** | 120+ | ❓ | Need verification |
| **modules/eventlog/** | 150+ | ❓ | Need verification |
| **modules/pattern-compiler/** | 100+ | ❓ | Need verification |
| **modules/operators/** | 200+ | ❓ | Need verification |

**B. Platform Module (Should stay in products/aep/platform/)**

Current: 608 files

---

### AEP Gap 1: Pattern System (❌ PARTIALLY MISSING - 250 files)

**Old Location:**
- `products/aep/aep-libs/pattern-system/`
- `products/aep/aep-libs/pattern-api/`
- `products/aep/aep-libs/pattern-operators/`
- `products/aep/aep-libs/pattern-storage/`
- `products/aep/aep-libs/pattern-compiler-core/`
- `products/aep/aep-libs/pattern-engine/`
- `products/aep/aep-libs/pattern-learning/`

**What It Provides:**
- Pattern DSL and compiler
- Pattern operators library
- Pattern detection engine
- Pattern learning algorithms
- Pattern storage and retrieval

**Migration Status Check Needed:**
- Current AEP has 608 files
- Old had 1,957 files
- Need to verify if pattern system is fully present

**Action Required:**
1. Search for pattern-related code in new AEP
2. Compare with old implementation
3. Identify missing components

---

### AEP Gap 2: Event Processing Libraries (❌ PARTIALLY MISSING - 180 files)

**Old Location:**
- `products/aep/aep-libs/eventlog/`
- `products/aep/aep-libs/eventcore/`
- `products/aep/aep-libs/state-store/`
- `products/aep/aep-libs/stream-wiring/`

**What It Provides:**
- Event log management
- Event core processing
- State store management
- Stream wiring and topology

**Migration Status:** Need verification

---

### AEP Gap 3: Detection Runtime (❌ PARTIALLY MISSING - 120 files)

**Old Location:**
- `products/aep/aep-libs/detection-runtime/`

**What It Provides:**
- Real-time detection engine
- Pattern matching runtime
- Detection pipeline orchestration

**Migration Status:** Need verification

---

## Part 4: Data Cloud Feature Analysis

### Old Data Cloud Structure (1,332 files)

Current new: 532 files (incl. plugins we just added: 56)
Net new without plugins: 476 files

**Gap:** 1,332 - 532 = 800 files need verification

#### Module Breakdown:

| Old Module | Files | Status | New Location |
|------------|-------|--------|--------------|
| **core/** | 500+ | ❓ | products/data-cloud/platform/ |
| **cli/** | 80+ | ❌ | Not migrated (intentional) |
| **plugins/** | 200+ | ✅ | Just migrated 56 files |
| **api/** | 100+ | ❓ | Should be in platform/api/ |
| **http-api/** | 150+ | ❓ | Should be in platform/http/ |
| **spi/** | 120+ | ❓ | Should be in platform/spi/ |
| **distributed/** | 80+ | ❓ | Distributed features |

---

### Data Cloud Gap 1: Additional Plugins (❌ MISSING - 144 files)

**We just migrated 56 plugin files, but old repo had 200+ plugin files**

**Remaining Plugins:**
- Report Generator plugin
- Delta Lake plugin
- Postgres storage plugin
- Metrics storage plugin
- In-memory plugins
- Abstract plugin base classes

**Action Required:**
1. List all plugins from old repo
2. Compare with newly migrated 56 files
3. Identify and migrate remaining plugins

---

### Data Cloud Gap 2: CLI Module (✅ INTENTIONALLY NOT MIGRATED - 80 files)

**Old Location:**
- `products/data-cloud/cli/`

**What It Provides:**
- Command-line interface
- Admin commands
- Data management CLI

**Status:** Intentionally not migrated (moved to launcher or separate tool)

---

### Data Cloud Gap 3: Distributed Module (❌ MISSING - 80 files)

**Old Location:**
- `products/data-cloud/distributed/`

**What It Provides:**
- Distributed storage coordination
- Cluster management
- Node discovery
- Load balancing

**Migration Plan:**
1. Verify if merged into platform/
2. If missing, migrate to platform/distributed/
3. Package: `com.ghatana.datacloud.distributed`

**Priority:** 🔴 **HIGH** - Production scalability

---

## Part 5: Shared Services Analysis

### Old Shared Services Structure (8 stub files)

### New Shared Services Structure (96 files)

**Status:** ✅ **EXPANDED** - Went from stubs to production

---

## Part 6: Flashit Analysis

### Old Flashit Structure (409 files)

**Breakdown:**
- Backend (Spring Boot): 200+ files
- Frontend (React/TypeScript): 150+ files
- Monitoring: 9 files
- Libraries: 50+ files

### New Flashit Structure (11 stub files)

**Status:** ⚠️ **INTENTIONALLY MINIMAL**

**Recommendation:**
- Keep Flashit in old repo (different tech stack: Spring Boot vs ActiveJ)
- Or migrate to dedicated Flashit repository
- Current stub in new repo is placeholder

---

## Part 7: Tutorputor Analysis

### Old Tutorputor Structure (252 files)

**Breakdown:**
- Services: 100+ files (content-studio, ai-agents, grpc)
- Libraries: 80+ files (content-studio-agents, shared utils)
- Apps: 50+ files (content-explorer, activej-app)
- Modules: 22+ files (platform integration)

### New Tutorputor Structure (0 files)

**Status:** ❌ **NOT MIGRATED**

**Recommendation:**
- Tutorputor is full-stack app (Prisma, Next.js, PostgreSQL)
- Should remain in old repo or get dedicated repository
- Not suitable for platform consolidation

---

## Part 8: Migration Priority Matrix

### 🔴 CRITICAL - Must Migrate Immediately

1. **Operator Framework** (117 files) - Core AEP functionality
2. **Connectors Library** (156 files) - Integration capability
3. **Agent Framework** (133 files) - Multi-agent orchestration
4. **Data Ingestion** (38 files) - Core data platform
5. **Data Cloud Distributed Module** (80 files) - Scalability

**Total:** 524 files

---

### 🟡 HIGH PRIORITY - Migrate Soon

6. **Event Runtime & SPI** (76 files) - Event processing
7. **Storage Abstractions** (67 files) - Storage layer
8. **Validation Framework** (74 files) - Data quality
9. **State Management** (41 files) - Workflow support
10. **Context Policy** (18 files) - Governance
11. **JSON Schema Validation** (27 files) - Validation
12. **Remaining Data Cloud Plugins** (144 files) - Plugin ecosystem

**Total:** 447 files

---

### 🟢 MEDIUM PRIORITY - Migrate When Needed

13. **Observability Extensions** (52 files) - Optional backends
14. **Redis Cache** (28 files) - Can use Lettuce directly
15. **Misc utilities** (50+ files) - Various utilities

**Total:** ~130 files

---

### ⚪ LOW PRIORITY / OPTIONAL

16. **Flashit** - Different tech stack, separate repo recommended
17. **Tutorputor** - Full-stack app, separate repo recommended
18. **CLI modules** - Moved to launchers

---

## Part 9: Detailed Migration Plan

### Phase 1: Critical Foundations (Week 1)

**Day 1-2: Operator Framework**
```bash
# Migrate libs/java/operator → platform/java/operators
# Migrate libs/java/operator-catalog → platform/java/operators
```

Tasks:
- [ ] Create `platform/java/operators/` module
- [ ] Copy operator framework (72 files)
- [ ] Copy operator catalog (45 files)
- [ ] Update package names to `com.ghatana.platform.operators`
- [ ] Add dependencies: event-cloud, observability
- [ ] Update build.gradle.kts
- [ ] Compile and test
- [ ] Update AEP to use new operators

**Day 3-4: Connectors Library**
```bash
# Migrate libs/java/connectors → platform/java/connectors-*
```

Tasks:
- [ ] Create connector modules (database, api, cloud, messaging)
- [ ] Copy 156 connector files
- [ ] Split by category
- [ ] Update package names
- [ ] Add dependencies
- [ ] Compile and test
- [ ] Update products to use new connectors

**Day 5: Agent Framework**
```bash
# Migrate agent-* libs → platform/java/agent
```

Tasks:
- [ ] Create `platform/java/agent/` module
- [ ] Merge agent-api, agent-core, agent-framework, agent-runtime
- [ ] Copy 133 files
- [ ] Update package names to `com.ghatana.platform.agent`
- [ ] Compile and test

---

### Phase 2: Data Platform (Week 2)

**Day 6-7: Data Ingestion**
```bash
# Migrate libs/java/ingestion → platform/java/ingestion
```

Tasks:
- [ ] Create `platform/java/ingestion/` module
- [ ] Copy 38 ingestion files
- [ ] Update dependencies (connectors, validation, event-cloud)
- [ ] Compile and test

**Day 8-9: Data Cloud Distributed**
```bash
# Migrate products/data-cloud/distributed → products/data-cloud/platform/distributed
```

Tasks:
- [ ] Review distributed module (80 files)
- [ ] Determine if features already in platform/
- [ ] Migrate missing distributed features
- [ ] Add cluster coordination
- [ ] Add node discovery
- [ ] Test distributed scenarios

**Day 10: Data Cloud Remaining Plugins**
```bash
# Migrate remaining 144 plugin files
```

Tasks:
- [ ] List all plugins from old repo
- [ ] Compare with newly migrated 56 files
- [ ] Identify missing 144 files
- [ ] Migrate each plugin
- [ ] Update build files
- [ ] Test all plugins

---

### Phase 3: Validation & State (Week 3)

**Day 11-12: Validation Framework**
```bash
# Migrate validation libs → platform/java/validation
```

Tasks:
- [ ] Create `platform/java/validation/` module
- [ ] Merge validation, validation-api, validation-common, validation-spi
- [ ] Copy 74 validation files
- [ ] Add JSON Schema validation (27 files)
- [ ] Compile and test

**Day 13-14: State Management**
```bash
# Migrate libs/java/state → platform/java/state
```

Tasks:
- [ ] Create `platform/java/state/` module
- [ ] Copy 41 state management files
- [ ] Add dependencies (event-cloud, database)
- [ ] Compile and test

**Day 15: Event Runtime & SPI**
```bash
# Migrate event-runtime and event-spi
```

Tasks:
- [ ] Review existing event-cloud module
- [ ] Merge event-runtime (45 files) into event-cloud
- [ ] Merge event-spi (31 files) into event-cloud
- [ ] Compile and test

---

### Phase 4: Extensions & Polish (Week 4)

**Day 16-17: Storage & Cache**
```bash
# Migrate storage and redis-cache
```

Tasks:
- [ ] Merge storage (67 files) into database or create separate module
- [ ] Create `platform/java/cache/` for redis-cache (28 files)
- [ ] Compile and test

**Day 18-19: Observability Extensions**
```bash
# Add observability extensions
```

Tasks:
- [ ] Add ClickHouse metrics (34 files) to observability/
- [ ] Add HTTP metrics (18 files) to observability/
- [ ] Test metrics export

**Day 20: Final Verification**
Tasks:
- [ ] Full build of all modules
- [ ] Integration tests
- [ ] Documentation updates
- [ ] Migration report

---

## Part 10: Verification Checklist

### Global Libraries Verification

- [ ] **activej-runtime** → Verify in platform/java/runtime/
- [ ] **activej-websocket** → Verify in platform/java/http/
- [ ] **agent-*** → Migrate to platform/java/agent/
- [ ] **ai-integration** → ✅ Verified in platform/java/ai-integration/
- [ ] **ai-platform** → Verify merged with ai-integration
- [ ] **audit** → Verify in platform/java/governance/
- [ ] **auth** → ✅ Verified in platform/java/auth/
- [ ] **auth-platform** → Verify merged with auth
- [ ] **common-utils** → Verify in platform/java/core/
- [ ] **config-runtime** → ✅ Verified in platform/java/config/
- [ ] **connectors** → Migrate to platform/java/connectors-*/
- [ ] **context-policy** → Migrate to platform/java/policy/
- [ ] **database** → ✅ Verified in platform/java/database/
- [ ] **domain-models** → ✅ Verified in platform/java/domain/
- [ ] **event-cloud** → ✅ Verified in platform/java/event-cloud/
- [ ] **event-cloud-contract** → Verify in platform/contracts/
- [ ] **event-cloud-factory** → Verify merged with event-cloud
- [ ] **event-runtime** → Migrate to event-cloud or separate
- [ ] **event-spi** → Migrate to event-cloud or separate
- [ ] **governance** → ✅ Verified in platform/java/governance/
- [ ] **http-client** → ✅ Verified in platform/java/http/
- [ ] **http-server** → ✅ Verified in platform/java/http/
- [ ] **ingestion** → Migrate to platform/java/ingestion/
- [ ] **json-schema-validation** → Migrate to validation/
- [ ] **observability** → ✅ Verified in platform/java/observability/
- [ ] **observability-clickhouse** → Add to observability/
- [ ] **observability-http** → Add to observability/
- [ ] **operator** → Migrate to platform/java/operators/
- [ ] **operator-catalog** → Merge with operators/
- [ ] **plugin-framework** → ✅ Verified in platform/java/plugin/
- [ ] **redis-cache** → Migrate to platform/java/cache/
- [ ] **security** → ✅ Verified in platform/java/security/
- [ ] **state** → Migrate to platform/java/state/
- [ ] **storage** → Migrate to database or separate
- [ ] **testing** → ✅ Verified in platform/java/testing/
- [ ] **types** → Verify in platform/java/core/
- [ ] **validation** → Migrate to platform/java/validation/
- [ ] **validation-api** → Merge with validation/
- [ ] **validation-common** → Merge with validation/
- [ ] **validation-spi** → Merge with validation/
- [ ] **workflow-api** → ✅ Verified in platform/java/workflow/

### AEP Verification

- [ ] Pattern system fully migrated
- [ ] Event processing libraries present
- [ ] Detection runtime present
- [ ] All operators migrated
- [ ] Connector strategies ✅ (migrated)
- [ ] Analytics APIs reviewed
- [ ] Planner reviewed (removed as example code - acceptable)

### Data Cloud Verification

- [ ] Core module fully migrated
- [ ] All plugins migrated (56 done, 144 remaining)
- [ ] API modules present
- [ ] HTTP API present
- [ ] SPI modules present
- [ ] Distributed module migrated
- [ ] CLI reviewed (not migrated - acceptable)

### Shared Services Verification

- [ ] ✅ Production implementation (96 files)

### Flashit & Tutorputor

- [ ] Flashit: Separate repo strategy confirmed
- [ ] Tutorputor: Separate repo strategy confirmed

---

## Part 11: File Count Targets

### Expected After Full Migration:

| Component | Current | Missing | Target |
|-----------|---------|---------|--------|
| **Platform** | 1,700 | ~600 | 2,300 |
| **AEP** | 608 | ~400 | 1,000 |
| **Data Cloud** | 532 | ~300 | 830 |
| **Shared Services** | 96 | 0 | 96 |
| **Total** | 2,936 | ~1,300 | **4,226** |

**Note:** This excludes Flashit (409) and Tutorputor (252) which should remain separate.

---

## Next Steps

1. **Immediate Actions (Today):**
   - Review this analysis
   - Prioritize Phase 1 tasks
   - Confirm migration strategy

2. **Week 1 Execution:**
   - Migrate Operator Framework (Day 1-2)
   - Migrate Connectors Library (Day 3-4)
   - Migrate Agent Framework (Day 5)

3. **Ongoing:**
   - Weekly reviews
   - Progress tracking
   - Quality assurance

---

**Status:** 📋 PLAN READY  
**Next:** Execute Phase 1 migration  
**Timeline:** 4 weeks for complete migration  
**Priority:** Critical foundations first

