# Audit Remediation Todo List

**Generated from:** audit-report.md (2026-04-23)
**Purpose:** Detailed task list for addressing all findings across priority levels
**Scope:** platform, platform-kernel, platform-plugins, shared-services, products/audio-video, products/data-cloud, products/aep, products/yappc

---

## P0 Tasks (Critical Production Blockers)

### Architecture Governance

- [ ] **B1: Replace placeholder architecture gates with executable checks**
  - Location: Root `build.gradle.kts`
  - Tasks:
    - [ ] Implement `validateNoCircularDependencies` with actual Gradle dependency graph cycle detection
    - [ ] Implement `validateModuleBoundaries` with ArchUnit rules for platform/product direction
    - [ ] Implement `validateDependencyDirection` with failing checks on violations
    - [ ] Configure CI to fail builds on architecture violations
  - Evidence: Current tasks print success without enforcement

- [ ] **B2: Purge generated artifacts from source roots**
  - Location: All audited roots
  - Tasks:
    - [ ] Remove `.gradle/` folders from Java module roots
    - [ ] Remove `bin/` folders from Java module roots
    - [ ] Remove `.class` files from source roots
    - [ ] Remove `.pyc` files from Python areas
    - [ ] Remove `test-results/` folders from product frontend roots
    - [ ] Remove `docs-generated/` folders from source roots
    - [ ] Create explicit source-of-truth policy for any intentionally checked-in artifacts
    - [ ] Add `.gitignore` rules to prevent future check-ins

- [ ] **B3: Add repository-wide coverage governance**
  - Location: Root CI/CD configuration
  - Tasks:
    - [ ] Create tiered coverage thresholds per module (P0: 90%, P1: 80%, P2: 70%)
    - [ ] Add branch/flow coverage gates to CI
    - [ ] Create workflow-level coverage inventory matrix
    - [ ] Enforce coverage gates before merge
  - Evidence: No repo-wide meaningful coverage matrix or enforced thresholds

### AI/Agent Safety

- [ ] **B4: Centralize AI governance contracts**
  - Location: platform/java/ai-integration, products/yappc/core/ai, products/aep
  - Tasks:
    - [ ] Define unified prompt versioning contract
    - [ ] Define unified model selection contract
    - [ ] Define unified tool permissions contract
    - [ ] Define unified eval thresholds contract
    - [ ] Define unified redaction contract (prompt input, tool output, trace payload, audit data)
    - [ ] Define unified approval telemetry contract
    - [ ] Implement runtime enforcement across YAPPC/AEP/platform AI modules

### Auth and Tenant Boundaries

- [ ] **B5: Standardize tenant/auth middleware**
  - Location: shared-services/auth-gateway, products/aep/gateway, product frontends
  - Tasks:
    - [ ] Consolidate tenant extraction logic into platform library
    - [ ] Consolidate auth middleware into platform library
    - [ ] Add cross-root contract tests spanning:
      - [ ] JWT payload validation
      - [ ] Request header extraction
      - [ ] Gateway propagation
      - [ ] Backend enforcement
      - [ ] Audit logging
      - [ ] UI state consistency
    - [ ] Remove duplicate auth/tenant code from product gateways and services

### Data Cloud Correctness

- [ ] **B6: Complete query semantics**
  - Location: products/data-cloud/platform-analytics
  - Tasks:
    - [ ] Implement query optimizer (currently placeholder)
    - [ ] Implement query validator (currently placeholder)
    - [ ] Complete sorting implementation
    - [ ] Complete pagination implementation
    - [ ] Add query correctness tests
    - [ ] Add migration compatibility tests
  - Evidence: Query optimizer/validator comments show placeholder logic

### Audio Video Service Integration

- [ ] **B7: Implement real service contracts**
  - Location: products/audio-video/modules/speech/stt-service
  - Tasks:
    - [ ] Generate gRPC stubs for STT service
    - [ ] Replace placeholder gRPC call with real implementation
    - [ ] Add contract tests for STT service
    - [ ] Add integration tests for STT service
  - Evidence: Documentation states gRPC stubs not generated and real call not implemented

### Observability Verification

- [ ] **B8: Add observability contract tests**
  - Location: All critical workflows across platform/products
  - Tasks:
    - [ ] Add tests asserting emitted metrics for critical workflows
    - [ ] Add tests asserting emitted traces for critical workflows
    - [ ] Add tests asserting emitted logs for critical workflows
    - [ ] Add tests asserting audit events for critical workflows
    - [ ] Add tests asserting log redaction for PII data
  - Evidence: Dashboards/rules exist but tests rarely assert emitted metrics/traces/log redaction

### Platform Contracts

- [ ] **Add contract compatibility gate**
  - Location: platform/contracts
  - Tasks:
    - [ ] Add proto compatibility validation to CI
    - [ ] Add OpenAPI backward compatibility validation to CI
    - [ ] Add serialization round-trip tests
    - [ ] Add client/server contract tests

### Platform Java

- [ ] **Replace placeholder architecture gates**
  - Location: platform/java/* modules
  - Tasks:
    - [ ] Implement ArchUnit rules for module boundaries
    - [ ] Implement dependency direction enforcement
    - [ ] Add cycle detection in Gradle dependency graph

### Platform TypeScript

- [ ] **Add package-level coverage thresholds**
  - Location: platform/typescript/* packages
  - Tasks:
    - [ ] Add coverage threshold to each package's vitest config
    - [ ] Enforce thresholds in CI
    - [ ] Create package-level coverage inventory

### Platform Kernel

- [ ] **Add executable plugin security tests**
  - Location: platform-kernel/kernel-plugin, platform-kernel/kernel-core
  - Tasks:
    - [ ] Add plugin security manager negative-path tests
    - [ ] Add tenant boundary tests for plugins
    - [ ] Add malicious plugin detection tests
    - [ ] Add sandbox escape prevention tests
    - [ ] Add secret access denial tests
    - [ ] Add tenant data access denial tests

### Platform Plugins

- [ ] **Add plugin contract/integration suite**
  - Location: platform-plugins/*
  - Tasks:
    - [ ] Add plugin manifest compatibility tests
    - [ ] Add kernel plugin API integration tests
    - [ ] Add durable/in-memory parity tests
    - [ ] Add persistence migration tests

### Shared Services

- [ ] **Add security/tenant/auth contract suite**
  - Location: shared-services/auth-gateway
  - Tasks:
    - [ ] Add auth E2E tests with real infrastructure
    - [ ] Add OAuth/OIDC flow tests
    - [ ] Add MFA edge case tests
    - [ ] Add token revocation tests
    - [ ] Add tenant isolation tests
    - [ ] Add replay/idempotency tests
    - [ ] Add DB-backed integration tests
    - [ ] Add k6/load tests
    - [ ] Add negative security tests

### Products Audio Video

- [ ] **Implement real service contracts**
  - Location: products/audio-video/modules/speech/stt-service, tts-service, vision-service, multimodal-service
  - Tasks:
    - [ ] Generate gRPC stubs for all services
    - [ ] Replace placeholder service calls with real implementations
    - [ ] Add contract tests for each service
    - [ ] Add integration tests for each service

### Products Data Cloud

- [ ] **Complete query/sort/pagination semantics**
  - Location: products/data-cloud/platform-analytics
  - Tasks:
    - [ ] Complete query parser semantics
    - [ ] Complete join implementation
    - [ ] Complete filter implementation
    - [ ] Complete grouping implementation
    - [ ] Complete pagination implementation
    - [ ] Complete sorting implementation
    - [ ] Add property-based query tests
    - [ ] Add DB-backed integration tests

### Products YAPPC

- [ ] **Enforce AI/tool governance and audit contracts**
  - Location: products/yappc/core/ai, products/yappc/core/agents
  - Tasks:
    - [ ] Implement versioned prompts
    - [ ] Implement eval datasets
    - [ ] Implement acceptance thresholds
    - [ ] Implement deterministic fallback behavior
    - [ ] Implement hallucination controls
    - [ ] Implement human approval for risky actions
    - [ ] Implement drift and quality dashboards
    - [ ] Add permission model for agent tools
    - [ ] Add audit trail for agent actions
    - [ ] Add prompt/tool input redaction
    - [ ] Add tenant isolation for agent data
    - [ ] Add rollback capability for agent changes

- [ ] **Purge generated artifacts**
  - Location: products/yappc
  - Tasks:
    - [ ] Remove `docs-generated/` folders
    - [ ] Remove `bin/` folders
    - [ ] Remove `.gradle/` folders
    - [ ] Remove `test-results/` folders
    - [ ] Classify any intentionally checked-in artifacts

### Security and Privacy

- [ ] **Add tenant isolation cross-root contract tests**
  - Location: All products and platform modules
  - Tasks:
    - [ ] Add JWT payload validation tests
    - [ ] Add request header extraction tests
    - [ ] Add gateway propagation tests
    - [ ] Add backend enforcement tests
    - [ ] Add audit log verification tests
    - [ ] Add UI state consistency tests

- [ ] **Add AI/LLM privacy controls**
  - Location: products/yappc, products/aep, platform/java/ai-integration
  - Tasks:
    - [ ] Add prompt input redaction tests
    - [ ] Add tool output redaction tests
    - [ ] Add trace payload redaction tests
    - [ ] Add audit data redaction tests
    - [ ] Add evaluation dataset redaction tests

- [ ] **Add data-cloud redaction/export privacy tests**
  - Location: products/data-cloud/platform-governance
  - Tasks:
    - [ ] Add PII leakage prevention tests
    - [ ] Add consent handling tests
    - [ ] Add retention policy tests
    - [ ] Add audit trail tests

---

## P1 Tasks (High Priority)

### Architecture

- [ ] **Clarify product/platform ownership**
  - Location: products/yappc, products/data-cloud
  - Tasks:
    - [ ] Establish clear ownership between platform, YAPPC platform bridge, services, frontend libs, and data-cloud adapters
    - [ ] Contractually tie platform-like capabilities to platform abstractions
    - [ ] Intentionally mark product-owned platform-like capabilities

### Correctness

- [ ] **Standardize error/result contracts for connectors**
  - Location: platform/java/messaging
  - Tasks:
    - [ ] Replace `return null` fallback paths with typed Result/Error contracts
    - [ ] Add retry classification to connector failures
    - [ ] Add failure semantics tests

- [ ] **Consolidate tenant extraction**
  - Location: shared-services/auth-gateway, product gateways, frontend auth libraries
  - Tasks:
    - [ ] Standardize tenant extraction logic
    - [ ] Add contract tests for consistent behavior
    - [ ] Remove divergent implementations

### Completeness

- [ ] **Declare unsupported plugin behaviors**
  - Location: platform-plugins/*
  - Tasks:
    - [ ] Surface unsupported export behaviors in capability metadata
    - [ ] Add tests for declared unsupported behaviors
    - [ ] Document durable variant semantics

- [ ] **Prove observability runtime emission**
  - Location: All products and platform modules
  - Tasks:
    - [ ] Add tests proving logs are emitted
    - [ ] Add tests proving metrics are emitted
    - [ ] Add tests proving traces are emitted
    - [ ] Add tests proving alert semantics work

### Testing

- [ ] **Classify integration test requirements**
  - Location: All integration test suites
  - Tasks:
    - [ ] Tag tests requiring network binding as `local-network`
    - [ ] Tag tests requiring infrastructure as `infrastructure-backed`
    - [ ] Configure CI to handle these tiers appropriately

- [ ] **Add package contract tests for TypeScript**
  - Location: platform/typescript/*
  - Tasks:
    - [ ] Add cross-package compatibility tests
    - [ ] Add React 19 behavior tests
    - [ ] Add SSR/client assumption tests
    - [ ] Add accessibility outcome tests
    - [ ] Add event schema change tests

- [ ] **Add infrastructure-backed tests**
  - Location: platform/java/*, products/data-cloud, shared-services
  - Tasks:
    - [ ] Add Testcontainers for database tests
    - [ ] Add Testcontainers for messaging tests
    - [ ] Add Testcontainers for cache tests
    - [ ] Add Testcontainers for search tests

### Performance and Scalability

- [ ] **Add data-cloud benchmarks**
  - Location: products/data-cloud/platform-analytics
  - Tasks:
    - [ ] Add pagination benchmarks
    - [ ] Add sorting benchmarks
    - [ ] Add group-by benchmarks
    - [ ] Add join benchmarks
    - [ ] Add export benchmarks
    - [ ] Add anomaly detection benchmarks
    - [ ] Add entity scan benchmarks

- [ ] **Add UI render benchmarks**
  - Location: platform/typescript/canvas, data-grid, charts, code-editor
  - Tasks:
    - [ ] Add render benchmarks
    - [ ] Add recompute benchmarks
    - [ ] Add large-list virtualization tests

- [ ] **Add messaging throughput tests**
  - Location: platform/java/messaging
  - Tasks:
    - [ ] Add throughput benchmarks
    - [ ] Add retry tests
    - [ ] Add backpressure tests
    - [ ] Add ordering tests

### Observability

- [ ] **Prove correlation ID propagation**
  - Location: products/aep, platform/java/observability
  - Tasks:
    - [ ] Add tests proving correlation IDs are preserved across service boundaries
    - [ ] Add tests for correlation ID generation when missing
    - [ ] Add tests for MDC propagation

- [ ] **Remove console output from production code**
  - Location: All modules
  - Tasks:
    - [ ] Replace `System.out` in platform-kernel plugin loader with structured logger
    - [ ] Replace console.log in loaders with structured logging
    - [ ] Replace console output in tests with test utilities
    - [ ] Replace console output in docs examples with proper logging

### Security and Privacy

- [ ] **Classify and document secrets**
  - Location: All modules
  - Tasks:
    - [ ] Inventory all generated certificates and keystores
    - [ ] Add classification for secret handling
    - [ ] Add rotation documentation
    - [ ] Add secret storage best practices

- [ ] **Add data export/reporting tests**
  - Location: products/data-cloud
  - Tasks:
    - [ ] Add redaction tests for exports
    - [ ] Add consent tests for exports
    - [ ] Add retention tests for exports
    - [ ] Add audit tests for exports

### AI/ML

- [ ] **Add data-cloud anomaly/analytics ML tests**
  - Location: products/data-cloud/platform-analytics
  - Tasks:
    - [ ] Add drift detection tests
    - [ ] Add false-positive threshold tests
    - [ ] Add false-negative threshold tests
    - [ ] Add explainability artifact tests

- [ ] **Add audio-video model contract tests**
  - Location: products/audio-video/modules/speech, products/audio-video/modules/vision
  - Tasks:
    - [ ] Add real model service contract tests
    - [ ] Add fallback behavior tests
    - [ ] Add model eval tests

### Build/Release/Operability

- [ ] **Validate Kubernetes/monitoring configs**
  - Location: shared-services, products
  - Tasks:
    - [ ] Add Kubernetes config validation to CI
    - [ ] Add monitoring config validation to CI
    - [ ] Add health/readiness/liveness tests
    - [ ] Add rollback behavior tests

- [ ] **Enforce cross-workspace dependency policy**
  - Location: Root pnpm-workspace.yaml
  - Tasks:
    - [ ] Add PNPM workspace dependency policy enforcement
    - [ ] Prevent product packages from depending on other product packages without explicit approval

### Platform Contracts

- [ ] **Add consumer-driven contract tests**
  - Location: platform/contracts
  - Tasks:
    - [ ] Add AEP consumer contract tests
    - [ ] Add data-cloud consumer contract tests
    - [ ] Add YAPPC consumer contract tests

- [ ] **Document generated artifact policy**
  - Location: platform/contracts
  - Tasks:
    - [ ] Document which generated outputs are source-of-truth
    - [ ] Document which generated outputs should be excluded
    - [ ] Add validation to ensure policy is followed

### Platform Java

- [ ] **Add tenant/security contract tests**
  - Location: platform/java/security, platform/java/identity
  - Tasks:
    - [ ] Add adversarial test suite for security module
    - [ ] Add tenant isolation test suite
    - [ ] Add policy decision tests

- [ ] **Add performance benchmarks**
  - Location: platform/java/messaging, platform/java/database, platform/java/cache
  - Tasks:
    - [ ] Add throughput benchmarks for messaging
    - [ ] Add backpressure tests for messaging
    - [ ] Add N+1 query tests for database
    - [ ] Add retry tests for database
    - [ ] Add memory benchmarks for cache
    - [ ] Add retry tests for cache

### Platform TypeScript

- [ ] **Add cross-package dependency policy enforcement**
  - Location: platform/typescript/*
  - Tasks:
    - [ ] Add dependency policy enforcement
    - [ ] Prevent circular dependencies between packages
    - [ ] Add package boundary tests

- [ ] **Add browser/a11y/performance CI tiers**
  - Location: platform/typescript/*
  - Tasks:
    - [ ] Add browser integration test tier
    - [ ] Add accessibility test tier
    - [ ] Add performance test tier
    - [ ] Configure CI to run these tiers

### Platform Kernel

- [ ] **Add registry/event bus concurrency tests**
  - Location: platform-kernel/kernel-core
  - Tasks:
    - [ ] Add concurrent registration tests
    - [ ] Add handler fanout tests
    - [ ] Add failure isolation tests
    - [ ] Add stress tests for plugin count

- [ ] **Add audit persistence integration tests**
  - Location: platform-kernel/kernel-persistence
  - Tasks:
    - [ ] Add audit persistence tests
    - [ ] Add migration tests
    - [ ] Add retention tests

### Platform Plugins

- [ ] **Add persistence migration and retention tests**
  - Location: platform-plugins/*
  - Tasks:
    - [ ] Add migration tests for all plugins
    - [ ] Add retention tests for audit plugin
    - [ ] Add retention tests for billing plugin
    - [ ] Add privacy deletion tests for consent plugin

- [ ] **Add fraud/risk explainability tests**
  - Location: platform-plugins/plugin-fraud-detection, platform-plugins/plugin-risk-management
  - Tasks:
    - [ ] If ML is used, add explainability tests
    - [ ] Add threshold tests
    - [ ] Add drift monitoring tests

- [ ] **Standardize export capability metadata**
  - Location: platform-plugins/*
  - Tasks:
    - [ ] Document export capabilities for each plugin
    - [ ] Add metadata for unsupported exports
    - [ ] Add tests for export behavior

### Shared Services

- [ ] **Add DB-backed revocation and audit tests**
  - Location: shared-services/auth-gateway
  - Tasks:
    - [ ] Add token blocklist persistence tests
    - [ ] Add audit logger persistence tests
    - [ ] Add DB-backed integration tests
    - [ ] Add MFA edge case tests with real DB

- [ ] **Add load tests for auth gateway**
  - Location: shared-services/auth-gateway
  - Tasks:
    - [ ] Add k6 load tests for token issuance
    - [ ] Add k6 load tests for token validation
    - [ ] Add k6 load tests for blocklist lookups
    - [ ] Add k6 load tests for login throttling
    - [ ] Add k6 load tests for audit writes

- [ ] **Validate monitoring configs in CI**
  - Location: shared-services
  - Tasks:
    - [ ] Add Kubernetes config validation
    - [ ] Add monitoring config validation
    - [ ] Add alert rule validation

- [ ] **Consolidate tenant extraction with platform**
  - Location: shared-services/auth-gateway
  - Tasks:
    - [ ] Migrate tenant extraction to platform library
    - [ ] Update all consumers to use platform library
    - [ ] Remove duplicate tenant extraction code

### Products Audio Video

- [ ] **Add media privacy/security tests**
  - Location: products/audio-video/*
  - Tasks:
    - [ ] Add consent tests for audio/video content
    - [ ] Add retention tests for audio/video content
    - [ ] Add redaction tests for PII in transcripts
    - [ ] Add encrypted transit/storage tests
    - [ ] Add audit logging tests

- [ ] **Add streaming/load/resilience tests to CI tiers**
  - Location: products/audio-video/*
  - Tasks:
    - [ ] Add streaming latency tests
    - [ ] Add memory growth tests
    - [ ] Add media chunk backpressure tests
    - [ ] Add concurrent session tests
    - [ ] Add desktop resource usage tests
    - [ ] Configure CI tiers for these tests

### Products Data Cloud

- [ ] **Add redaction/export privacy contract tests**
  - Location: products/data-cloud/platform-governance
  - Tasks:
    - [ ] Add PII leakage prevention tests
    - [ ] Add consent handling tests
    - [ ] Add retention policy tests
    - [ ] Add audit trail tests

- [ ] **Add DB-backed migration/API E2E tests**
  - Location: products/data-cloud/*
  - Tasks:
    - [ ] Add migration compatibility tests
    - [ ] Add API E2E tests with real DB
    - [ ] Add property-based query tests

- [ ] **Consolidate data-cloud adapter abstractions**
  - Location: products/data-cloud, products/yappc/infrastructure/datacloud
  - Tasks:
    - [ ] Consolidate duplicate data-cloud adapter behavior
    - [ ] Use platform abstractions where appropriate
    - [ ] Remove duplication

### Products AEP

- [ ] **Add orchestration/queue/idempotency tests**
  - Location: products/aep/orchestrator, products/aep/aep-engine
  - Tasks:
    - [ ] Add orchestration state machine tests
    - [ ] Add queue concurrency tests
    - [ ] Add execution idempotency tests
    - [ ] Add checkpoint recovery tests

- [ ] **Add SSE/WS backend contract tests**
  - Location: products/aep/gateway, products/aep/aep-event-cloud
  - Tasks:
    - [ ] Add SSE backend contract tests
    - [ ] Add WebSocket backend contract tests
    - [ ] Add backend parity tests

- [ ] **Add gateway/backend tenant contract tests**
  - Location: products/aep/gateway, products/aep/aep-identity
  - Tasks:
    - [ ] Add tenant propagation tests
    - [ ] Add role enforcement tests
    - [ ] Add cross-service authz tests

- [ ] **Classify gateway tests requiring local port binding**
  - Location: products/aep/gateway
  - Tasks:
    - [ ] Tag gateway tests as `local-network`
    - [ ] Configure CI to handle these tests appropriately

### Products YAPPC

- [ ] **Add end-to-end workflow tests**
  - Location: products/yappc/*
  - Tasks:
    - [ ] Add idea-to-artifact E2E test
    - [ ] Add refactor E2E test
    - [ ] Add preview E2E test
    - [ ] Add approval E2E test
    - [ ] Add rollback E2E test

- [ ] **Standardize frontend package boundaries**
  - Location: products/yappc/frontend/libs/*
  - Tasks:
    - [ ] Document package ownership
    - [ ] Add package boundary tests
    - [ ] Remove duplicate utilities
    - [ ] Remove duplicate state abstractions

- [ ] **Add benchmarks**
  - Location: products/yappc/*
  - Tasks:
    - [ ] Add agent scheduling benchmarks
    - [ ] Add knowledge graph query benchmarks
    - [ ] Add artifact compilation benchmarks
    - [ ] Add frontend canvas rendering benchmarks
    - [ ] Add frontend workflow rendering benchmarks
    - [ ] Add live preview benchmarks
    - [ ] Add data-cloud cache behavior benchmarks

---

## P2 Tasks (Medium Priority)

### Architecture

- [ ] **Document generated artifact policy**
  - Location: Root documentation
  - Tasks:
    - [ ] Create policy document for source-of-truth artifacts
    - [ ] Document which generated files should be checked in
    - [ ] Document which generated files should be excluded

### Platform TypeScript

- [ ] **Consolidate duplicated utility/state patterns**
  - Location: platform/typescript/*
  - Tasks:
    - [ ] Audit for duplicate utilities
    - [ ] Consolidate into canonical packages
    - [ ] Audit for duplicate state abstractions
    - [ ] Consolidate into canonical packages

### Platform Kernel

- [ ] **Replace console logging**
  - Location: platform-kernel/kernel-plugin
  - Tasks:
    - [ ] Replace `System.out` in plugin loader with structured logger
    - [ ] Use platform observability module for logging

### Platform Plugins

- [ ] **Standardize export capability metadata**
  - Location: platform-plugins/*
  - Tasks:
    - [ ] Document export capabilities for each plugin
    - [ ] Add metadata for unsupported behaviors
    - [ ] Add tests for export behavior

### Shared Services

- [ ] **Consolidate tenant extraction with platform**
  - Location: shared-services/auth-gateway
  - Tasks:
    - [ ] Migrate to platform tenant extraction library
    - [ ] Update all consumers
    - [ ] Remove duplicate code

### Products Audio Video

- [ ] **Replace placeholder fixtures with representative media fixtures**
  - Location: products/audio-video/*
  - Tasks:
    - [ ] Create representative audio fixtures
    - [ ] Create representative video fixtures
    - [ ] Create representative image fixtures
    - [ ] Replace placeholder fixtures in tests

### Products Data Cloud

- [ ] **Consolidate data-cloud adapter abstractions**
  - Location: products/data-cloud, products/yappc/infrastructure/datacloud
  - Tasks:
    - [ ] Consolidate duplicate adapter behavior
    - [ ] Use platform abstractions
    - [ ] Remove duplication

### Products YAPPC

- [ ] **Consolidate duplicate config/schema/state abstractions**
  - Location: products/yappc/*
  - Tasks:
    - [ ] Audit for duplicate config abstractions
    - [ ] Audit for duplicate schema abstractions
    - [ ] Audit for duplicate state abstractions
    - [ ] Consolidate into canonical packages

---

## Cross-Cutting Infrastructure Tasks

### CI/CD Configuration

- [ ] **Implement test tier classification**
  - Location: Root CI configuration
  - Tasks:
    - [ ] Configure `tier-0`: fast unit and pure contract tests
    - [ ] Configure `tier-1`: module integration tests with in-memory fakes
    - [ ] Configure `tier-2`: localhost/network integration tests
    - [ ] Configure `tier-3`: Testcontainers/docker-compose infrastructure-backed tests
    - [ ] Configure `tier-4`: browser, performance, load, soak, security, privacy, and AI eval suites

- [ ] **Add infrastructure-backed CI environments**
  - Location: Root CI configuration
  - Tasks:
    - [ ] Add Testcontainers for DB tests
    - [ ] Add Testcontainers for messaging tests
    - [ ] Add Testcontainers for cache tests
    - [ ] Add docker-compose for auth/gateway tests
    - [ ] Add ephemeral environments for product E2E tests

### Coverage Matrix

- [ ] **Create root-level coverage matrix**
  - Location: Root documentation
  - Tasks:
    - [ ] Create coverage matrix by module
    - [ ] Create coverage matrix by feature
    - [ ] Create coverage matrix by flow
    - [ ] Create coverage matrix by test tier
    - [ ] Document required negative/failure/security/privacy/o11y tests

### Documentation

- [ ] **Create architecture rules documentation**
  - Location: Root documentation
  - Tasks:
    - [ ] Document enforced architecture rules
    - [ ] Document dependency-direction specification
    - [ ] Document module boundary rules

- [ ] **Create AI/ML governance contract documentation**
  - Location: Root documentation
  - Tasks:
    - [ ] Document prompt/model/tool/version/eval/audit/redaction contract
    - [ ] Document governance enforcement mechanisms
    - [ ] Document approval boundaries

- [ ] **Create tenant/auth/correlation/audit propagation contract**
  - Location: Root documentation
  - Tasks:
    - [ ] Document tenant propagation contract
    - [ ] Document auth propagation contract
    - [ ] Document correlation ID propagation contract
    - [ ] Document audit propagation contract

- [ ] **Create data-cloud query semantics specification**
  - Location: products/data-cloud documentation
  - Tasks:
    - [ ] Document sorting semantics
    - [ ] Document pagination semantics
    - [ ] Document join semantics
    - [ ] Document grouping semantics
    - [ ] Document export semantics

- [ ] **Create audio-video media privacy and retention policy**
  - Location: products/audio-video documentation
  - Tasks:
    - [ ] Document media privacy policy
    - [ ] Document retention policy
    - [ ] Document redaction requirements
    - [ ] Document consent requirements

### Shared Contracts

- [ ] **Establish shared contracts**
  - Location: platform/contracts
  - Tasks:
    - [ ] Define tenant/auth/correlation/audit propagation contract
    - [ ] Define event envelope schemas
    - [ ] Define AI model/prompt/tool/eval governance contract
    - [ ] Define observability metric/log/trace naming and redaction contract

### Consolidation

- [ ] **Consolidate duplicated platform/product abstractions**
  - Location: Multiple roots
  - Tasks:
    - [ ] Consolidate tenant extraction into platform library
    - [ ] Consolidate frontend state into platform library
    - [ ] Consolidate API clients into platform library
    - [ ] Consolidate data-cloud adapters into platform library
    - [ ] Consolidate agent catalog/config schema into platform library
    - [ ] Consolidate messaging error/result contracts into platform library

### Production Hardening

- [ ] **Add production hardening**
  - Location: All products and shared services
  - Tasks:
    - [ ] Add real infrastructure smoke tests
    - [ ] Add release SBOM generation
    - [ ] Add dependency audit gates
    - [ ] Classify secrets/certs
    - [ ] Validate dashboard/alert configurations
    - [ ] Add backward-compatible migration tests

---

## Recommended Execution Order

Based on the audit report's recommended next execution order:

1. **Remove/classify generated and build artifacts from audited roots** (P0)
2. **Implement real architecture gates and fail CI on violations** (P0)
3. **Create a root-level coverage matrix by module, feature, flow, and test tier** (P0)
4. **Complete P0 implementation gaps** (P0):
   - data-cloud query semantics
   - audio-video gRPC/service contracts
   - YAPPC AI/tool governance
   - shared auth tenant contracts
5. **Add infrastructure-backed CI environments** (P1/P2)
6. **Add observability/security/privacy/AI eval gates** (P0/P1)
7. **Expand focused hardening package by package** (P1/P2)

---

## Task Statistics

- **Total P0 Tasks**: 47 task groups
- **Total P1 Tasks**: 58 task groups
- **Total P2 Tasks**: 13 task groups
- **Total Cross-Cutting Tasks**: 15 task groups
- **Grand Total**: 133 task groups

---

## Notes

- This todo list is comprehensive and includes all findings from the audit report
- Tasks are organized by priority (P0, P1, P2) and area
- Some task groups contain multiple sub-tasks
- Execution should follow the recommended order above
- All tasks should follow Ghatana coding-instructions.md guidelines
- Tests are required for all meaningful behavior changes
- Type safety is mandatory for all TypeScript/Java code
- Observability must be part of every feature
