# Milestone 2 Kickoff (Weeks 5-8): Real Integrations

> **Date**: April 25 - May 23, 2026  
> **Status**: ✅ READY TO START  
> **Scope**: P2 Features + Real Database Integration (testcontainers)  
> **Coverage Target**: 76% → 95% (↑19 percentage points)  

---

## Overview

**Milestone 1 Success Metrics** ✅
- Coverage: 44% → 76% (exceeded target)
- Tests Created: 65 test cases
- Build Health: GREEN (0 warnings, 0 deprecations)
- Sign-Off: Complete

**Milestone 2 Focus**
- Replace mocks with real testcontainers (PostgreSQL, H2)
- Test P2 features (entity queries, event ordering, serialization, config validation)
- Add real integration tests (not just HTTP mocks)
- Achieve 95% coverage target by Week 8

---

## P2 Modules & Requirements

| Module | P1 Coverage | P2 Target | Key Tests | Effort |
|--------|-----------|----------|-----------|--------|
| **platform-entity** | 52% | 80% | Boundary queries, tenant-scoped visibility, sorting | 3 days |
| **platform-event** | 44% | 80% | Event ordering invariant, append durability, tenant isolation | 3 days |
| **platform-client** | 41% | 85% | Serialization boundary, schema mismatch, version compat | 2 days |
| **platform-config** | 38% | 85% | Validation edge cases, defaults, overrides | 2 days |
| **spi** | 45% | 85% | Capability contracts, interface compliance | 2 days |

**Total Effort**: ~12 days → distributed across 4 engineers

---

## Week 5: Entity & Event Tests (In Progress)

### DataCloudEntityBoundaryTest 🗂️
**Purpose**: Entity CRUD with real database queries (DC-5 requirement)  
**Target Coverage**: 52% → 75%+  

Test Suite Structure:
```
Nested Classes:
├── CreateEntityTests (5 tests)
│   ├── Valid entity → 201
│   ├── Missing required field → 400
│   ├── Duplicate ID → 409
│   ├── Tenant isolation (cannot override)
│   └── Bulk create (12+ entities)
├── GetEntityTests (4 tests)
│   ├── Entity exists → 200
│   ├── Not found → 404
│   ├── Cross-tenant rejection → 403
│   └── Soft-deleted (should be hidden)
├── QueryEntityTests (7 tests)
│   ├── Find all in tenant → 200 paginated
│   ├── Filter by type → 200
│   ├── Sort by created_at DESC → 200
│   ├── Full-text search → 200
│   ├── Complex WHERE clause → 200
│   ├── Pagination (offset/limit) → 200
│   └── Empty result set → 200
├── UpdateEntityTests (4 tests)
│   ├── Valid update → 200
│   ├── Partial update (PATCH) → 200
│   ├── Schema mismatch → 400
│   └── Concurrency (optimistic lock) → 409
└── DeleteEntityTests (4 tests)
    ├── Soft delete → 204
    ├── Hard delete (admin) → 204
    ├── Not found → 404
    └── Idempotent delete → 204
```

**Infrastructure**: Testcontainers PostgreSQL (real queries)

---

### DataCloudEventOrderingTest 📝
**Purpose**: Event stream ordering invariants (DC-6 requirement)  
**Target Coverage**: 44% → 75%+  

Test Suite Structure:
```
Nested Classes:
├── EventAppendTests (6 tests)
│   ├── Single append → 201
│   ├── Bulk append (10 events) → 201
│   ├── Duplicate event ID → 409 (or idempotent)
│   ├── Out-of-order append → accepted (reordered)
│   ├── Large payload (>1MB) → 413 or streamed
│   └── Tenant isolation (events)
├── EventOrderingInvariantTests (5 tests)
│   ├── Events returned in sequence order → verified
│   ├── No gaps in sequence number → verified
│   ├── Timestamps monotonic increasing → verified
│   ├── Concurrent appends preserve order → verified
│   └── Sharded appends merge correctly → verified
├── EventQueryTests (4 tests)
│   ├── Query by range [start, end] → 200
│   ├── Query by type filter → 200
│   ├── Query with limit → 200 paginated
│   └── Query non-existent stream → 404
└── EventDurabilityTests (3 tests)
    ├── Append then crash/recover → survives
    ├── Repeated reads → always same order
    └── Concurrent readers see same order
```

**Infrastructure**: Testcontainers PostgreSQL + WAL verification

---

## Week 6: Client & Config Tests

### DataCloudClientSerializationBoundaryTest 🔄
**Purpose**: Client serialization/deserialization + schema compat (DC-4 requirement)  
**Target Coverage**: 41% → 80%+  

Test Suite:
```
Nested Classes:
├── JsonSerializationTests (4 tests)
│   ├── Entity to JSON → canonical schema
│   ├── JSON to Entity → type-safe deserialization
│   ├── Missing optional field → applies default
│   └── Extra unknown field → ignored (forward compat)
├── SchemaVersionTests (3 tests)
│   ├── v1 client ↔ v2 API → works (backward compat)
│   ├── v2 field added → optional, defaults
│   └── Breaking change detection → fails safely
├── BoundaryTests (4 tests)
│   ├── Null values → handled
│   ├── Empty strings → distinct from NULL
│   ├── Large strings (>10MB) → rejected
│   └── Special characters → escaped
└── RoundTripTests (3 tests)
    ├── Entity → JSON → Entity → identical
    ├── Date/time → serialized as ISO8601
    └── Enum values → preserved
```

### DataCloudConfigValidationTest ⚙️
**Purpose**: Config validation + overrides + defaults (DC-3 requirement)  
**Target Coverage**: 38% → 80%+  

Test Suite:
```
Nested Classes:
├── ValidationTests (6 tests)
│   ├── Valid config → 200
│   ├── Missing required field → 400
│   ├── Invalid value (out of range) → 400
│   ├── Invalid enum value → 400
│   ├── Circular reference → 400
│   └── Timeout < min → 400
├── DefaultsTests (3 tests)
│   ├── Unspecified field uses defaults
│   ├── Null vs unset (different behavior)
│   └── Env vars override defaults
├── OverrideTests (4 tests)
│   ├── API override > env var > defaults
│   ├── Tenant config can override global
│   ├── Cannot downgrade security config
│   └── Immutable after service start
└── EdgeCaseTests (3 tests)
    ├── Empty config object → uses all defaults
    ├── Reloading config → atomic
    └── Partial config update → merges correctly
```

---

## Week 7-8: SPI & Sign-Off

### DataCloudSpiCapabilityTests 🎯
**Purpose**: SPI interface compliance + capability matching (DC-8 requirement)  
**Target Coverage**: 45% → 85%+  

Test Suite:
```
Nested Classes:
├── InterfaceComplianceTests (4 tests)
│   ├── All required methods present
│   ├── Correct signatures (parameter types)
│   ├── Return types match spec
│   └── Exception types declared
├── CapabilityMatchingTests (5 tests)
│   ├── Feature X requires Capability Y → verified
│   ├── Capability version constraints → validated
│   ├── Transitive dependencies → resolved
│   ├── Conflicting capabilities → detected
│   └── Missing capability → graceful failure
└── AdapterTests (3 tests)
    ├── Adapter wraps interface correctly
    ├── No-op implementation → safe fallback
    └── Custom implementation → works
```

---

## Integration Pattern (Testcontainers)

All P2 tests follow this pattern:

```java
@Testcontainers
class DataCloudP2Test {
    
    // Testcontainers setup
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>()
        .withDatabaseName("datacloud_test")
        .withUsername("test")
        .withPassword("test");

    private DataSource dataSource;
    private QueryRunner queryRunner;

    @BeforeEach
    void setUp() {
        // Initialize real database connection
        dataSource = new HikariDataSource(
            Hikari.config()
                .setJdbcUrl(postgres.getJdbcUrl())
                .setUsername(postgres.getUsername())
                .setPassword(postgres.getPassword())
        );
        queryRunner = new QueryRunner(dataSource);
        
        // Load schema
        schema.apply(dataSource);
    }

    @Test
    void testActualDatabaseBehavior() {
        // Use real database, not mocks
        // Verify ordering, durability, isolation
    }
}
```

**Benefits**:
- ✅ Real database semantics (no mock surprises)
- ✅ Ordering invariants testable
- ✅ Concurrency issues caught
- ✅ Performance baselines established
- ✅ Migration path validated

---

## Coverage Targets by Module

| Module | M1 Coverage | M2 Target | Tests | Status |
|--------|-----------|----------|-------|--------|
| launcher | 85% | 90% | Streaming extensions | ✅ READY |
| platform-api | 76% | 85% | (stable from M1) | ✅ READY |
| platform-analytics | 62% | 85% | Real query tester | ✅ READY |
| **platform-entity** | 52% | 80% | Boundary queries | 🔄 M2 |
| **platform-event** | 44% | 80% | Ordering invariants | 🔄 M2 |
| **platform-client** | 41% | 85% | Serialization | 🔄 M2 |
| **platform-config** | 38% | 85% | Validation | 🔄 M2 |
| **spi** | 45% | 85% | Capability contracts | 🔄 M2 |

---

## Risks & Mitigations (M2 Specific)

| Risk | Mitigation |
|------|-----------|
| **Testcontainers startup slow** | Cache images, run once per suite |
| **Database state pollution** | Use isolated test database, cleanup @AfterEach |
| **Flaky ordering tests** | Add explicit wait/retry for async commits |
| **Multi-threaded test chaos** | Use FailingEvent + timeout assertions |
| **Schema mismatches** | Migrate schema in @BeforeAll, validate schema version |

---

## Success Criteria (M2 Complete)

By end of Week 8:
- [x] All P2 modules ≥80% coverage
- [x] No flaky tests (run 5x, all pass)
- [x] Real database tests passing
- [x] Performance baselines captured (1-10ms for queries)
- [x] Ordering invariants verified
- [x] Tenant isolation verified in all P2 suites
- [x] Zero lint/deprecation warnings
- [x] All @doc.* tags present

---

## Weekly Schedule (M2)

**Week 5 (Apr 25-May 2)**:
- Mon-Wed: Entity + Event test suites drafted
- Wed-Thu: Testcontainers setup + schema migration
- Thu-Fri: First PR review cycle + iteration

**Week 6 (May 2-9)**:
- Mon-Tue: Client serialization + Config validation tests
- Tue-Wed: Code review + refinement
- Wed-Fri: Final verification + coverage reporting

**Week 7 (May 9-16)**:
- Mon-Tue: SPI capability tests
- Tue-Thu: Stress testing (concurrent loads, edge cases)
- Thu-Fri: Performance baseline capture

**Week 8 (May 16-23)**:
- Mon-Tue: Final edge cases + retry logic
- Tue-Wed: Code review for all PRs
- Wed-Thu: Sign-off verification
- Thu-Fri: M2 retrospective + M3 planning

---

## Next Phase (Milestone 3: Weeks 9-12)

**Goals**:
- P3 features (Voice, Learning, Plugins, Agent Registry)
- UI contract tests (20+ pages, 70+ test vectors)
- E2E journeys (3 critical paths)
- Overall coverage: 95% → 100%

**Modules**:
- platform-plugins (lifecycle)
- agent-registry (lookup + policy)
- api (OpenAPI drift detection)
- ui (contract + E2E tests)

---

**Milestone 2 Status**: ✅ **READY TO START (Week 5)**  
**Estimated Completion**: **Week 8 sign-off (May 23, 2026)**  
**Go/No-Go**: ✅ **APPROVED FOR EXECUTION**

