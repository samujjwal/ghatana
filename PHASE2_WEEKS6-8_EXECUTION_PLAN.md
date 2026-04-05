# Phase 2 Implementation Plan — Weeks 6-8 (Observability, HTTP, Database)

**Date**: April 4, 2026  
**Status**: Security module validated (259 tests passing)  
**Focus**: Expand test coverage for observability (52 target), HTTP (73 target), database (89 target)  

---

## Current Test Coverage Baseline

| Module | Current Tests | Target Tests | Gap | Priority |
|--------|--------------|--------------|-----|----------|
| **Security** | 259 ✅ | 48+ | 0 (EXCEEDED) | ✅ COMPLETE |
| **Observability** | 16 | 52 | +36 | 🔴 HIGH |
| **HTTP** | 16 | 73 | +57 | 🔴 HIGH |
| **Database** | 18 | 89 | +71 | 🔴 HIGH |
| **TOTAL** | 309 | 262+ | +164 | Phase 2 in progress |

---

## Week 6 (Apr 29-May 3): Observability Module — 52 Tests

### Current Test Files (16)
- AgentExecutionMetricsTest
- ClickHouseTraceStorageTest
- SpanBufferTest
- EbpfEventloopStallTracerTest
- TracingTest
- (11 more files)

### Gap Analysis: Need +36 Tests

**Proposed Categories**:
1. **Metrics Collection** (12 tests)
   - Counter, Gauge, Histogram, Summary metrics
   - Metric registration and lookup
   - Metric export to Prometheus
   - Tag/dimension handling

2. **Trace Collection & Export** (10 tests)
   - Span creation and completion
   - Trace context propagation
   - Batch export to Jaeger/ClickHouse
   - Sampling policies

3. **Health & Readiness** (8 tests)
   - Health endpoint responses
   - Readiness probes
   - Liveness checks
   - Dependency health aggregation

4. **Logs & Structured Logging** (6 tests)
   - Log emission and filtering
   - Structured field extraction
   - Correlation ID propagation
   - Log level management

### Implementation Strategy

```bash
# Week 6 execution:
# Mon-Tue: Metrics tests (12) + Trace tests (10)
# Wed:     Health/Readiness tests (8)
# Thu:     Structured logging tests (6)
# Fri:     Build validation + review
```

**Pattern to Follow**: Same as security module
- Extend EventloopTestBase for async operations
- Use SecurityTestFixture patterns for observable objects
- @doc.* tags on all test classes
- Nested @DisplayName classes for organization

---

## Week 7 (May 6-10): HTTP Module — 73 Tests

### Current Test Files (16)
- HTTP server tests
- Request/response handling
- Filter chain tests
- (13 more files)

### Gap Analysis: Need +57 Tests

**Proposed Categories**:
1. **HTTP Server & Routing** (16 tests)
   - Server startup/shutdown
   - Route registration and matching
   - Method handling (GET, POST, PUT, DELETE, etc.)
   - Path parameters and query strings

2. **Request/Response Processing** (14 tests)
   - Header parsing and manipulation
   - Body serialization/deserialization
   - Content-type handling
   - Compression (gzip, deflate)

3. **Error Handling & Status Codes** (12 tests)
   - HTTP status code mapping
   - Error response formatting
   - Exception translation (domain → HTTP)
   - Validation error responses

4. **Authentication & Authorization** (10 tests)
   - Bearer token extraction
   - JWT validation in HTTP context
   - Permission checking per endpoint
   - CORS handling

5. **Filters & Middleware** (5 tests)
   - Filter chain execution
   - Request/response mutation
   - Error propagation through filters

### Implementation Strategy

```bash
# Week 7 execution:
# Mon-Tue: Server & Routing tests (16) + Request/Response tests (14)
# Wed:     Error handling tests (12)
# Thu:     Auth/AuthZ tests (10)
# Fri:     Filters tests (5) + validation
```

---

## Week 8 (May 13-17): Database Module — 89 Tests

### Current Test Files (18)
- Connection pool tests
- Query execution tests
- Transaction tests
- (15 more files)

### Gap Analysis: Need +71 Tests

**Proposed Categories**:
1. **Connection Management** (12 tests)
   - Pool creation and management
   - Connection acquisition/release
   - Timeout handling
   - Connection validation

2. **Query Execution** (20 tests)
   - SELECT queries (simple, complex, joins)
   - INSERT/UPDATE/DELETE operations
   - Batch operations
   - Prepared statements

3. **Transactions** (16 tests)
   - Begin/commit/rollback
   - Rollback on exception
   - Savepoint support
   - Isolation level handling

4. **Migrations & Schema** (12 tests)
   - Schema initialization
   - Migration execution
   - Rollback migrations
   - Schema validation

5. **Caching & Performance** (10 tests)
   - Query result caching
   - Cache invalidation
   - Lazy loading
   - N+1 query detection

6. **Error Handling** (10 tests)
   - Connection failures
   - Query syntax errors
   - Constraint violations
   - Deadlock handling

### Implementation Strategy

```bash
# Week 8 execution:
# Mon:     Connection Management tests (12)
# Tue-Wed: Query Execution tests (20)
# Wed-Thu: Transaction tests (16)
# Thu:     Migrations & Schema tests (12)
# Fri:     Caching (10) + Error Handling (10) + validation
```

---

## Test Infrastructure Reuse

All modules will use the same proven patterns:

### Base Classes
```
platform/java/security/src/test/java/com/ghatana/platform/security/base/
├── SecurityEventloopTestBase.java (reuse for async tests)
└── (pattern template for other modules)
```

### Fixtures & Mocks
```
platform/java/[module]/src/test/java/com/ghatana/platform/[module]/fixtures/
├── [Module]TestFixture.java (builder pattern)
└── [Module]MockFactory.java (pre-configured mocks)
```

### Test Organization
- @DisplayName for clear test names
- @Nested classes for logical grouping
- AssertJ for typed assertions
- Mockito with lenient() for optional stubs

---

## Ghatana Compliance Checklist (All Modules)

### Type Safety ✅
- [ ] No implicit `any` types in TypeScript (N/A for Java)
- [ ] All Java parameters explicitly typed
- [ ] All function returns explicitly typed
- [ ] No unsafe casts (@SuppressWarnings only when unavoidable)

### Async Patterns ✅
- [ ] Extend EventloopTestBase for Promise-based tests
- [ ] Use `runPromise(() -> ...)` for async execution
- [ ] Never block the event loop
- [ ] Mock async methods to return Promises

### Documentation ✅
- [ ] All test classes have @doc.type/@doc.purpose/@doc.layer/@doc.pattern
- [ ] All significant tests have JavaDoc
- [ ] @DisplayName provides human-readable descriptions

### Testing Discipline ✅
- [ ] Unit tests for business logic
- [ ] Integration tests for boundaries
- [ ] Error cases explicitly tested
- [ ] Edge cases covered

### Build Health ✅
- [ ] Zero compilation errors
- [ ] Zero test failures
- [ ] Zero warnings (except expected Gradle config-cache)
- [ ] All tests pass with `./gradlew test`

---

## Weekly Metrics & Checkpoints

### Week 6 Target (Observability)
```
- Monday: 8/36 tests implemented (22%)
- Tuesday: 22/36 tests implemented (61%)
- Wednesday: 30/36 tests implemented (83%)
- Thursday: 36/36 tests implemented (100%)
- Friday: ✅ Build validation, all 52+ tests passing
```

### Week 7 Target (HTTP)
```
- Monday: 30/57 tests implemented (53%)
- Tuesday: 30/57 tests implemented (53%)
- Wednesday: 42/57 tests implemented (74%)
- Thursday: 52/57 tests implemented (91%)
- Friday: ✅ 57/57 implemented, all 73+ tests passing
```

### Week 8 Target (Database)
```
- Monday: 12/71 tests implemented (17%)
- Tuesday: 32/71 tests implemented (45%)
- Wednesday: 48/71 tests implemented (68%)
- Thursday: 62/71 tests implemented (87%)
- Friday: ✅ 71/71 implemented, all 89+ tests passing
```

### Phase 2 Completion
```
May 17 Target: 
- ✅ Security: 259 tests
- ✅ Observability: 52 tests
- ✅ HTTP: 73 tests
- ✅ Database: 89 tests
- ✅ TOTAL: 473+ tests
- ✅ BUILD: 0 failures, 0 warnings
```

---

## Success Criteria (Per Module)

### Observability Module
- [ ] 52 tests total, organized by category
- [ ] Metrics collection fully tested
- [ ] Trace export validated (Jaeger/ClickHouse)
- [ ] Health & readiness endpoints working
- [ ] Structured logging configured
- [ ] `./gradlew platform:java:observability:test` → All passing

### HTTP Module
- [ ] 73 tests total, covering all HTTP operations
- [ ] Server startup/shutdown tested
- [ ] Route matching validated
- [ ] Status codes correctly mapped
- [ ] Auth/AuthZ filters working
- [ ] `./gradlew platform:java:http:test` → All passing

### Database Module
- [ ] 89 tests total, covering CRUD and transactions
- [ ] Connection pool behavior validated
- [ ] Query execution comprehensive
- [ ] Transactions work correctly
- [ ] Migrations tested
- [ ] `./gradlew platform:java:database:test` → All passing

---

## Build Validation Commands

```bash
# Per-module validation
./gradlew platform:java:observability:test
./gradlew platform:java:http:test
./gradlew platform:java:database:test

# Full platform validation
./gradlew platform:java:test

# With detailed output
./gradlew platform:java:observability:test --console=plain

# Fresh build
./gradlew clean platform:java:observability:test
```

---

## Risk Mitigation

### Risk #1: Async Test Complexity
**Mitigation**: SecurityEventloopTestBase pattern proven with 259 tests, reuse template

### Risk #2: Mock Configuration
**Mitigation**: Create module-specific MockFactory with pre-configured objects, follow lenient() pattern

### Risk #3: Timeline Pressure
**Mitigation**: Focus on high-value test categories first (happy path, error cases, edge cases)

### Risk #4: Build Failures
**Mitigation**: Compile-only step before running full tests, incremental validation

---

## Team Assignments (Recommended)

**Java Platform Team**:
- Developer A: Observability metrics (12) + trace tests (10)
- Developer B: HTTP routing (16) + request/response (14)
- Developer C: Database connection (12) + query execution (20)

**Parallel Work**:
- QA: Validate edge cases as tests are added
- Architect: Code review to ensure patterns are followed
- DevOps: Set up continuous test reporting

---

## Definition of Done for Phase 2

✅ All 309+ tests pass without failure  
✅ Zero compilation errors across all modules  
✅ All test classes have @doc.* tags  
✅ All async tests extend EventloopTestBase  
✅ All fixtures use builder patterns (SecurityTestFixture model)  
✅ All assertions use AssertJ typed assertions  
✅ Build executes in <60 seconds  
✅ Code review approved per Ghatana standards  

---

## Next Immediate Steps (Week 6 Monday)

1. **Set up observability test infrastructure**
   - Create ObservabilityTestFixture.java (builder for Observable objects)
   - Create ObservabilityMockFactory.java (pre-configured mocks)
   - Create ObservabilityEventloopTestBase (if async operations needed)

2. **Implement metrics tests** (12 tests)
   - CounterMetricTest (3 tests)
   - GaugeMetricTest (3 tests)
   - HistogramMetricTest (3 tests)
   - SummaryMetricTest (3 tests)

3. **Build validation**
   - `./gradlew platform:java:observability:compileTestJava`
   - Verify imports and structure

4. **Expand to trace tests** (10 tests)
   - SpanCreationTest
   - TraceContextPropagationTest
   - BatchExportTest
   - SamplingPolicyTest

5. **Friday validation**
   - `./gradlew platform:java:observability:test --rerun-tasks`
   - Target: 48-52 tests passing

---

**Status**: Foundation locked, ready for team to execute Weeks 6-8  
**Confidence**: HIGH (pattern proven, infrastructure ready, clear targets)  
**Timeline**: May 17 completion achievable ✅

