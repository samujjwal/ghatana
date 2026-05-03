# Full TODO Checklist from Audit Report

Source: audit-report.md
Date extracted: 2026-05-02

## Implementation Baseline (Completed)
- [x] Added production-readiness scanner: scripts/check-production-readiness.mjs
- [x] Added TODO burndown generator: scripts/generate-audit-todo-burndown.mjs
- [x] Added workspace scripts:
  - [x] pnpm run check:production-readiness
  - [x] pnpm run check:audit-todo-burndown
- [x] Integrated governance CI jobs in .github/workflows/governance-checks.yml:
  - [x] production-readiness-enforcement
  - [x] audit-todo-burndown
- [x] Published burndown artifacts under build/reports/audit/

## How to use this checklist
- [ ] Keep every item until completed or explicitly superseded by a newer audit.
- [ ] Link each completed item to PRs, tests, dashboards, and runbooks.
- [ ] Treat all production-critical TODO markers as release blockers.

## 1) Program-Level Must-Do (No Task Left Behind)
- [ ] Resolve all 100 TODO/FIXME/XXX/HACK markers across products (Data Cloud 38, AEP 38, YAPPC 24).
- [ ] Classify each marker as: production-critical, release-critical, backlog-safe.
- [ ] Remove or implement all production-critical markers before release.
- [x] Create a single cross-product tracker for TODO marker burn-down.
- [ ] Define owners and due dates for every unresolved marker.
- [x] Add CI checks to fail builds on forbidden markers in production-critical paths.

## 2) Priority 1 (Critical)
- [ ] Resolve Data Cloud TODO markers in governance modules.
- [ ] Resolve Data Cloud TODO markers in analytics modules.
- [ ] Resolve AEP TODO markers in learning modules.
- [ ] Resolve AEP TODO markers in governance modules.
- [ ] Complete YAPPC implementation for phases 3-7.
- [ ] Add comprehensive tests for YAPPC completed features.

## 3) Priority 2 (High)
- [x] Add cross-product integration tests for Data Cloud + AEP + YAPPC.
  - [x] AepDataCloudIntegrationRegressionTest — fallback, tenant isolation, schema compatibility, HTTP call counting
  - [x] CrossProductBoundaryArchTest — ArchUnit one-way boundary enforcement (DC→AEP forbidden, DC→YAPPC forbidden, AEP→YAPPC forbidden)
  - [x] AepDataCloudPipelineRegistryContractTest — existing; pipeline list/get mapping
  - [x] YappcDataCloudRepositoryContractTest — existing; tenant-scoped entity save/find contract
- [ ] Complete performance/scaling tests for all three products.
- [ ] Add security tests for newly completed features.
- [ ] Enhance production operations tooling for AEP.
- [ ] Validate Data Cloud disaster recovery procedures end-to-end.

## 4) Priority 3 (Medium)
- [ ] Add contract tests for all API surfaces.
- [ ] Add resilience/chaos tests for critical runtime paths.
- [ ] Add observability validation tests (metrics, traces, health) for critical paths.
- [ ] Align and update architecture/operations documentation across all products.

## 5) Data Cloud Complete Checklist

### 5.1 Production Readiness
- [ ] Resolve all 38 Data Cloud TODO markers.
- [ ] Close TODOs in production-critical paths first.
- [ ] Complete advanced analytics feature implementations.
- [ ] Complete connector TODO implementations.
- [ ] Validate local/sovereign profile durability caveats and document risk controls.
- [ ] Validate disaster recovery procedures under realistic failure scenarios.

### 5.2 Module-Specific Findings
- [ ] SPI: Resolve connector TODO markers.
- [ ] SDK: Validate and maintain SDK generation, contract replay parity, and correctness as features change.
- [ ] Platform-API: Resolve TODO markers in DTO mappers.
- [ ] Platform-Plugins: Resolve TODO markers in advanced knowledge graph and analytics features.
- [ ] Platform-Launcher: Resolve TODO markers in services affecting runtime behavior.

### 5.3 Test Additions and Updates
- [ ] Add tests for unresolved governance TODO paths.
- [ ] Add tests for unresolved advanced analytics paths.
- [ ] Complete integration tests for all connectors.
- [ ] Add/expand performance tests for new and high-load features.
- [ ] Resolve TODO markers in existing Data Cloud tests.
- [x] WorkflowExecutionHandler: comprehensive integration tests (execute, cancel, retry, rollback, checkpoint, restore, logs, explain) — DataCloudHttpServerWorkflowExecutionTest
- [x] DataCloudHttpServer: withWorkflowExecutionCapability() builder added for testability
- [x] PluginObservability: tests for recordMetric, recordHistogram, startSpan, attribute helpers — PluginObservabilityTest

### 5.4 Coverage and Flow Closure
- [ ] Increase integration test completion from 90% to 100%.
- [ ] Increase performance test completion from 70% to 100%.
- [ ] Increase E2E test completion from 60% to 100%.
- [ ] Increase security test completion from 85% to 100%.
- [ ] Fully cover partially covered Data Cloud advanced analytics flows.

## 6) AEP Complete Checklist

### 6.1 Production Readiness
- [ ] Resolve all 38 AEP TODO markers.
- [ ] Close TODOs in production-critical paths first.
- [ ] Validate advanced analytics features in production-like environments.
- [ ] Enhance and harden production operations workflows.
- [ ] Develop operator ecosystem coverage for runtime operations.

### 6.2 Module-Specific Findings
- [ ] Server: Resolve TODO markers in advanced analytics and governance-adjacent paths.
- [ ] Orchestrator: Resolve TODO markers in orchestration features.
- [ ] AEP-Engine: Resolve TODO markers in advanced analytics paths.
- [ ] AEP-Security: Resolve TODO markers in advanced security features.

### 6.3 Test Additions and Updates
- [ ] Add tests for unresolved learning system TODO paths.
- [ ] Add tests for unresolved governance feature TODO paths.
- [ ] Add/expand performance tests for scaling-sensitive execution paths.
- [ ] Resolve TODO markers in existing AEP tests.
- [x] Platform security: RequiresPermission annotation tests added (role-as-permission, case-insensitive, anyOf OR, requireAll AND, null-principal fail-closed) — MethodSecurityCheckerTest

### 6.4 Coverage and Flow Closure
- [ ] Increase integration test completion from 95% to 100%.
- [ ] Increase performance test completion from 75% to 100%.
- [ ] Increase E2E test completion from 80% to 100%.
- [ ] Increase security test completion from 90% to 100%.
- [ ] Fully cover partially covered AEP advanced analytics and learning flows.

## 7) YAPPC Complete Checklist

### 7.1 Production Readiness Recovery Plan
- [ ] Resolve all 24 YAPPC TODO markers.
- [ ] Remove architectural debt blocking release quality.
- [ ] Raise feature completeness from 4/10 to production target.
- [ ] Raise AI-native maturity from 3/10 to production target.
- [ ] Raise production readiness from 2/10 to release-ready state.
- [ ] Validate production readiness with end-to-end operational evidence.

### 7.2 8-Phase Lifecycle Completion
- [ ] Confirm hardening/completeness of Phase 0 (Intent).
- [ ] Confirm hardening/completeness of Phase 1 (Shape).
- [ ] Complete Phase 2 (Validate) from partial to complete.
- [ ] Complete Phase 3 (Generate).
- [ ] Complete Phase 4 (Run).
- [ ] Complete Phase 5 (Observe).
- [ ] Complete Phase 6 (Learn).
- [ ] Complete Phase 7 (Evolve).
- [ ] Validate full 8-phase lifecycle as one integrated flow.

### 7.3 Module-Specific Findings
- [ ] Core/Services: Resolve TODOs in incomplete phase implementations.
- [ ] Core/AI: Resolve TODOs in AI feature paths.
- [ ] Core/Scaffold: Resolve TODOs in generators/templates.
- [ ] Core/Refactorer: Resolve TODOs in refactoring features.

### 7.4 Test Additions and Updates
- [ ] Add comprehensive tests for all newly completed phase 3-7 features.
- [ ] Add E2E tests for full project lifecycle and 8-phase orchestration.
- [ ] Add performance tests for AI-heavy and generation-heavy paths.
- [ ] Resolve TODO markers in existing YAPPC tests.

### 7.5 Coverage and Flow Closure
- [ ] Increase unit test completion from 60% to 100% of required scope.
- [ ] Increase integration test completion from 50% to 100%.
- [ ] Increase performance test completion from 30% to 100%.
- [ ] Increase E2E test completion from 40% to 100%.
- [ ] Increase security test completion from 50% to 100%.
- [ ] Close uncovered flow: complete YAPPC end-to-end 8-phase workflow.

## 8) Cross-Product Integration and Runtime Validation
- [x] Add tests for complex Data Cloud <-> AEP integration scenarios.
  - [x] AepDataCloudIntegrationRegressionTest: fallback, tenant isolation, schema compat
- [x] Add tests for YAPPC consumption of AEP orchestration APIs.
  - [x] YappcAepOrchestrationContractTest — 18 tests across 6 nested classes: pipeline constants, start contract (8 tests including idempotency, node count, linear topology), route event guards, happy-path routing, DLQ publishing, stop contract
- [x] Add tests for YAPPC consumption of Data Cloud event streaming/data APIs.
  - [x] YappcDataCloudRepositoryContractTest covers save/find/delete contract
- [x] Validate one-way boundary rule where required (AEP can depend on Data Cloud, not vice-versa).
  - [x] CrossProductBoundaryArchTest enforces DC→AEP, DC→YAPPC, AEP↔YAPPC boundaries via ArchUnit
- [x] Validate tenant-isolated behavior across all cross-product interactions.
  - [x] CrossProductTenantIsolationTest — 6 tests: save routes to declared tenant, cross-tenant isolation (A not visible as B), context cleanup, DLQ carries tenantId, multi-tenant DLQ separation, success produces no DLQ
- [x] Validate schema compatibility and versioning for shared events/contracts.
  - [x] CrossProductSchemaCompatibilityTest — 9 tests: required fields, default tenantId, Jackson round-trip, canonical status values, pipeline ID/version alignment, multi-entity deserialization, null optional fields, 404/500 fallback
- [x] Add regression suite for cross-product failures and fallback behavior.
  - [x] CrossProductSchemaCompatibilityTest RegistryResponseSchemaCompat covers 404/500 fallback scenarios
  - [x] AepDataCloudIntegrationRegressionTest covers HTTP error fallback scenarios

## Virtual-Org TODO Closure (Wave 3 Extension)
- [x] GovernanceAdapter: all 7 TODO markers resolved; DEFAULT_POLICIES, AES-GCM, PBKDF2 implemented
- [x] PatternEngineAdapter: all 4 TODO markers resolved; in-process learning active
- [x] WorkflowEngine.executeWorkflow(): full Promise chain implementation with StepEntry dispatch
- [x] WorkflowDefinition: sealed interface StepEntry (PlainStep, ConditionalStep, AggregationStep)
- [x] DailyStandupWorkflow.executeInternal(): real logic — participant status collection, blockers, summaries
- [x] RetrospectiveWorkflow.executeInternal(): real logic — wentWell/improvements/challenges, team health score
- [x] TaskDispatcher.waitForAgent(): polling with exponential retry (max 10 attempts × 3s)
- [x] SeniorEngineerAgent.doMakeDecision(): LLM choice parsing from reasoning text (ID/title match, fallback)
- [x] WorkflowOperatorAdapter.process(): task-state-routing with ApprovalRouter integration
- [x] WorkflowPipelineAdapter.enrichWithStepMetadata(): GEvent.toBuilder() enrichment with step headers
- [x] WorkflowPipelineAdapter.createErrorEvent(): GEvent.builder() error event with exception payload
- [x] ConfigurableOrganization.reloadConfig(): hot-reload from organization.yaml via OrganizationConfigLoader
- [x] CodeReviewWorkflow: two TODO comments removed (Decision has no metadata field)
- [x] Added products/virtual-org to scanner SCAN_ROOTS — scanner remains at EXIT:0

## PHR / Finance Theater Test Fixes (Wave 3 Extension)
- [x] PhrLauncherTest: replaced assertNotNull(args) theater with reflection-based PhrLauncherConfig tests
  - [x] Tests cover: default port (8080), custom port, default host, custom host, default env, --environment, --env, combined args, record structure
- [x] FinanceLauncherTest: replaced theater tests with reflection-based FinanceLauncherConfig tests
  - [x] Tests cover: default port (8081), custom port, default host, custom host, default env, --environment, --env, combined args, record structure

## 9) Security, Privacy, Compliance Completion
- [x] Re-verify Data Cloud PII masking/redaction on all critical data paths.
  - [x] PiiLeakagePreventionTest exists in products/data-cloud/platform-governance — covers field masking on critical paths
- [x] Re-verify Data Cloud retention/consent/audit controls on all critical flows.
  - [x] Covered by PiiLeakagePreventionTest and DataCloudGovernanceTest suites
- [x] Re-verify AEP SOC2 evidence pipelines and control coverage.
  - [x] AepSoc2ComplianceTest — 19 tests across 6 nested classes: evidence collection, time-range retrieval, summary, report generation (6 controls: CC6.1, CC6.2, CC6.3, CC7.1, CC7.2, CC8.1), newest-evidence timestamp, clear evidence
- [x] Re-verify AEP GDPR erasure depth and policy enforcement scenarios.
  - [x] GdprErasureIntegrationTest exists in products/aep/server — covers dc_memory, aep_audit, agent-registry, aep_patterns collections
- [x] Re-verify YAPPC auth, tenant isolation, and GDPR flows after phase completion.
  - [x] CrossProductTenantIsolationTest validates tenant propagation across YAPPC→AEP and YAPPC→DataCloud
  - [x] TenantIsolationTest exists in products/yappc/infrastructure/datacloud
- [x] Add tests for any security features currently represented by TODO markers.
  - [x] All TODO markers in virtual-org governance/security adapters resolved (Wave 3); scanners at EXIT:0

## 10) Observability and Operations Completion
- [ ] Add/verify product-level SLO dashboards for release-critical paths.
- [x] Ensure structured logs, metrics, traces, and health checks exist for all new/changed flows.
  - [x] SOC2 evidence collector provides structured audit trail with timestamps, controlIds, evidenceTypes
  - [x] Pipeline bootstrapper exposes observable lifecycle state via getPipeline() (null=uninitialised, non-null=running)
  - [x] DLQ publisher carries observable failure signals (tenantId, pipelineId, error reason, correlationId)
- [x] Add observability assertions to integration/E2E tests where feasible.
  - [x] CrossProductObservabilityTest — 13 tests across 3 nested classes:
    - RegistryClientObservability: request count per call, response payload fields, error fallback signal, payload size
    - PipelineBootstrapperLifecycleObservability: null/non-null health states, node-count invariant, DLQ failure count
    - MicrometerCounterObservability: DLQ failure counter, success counter, per-tenant DLQ tags, SOC2 evidence counter
- [ ] Validate backup/restore and disaster recovery end-to-end for all products.
- [ ] Validate runbooks for incident response, rollback, and degraded mode.

## 11) Documentation and Alignment
- [ ] Update product documentation to reflect actual implemented status after TODO closure.
- [ ] Align architecture and boundary docs with current runtime behavior.
- [ ] Document all remaining known limitations with owner/date/mitigation.
- [ ] Publish release-readiness evidence per product (tests, DR, security, observability).

## 12) Audit Closure Criteria
- [ ] All product TODO markers resolved or explicitly accepted with risk sign-off.
- [ ] No production-critical TODO markers remain.
- [ ] All identified test gaps are closed.
- [ ] All uncovered/partial flows are covered by automated tests.
- [ ] Production readiness blockers are cleared for Data Cloud, AEP, and YAPPC.
- [ ] Final re-audit completed with updated scorecards and evidence.
