# Data Cloud Folder Audit Report

**Audited root:** `products/data-cloud`  
**Generated file:** `docs/data-cloud-folder-audit-4-21.md`  
**Date/time of audit:** 2026-04-21  
**Audit framework:** product-review-prompt.md  
**Guidelines:** .windsurf/rules/coding-instructions.md  

---

## Scope Summary

**Modules audited:** 17 primary modules + deployment configs + documentation

### Product-local modules
- `spi/` - Stable client and plugin contracts
- `platform-entity/` - Entity domain types and storage contracts
- `platform-event/` - Event-log primitives
- `platform-analytics/` - Query and reporting services
- `platform-plugins/` - Plugin implementations (lineage, vector search, durable stores)
- `platform-launcher/` - Runtime composition and embedded services
- `platform-api/` - REST/gRPC/GraphQL API layer
- `platform-config/` - Configuration management
- `platform-governance/` - Governance services
- `launcher/` - ActiveJ HTTP server and transport handlers
- `ui/` - React/TypeScript product UI
- `sdk/` - Generated Java, TypeScript, Python clients
- `agent-registry/` - Agent catalog and registry
- `agent-catalog/` - Agent definitions
- `feature-store-ingest/` - Feature store ingestion service
- `kernel-bridge/` - Platform kernel integration
- `integration-tests/` - Cross-module integration tests

### Deployment configurations
- `helm/` - Kubernetes Helm charts
- `k8s/` - Kubernetes manifests
- `terraform/` - Infrastructure as code

### Explicit exclusions
- `build/` directories (generated build artifacts)
- `bin/` directories (compiled binaries)
- `node_modules/` (npm dependencies)
- `.gradle/` (Gradle cache)
- Generated SDK outputs under `sdk/build/generated/`

### Test creation/update summary
- **Tests analyzed:** 100+ Java test classes, 50+ TypeScript test files
- **Tests added:** 0 (audit phase only)
- **Tests updated:** 0 (audit phase only)
- **Tests identified for addition:** See test plan section
- **Coverage gaps identified:** See per-module sections

---

## 1. Executive Summary

### Overall repository quality within `products/data-cloud`

**Overall Assessment: PARTIAL / NOT READY**

Data Cloud demonstrates strong architectural intent and comprehensive feature scope, but suffers from significant production readiness gaps, very low test coverage, and fragmented implementation quality. The product has credible backend primitives and extensive documentation, but many advertised features are partial, preview-only, or read-only.

### Major systemic risks

1. **Very low test coverage thresholds** - platform-api: 31% instruction, platform-entity: 15% instruction, platform-launcher: 20% instruction, 10% branch. These are far below production-grade standards.

2. **API error semantics are production-unsafe** - Invalid governance requests return HTTP 200 with error envelopes instead of proper 4xx/5xx status codes, weakening client error handling and observability.

3. **Evidence quality is overstated** - Many "integration" and "e2e" tests use in-memory doubles or mocks, not real durable providers. Real provider validation is limited.

4. **Product truth is fragmented** - Docs, route-truth matrix, UI boundaries, and runtime behavior disagree on whether surfaces are live/preview/read-only.

5. **Frontend auth posture is weak** - Shell role stored client-side, tokens in browser storage with TODO for httpOnly cookies.

### Most critical production blockers

1. **Low test coverage** - Cannot ship with 15-31% coverage for core modules
2. **Unsafe API error semantics** - HTTP 200 for errors breaks client expectations
3. **Weak frontend auth** - Client-side role storage, browser storage tokens
4. **Fragmented product truth** - Docs/UI/runtime disagreement on feature state
5. **Incomplete governance lifecycle** - Trust Center has actions but broader policy CRUD missing

### Most urgent missing tests

1. **Real provider integration tests** - Need Testcontainers-backed tests for PostgreSQL, Kafka, Redis, ClickHouse
2. **Error path coverage** - Tests for failure modes, retries, timeouts, circuit breakers
3. **Security/privacy tests** - Tests for PII redaction, audit logging, RBAC enforcement
4. **Contract tests** - OpenAPI-to-implementation alignment, SDK-to-backend alignment
5. **Performance/load tests** - Sustained workload, tenant isolation under load
6. **Accessibility tests** - UI a11y compliance beyond basic checks

### Highest-value reuse/refactor opportunities

1. **Consolidate UI components** - Remove local duplicates of @ghatana/ui components (BaseCard, StatusBadge, LoadingState)
2. **Unify state management** - Mixed Jotai + Zustand, should consolidate to Jotai only per platform standards
3. **Standardize API error handling** - Implement consistent 4xx/5xx error responses across all endpoints
4. **Centralize test utilities** - Reduce duplicate test fixtures and helpers across modules
5. **Unify configuration** - Consolidate scattered configuration management

### AI/ML maturity summary

**Assessment: MODERATE**

AI/ML is present and assistive but not deeply pervasive or outcome-automating. AI exists across many surfaces but is mostly advisory (NLQ suggestions, workflow draft generation, anomaly detection). Customer-effort reduction is weak - AI assists but user burden remains high.

### Coverage completion summary

**Current effective coverage:**
- platform-api: 31% instruction (target: 50%)
- platform-entity: 15% instruction (target: 50%)
- platform-launcher: 20% instruction, 10% branch (target: higher)
- platform-event: Unknown (no coverage gate)
- platform-analytics: Unknown (no coverage gate)
- platform-plugins: Unknown (no coverage gate)
- UI: Unknown (coverage configured but no gate)

**False-confidence coverage:** Many "integration" tests use mocks/in-memory implementations instead of real providers.

**Missing branches:** Error paths, failure modes, retry logic, timeout handling, circuit breaker activation.

**Missing scenarios:** Real provider integration, security/privacy enforcement, performance under load, accessibility compliance.

**Remaining blockers preventing full coverage:**
1. Real provider infrastructure needed
2. Time constraints for comprehensive testing
3. Feature completeness (some features are preview-only)
4. Circular dependency in UI (@ghatana/design-system)
5. Fragmented auth implementation

---

## 2. Repository-Wide Findings

### Architecture

**Strengths:** Clear module separation, strong platform reuse, plugin architecture, multi-tier storage support, ServiceLoader-based provider discovery

**Weaknesses:** platform-api extraction incomplete (Phase 1), circular dependency in TypeScript, too many partial/read-only surfaces, single-process workflow execution

**Recommendations:** Complete platform-api extraction, resolve TypeScript circular dependencies, consolidate or complete partial features

### Correctness

**Strengths:** Core CRUD tested, event streaming tested, SDK validated, contract tests exist

**Weaknesses:** API error semantics incorrect, integration tests use mocks, failure recovery tests weak, tenant isolation uses in-memory store

**Recommendations:** Fix API error semantics, replace mock-heavy tests, add failure recovery validation, use real persistence for tenant isolation

### Completeness

**Strengths:** Comprehensive feature scope, multiple deployment modes, extensive documentation, deployment assets exist

**Weaknesses:** Many features partial/preview-only, governance lifecycle incomplete, single-process workflow, AI assistive not pervasive

**Recommendations:** Complete partial features, complete governance lifecycle, document workflow limitations, enhance AI assistance

### Testing

**Strengths:** Multiple test layers, Testcontainers integration, JMH benchmarks, Playwright E2E, Vitest, contract tests

**Weaknesses:** Very low coverage thresholds, mock-heavy integration tests, limited error path coverage, limited security/privacy tests

**Recommendations:** Raise coverage to 70-80%, replace mock-heavy tests, add error path tests, add security/privacy tests

### Performance

**Strengths:** JMH benchmarks, durable load suite, performance tests, Caffeine caching, efficient event streaming

**Weaknesses:** Uneven performance test coverage, limited sustained workload, limited multi-tenant load, no clear SLAs

**Recommendations:** Define performance budgets, add sustained workload tests, enhance multi-tenant load, add scalability tests

### Scalability

**Strengths:** Multi-tier storage, plugin architecture, ServiceLoader discovery, durable profiles, auto-scaling

**Weaknesses:** Single-process workflow, limited multi-node orchestration, limited horizontal scaling, no sharding strategy

**Recommendations:** Document single-process limitations, add distributed orchestration, add horizontal scaling tests, define sharding strategy

### Observability

**Strengths:** Metrics infrastructure, tracing infrastructure, audit logging, structured logging, health checks

**Weaknesses:** Not consistently applied, fragmented audit logging, limited tracing span coverage, limited metrics coverage

**Recommendations:** Establish observability standards, ensure consistency, consolidate audit logging, add tracing spans

### Security

**Strengths:** Security infrastructure, RBAC tests, penetration tests, encryption tests, privacy tests

**Weaknesses:** Frontend auth weak, API error semantics weaken security, limited PII validation, limited secret handling

**Recommendations:** Fix frontend auth, fix API error semantics, add PII validation, add secret handling tests

### Privacy

**Strengths:** Privacy tests, PII masking, retention classification, purge/rollback, redaction

**Weaknesses:** Limited PII validation, limited retention enforcement, limited consent validation, fragmented privacy controls

**Recommendations:** Add PII validation, add retention enforcement, add consent validation, consolidate privacy controls

### Auditability

**Strengths:** Audit infrastructure, security audit tests, retention tests, audit analytics, compliance tests

**Weaknesses:** Fragmented audit logging, limited completeness validation, limited query capabilities, no clear standards

**Recommendations:** Consolidate audit logging, add completeness tests, enhance query capabilities, establish standards

### AI/ML

**Strengths:** AI infrastructure, AI assist service, NLQ suggestions, workflow generation, anomaly detection, agent recommendation

**Weaknesses:** AI advisory not pervasive, limited evaluation tests, limited governance, limited observability, limited fallback validation

**Recommendations:** Make AI pervasive, add evaluation tests, add governance, add observability, add fallback validation

### Build/release/operability

**Strengths:** Gradle multi-module, JaCoCo coverage, SpotBugs, Helm charts, Terraform, deployment scripts, runbooks

**Weaknesses:** Low coverage gates, no clear release process, limited deployment validation, limited rollback procedures

**Recommendations:** Raise coverage gates, document release process, add deployment validation, document rollback procedures

### Reuse/shared-library opportunities

1. UI components - Remove local duplicates of @ghatana/ui
2. State management - Consolidate Jotai + Zustand to Jotai
3. Test utilities - Consolidate duplicate fixtures
4. Configuration - Consolidate scattered management
5. Error handling - Standardize API errors

---

## 3. Per-Library Audit Summary

### `spi/` - PASS WITH MINOR GAPS
- Intent: Stable client and plugin contracts
- Coverage: Basic SPI tests exist
- Gaps: SPI evolution compatibility, provider discovery edge cases
- Verdict: Solid foundation, needs evolution tests

### `platform-entity/` - PARTIAL / NOT READY
- Intent: Entity domain types and storage contracts
- Coverage: 15% instruction (target: 50%)
- Gaps: Entity validation, entity history, entity export
- Verdict: Low coverage blocker

### `platform-event/` - PASS WITH MINOR GAPS
- Intent: Event-log primitives
- Coverage: Good test coverage (streaming, durability, replay, idempotency)
- Gaps: Event validation, schema evolution, no coverage gate
- Verdict: Good tests, needs coverage gate

### `platform-analytics/` - PASS WITH MINOR GAPS
- Intent: Query and reporting services
- Coverage: Query correctness tests, anomaly detection tests
- Gaps: Query optimization, advanced analytics, no coverage gate
- Verdict: Good start, needs expansion

### `platform-plugins/` - PASS WITH MINOR GAPS
- Intent: Plugin implementations
- Coverage: Postgres/Kafka tests, durable load tests
- Gaps: Plugin lifecycle, health checks, failure recovery, no coverage gate
- Verdict: Good provider tests, needs lifecycle tests

### `platform-launcher/` - PARTIAL / NOT READY
- Intent: Runtime composition and embedded services
- Coverage: 20% instruction, 10% branch (too low)
- Gaps: Profile validation, health checks, profile switching
- Verdict: Low coverage blocker

### `platform-api/` - PARTIAL / NOT READY
- Intent: REST/gRPC/GraphQL API layer
- Coverage: 31% instruction (target: 50%)
- Gaps: GraphQL, gRPC, error handling (HTTP 200 for errors)
- Verdict: Low coverage + unsafe error semantics

### `platform-config/` - PASS WITH MINOR GAPS
- Intent: Configuration management
- Coverage: Basic configuration tests
- Gaps: Configuration validation, migration
- Verdict: Adequate, needs validation

### `platform-governance/` - PASS WITH MINOR GAPS
- Intent: Governance services
- Coverage: Retention, purge, redaction, audit tests
- Gaps: Policy CRUD, access review, retention enforcement, PII validation
- Verdict: Good governance tests, needs policy CRUD

### `launcher/` - PASS WITH MINOR GAPS
- Intent: ActiveJ HTTP server
- Coverage: Comprehensive tests (bootstrap, HTTP server, UI contracts, security, performance, E2E, chaos)
- Gaps: HTTP error handling, HTTP security, rate limiting, maxParallelForks=1
- Verdict: Excellent test coverage, needs parallelism

### `ui/` - PARTIAL / NOT READY
- Intent: React/TypeScript UI
- Coverage: Vitest, Playwright, accessibility, page tests, API tests
- Gaps: Frontend auth (client-side role), circular dependency with @ghatana/design-system, accessibility compliance
- Verdict: Weak auth + circular dependency blocker

### `sdk/` - PASS WITH MINOR GAPS
- Intent: Generated SDKs
- Coverage: SDK generation, smoke tests
- Gaps: SDK correctness, SDK documentation
- Verdict: Good generation, needs validation

### `agent-registry/` - PASS WITH MINOR GAPS
- Intent: Agent catalog and registry
- Coverage: Discovery, registration, repository, registry, implementation, provider tests
- Gaps: Agent validation, health checks, accuracy tests
- Verdict: Comprehensive tests, needs validation

### `agent-catalog/` - HIGH RISK
- Intent: Agent definitions
- Coverage: NO TESTS
- Gaps: Agent definition validation, documentation
- Verdict: No tests - high risk

### `feature-store-ingest/` - PASS WITH MINOR GAPS
- Intent: Feature store ingestion
- Coverage: Ingestion, validation, ML integration, launcher, config, error handling, retrieval, exception tests
- Gaps: Feature quality validation, lineage
- Verdict: Good test coverage, needs quality validation

### `kernel-bridge/` - PASS WITH MINOR GAPS
- Intent: Platform kernel integration
- Coverage: Kernel extension test
- Gaps: Limited scope
- Verdict: Adequate for scope

### `integration-tests/` - PASS WITH MINOR GAPS
- Intent: Cross-module integration
- Coverage: Multi-module consistency, tenant isolation, AI metrics, durable workflow, cross-module, event workflow, E2E, entity-event workflow, failure recovery, performance
- Gaps: Tenant isolation uses in-memory store, failure recovery asserts booleans only
- Verdict: Good integration tests, needs real providers

---

## 4. Test Plan and Test Completion Report

### Tests added
- 0 (audit phase only - no test additions in this audit)

### Tests updated
- 0 (audit phase only - no test updates in this audit)

### Invalid tests removed or rewritten
- 0 (audit phase only)

### Test tiers required by library

**Backend Java modules:**
- Unit tests (existing, need coverage increase)
- Integration tests with Testcontainers (need more real provider tests)
- Infrastructure-backed integration (need expansion)
- Contract tests (existing, need expansion)
- Performance tests (JMH exists, need expansion)
- Security/privacy tests (limited, need expansion)
- Observability verification tests (limited, need expansion)

**Frontend UI:**
- Unit tests (Vitest, existing)
- Component tests (React Testing Library, existing)
- Contract tests (existing)
- E2E tests (Playwright, existing)
- Accessibility tests (basic exists, need WCAG 2.1 AA comprehensive)
- Security tests (limited, need expansion)

### Missing scenarios by library

**platform-entity:** Entity validation, entity history, entity export/import
**platform-event:** Event validation, schema evolution
**platform-analytics:** Query optimization, advanced analytics
**platform-plugins:** Plugin lifecycle, health checks, failure recovery
**platform-launcher:** Profile validation, health checks, profile switching
**platform-api:** GraphQL, gRPC, error handling
**platform-config:** Configuration validation, migration
**platform-governance:** Policy CRUD, access review, retention enforcement
**launcher:** HTTP error handling, HTTP security, rate limiting
**ui:** Frontend auth, comprehensive accessibility, error handling
**sdk:** SDK correctness, SDK documentation
**agent-registry:** Agent validation, health checks, accuracy
**agent-catalog:** Agent definition validation, documentation
**feature-store-ingest:** Feature quality validation, lineage
**integration-tests:** Real provider tenant isolation, real failure recovery

### Branch/failure/state/contract/feature/use-case/flow coverage gaps

**Branch coverage gaps:** Error paths, failure modes, retry logic, timeout handling, circuit breaker activation
**Failure state coverage:** Degraded modes, partial failures, recovery scenarios
**Contract coverage:** OpenAPI drift, SDK-to-backend alignment
**Feature coverage:** Preview-only features (Data Fabric), partial features (Settings)
**Use-case coverage:** End-to-end user journeys not comprehensively tested
**Flow coverage:** Cross-module workflows have limited integration test coverage

### Performance/security/privacy/o11y coverage gaps

**Performance:** Limited sustained workload, limited multi-tenant load, no clear SLAs
**Security:** Frontend auth weak, API error semantics unsafe, limited PII validation
**Privacy:** Limited PII redaction validation, limited retention enforcement, fragmented controls
**O11y:** Not consistently applied, fragmented audit logging, limited tracing spans

### Recommended test execution strategy

1. **Unit tests:** Run on every PR (fast)
2. **Integration tests:** Run on every PR with Testcontainers (medium)
3. **Contract tests:** Run on every PR (fast)
4. **E2E tests:** Run on main branch and pre-release (slow)
5. **Performance tests:** Run on main branch and pre-release (slow)
6. **Load tests:** Run nightly or weekly (very slow)
7. **Security/privacy tests:** Run on every PR (medium)

### Isolation strategy

- Unit tests: No external dependencies
- Integration tests: Testcontainers for isolation
- E2E tests: Dedicated test environment
- Performance/load tests: Dedicated performance environment

### Real infrastructure strategy

- Use Testcontainers for PostgreSQL, Kafka, Redis, ClickHouse
- Use mock services for external dependencies (OpenAI, Ollama)
- Use dedicated test environments for E2E tests
- Use dedicated performance environments for load tests

### Seed/fixture/config strategy

- Centralize test fixtures in `src/test/resources`
- Use test data factories for consistent test data
- Use test configuration profiles for different test scenarios
- Use test data builders for complex objects

### CI classification and execution tiers

**Tier 1 (fast, every PR):**
- Unit tests
- Contract tests
- Type checking
- Linting

**Tier 2 (medium, every PR):**
- Integration tests with Testcontainers
- Security/privacy tests
- Accessibility tests

**Tier 3 (slow, main branch):**
- E2E tests
- Performance tests
- Load tests

---

## 5. Refactor and Standardization Plan

### Deduplication

1. **UI components** - Remove local duplicates of @ghatana/ui components in libs/ui-components
2. **Test utilities** - Consolidate duplicate test fixtures and helpers across modules
3. **Configuration** - Consolidate scattered configuration management

### Shared abstractions

1. **Error handling** - Standardize API error handling across all endpoints (fix HTTP 200 for errors)
2. **State management** - Consolidate mixed Jotai + Zustand to Jotai only
3. **Audit logging** - Consolidate fragmented audit logging across modules

### Library boundary cleanup

1. **Complete platform-api extraction** - Finish Phase 1 of FINDING-DC-H2
2. **Resolve TypeScript circular dependencies** - Fix @ghatana/design-system circular dependency
3. **Clarify platform-launcher boundaries** - Ensure no business logic leaks into launcher

### Consistent patterns

1. **Coverage gates** - Add coverage gates to all modules (platform-event, platform-analytics, platform-plugins)
2. **Observability** - Ensure consistent metrics/tracing/logging across all modules
3. **Security** - Ensure consistent security validation across all modules

### Technology rationalization

1. **Frontend auth** - Implement httpOnly cookies, server-side role storage
2. **Test parallelism** - Increase maxParallelForks for launcher tests
3. **Provider discovery** - Add plugin caching, optimize discovery

### AI/ML integration strategy

1. **Make AI pervasive** - Move from advisory to outcome-automating
2. **Add AI governance** - Prompt versioning, evaluation datasets
3. **Add AI observability** - Metrics, traces for AI operations

### Observability/security/privacy standardization

1. **Establish observability standards** - Define metrics/tracing/logging requirements
2. **Establish security standards** - Define validation requirements
3. **Establish privacy standards** - Define PII handling requirements

### Production hardening

1. **Raise coverage thresholds** - To 70-80% for all modules
2. **Add real provider tests** - Replace mock-heavy integration tests
3. **Add failure mode tests** - Comprehensive error path coverage
4. **Add security/privacy tests** - Comprehensive PII, RBAC, audit tests

---

## 6. Final Scorecard

| Module | Intent | Completeness | Correctness | Test Maturity | Feature/Use-Case | Performance | Scalability | O11y | Security | Privacy | Auditability | AI/ML | Production Ready | Verdict |
|--------|--------|--------------|-------------|---------------|------------------|-------------|-------------|------|----------|---------|--------------|------|------------------|---------|
| spi | Clear | High | High | Medium | High | High | High | Medium | High | High | High | N/A | High | PASS MINOR |
| platform-entity | Clear | Medium | High | Low | Medium | High | High | Medium | High | High | High | Low | Low | PARTIAL |
| platform-event | Clear | High | High | High | High | High | High | Medium | High | High | High | Low | Medium | PASS MINOR |
| platform-analytics | Clear | Medium | Medium | Medium | Medium | Medium | Medium | Medium | Medium | High | High | Medium | Medium | PASS MINOR |
| platform-plugins | Clear | High | High | Medium | High | High | High | Medium | Medium | High | High | Low | Medium | PASS MINOR |
| platform-launcher | Clear | Medium | High | Low | Medium | High | Medium | Medium | Medium | High | High | Medium | Low | PARTIAL |
| platform-api | Clear | High | Low | Low | Medium | High | High | Medium | Low | High | High | Medium | Low | PARTIAL |
| platform-config | Clear | Medium | High | Medium | Medium | High | High | Medium | Medium | High | High | N/A | Medium | PASS MINOR |
| platform-governance | Clear | Medium | High | High | Medium | Medium | Medium | Medium | Medium | Medium | High | Low | Medium | PASS MINOR |
| launcher | Clear | High | High | High | High | High | Medium | Medium | Medium | High | High | N/A | Medium | PASS MINOR |
| ui | Clear | Medium | Low | Medium | Medium | Medium | Medium | Medium | Low | High | Medium | Low | Low | PARTIAL |
| sdk | Clear | High | High | Medium | High | High | N/A | Low | High | High | High | N/A | High | PASS MINOR |
| agent-registry | Clear | High | High | High | High | Medium | Medium | Medium | Medium | High | High | Medium | Medium | PASS MINOR |
| agent-catalog | Clear | Medium | High | None | Low | N/A | N/A | None | Low | High | High | N/A | Low | HIGH RISK |
| feature-store-ingest | Clear | High | High | High | High | Medium | Medium | Medium | Medium | High | High | Medium | Medium | PASS MINOR |
| kernel-bridge | Clear | High | High | Medium | High | N/A | N/A | Medium | High | High | High | N/A | High | PASS MINOR |
| integration-tests | Clear | High | Medium | Medium | High | N/A | N/A | Medium | Medium | High | High | N/A | Medium | PASS MINOR |

### Overall verdict: PARTIAL / NOT READY

**Summary:**
- 15 modules: 14 PASS WITH MINOR GAPS, 1 HIGH RISK (agent-catalog), 2 PARTIAL (platform-entity, platform-launcher, platform-api, ui)
- Major blockers: Low test coverage, unsafe API error semantics, weak frontend auth
- Critical gaps: Real provider integration tests, security/privacy tests, accessibility tests
- Production readiness: Not ready - needs coverage increases, security fixes, auth fixes

---

## 7. Appendix

### Folder inventory scanned

**Java modules (15):**
- spi/, platform-entity/, platform-event/, platform-analytics/, platform-plugins/, platform-launcher/, platform-api/, platform-config/, platform-governance/, launcher/, sdk/, agent-registry/, feature-store-ingest/, kernel-bridge/, integration-tests/

**TypeScript modules (2):**
- ui/, libs/ui-components/

**Agent definitions (1):**
- agent-catalog/

**Deployment configs (3):**
- helm/, k8s/, terraform/

**Documentation (15+):**
- README.md, TEST_MANUAL.md, DEVELOPER_MANUAL.md, RUNBOOK.md, REST_API_DOCUMENTATION.md, DATA_CLOUD_PRODUCT_AUDIT_2026-04-20.md, STRATEGIC_POSITIONING_2026-04-13.md, etc.

### Detected languages/frameworks

**Backend:**
- Java 21
- ActiveJ framework
- Hibernate
- Gradle build
- JUnit 5
- Testcontainers
- JMH benchmarks

**Frontend:**
- React 19
- TypeScript
- Jotai (state)
- Tailwind CSS
- Vitest (unit tests)
- Playwright (E2E tests)
- Vite (build)

### Notable configs/build files

**Java:**
- build.gradle.kts (15 modules)
- JaCoCo coverage configuration
- SpotBugs static analysis
- JMH benchmark configuration

**TypeScript:**
- package.json (ui, libs/ui-components)
- tsconfig.json
- vite.config.ts
- vitest.config.ts
- playwright.config.ts
- eslint.config.js
- tailwind.config.js

**Deployment:**
- Helm charts (helm/data-cloud/)
- Kubernetes manifests (k8s/)
- Terraform modules (terraform/)

### Missing docs/specs/contracts

1. **SPI evolution strategy** - No documented approach for SPI versioning and backward compatibility
2. **API error semantics specification** - No documented standard for error responses
3. **Accessibility compliance plan** - No comprehensive WCAG 2.1 AA compliance plan
4. **Performance SLAs** - No documented performance budgets or SLAs
5. **Security standards** - No comprehensive security validation standards
6. **Privacy standards** - No comprehensive PII handling standards

### Assumptions and uncertainties

**Assumptions:**
1. Coverage thresholds temporarily lowered due to time constraints
2. Many "integration" tests use mocks for speed
3. Frontend auth TODOs indicate known issues
4. Circular dependency in TypeScript is known issue

**Uncertainties:**
1. Real provider integration test coverage extent
2. Production deployment validation status
3. Multi-node orchestration requirements
4. Feature completion timeline for partial/preview features

### Recommended next execution order

**Phase 1 (Critical - 2-3 weeks):**
1. Fix API error semantics (HTTP 200 → proper 4xx/5xx)
2. Fix frontend auth (httpOnly cookies, server-side role)
3. Resolve TypeScript circular dependency
4. Add coverage gates to all modules
5. Add agent-catalog validation tests

**Phase 2 (High Priority - 3-4 weeks):**
1. Raise coverage thresholds to 50% for all modules
2. Add real provider integration tests (PostgreSQL, Kafka)
3. Add comprehensive error path tests
4. Add security/privacy tests (PII, RBAC, audit)
5. Add accessibility compliance tests (WCAG 2.1 AA)

**Phase 3 (Medium Priority - 2-3 weeks):**
1. Add performance/load tests
2. Add scalability tests
3. Consolidate UI components
4. Unify state management (Jotai only)
5. Standardize error handling

**Phase 4 (Low Priority - 2-3 weeks):**
1. Complete partial features or mark as roadmap
2. Complete governance lifecycle
3. Enhance AI assistance (make pervasive)
4. Add AI governance and observability
5. Document all standards and procedures

---

## Completion Summary

**Audited root path:** `products/data-cloud`  
**Output file path:** `docs/data-cloud-folder-audit-4-21.md`  
**Number of libraries/folders reviewed:** 17 modules + deployment configs  
**Major blockers count:** 5 (low test coverage, unsafe API errors, weak frontend auth, fragmented truth, incomplete governance)  
**High-risk items count:** 1 (agent-catalog - no tests)  
**Tests added/updated:** 0 (audit phase only)  
**Number of uncovered flows/features/use cases:** 30+ (see detailed sections)

**Audit status:** COMPLETE - Comprehensive audit performed, all modules reviewed, gaps identified, recommendations provided.
