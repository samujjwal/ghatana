# Audit Remediation TODO List

- **Source audit:** `docs/audit-report-2026-04-22.md`
- **Source implementation plan:** `docs/AUDIT_REMEDIATION_IMPLEMENTATION_PLAN_2026-04-22.md`
- **Governing repo rules:** `.github/copilot-instructions.md` §§29–33
- **Status legend:** `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

## Progress Notes (2026-04-22)

- `[x]` Deprecated package fix-forward completed for targeted audited roots in `platform/typescript/{patterns,accessibility,design-system}` and `products/yappc/frontend/web/vite.config.ts`.
- `[x]` Deprecated package guard enhanced in `scripts/check-deprecated-ui.sh` and verified passing.
- `[x]` Placeholder `expect(true).toBe(true)` assertions removed from audited roots (verification grep now returns zero within audited scope).
- `[x]` Test authenticity guard added as `scripts/check-test-authenticity.sh`, integrated into `.github/workflows/boundary-check.yml`, and verified passing for audited roots.
- `[x]` D-series re-enable tranche completed for `D-1..D-8` (kernel-core, messaging, and platform architecture critical tests active).
- `[x]` `B-4` committed AI/ML eval-fixture strategy and baseline fixture roots added for `ai-integration`, `yappc/core/ai`, and `aep-agent-runtime`.
- `[x]` `AR-1` and `AR-4` foundation coverage added in AEP server tests (create/read route round-trip and tenant-scoped registry access).
- `[x]` `D-1` re-enabled with concrete `InMemoryAgentRegistry` SPI assertions (list/resolve/stats).
- `[x]` `A-1..A-3` foundational hardening coverage added in `agent-core` tests.
- `[x]` `AR-2`, `AR-3`, and `AR-5` advanced with execute-route integration coverage, platform OpenAPI agent contract verification, and a dedicated k6 load profile for `GET /api/v1/agents`.
- `[x]` `A-4` and `A-5` added in `agent-core` to validate same-event-loop dual-agent execution and failed-`Promise` surfacing as `AgentResult.failure`.
- `[x]` `KP-1..KP-5` hardening coverage landed in `kernel-plugin` (lifecycle FSM, provider-failure isolation, version mismatch rejection, classloader-release, idempotent SPI registration).
- `[x]` `AT-1..AT-9` canonical runtime taxonomy coverage completed in `aep-agent-runtime` (deterministic/probabilistic/stream-processor/planning/hybrid/adaptive/composite/reactive/custom).
- `[x]` `AT-10` golden-set evaluation coverage landed with committed fixture manifest + runtime execution assertions.
- `[x]` `AT-11` resilience coverage landed for timeout/retry/circuit-break behavior in `ResilientTypedAgent`.
- `[x]` `D-2..D-6` re-enabled with kernel-scope end-to-end/integration/compliance coverage that no longer depends on missing product modules.
- `[x]` `D-8` re-enabled with active architecture boundary checks in `platform/java/testing`.
- `[x]` `D-7` re-enabled with RabbitMQ consumer integration coverage and validated alongside Kafka/SQS broker tests.
- `[x]` `HA-1..HA-3` completed in `plugin-human-approval` with FSM terminal-state coverage, quorum semantics, and timeout-expiration escalation.

---

## Phase 0 — Blockers and module decisions

- [x] `W-1` Wire or delete `shared-services/integration-tests/`
- [x] `W-2` Implement or delete `products/data-cloud/platform-client/`
- [x] `W-3` Implement or delete `products/yappc/launcher/`
- [x] `W-4` Verify `platform-kernel/kernel-persistence/` and implement or delete it
- [x] `B-1` Close the `shared-services/integration-tests` build blocker
- [x] `B-2` Close the `kernel-persistence` no-production-code blocker
- [x] `B-3` Add `aep-registry` integration-test fixture support and dependencies
- [x] `B-4` Create committed AI/ML eval-fixture strategy

## Phase 1 — Remove test theatre

- [x] `T-001` Rewrite or delete `platform/typescript/design-system/src/test/components/ComponentIntegration.test.tsx`
- [x] `T-002` Rewrite or delete `platform/typescript/design-system/src/test/theme/ThemeSystem.test.ts`
- [x] `T-003` Rewrite or delete `platform/typescript/code-editor/src/LazyMonacoEditor.test.tsx`
- [x] `T-004` Rewrite or delete `products/data-cloud/ui/src/__tests__/ShellRouting.test.tsx`
- [x] `T-005…T-009` Rewrite or delete the flagged YAPPC anomaly dashboard theatre tests
- [x] `T-010…T-053` Rewrite or delete the remaining flagged TS theatre tests in audited roots
- [x] Add CI guard to block new placeholder assertions

## Phase 2 — Re-enable disabled Java tests

- [x] `D-1` Re-enable `platform/java/agent-core/.../AgentTenantIsolationTest.java`
- [x] `D-2` Re-enable `platform-kernel/.../KernelEndToEndTest.java`
- [x] `D-3` Re-enable `platform-kernel/.../KernelModuleIntegrationTest.java`
- [x] `D-4` Re-enable `platform-kernel/.../CircularDependencyDetectionTest.java`
- [x] `D-5` Re-enable `platform-kernel/.../RegulatoryComplianceTest.java`
- [x] `D-6` Re-enable `platform-kernel/.../PhrFinanceIntegrationTest.java`
- [x] `D-7` Re-enable `platform/java/messaging/.../BrokerConnectorIntegrationTest.java`
- [x] `D-8` Re-enable `platform/java/testing/.../PlatformArchitectureTest.java`
- [x] `D-9…D-17` Re-enable or ticket the remaining disabled Java tests
- [x] Add guard to block unticketed disabled critical-path tests

## Phase 3 — Canonical package fix-forward

- [x] `P-1` Replace `@ghatana/ui` alias in `platform/typescript/patterns/vitest.config.ts`
- [x] `P-2` Replace SARIF output name in `platform/typescript/accessibility/src/formatters/OutputFormatter.ts`
- [x] `P-3` Update the broken filter command in `platform/typescript/accessibility/src/test/manual.quick-audit.test.ts`
- [x] `P-4…P-15` Replace remaining deprecated `@ghatana/ui` and `@ghatana/accessibility-audit` references in audited roots
- [x] Add lint enforcement for forbidden `@ghatana/*` package names

## Phase 4 — AEP central registry hardening

- [x] `AR-1` Add create/read integration test for `POST /api/v1/agents` and `GET /api/v1/agents/{id}`
- [x] `AR-2` Add execute-path integration test for `/api/v1/agents/:agentId/execute`
- [x] `AR-3` Add schema/contract verification against platform contracts
- [x] `AR-4` Add tenant isolation unit coverage for `aep-registry`
- [x] `AR-5` Add load test for `GET /api/v1/agents`

## Phase 5 — Platform core hardening

- [x] `A-1` Add tenant-isolation coverage across two `AgentContext` instances
- [x] `A-2` Add cancellation propagation coverage in `platform/java/agent-core`
- [x] `A-3` Add per-agent-type lifecycle transition coverage
- [x] `A-4` Add same-event-loop concurrency coverage for two agents
- [x] `A-5` Add failed-`Promise` surfacing coverage
- [x] `KP-1` Add kernel plugin lifecycle FSM coverage
- [x] `KP-2` Add plugin load-failure isolation coverage
- [x] `KP-3` Add version-mismatch rejection coverage
- [x] `KP-4` Add classloader-release / leak coverage
- [x] `KP-5` Add idempotent SPI registration coverage

## Phase 6 — High-risk module coverage uplift

- [x] `AT-1…AT-9` Add one runtime test per canonical agent type in `products/aep/aep-agent-runtime`
- [x] `AT-10` Add golden-set evaluation coverage for `aep-agent-runtime`
- [x] `AT-11` Add timeout/retry/circuit-break resilience coverage for `aep-agent-runtime`
- [x] `DE-1` Add tenant-isolation coverage in `products/data-cloud/platform-entity`
- [x] `DE-2` Add optimistic-concurrency conflict coverage in `products/data-cloud/platform-entity`
- [x] `DE-3` Add soft-delete audit-preservation integration coverage in `products/data-cloud/platform-entity`
- [x] `DE-4` Add DTO/persistence contract coverage in `products/data-cloud/platform-entity`
- [x] `DC-1` Add schema-validation coverage in `products/data-cloud/platform-config`
- [x] `DC-2` Add env/file/flag precedence coverage in `products/data-cloud/platform-config`
- [x] `DC-3` Add reload-on-file-change integration coverage in `products/data-cloud/platform-config`
- [x] `AV-1` Add streaming throughput coverage for `audio-streaming` and `video-streaming`
- [x] `AV-2` Add network-loss recovery coverage for `audio-streaming` and `video-streaming`
- [x] `AV-3` Add wire-format contract coverage for `audio-streaming` and `video-streaming`
- [x] `AV-4` Add frame-ordering coverage across reconnects
- [x] `YS-1` Add DTO round-trip coverage in `products/yappc/core/yappc-shared`
- [x] `YS-2` Add shared util boundary/error-path coverage in `products/yappc/core/yappc-shared`
- [x] `YS-3` Add ArchUnit dependency rule for `products/yappc/core/yappc-shared`
- [x] `YD-1` Add domain aggregate invariant coverage in `products/yappc/core/yappc-domain-impl`
- [x] `YD-2` Add repository-port adherence coverage in `products/yappc/core/yappc-domain-impl`
- [x] `YD-3` Add idempotency/property-based coverage in `products/yappc/core/yappc-domain-impl`
- [x] `AG-1` Add token replay denial coverage in `shared-services/auth-gateway`
- [x] `AG-2` Add token substitution denial coverage in `shared-services/auth-gateway`
- [x] `AG-3` Add key-rotation coverage in `shared-services/auth-gateway`
- [x] `AG-4` Add OIDC discovery failure integration coverage in `shared-services/auth-gateway`
- [x] `HA-1` Add approval-state-machine coverage in `platform-plugins/plugin-human-approval`
- [x] `HA-2` Add quorum semantics integration coverage in `platform-plugins/plugin-human-approval`
- [x] `HA-3` Add timeout escalation coverage in `platform-plugins/plugin-human-approval`

## Phase 7 — AI/ML evaluation and performance tiers

- [x] Add committed eval fixtures for `platform/java/ai-integration`
- [x] Add committed eval fixtures for `products/yappc/core/ai`
- [x] Add committed eval fixtures for `products/aep/aep-agent-runtime`
- [ ] Add performance/load tier for `aep-registry`
- [ ] Add cold-start/warm-start performance tier for `data-cloud/platform-launcher`
- [ ] Add performance tier for `audio-video/{audio,video}-streaming`
- [ ] Add performance tier for `products/yappc/core/yappc-services`

## Phase 8 — Refactor and standardization follow-through

- [ ] Consolidate duplicated product-local `platform-*` abstractions where truly shared
- [ ] Promote reusable launcher-test patterns into `platform/java/testing`
- [ ] Converge `agent-catalog` ownership on `platform/agent-catalog/`
- [ ] Centralize agent-registry usage behind AEP where applicable
- [x] Verify `gradle/doc-tag-check.gradle` is wired into standard checks
- [x] Keep package-name, module-wiring, and anti-theatre guardrails enforced in CI

## Final closure

- [ ] Verify every audit ID in the implementation plan is closed or explicitly waived with justification
- [x] Verify no placeholder assertions remain in audited roots
- [x] Verify no unticketed disabled tests remain on critical paths
- [ ] Verify no forbidden `@ghatana/*` names remain in active source or live outputs
- [ ] Verify all orphan/empty modules are resolved
- [x] Verify AEP registry routes are covered by contract/integration tests
- [x] Verify agent-core tenant isolation and kernel plugin lifecycle are covered
- [x] Verify high-risk modules have been re-evaluated after remediation

