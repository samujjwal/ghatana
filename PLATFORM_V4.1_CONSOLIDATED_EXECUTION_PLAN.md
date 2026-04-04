# Ghatana Platform V4.1: Consolidated Execution Plan
**Owner**: Platform Engineering Team  
**Date**: 2026-04-04  
**Status**: Ready for Implementation  
**Authority**: Binding for platform-wide coordination  

---

## Executive Summary

This plan consolidates **three strategic documents** into one coherent roadmap:

1. **Platform V4.1 Audit** (47 modules, static inspection findings)
2. **Integration Platform Architecture** (repo-native design, business ports, capability ports)
3. **Monorepo Architecture** (hierarchical structure, platform layer vs product layer)

**Result**: A 10-week, 5-phase plan to transform all 47 platform modules from fragmented (25+ duplicates, test gaps, documentation absence) into a cohesive, production-grade platform with clear business ports, thin adapters, and strong governance.

**Target**: All 47 modules **PRODUCTION-GO** by 2026-06-13, aligned with:
- ✅ Monorepo hierarchical structure
- ✅ Integration platform repo-native approach
- ✅ M4 completion rigor (98.9% test pass rate, zero duplication)
- ✅ Ghatana conventions (reuse before create, boundaries explicit)

---

## Part 1: Context Integration

### Ghatana Monorepo Structure (Reference)

```
ghatana/
├── platform/                    ← SHARED PLATFORM LAYER (47 modules)
│   ├── java/                    ← Java platform (24 modules)
│   │   ├── config              ← Foundation: Typed config, validation
│   │   ├── observability       ← Foundation: Tracing, metrics, health
│   │   ├── database            ← Foundation: SQL port + adapters
│   │   ├── distributed-cache   ← Foundation: Cache port + adapters
│   │   ├── connectors          ← Foundation: Messaging port + adapters
│   │   ├── runtime            ← Foundation: Lifecycle, startup
│   │   ├── kernel             ← Foundation: Service contracts
│   │   ├── kernel-persistence ← Foundation: Durable adapters
│   │   ├── testing            ← Foundation: Fixtures, contract harnesses
│   │   ├── agent-core         ← Feature: Coordination framework
│   │   ├── agent-memory       ← Feature: Memory/embedding runtime
│   │   ├── security           ← Feature: RBAC, auth
│   │   ├── governance         ← Feature: Policy, roles
│   │   ├── audit              ← Feature: Audit trails
│   │   ├── ai-integration     ← Feature: AI platform integration
│   │   ├── domain             ← Feature: Domain models
│   │   ├── plugin             ← Feature: Plugin system
│   │   ├── tool-runtime       ← Feature: Tool execution
│   │   ├── identity           ← Feature: Identity management
│   │   ├── billing            ← Feature: Billing
│   │   ├── http               ← Feature: HTTP infrastructure
│   │   └── ...others          ← Additional features
│   ├── typescript/             ← TypeScript platform (20 modules)
│   │   ├── platform-utils     ← Foundation: Shared utilities
│   │   ├── design-system      ← Foundation: UI components
│   │   ├── theme              ← Foundation: Theming
│   │   ├── tokens             ← Foundation: Design tokens
│   │   ├── api                ← Foundation: API client
│   │   ├── accessibility-audit ← Feature: A11y testing
│   │   ├── canvas             ← Feature: Canvas components
│   │   ├── realtime           ← Feature: Real-time comms
│   │   └── ...others          ← Additional features
│   └── contracts/              ← Contracts layer (3 modules)
│       ├── OpenAPI specs
│       ├── Protocol Buffers
│       └── JSON schemas
├── products/                    ← PRODUCT LAYER (implementations)
│   ├── tutorputor
│   ├── data-cloud             ← M4 COMPLETE ✅
│   ├── phr
│   ├── yappc
│   └── ...others
├── shared-services/            ← COMPOSITION ROOTS
│   ├── auth-gateway
│   └── ...others
└── docs/
    ├── MONOREPO_ARCHITECTURE.md
    ├── INTEGRATION_PLATFORM_ARCHITECTURE.md ← **This plan realizes it**
    └── ...others
```

### Integration Platform Strategy (Realizing in This Plan)

**Three-Tier Architecture** (repo-native):

1. **Business Ports** (product-facing default)
   - Example: `QueryExecutor`, `KeyValueCache`, `BatchEventPublisher`
   - Products depend on these
   - Implemented in foundation modules (`platform/java/*`)

2. **Capability Ports** (shared infrastructure, proven need)
   - Example: `BulkUpsertStore`, `PipelinedKeyValueOperations`
   - Created only after real cross-product demand
   - Documented justification required

3. **Native Escape Hatches** (rare, approved)
   - Example: Kafka native producer, Redis pipeline
   - Package-level within foundation modules
   - Benchmark evidence + owner required

**Repo-Native Rules**:
- Refactor existing `platform/java/*` modules in place (no new modules yet)
- Promote only when module admission checklist satisfied
- Consolidate duplicates into canonical foundation modules
- Preserve thin adapter standard (no product logic in adapters)

---

## Part 2: Audit Findings Integration

### Issue Summary (47 Modules)

| Category | Count | Severity | Fix | Effort |
|----------|-------|----------|-----|--------|
| **Duplicate symbols** | 25+ | P0 | Consolidate to canonical foundation | 65h |
| **Missing documentation** | 37 | P1 | Add READMEs + API surface docs | 40h |
| **Zero test coverage** | 7 | P1 | Add 148+ tests with real infra | 154h |
| **Stale files** | 2 | P0 | Remove .old.ts/.old.tsx | 2h |
| **Orphan directories** | 2 | P1 | Audit java/cache, typescript/testing | 8h |

### Module Status Distribution

```
PRODUCTION GO:        0 modules
CONDITIONAL GO:      42 modules (need P0/P1 fixes)
NO-GO:                2 modules (agent-catalog, agent-memory)
```

---

## Part 3: Consolidated Consolidation Strategy

### Principle: Repo-Native Integration Architecture Realization

Instead of creating new `libs/integrations/*` tree, we:
1. **Consolidate duplicates INTO existing foundation modules**
2. **Promote foundation modules to explicit business port homes**
3. **Clarify canonical locations using integration architecture tiers**

### Foundation Modules (Canonical Homes)

These 8 modules become the canonical homes for shared platform capabilities:

#### 1. `platform/java/config` — Configuration Port
```
Purpose: Typed config sources, validation, precedence, reload

Consolidate INTO this module:
  (No duplicates, but clarify as canonical configuration foundation)

Business Port:
  - ConfigLoader<T>
  - Validator
  - EnvProfile composition

Responsibility:
  - All shared adapter typed configs load here
  - All service startup config validation here
  - No product business logic
```

#### 2. `platform/java/observability` — Telemetry Port
```
Purpose: Tracing, metrics, health, logs (Micrometer + OTel)

Consolidate INTO this module:
  (No duplicates; clarify as telemetry foundation)

Business Port:
  - MetricRegistry
  - TracingProvider
  - HealthProbe

Capability Ports (to add):
  - StandardMetricNames (naming conventions)
  - AdapterReadinessChecklist (mandatory contract)

Responsibility:
  - Standard metric/span names for all adapters
  - Health check definitions
  - Structured logging
  - No product-specific metrics
```

#### 3. `platform/java/database` — SQL Port & Adapters
```
Purpose: SQL execution, transactions, batch operations

Consolidate INTO this module:
  (No duplicates; refactor surface)

Business Ports (to establish):
  - QueryExecutor<T>
  - TransactionalExecutor
  - TransactionHandle

Capability Ports (future):
  - BulkUpsertStore
  - OutboxStore
  - CheckpointStore (if multi-consumer)

Adapters:
  - PostgreSQL/JDBC adapter (refactor in place)
  - Connection pooling, Flyway, migrations

Remove/Freeze:
  - Generic DatabaseClient (legacy, too broad)
  - Untyped operations

Responsibility:
  - Narrow business ports (not generic)
  - Thin Postgres adapter
  - Contract tests with real PostgreSQL
  - Performance smoke tests
```

#### 4. `platform/java/distributed-cache` — Cache & Lease Ports
```
Purpose: Key-value cache, coordination, leasing

Consolidate INTO this module:
  (No duplicates; expand and tighten)

Business Ports (to establish):
  - KeyValueCache<K,V>
  - LeaseCoordinator
  - BatchKeyValueOperations (required for production)
  - PipelinedKeyValueOperations

Adapters:
  - In-memory adapter
  - Redis adapter (split cache vs coordination)

Remove/Freeze:
  - Generic DistributedCachePort (too broad)

Responsibility:
  - Split cache semantics from lease/coordination
  - Batched operations mandatory
  - Contract tests with real Redis
  - Lease expiration and owner validation
```

#### 5. `platform/java/connectors` — Messaging Port & Adapters
```
Purpose: Event publishing, consumption, checkpoints

Consolidate INTO this module:
  (Clarify Connector, EventSource, EventSink as low-level)

Business Ports (to establish):
  - BatchEventPublisher
  - ConsumerLoop
  - EventSchema contract

Capability Ports (future):
  - OrderedPartitionPublisher
  - CheckpointStore

Adapters:
  - Kafka publisher adapter (refactor in place)
  - Kafka consumer adapter (refactor in place)

Re-export from products:data-cloud:spi:
  - Approved cross-cutting event/storage abstractions (existing allowlist)

Remove/Freeze:
  - Generic EventSource/EventSink as primary API
  - Leaking Kafka-specific types

Responsibility:
  - Batch publish path mandatory
  - Ordering and checkpoint semantics documented
  - Contract tests with real Kafka
  - Prevent products:data-cloud SPI replication
```

#### 6. `platform/java/testing` — Contract Harnesses & Fixtures
```
Purpose: Shared integration test infrastructure

Consolidates INTO this module:
  - Testcontainers helpers (from scattered locations)
  - JUnit fixture support
  - Real-infrastructure contract test base classes
  - Shared random naming (containers, topics, schemas)

Add:
  - PostgreSQL Testcontainers fixture
  - Redis Testcontainers fixture
  - Kafka Testcontainers fixture
  - Deterministic cleanup and diagnostics
  - EventloopTestBase (existing, keep)

Responsibility:
  - Contract tests for all shared adapters
  - Performance smoke test framework
  - No product-specific test logic
  - Docs on fixture lifecycle and best practices
```

#### 7. `platform/java/kernel-persistence` — Durable Adapter Collection
```
Purpose: Durable implementations for kernel-owned concepts

Current:
  - PostgresAuditTrailPersistence
  - RedisKernelConfigResolver
  - Other kernel-facing durables

Consolidate INTO this module:
  - Promoted only for genuine cross-product persistence scenarios
  - Product-specific logic stays in products until 2nd consumer

Business Port:
  - PersistenceAdapter<T> (typed)
  - Lifecycle hooks

Responsibility:
  - Thin, focused adapters
  - Kernel-aligned semantics
  - Real-infra contract tests
```

#### 8. `platform/contracts` — Schema & Contract Definitions
```
Purpose: Shared API contracts, serialization schemas

Contents:
  - OpenAPI specifications
  - Protocol Buffer definitions
  - JSON schemas
  - Type definitions

Consolidate INTO this module:
  - JsonSchemaBundleToPojoGenerator (duplicate consolidation)
  - All shared serialization contracts

Responsibility:
  - Single source of truth for shared contracts
  - Reproducible generation
  - Version management
  - No product-specific contracts
```

### Feature Modules (Tighten Surface, No Consolidation)

These modules are feature-specific and stay mostly as-is, but tighten their public surfaces to avoid accidental use of internal details:

**Java Feature Modules** (12):
- `agent-core` (166 files) → Tighten SPI, reduce fan-in (6 dependencies)
- `agent-memory` → Complete test suite
- `security` → Consolidate Policy/Role as canonical
- `governance` → Reuse Role, Policy from security
- `audit` → Consolidate AuditEvent as canonical
- `ai-integration` → Consolidate Feature as canonical
- `domain` → Consolidate Role/AgentInfo/AgentSpec as canonical
- `identity` → Documentation, boundary clarity
- `billing` → Documentation, boundary clarity
- `plugin` → Consolidate PluginLoader as canonical
- `tool-runtime` → Consolidate Approval types as canonical
- Others → Documentation, surface review

**TypeScript Feature Modules** (12):
- `design-system` (176 files) → Consolidate components, dependency cleanup
- `canvas` → Remove accessibility/theme/validation duplicates
- `accessibility-audit` → WCAG testing, remove .old files
- `api` → Consolidate client.ts as canonical
- `realtime` → Re-export from api client, remove client.ts
- `theme` → Consolidate theme.ts as canonical
- `tokens` → Consolidate validation.ts as canonical
- Others → Documentation, surface review

### Canonical Location Mapping (Complete)

#### Java Duplicates (11 symbols)

| Symbol | Module | Canonical Location | Delete | Migrate | Effort |
|--------|--------|------------------|--------|---------|--------|
| HealthStatus.java | core | `platform/java/core/src/.../health/HealthStatus.java` | 3 copies | 4 modules | 5h |
| Policy.java | security | `platform/java/security/src/.../rbac/Policy.java` | 2 copies | 2 modules | 4h |
| Role.java | security | `platform/java/security/src/.../rbac/Role.java` | 2 copies | 2 modules | 4h |
| ValidationError.java | core | `platform/java/core/src/.../error/ValidationError.java` | 1 copy | 1 module | 2h |
| AuditEvent.java | audit | `platform/java/audit/src/.../AuditEvent.java` | 1 copy | 1 module | 2h |
| Feature.java | core | `platform/java/core/src/.../feature/Feature.java` | 1 copy | 1 module | 2h |
| ApprovalRequest.java | tool-runtime | `platform/java/tool-runtime/src/.../approval/ApprovalRequest.java` | 1 copy | 1 module | 2h |
| ApprovalStatus.java | tool-runtime | `platform/java/tool-runtime/src/.../approval/ApprovalStatus.java` | 1 copy | 1 module | 2h |
| AgentInfo.java | domain | `platform/java/domain/src/.../agent/AgentInfo.java` | 1 copy | 1 module | 2h |
| AgentSpec.java | domain | `platform/java/domain/src/.../agent/AgentSpec.java` | 1 copy | 1 module | 2h |
| PluginLoader.java | plugin | `platform/java/plugin/src/.../loader/PluginLoader.java` | 1 copy | 1 module | 2h |

**Total Java**: 60 hours

#### TypeScript Duplicates (7 symbols)

| Symbol | Module | Canonical Location | Delete | Migrate | Effort |
|--------|--------|------------------|--------|---------|--------|
| accessibility.ts | platform-utils | `platform/typescript/foundation/platform-utils/src/.../a11y/accessibility.ts` | 3 copies | 3 modules | 5h |
| client.ts | api | `platform/typescript/api/src/client.ts` | 1 copy | 1 module | 2h |
| theme.ts | theme | `platform/typescript/theme/src/theme.ts` | 1 copy | 1 module | 2h |
| validation.ts | tokens | `platform/typescript/tokens/src/validation.ts` | 1 copy | 1 module | 2h |
| CommandPalette.tsx | design-system | `platform/typescript/design-system/src/molecules/CommandPalette.tsx` | 2 copies | 2 modules | 3h |
| ErrorBoundary.tsx | design-system | `platform/typescript/design-system/src/organisms/ErrorBoundary.tsx` | 1 copy | 1 module | 2h |
| List.tsx | design-system | `platform/typescript/design-system/src/molecules/List.tsx` | 1 copy | 1 module | 2h |

**Total TypeScript**: 45 hours

**Total Consolidation**: **105 hours** (65h Java foundation + 40h typescript foundation)

---

## Part 4: Real-Infrastructure Testing Strategy

Aligned with **Integration Platform Architecture**: All shared adapters use real-infrastructure contract tests.

### Foundation Module Testing Requirements

#### `platform/java/database` Testing

**Functional Contract Tests** (using real PostgreSQL via Testcontainers):
```java
QueryExecutorContractTest extends SqlContractTestBase
  ✓ SELECT with type mapping
  ✓ WHERE clauses with filters
  ✓ JOINs and aggregates
  ✓ Parameterized queries (no SQL injection)
  ✓ NULL handling
  ✓ Type-safe result mapping

TransactionalExecutorContractTest extends SqlContractTestBase
  ✓ COMMIT on success
  ✓ ROLLBACK on exception
  ✓ Nested transactions (SAVEPOINT)
  ✓ Isolation levels (READ_COMMITTED, etc.)
  ✓ Lock detection and deadlock handling
  ✓ Idempotency guarantees

BulkOperationsContractTest extends SqlContractTestBase (if added)
  ✓ Batch INSERT (N rows)
  ✓ Batch UPDATE
  ✓ ON CONFLICT / UPSERT semantics
  ✓ Performance: P95 latency for N=1000, N=10000
  ✓ Row limit validation
```

**Performance Smoke Tests**:
```
- Single INSERT: p95 < 10ms
- Batch INSERT (1000): p95 < 50ms
- SELECT with aggregates: p95 < 20ms
- Transaction roundtrip: p95 < 15ms
```

#### `platform/java/distributed-cache` Testing

**Functional Contract Tests** (using real Redis via Testcontainers):
```java
KeyValueCacheContractTest extends CacheContractTestBase
  ✓ GET non-existent key
  ✓ SET and GET
  ✓ DELETE
  ✓ TTL expiration
  ✓ Overwrite existing key
  ✓ Timeout handling
  ✓ Concurrent operations
  ✓ Serialization round-trip

BatchKeyValueOperationsContractTest extends CacheContractTestBase
  ✓ getMany(keys [])
  ✓ setMany(kvs [])
  ✓ deleteMany(keys [])
  ✓ Pipeline operations
  ✓ Partial failure handling
  ✓ Atomic guarantees per operation
  ✓ P95 latency N=100, N=1000

LeaseCoordinatorContractTest extends CacheContractTestBase
  ✓ acquireLease(id, duration)
  ✓ renewLease(id) extends TTL
  ✓ releaseLease(id) frees key
  ✓ Owner validation (only owner can renew)
  ✓ Expiration → auto release
  ✓ Concurrent acquire conflicts
  ✓ Cascade releases
```

**Performance Smoke Tests**:
```
- Single GET: p95 < 5ms
- Single SET: p95 < 5ms
- Batch GET (100 keys): p95 < 15ms
- Batch SET (100 keys): p95 < 20ms
- Lease acquire/renew: p95 < 10ms
```

#### `platform/java/connectors` Testing

**Functional Contract Tests** (using real Kafka via Testcontainers):
```java
BatchEventPublisherContractTest extends PublisherContractTestBase
  ✓ publish(event) single
  ✓ publishBatch(events []) atomic
  ✓ Topic routing
  ✓ Partition key assignment
  ✓ Headers and metadata
  ✓ Ordering guarantees per partition
  ✓ Batch timeout (fail if too large)
  ✓ Idempotency token handling

ConsumerLoopContractTest extends ConsumerContractTestBase
  ✓ subscribe(topic)
  ✓ consumeEvent() → typed message
  ✓ commitCheckpoint(offset)
  ✓ Failure recovery and rewind
  ✓ Group lifecycle (join/leave)
  ✓ Rebalance handling
  ✓ Ordering per partition
  ✓ Concurrent consumers

EventSchemaContractTest
  ✓ JSON schema validation
  ✓ Backward compatibility
  ✓ Version negotiation
```

**Performance Smoke Tests**:
```
- Single publish: p95 < 20ms
- Batch publish (100): p95 < 50ms
- Consume latency: p95 < 100ms
- Throughput: min 1000 msgs/sec
```

### Shared Fixture Lifecycle (in `platform/java/testing`)

```java
SqlFixture {
  - Random container naming
  - Unique database per test
  - Schema migration (Flyway)
  - Connection pool setup
  - Readiness wait
  - Deterministic cleanup
  - Failure log capture
}

RedisFixture {
  - Random Redis instance
  - Unique port allocation
  - Key prefix isolation
  - Readiness wait
  - Auto-flush between tests
  - Diagnostics on failure
}

KafkaFixture {
  - Broker cluster setup
  - Random topic naming (prefix)
  - Consumer group isolation
  - Schema registry integration
  - Offset reset policy
  - Readiness validation
}
```

---

## Part 5: Four-Phase Implementation Roadmap

### Phase 1: Foundation Consolidation (Weeks 2–4, 65 hours)

**Goal**: Eliminate all 25+ duplicate symbols, consolidate into canonical foundation modules

#### Week 2: Java Foundation Phase 1
```
Mon-Wed: Consolidate HealthStatus.java
  - Keep: platform/java/core/.../health/HealthStatus.java
  - Delete: 3 copies (agent-core, database, domain)
  - Migrate: ArchUnit test to enforce single copy
  - Impact: agent-core, database, domain modules

Thu-Fri: Consolidate Policy.java + Role.java
  - Keep: platform/java/security/.../rbac/{Policy.java, Role.java}
  - Delete: policy copies in agent-core, kernel
  - Delete: role copies in domain, governance
  - Migrate: 4 module imports
  - ArchUnit: Prevent re-creation

Governance:
  - Batch atomic imports (verify via CI 3 times)
  - Pre-commit hook blocks duplicate symbol patterns
  - ESLint rule for TypeScript parallels
```

#### Week 3: Java Foundation Phase 2
```
Remaining Java duplicates:
  - ValidationError → core (audio-video delete)
  - AuditEvent → audit (domain delete)
  - Feature → core (ai-integration delete)
  - ApprovalRequest/Status → tool-runtime (agent-core delete)
  - AgentInfo/AgentSpec → domain (agent-core delete)
  - PluginLoader → plugin (kernel delete)

Per symbol:
  - Delete, migrate, ArchUnit test
  - Atomic batch per class of dependency
  - CI validation 3x

Consolidation Tasks:
  - Refactor canon locations for clarity
  - Add package-level documentation
  - Establish naming conventions
```

#### Week 4: TypeScript Foundation
```
TypeScript duplicates:
  - accessibility.ts → platform-utils (canvas 2, design-system delete)
  - client.ts → api (realtime delete)
  - theme.ts → theme (canvas delete)
  - validation.ts → tokens (canvas delete)
  - CommandPalette → design-system (canvas, design-system delete)
  - ErrorBoundary → design-system (design-system delete)
  - List → design-system (design-system delete)

Per symbol:
  - Delete duplicate files
  - Update imports
  - ESLint rule: block duplicate export patterns
  - Verify no re-introduction in PR checks

Parallel: Start Phase 2 tests for Java foundation modules
```

---

### Phase 2: Real-Infrastructure Testing (Weeks 5–8, 154 hours)

**Goal**: Add 148+ tests for NO-GO modules, implement integration architecture contract tests

#### Week 5: NO-GO Module Tests (Core Path)
```
agent-catalog (0 → 48 tests):
  - Unit tests: Catalog discovery, metadata, validation (12 tests)
  - Integration tests: Registration lifecycle, cross-product visibility (18 tests)
  - E2E tests: Full agent lifecycle from registration to invocation (18 tests)
  - Performance: Catalog query latency baselines (4 tests)

agent-memory (0 → 32 tests):
  - Unit tests: Memory lifecycle, tensor operations (8 tests)
  - Integration tests: Embedding retrieval, privacy isolation (12 tests)
  - E2E tests: Multi-turn conversation with memory (8 tests)
  - Performance: Embedding query latency (4 tests)

canvas/flow-canvas (0 → 40 tests):
  - Unit tests: Node operations, canvas state (12 tests)
  - Integration tests: Rendering, event handling (16 tests)
  - E2E tests: Complete canvas workflow (8 tests)
  - Performance: Canvas rendering benchmarks (4 tests)

platform-shell (0 → 36 tests):
  - Unit tests: Shell composition, state management (10 tests)
  - Integration tests: Plugin lifecycle, extensions (14 tests)
  - E2E tests: Complete shell workflow (8 tests)
  - Performance: Shell initialization time (4 tests)

Total: 156 tests, all passing by Friday

Governance:
  - 0 NO-GO modules after Week 5
  - Coverage reporting integrated into CI
```

#### Week 6: Foundation Module Contract Tests
```
platform/java/database (real PostgreSQL):
  - QueryExecutor contract tests (12 tests)
  - TransactionalExecutor contract tests (10 tests)
  - Performance smoke tests (5 tests)
  - Testcontainers fixture setup in platform/java/testing
  - CI: Tests run on every PR

platform/java/distributed-cache (real Redis):
  - KeyValueCache contract tests (12 tests)
  - BatchKeyValueOperations contract tests (8 tests)
  - LeaseCoordinator contract tests (8 tests)
  - Performance smoke tests (5 tests)
  - Testcontainers fixture in platform/java/testing

Total: 60+ contract tests
All real-infra, CI-validated
```

#### Week 7: Enhanced CONDITIONAL Tests
```
contracts (23 → 60 tests):
  - Add integration tests for schema generation (20 tests)
  - Add contract evolution tests (10 tests)
  - Add cross-product compatibility tests (7 tests)

accessibility-audit (thin → WCAG AA):
  - WCAG AA compliance tests (24 tests)
  - Visual regression baseline (12 tests)
  - Accessibility scanning automated (8 tests)

Parallel: agent-core concurrency/E2E tests start
```

#### Week 8: Final Test Coverage
```
agent-core (37 → 120 tests):
  - Concurrency tests (30 tests)
  - E2E agent workflows (25 tests)
  - Multi-product integration scenarios (20 tests)
  - Performance baselines (8 tests)

All modules:
  - Run full test suite 3x
  - Identify and fix flaky tests
  - Performance smoke test baselines locked
  - Coverage reports: 90%+ across all modules

Exit Criteria:
  - 1,000+ new tests passing at scale
  - 0 NO-GO modules
  - All foundation modules have real-infra contract tests
```

---

### Phase 3: Documentation & API Clarification (Week 9, 40 hours)

**Goal**: Document all 47 modules, clarify business/capability/native tier structure

#### Module Documentation Template
```
# Module: <Name>

## Purpose
One-sentence statement of module intent and scope.

## Target Personas
Who uses this module (product engineers, platform engineers, etc.)

## Critical Workflows
What user/system workflows depend on this module.

## Architectural Tier
- [x] Foundation (config, observability, database, cache, messaging, testing, contracts)
- [ ] Feature (security, governance, agent-core, etc.)
- [ ] Product-Specific (not applicable for platform modules)

## Public API Surface
### Business Ports (product-facing)
- Port 1: Description, examples
- Port 2: Description, examples

### Capability Ports (infrastructure-heavy, proven need)
- Port A: Description, justification
- Port B: Description, justification

### Native Escape Hatches (rare, approved)
- Escape 1: Description, approval reference

## Module Boundaries
### Owns
- Responsibility 1
- Responsibility 2

### Depends On
- Dependency 1 (reason)
- Dependency 2 (reason)

### Does NOT
- Anti-pattern 1 (why)
- Anti-pattern 2 (why)

## Extension Rules
How to add features without breaking the module:
- Rule 1: ...
- Rule 2: ...

## Related Modules & Products
- `platform/java/...` (dependency)
- `platform/typescript/...` (dependency)
- `products/...` (consumer)

## Testing Strategy
- Unit tests: Focus areas
- Integration tests: Real-infra approach
- Contract tests: Required coverage
- E2E tests: Key scenarios

## Observability
### Metrics Emitted
- Metric 1 (name, semantics)
- Metric 2 (name, semantics)

### Traces Recorded
- Span 1 (operation, attributes)
- Span 2 (operation, attributes)

### Health Probes
- Probe 1 (what it measures)
- Probe 2 (what it measures)

## Known Limitations & Future Work
- Limitation 1: Impact, workaround
- Future: Feature 1 (reason, timeline)

## Owner & Governance
- Owner: [Name/Team]
- Governance Model: [architecture review, module admission, etc.]
- Last Updated: [Date]
```

#### Week 9 Task Breakdown
```
Monday-Tuesday:
  - Finalize template with Platform Team
  - Identify module owners for each of 47 modules
  - Set up documentation review process

Wednesday-Friday:
  - Foundation modules (8): Detailed business/capability/native docs (8h)
  - Feature modules (24): API surface + extension rules (20h)
  - Product-specific (15): Boundary docs (12h)

All modules:
  - Peer review (1h each)
  - Incorporated into module repos
  - Linked from module README
  - Versioned with code

Exit Criteria:
  - 47/47 modules documented
  - Clear business/capability/native tiers
  - API surface unambiguous
  - Extension rules explicit
```

---

### Phase 4: Governance & Verification (Week 10, 40 hours)

**Goal**: Enforce architecture constraints, verify boundary integrity, sign off

#### Governance Enforcement (Gradle + ESLint)

**Gradle Tasks** (add to `gradle/platform-boundary-check.gradle`):
```gradle
:platform:validateDuplicateSymbols {
  // Fail if any symbol appears in >1 module
  // Maintain canonical location list
}

:platform:validateBusinessPorts {
  // Fail if product imports vendor SDKs directly
  // (JDBC, Kafka, Redis, Jedis clients, etc.)
}

:platform:validateCapabilityPorts {
  // Fail if new capability port has no documented justification
  // Fail if capability port duplicates nearby business port
}

:platform:validateNativeEscapeHatches {
  // Fail if new native escape without approval
  // Fail if used outside approved modules
  // Track usage and ownership
}

:platform:validateThinAdapters {
  // Fail if adapter contains product business logic
  // Fail if adapter exports vendor types
  // Enforce error mapping to shared taxonomy
}

:platform:validateArchunitTests {
  // All foundation modules must have ArchUnit tests
  // Test duplicate prevention rules
  // Test boundary integrity
}

:platform:validateCoverageGates {
  // All modules: ≥90% code coverage
  // Critical paths: 100% coverage
  // Spot-check E2E coverage
}

:platform:auditValidation {
  // Master validation task
  // Runs all checks above
  // Generates compliance report
}
```

**ESLint Rules** (in `eslint-rules/ghatana-architecture-rules.js`):
```javascript
// Block duplicate symbol re-introduction
rule: 'no-duplicate-exports' {
  // Fail if accessibility.ts exported from non-canonical module
  // Fail if client.ts exported from non-api module
  // Fail if theme.ts exported from non-theme module
  // etc. for all 7 TypeScript duplicates
}

// Block direct vendor imports in products
rule: 'no-direct-vendor-imports' {
  // Fail: import { Kafka } from 'kafkajs' in product code
  // Fail: import { Redis } from 'redis' in product code
  // Fail: import { Pool } from 'pg' in product code
  // Allow: Only in adapter/integration packages
}

// Block untyped data flow
rule: 'no-any-in-ports' {
  // Fail if business port parameter or return has any
  // Fail if capability port uses untyped schemas
}

// Enforce real-infra contract tests
rule: 'foundation-modules-need-contract-tests' {
  // Fail if platform/java/* adapter lacks Testcontainers tests
  // Fail if platform/typescript/* component lacks integration tests
}
```

**CI Workflow** (`github/workflows/platform-audit-validation.yml`):
```yaml
name: Platform Audit Validation

on: [pull_request, push]

jobs:
  duplicates:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - run: ./gradlew :platform:validateDuplicateSymbols

  business-ports:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - run: ./gradlew :platform:validateBusinessPorts

  eslint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - run: pnpm install && pnpm lint --rule=ghatana-architecture

  coverage:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - run: ./gradlew :platform:testCoverage
      - run: if [[ $(cat coverage-report.txt) -lt 90 ]]; then exit 1; fi

  full-audit:
    runs-on: ubuntu-latest
    needs: [duplicates, business-ports, eslint, coverage]
    steps:
      - run: ./gradlew :platform:auditValidation
      - uses: actions/upload-artifact@v3
        with:
          name: audit-report
          path: build/reports/platform-audit.html
```

#### Week 10 Execution
```
Monday-Tuesday: Governance Implementation
  - Create all Gradle validation tasks
  - Implement all ESLint rules
  - Wire CI workflow
  - Test locally

Wednesday: Boundary Verification
  - Run full audit validation on all 47 modules
  - Generate compliance report
  - Identify any gaps

Thursday: Module Sign-Off
  - Platform Team lead: Boundary integrity verification
  - Architecture Team: API surface and port tiers
  - QA Lead: Coverage and test validation report
  - Engineering Lead: Final production readiness

Friday: Commit & Promote
  - Merge governance code
  - Tag release: platform-v4.1-production
  - Update status for all 47 modules → PRODUCTION-GO
  - Archive audit reports
```

---

## Part 6: Success Criteria & Sign-Off

### Definition of Done (All 47 Modules)

✅ **Zero Duplication**
- 0 duplicate symbols (25+ → 0)
- All imports migrated
- ArchUnit tests pass

✅ **Complete Testing** (M4 Rigor)
- ≥90% code coverage per module
- Real-infrastructure contract tests (foundation modules)
- E2E scenarios documented and passing
- Performance smoke tests locked
- 0 flaky tests at scale

✅ **Complete Documentation**
- 47/47 modules have README
- Business/capability/native tiers clearly documented
- API surface unambiguous
- Extension rules explicit
- Ownership clear

✅ **Strong Governance**
- ArchUnit tests enforce boundaries
- ESLint rules prevent duplicates
- Pre-commit hooks validate
- CI workflow validates every PR
- Compliance reports generated

✅ **Production Readiness**
- 0 build warnings
- 0 lint errors
- Clean dependency graph
- Observable (metrics, logs, traces)
- Aligned with monorepo architecture

### Module Status Transformation

**Before Plan**:
```
Production GO:       0 modules
Conditional GO:     42 modules
NO-GO:               2 modules
```

**After Week 10**:
```
Production GO:      47 modules ✅
Conditional GO:      0 modules
NO-GO:               0 modules
```

### Sign-Off Authority

| Role | Approval Criterion | Deadline |
|------|-------------------|----------|
| **Java Platform Lead** | Phase 1 complete (duplicates), Phase 2 Java tests | Week 3 |
| **TypeScript Platform Lead** | Phase 1 complete (duplicates), Phase 2 TypeScript tests | Week 4 |
| **QA Lead** | Phase 2 complete (1,000+ tests passing, 90%+ coverage) | Week 8 |
| **Documentation Lead** | Phase 3 complete (47/47 modules documented) | Week 9 |
| **Architecture Lead** | Phase 4 complete (governance enforced, boundaries verified) | Week 10 |
| **Platform Engineering Lead** | Final approval (all 47 modules PRODUCTION GO) | Week 10 |

---

## Part 7: Risk Mitigation & Contingencies

### Critical Path Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Import breakage during consolidation | Medium | High | Batch 3–4 imports per atomic commit, CI validate 3x per batch |
| Test flakiness (async, timing) | Medium | Medium | Run all new tests 3x in CI before merge, profile async patterns |
| Governance rules too strict | Low | Medium | Phase rules into CI gradually (warn → error by Week 10) |
| Module owner unavailability | Medium | Medium | Pair programming, document decisions, async collaboration channels |
| Scope creep (additional modules) | Medium | Medium | Fix scope: 47 modules only, Phase 5 for new issues |

### Fallback Strategies

**If Java consolidation behind** (Week 3):
- Freeze scope to top 6 duplicates only (HealthStatus, Policy, Role, ValidationError, AuditEvent, Feature)
- Defer remaining 5 to Phase 2 (can run parallel with tests)
- Accept slightly longer per-module consolidation (5h → 8h each)

**If tests behind schedule** (Week 6):
- Prioritize real-infra contract tests for foundation modules only
- Defer NO-GO module E2E tests to targeted phase
- Accept lower coverage initially (85% → 90% by Week 8)

**If documentation incomplete** (Week 9):
- Template-only approach (1 README per module minimum)
- Defer detailed API docs to Phase 2
- Commit skeleton READMEs by Week 9 end

**If governance implementation blocks** (Week 10):
- Deploy without full automation (manual checklist)
- Phase in automated checks across next 4 weeks
- Accept manual review for first release

---

## Part 8: Alignment Evidence

### How This Plan Integrates All Three Sources

#### ✅ Monorepo Architecture Integration
```
Structure:
  ✓ Respects platform vs product layer separation
  ✓ Consolidates duplicates INTO foundation modules (not new top-level tree)
  ✓ Preserves existing module boundaries
  ✓ Align with existing shared-services composition pattern

Module Classification:
  ✓ Foundation modules explicitly documented (config, observability, database, cache, connectors, testing, contracts)
  ✓ Feature modules (agent-core, security, governance, etc.) tighten surface
  ✓ Product layer unchanged
  ✓ Composition roots (shared-services) use business ports
```

#### ✅ Integration Platform Architecture Realization
```
Business Ports (product-facing default):
  ✓ QueryExecutor, TransactionalExecutor (database)
  ✓ KeyValueCache, LeaseCoordinator (distributed-cache)
  ✓ BatchEventPublisher, ConsumerLoop (connectors)
  ✓ Standard interfaces in `platform/java/*` modules

Capability Ports (infrastructure, proven need):
  ✓ BulkUpsertStore, OutboxStore (future, multi-consumer only)
  ✓ PipelinedKeyValueOperations (proven redis need)
  ✓ OrderedPartitionPublisher (messaging optimization)
  ✓ Created only post-demand with documented justification

Native Escape Hatches (rare, approved):
  ✓ Kafka native producer (in connectors, approved use case)
  ✓ Redis pipeline (in distributed-cache, benchmark evidence)
  ✓ JDBC/Postgres native (in database, rare and justified)

Thin Adapter Standard:
  ✓ No product business logic in adapters
  ✓ Error mapping to shared taxonomy
  ✓ Lifecycle management
  ✓ Telemetry emission
  ✓ Vendor SDK hidden behind port interfaces

Real-Infrastructure Testing:
  ✓ All foundation adapters: Testcontainers (PostgreSQL, Redis, Kafka)
  ✓ Contract tests mandatory
  ✓ Performance smoke tests baseline
  ✓ Shared fixtures in platform/java/testing
```

#### ✅ Platform V4.1 Audit Incorporation
```
Duplicate Resolution:
  ✓ All 25+ symbols consolidated to canonical locations
  ✓ ArchUnit tests prevent re-creation
  ✓ ESLint rules for TypeScript parallels
  ✓ Atomic batch imports, CI validation 3x

Documentation Gaps:
  ✓ All 37 missing READMEs created
  ✓ API surface documented per module
  ✓ Business/capability/native tiers explicit
  ✓ Extension rules documented

Test Coverage Gaps:
  ✓ NO-GO modules: 148+ new tests (agent-catalog, agent-memory, canvas/flow-canvas, platform-shell)
  ✓ CONDITIONAL modules: Enhanced with real-infra contract tests
  ✓ 1,000+ new tests total
  ✓ 90%+ coverage per module

Stale Files & Orphans:
  ✓ Remove .old.ts/.old.tsx
  ✓ Audit java/cache (integrate or delete)
  ✓ Clarify typescript/testing (integrate or archive)
```

---

## Part 9: Weekly Execution Checklist

### Week 1: Foundations (Parallel Activities)

**Team A: Stale Files**
- [ ] Remove typescript/accessibility-audit/.old.ts
- [ ] Remove TypeScript/.old.tsx
- [ ] Add ESLint rule to prevent .old.* re-introduction
- [ ] Merge ESLint update to repo

**Team B: Orphan Audit**
- [ ] Grep java/cache usage across codebase
- [ ] Grep typescript/testing usage
- [ ] Decision: integrate, delete, or archive
- [ ] Document decision in PR

**Team C: Documentation Foundation**
- [ ] Create README template (Part 6)
- [ ] Review with Platform Team
- [ ] Assign module owners (47 modules)
- [ ] Create documentation tracking spreadsheet

**Governance Setup**
- [ ] Create gradle/platform-boundary-check.gradle (draft)
- [ ] Draft eslint-rules/ghatana-architecture-rules.js
- [ ] Plan CI workflow (github/workflows/platform-audit-validation.yml)

---

### Week 2–3: Java Consolidation

**Daily Standup**: Status per consolidation (HealthStatus → Policy → Role → ValidationError → AuditEvent → Feature)

**Per Consolidation**:
- [ ] Delete copies
- [ ] Update imports (batch atomic)
- [ ] Add ArchUnit test
- [ ] CI validation 3x
- [ ] Merge to main

---

### Week 4: TypeScript Consolidation

**Rolling Consolidations** (accessibility.ts → client.ts → theme.ts → validation.ts → Components)

Per symbol:
- [ ] Delete duplicates
- [ ] Update imports
- [ ] Add ESLint rule
- [ ] CI validation
- [ ] Merge

---

### Week 5–8: Test Implementation

**Weekly Structure**:
- Monday: Kickoff (assign tests, review contract requirements)
- Tue-Thu: Implementation (write tests, fix failures)
- Friday: Review, merge, coverage reporting

**Tracking**:
- Tests written this week: ___ / target
- Tests passing: ___ / target
- Coverage increase: ___ %

---

### Week 9: Documentation

**Daily Assignments** (4 people × 2 modules/day):
- Monday: Foundation modules (8 modules)
- Tue-Wed: Feature modules (16 modules)
- Thu-Fri: Product modules (12 modules) + review

**Tracking**:
- READMEs drafted: ___ / 47
- Peer reviews pending: ___ / 47
- Merged: ___ / 47

---

### Week 10: Governance & Sign-Off

**Daily Sequence**:
- Mon-Tue: Implement governance code
- Wed: Full validation run
- Thu: Leadership review & sign-off
- Fri: Final PR, merge, release tag

**Gate Criteria**:
- [ ] Duplicate validation passing
- [ ] Business port validation passing
- [ ] ESLint validation passing
- [ ] Coverage gates passing
- [ ] All 47 modules → PRODUCTION-GO
- [ ] Audit report generated

---

## Part 10: Metrics & Reporting

### Compliance Dashboard (Weekly)

```
Platform V4.1 Audit Compliance Report — Week X

CONSOLIDATION PROGRESS
Total symbols identified: 25+
Symbols consolidated: __/25+
Modules affected: __/28
Coverage: __%

TEST COVERAGE PROGRESS
Tests written: __/1,000+
Tests passing: __/__
Coverage % overall: __%
NO-GO modules remaining: __/2

DOCUMENTATION PROGRESS
READMEs created: __/47
API surfaces documented: __/47
Business/capability tiers: __/47
Extension rules: __/47

GOVERNANCE IMPLEMENTATION
Gradle tasks implemented: __/6
ESLint rules implemented: __/4
CI workflow enabled: [Y/N]
Pre-commit hooks active: [Y/N]

PRODUCTION-GO STATUS
Foundation modules: __/8
Feature modules: __/24
Product modules: __/15
Total: __/47

BLOCKERS & RISKS
P0 blockers: []
P1 blockers: []
Next week focus: [description]
```

### Artifacts Generated

**Weekly**:
- Test execution report with timing/failures
- Coverage trending graph
- Consolidation progress checklist
- Module owner engagement summary

**Phase-end**:
- Consolidation audit report
- Test coverage gap analysis
- Documentation completeness report
- Governance implementation status

**Final (Week 10)**:
- Platform V4.1 Production Release Notes
- Architecture Compliance Certificate
- Governance Enforcement Summary
- Performance Baseline Report

---

## Part 11: Approved Extensions (Future Phases)

**In Scope for This Plan**: 47 platform modules, 4 phases, 10 weeks

**Out of Scope (Phase 2+)**:

1. **New Integration Ports** (only after Wave pattern)
   - `BlobStore` (when 2+ products need)
   - `CheckpointStore` (only if multi-product)
   - `OutboxStore` (only if distributed transactions needed)

2. **Product Layer Audit** (products/* modules)
   - Will reuse business ports from foundation
   - Separate governance phase

3. **CI/CD Pipeline Expansion**
   - Automated contract test generation
   - Performance regression detection
   - Canary deployment validation

4. **Platform Library Versioning**
   - Semantic versioning strategy
   - Backward compatibility policy (likely none per Ghatana)
   - Release coordination

---

## Conclusion

This consolidated execution plan **unifies all three strategic documents** into a single, coherent 10-week roadmap to transform Ghatana's 47 platform modules from fragmented into a production-grade, well-governed architecture.

**Key Outcomes**:
- ✅ Zero duplicate symbols (25+ → 0)
- ✅ 1,000+ new tests, real-infra validated
- ✅ All 47 modules PRODUCTION-GO
- ✅ Clear business/capability/native tiers
- ✅ Enforced governance (CI + Gradle + ESLint)
- ✅ Aligned with monorepo structure
- ✅ Realizing integration platform architecture

**Ready for stakeholder approval and Week 1 kickoff (2026-04-08).**

---

**Document Version**: 2.0 (Consolidated)  
**Supersedes**: PLATFORM_V4.1_AUDIT_EXECUTION_PLAN.md (v1.0)  
**Authority**: Platform Engineering Team  
**Next Review**: 2026-04-11 (Week 2 checkpoint)  
**Target Completion**: 2026-06-13
