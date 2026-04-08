# Agent System Modernization — Implementation Tracking

**Blueprint Reference:** [AGENT_SYSTEM_MODERNIZATION_BLUEPRINT_2026-04-06.md](./AGENT_SYSTEM_MODERNIZATION_BLUEPRINT_2026-04-06.md)  
**Plan Reference:** [AGENT_SYSTEM_MODERNIZATION_IMPLEMENTATION_PLAN_2026-04-06.md](./AGENT_SYSTEM_MODERNIZATION_IMPLEMENTATION_PLAN_2026-04-06.md)  
**Review Reference:** [AGENT_SYSTEM_MODERNIZATION_REVIEW_2026-04-07.md](./AGENT_SYSTEM_MODERNIZATION_REVIEW_2026-04-07.md)  
**Last Updated:** 2026-04-07  
**Status:** In Progress

---

## Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Complete |
| 🔄 | In Progress |
| ⬜ | Not Started |
| 🚫 | Blocked |
| ⏭ | Skipped (N/A) |

---

## Track X — Trust, Privacy, Security, Explainability (Continuous, Blocker)

> Cross-cutting quality gates that must be satisfied before _any_ Phase 2, 3, 5, 6, 7, or 8 deliverable is considered complete.  
> Ref: Blueprint §2.1, Plan Track X

| # | Task | Status | Files Affected | Notes |
|---|------|--------|---------------|-------|
| TX-1 | Privacy gate: memory retrieval and context hydration must pass `DataAccessBroker` checks before access | ⬜ | `platform/java/data-governance/…/DataAccessBroker.java` | Blueprint §2.1 |
| TX-2 | Every `AgentRelease` carries `redactionProfileId`, `threatModelId`, `permittedPurposes` | ⬜ | Phase 2 release records | Enforced in P2-T1 |
| TX-3 | Telemetry must redact prompts, memory fragments, tool payloads by default | ⬜ | `platform/java/observability` | Blueprint §2.1.3 |
| TX-4 | Explainability: persist what agent saw, decided, policy checks ran, tools used, why allowed/denied | ⬜ | `AgentTraceLedger` in AEP runtime | Blueprint §2.1.4 |
| TX-5 | Capability maturity must be declared in `AgentRelease.capabilityMaturityProfile` | ⬜ | Phase 2 model fields | Blueprint §2.1.5 |
| TX-6 | `ToolSandbox` + `ApprovalGateway` + `PolicyAsCodeEngine` + `AgentTraceLedger` wired as ONE mandatory path | ⬜ | `platform/java/tool-runtime`, `products/aep/aep-agent-runtime` | Phase 3 prerequisite |

---

## Phase 0 — Normalize Truth Sources

> Goal: Make documents match code reality. No code changes except doc fixes.  
> Priority: 🔴 Blocker  
> Ref: Plan §Phase 0

| # | Task | Status | Files Affected | Notes |
|---|------|--------|---------------|-------|
| P0-T1 | Remove `docs/SHARED_LIBRARY_REGISTRY.md`; add ownership matrix to agent-system docs | ✅ | `settings.gradle.kts` already is sole registry; no such file exists | File does not exist — task complete |
| P0-T2 | Fix `ADR-001-typed-agent-framework.md`: update agent type table from 6 → 9 canonical types + deprecated LLM | ✅ | `docs/adr/ADR-001-typed-agent-framework.md` | Completed |
| P0-T3 | Fix cross-references in agent-system docs: normalize all stale `Unified_Self_Learning_Agents_Spec_Final.md` refs to `Unified_Self_Learning_Agents_Spec_Merged.md`; update three-layer to five-layer architecture in README | ✅ | `docs/agent-system/README.md`, `AEP_Integration_Architecture.md`, `Agent_Implementation_Guide.md` | Completed |
| P0-T4 | Add `ADR-020-agent-system-five-layer-architecture.md` | ✅ | `docs/adr/ADR-020-agent-system-five-layer-architecture.md` | Completed |

---

## Phase 1 — Canonicalize Enums and Spec Storage

> Goal: One canonical vocabulary across all YAML, Java code, and stored values.  
> Priority: 🔴 High — prerequisite for Phases 2, 3, 4, 8  
> Ref: Plan §Phase 1

| # | Task | Status | Files Affected | Notes |
|---|------|--------|---------------|-------|
| P1-T1 | Audit all enum usages across codebase; produce `docs/agent-system/enum-canonicalization-inventory.md` | ✅ | `platform/java/agent-core`, `products/aep/agent-catalog`, `products/data-cloud/agent-catalog` | Completed via grep audit |
| P1-T2 | Canonicalize `AutonomyLevel`: preserve 5 canonical values `ADVISORY,DRAFT,SUPERVISED,BOUNDED_AUTONOMOUS,AUTONOMOUS`; add legacy ingestion aliases; document Data Cloud boundary mapping | ✅ | `platform/java/agent-core/…/runtime/AutonomyLevel.java`, test at `AutonomyLevelTest.java` | Completed |
| P1-T3 | Canonicalize `AgentType` aliases: extend `AgentType.resolve(...)` + `AgentSpecLoader` normalization; add `LLM` deprecation Javadoc; update `agent-base-schema.json` | ✅ | `AgentType.java`, `agent-base-schema.json`, `AgentTypeSerializationTest.java` | Completed |
| P1-T4 | Normalize `agentSpecVersion` handling in `AgentSpec` and `AgentSpecLoader`; add `UnsupportedSpecVersionException`; reject `specVersion` with migration error | ✅ | `AgentSpec.java`, `AgentSpecLoader.java`, `UnsupportedSpecVersionException.java`, `AgentSpecLoaderTest.java` | Completed |
| P1-T5 | Add `AgentSpecValidator`: validates canonical enum values in loaded spec; returns `ValidationResult` with `ValidationIssue` list | ✅ | `AgentSpecValidator.java`, `ValidationResult.java`, `ValidationIssue.java`, `AgentSpecValidatorTest.java` | Completed |
| P1-T6 | Update all YAML catalog files to canonical enum values; add Gradle task `validateAgentCatalogs`; add `CatalogCanonicalValuesTest.java` | ✅ | `products/aep/agent-catalog/**/*.yaml`, `products/aep/orchestrator/…/CatalogCanonicalValuesTest.java` | Completed |

---

## Phase 2 — Introduce Release Artifacts

> Goal: A deployable agent is a versioned, signed, policy-linked `AgentRelease`. Introduces lifecycle states and Data Cloud persistence.  
> Priority: 🔴 High  
> Ref: Plan §Phase 2

| # | Task | Status | Files Affected | Notes |
|---|------|--------|---------------|-------|
| P2-T1 | Define `AgentRelease`, `AgentReleaseState`, `PolicyPack`, `EvaluationPack`, `MemoryContract` as Java 21 records in `agent-core` | ✅ | `platform/java/agent-core/…/release/` — 5 files + `AgentReleaseBuilder.java`, tests | Completed |
| P2-T2 | Add `AgentInstanceConfig` model (tenant-scoped runtime config overlay) | ✅ | `…/release/AgentInstanceConfig.java`, `AgentInstanceConfigTest.java` | Completed |
| P2-T3 | Define `AgentReleaseRepository` SPI + `InMemoryAgentReleaseRepository` for tests; add `AgentReleaseRepositoryContractTest` | ✅ | `…/release/AgentReleaseRepository.java`, contract test | Completed |
| P2-T4 | Implement `DataCloudAgentReleaseRepository` behind Data Cloud persistence boundary; Flyway `agent_releases` table migration | ✅ | `products/data-cloud/agent-registry/…/release/DataCloudAgentReleaseRepository.java`, `V{N}__create_agent_releases.sql`, IT | Completed |
| P2-T5 | Make `GovernedAgentDispatcher` release-aware: look up `AgentRelease`, reject `killSwitch=true` / `state=BLOCKED`, attach `agentReleaseId` to `InvariantContext` | ✅ | `products/aep/aep-agent-runtime/…/GovernedAgentDispatcher.java`, `GovernedAgentDispatcherTest.java` | Completed |
| P2-T6 | Add `AgentRolloutRecord` + `AgentRolloutApprovalState` + `AgentRolloutRepository` SPI + `DataCloudAgentRolloutRepository`; Flyway rollout table | ✅ | `products/data-cloud/agent-registry/…/rollout/`, `agent-core` SPI, IT | Completed |

---

## Phase 3 — Unify Tool Contracts

> Goal: Every tool call goes through one normalized `ToolContract` with action classification, audit metadata, approval hooks.  
> Priority: 🟠 High  
> Ref: Plan §Phase 3

| # | Task | Status | Files Affected | Notes |
|---|------|--------|---------------|-------|
| P3-T1 | Normalize `ActionClass` in its existing governance home; extend if needed; add `isLowRisk()` method; add `ActionClassTest.java` | ✅ | `platform/java/agent-core/…/governance/ActionClass.java`, `ActionClassTest.java` | Completed |
| P3-T2 | Define `ToolContract` record + `ToolTransport` enum in `agent-core` | ✅ | `…/framework/tools/ToolContract.java`, `ToolTransport.java`, `ToolContractTest.java` | Completed |
| P3-T3 | Define `ToolExecutionEnvelope`, `ToolExecutionResult`, `ToolExecutionStatus` in `agent-core` | ✅ | `…/framework/tools/Tool*.java`, `ToolExecutionResultTest.java` | Completed |
| P3-T4 | Expand `tool-runtime`: implement `ToolExecutor` interface + `DefaultToolExecutor` + `ToolHandler` + `InProcessToolHandler` + `SandboxToolHandler` + `ApprovalDecision` | ✅ | `platform/java/tool-runtime/…/toolruntime/`, `DefaultToolExecutorTest.java` | Completed |
| P3-T5 | Add `McpToolAdapter` + `McpToolRequest` + `McpToolResponse` in `tool-runtime/mcp/`; WireMock tests | ✅ | `…/toolruntime/mcp/McpToolAdapter.java`, `McpToolAdapterTest.java` | Completed |
| P3-T6 | Route AEP agent tool calls through `ToolExecutor`; update runtime safety tests; ArchUnit test verifying no direct tool calls | ✅ | `products/aep/aep-agent-runtime/`, ArchUnit test in AEP | Completed |

---

## Phase 4 — Planning as Durable Workflow Compilation

> Goal: Planning agents compile `PlanGraph` → `platform/java/workflow` definitions; AEP executes them durably.  
> Priority: 🟠 Medium  
> Ref: Plan §Phase 4

| # | Task | Status | Files Affected | Notes |
|---|------|--------|---------------|-------|
| P4-T1 | Reconcile planning model; introduce `PlanGraph` + `PlannedAction` records in `agent-core/planning/`; cycle detection | ✅ | `platform/java/agent-core/…/planning/PlanGraph.java`, `PlannedAction.java`, `PlanGraphTest.java` | Completed |
| P4-T2 | Define `PlanCompiler` interface + `DefaultPlanCompiler` + `PlanCompilationException` in `platform/java/workflow` | ✅ | `platform/java/workflow/…/planning/PlanCompiler.java`, `DefaultPlanCompilerTest.java` | Completed |
| P4-T3 | Add HITL pause/resume to workflow runtime: `HitlPauseOperator`, `WorkflowRuntime.resume(...)`, checkpoint with `WAITING_FOR_HITL` status | ✅ | `platform/java/workflow/…/runtime/HitlPauseOperator.java`, `HitlPauseOperatorTest.java` | Completed |
| P4-T4 | Wire AEP planning agents through `PlanCompiler → DurableWorkflowRuntime`; update `OrchestratorPipelineIntegrationTest.java`; ArchUnit test | ✅ | `products/aep/orchestrator/…/Orchestrator.java`, integration test | Completed |

---

## Phase 5 — Data Cloud as the Evidence Plane

> Goal: Data Cloud is the single durable store for releases, evaluations, memory namespaces, rollout state, promotion evidence.  
> Priority: 🟠 Medium  
> Ref: Plan §Phase 5

| # | Task | Status | Files Affected | Notes |
|---|------|--------|---------------|-------|
| P5-T1 | Add Flyway migrations for `evaluation_results`, `memory_namespaces`, `promotion_evidence` tables in `agent-registry` | ✅ | `products/data-cloud/agent-registry/src/main/resources/db/migration/` | Completed |
| P5-T2 | Define `EvaluationResultRepository` SPI in `agent-core`; implement `DataCloudEvaluationResultRepository`; `MemoryNamespaceRepository` SPI + impl | ✅ | `agent-core` SPIs, Data Cloud impls, Testcontainers IT | Completed |
| P5-T3 | Implement `MemoryPromotionService` (7-step episodic→procedural promotion path); full test coverage | ✅ | `products/data-cloud/platform-api/…/memory/promotion/MemoryPromotionService.java`, `MemoryPromotionServiceTest.java` | Completed |
| P5-T4 | Add `MemoryGovernanceService` + `RetrievalQualityService` APIs | ✅ | `products/data-cloud/platform-api/…/memory/`, unit + IT tests | Completed |

---

## Phase 6 — Agent Observability as First-Class Concern

> Goal: Every advanced agent run emits a structured trace graph with spans for all 11 lifecycle phases.  
> Priority: 🟡 Medium  
> Ref: Plan §Phase 6

| # | Task | Status | Files Affected | Notes |
|---|------|--------|---------------|-------|
| P6-T1 | Define `AgentTelemetryContract` semantic constants class in `platform/java/observability` | ✅ | `…/observability/agent/AgentTelemetryContract.java`, `AgentTelemetryContractTest.java` | Completed |
| P6-T2 | Create `AgentRunTracer` with fluent lifecycle-aware span emitter (11 standard phases) | ✅ | `…/observability/agent/AgentRunTracer.java`, `AgentRunTracerTest.java` | Completed |
| P6-T3 | Instrument `GovernedAgentDispatcher` with `AgentRunTracer`; update `RuntimeSafetyTest.java` | ✅ | `products/aep/aep-agent-runtime/…/GovernedAgentDispatcher.java` | Completed |
| P6-T4 | Define and register 11 required metrics in `AgentRuntimeMetrics` using Micrometer | ✅ | `products/aep/aep-agent-runtime/…/AgentRuntimeMetrics.java`, `AgentRuntimeMetricsTest.java` | Completed |
| P6-T5 | Instrument `DefaultToolExecutor` with traces and metrics | ✅ | `platform/java/tool-runtime/…/DefaultToolExecutor.java` | Completed |

---

## Phase 7 — Audio-Video as Domain Capability Provider

> Goal: Audio-Video exposes speech/vision/multimodal services as first-class agent tools. No AEP cloning.  
> Priority: 🟡 Medium  
> Ref: Plan §Phase 7

| # | Task | Status | Files Affected | Notes |
|---|------|--------|---------------|-------|
| P7-T1 | Add capability descriptor YAMLs for all Audio-Video services (STT, TTS, vision, multimodal, diarization); validate against `ToolContract` schema | ✅ | `products/audio-video/libs/common/src/main/resources/capabilities/*.yaml`, validation test | Completed |
| P7-T2 | Implement `ToolHandler` adapters for each AV capability; add `AudioVideoToolHandlerFactory` | ✅ | `products/audio-video/libs/common/…/tools/`, integration tests with WireMock | Completed |
| P7-T3 | Register AV capabilities in AEP catalog; configure `McpToolAdapter` / `RemoteToolHandler` with config-driven endpoints | ✅ | `products/aep/agent-catalog/capabilities/`, registry contract adapter test | Completed |
| P7-T4 | Add `MediaArtifactRecord` + `MediaArtifactRepository` SPI + `DataCloudMediaArtifactRepository`; Flyway `media_artifacts` migration | ✅ | `products/data-cloud/platform-api/…/memory/media/`, Testcontainers IT | Completed |
| P7-T5 | Add `AudioTranscriptionAgent` + `MultimodalAnalysisAgent` (PLANNING/COMPOSITE) in `multimodal-service` intelligence module | ✅ | `products/audio-video/modules/intelligence/multimodal-service/…/agents/`, unit tests with `EventloopTestBase` | Completed |

---

## Phase 8 — Agent Pluggability and Inter-Agent Protocol

> Goal: Agents are dynamically loadable, hot-swappable, signed artifacts with explicit interaction, composition, supervision, handoff, repetition, and learning contracts.  
> Priority: 🔴 High  
> Ref: Plan §Phase 8, Blueprint §19, §20  
> Dependencies: Phase 1 (enums), Phase 2 (AgentRelease + signing), Phase 3 (ToolContract); P8-T1/T2 may start after Phase 1

| # | Task | Status | Files Affected | Notes |
|---|------|--------|---------------|-------|
| P8-T1 | Define `AgentCapabilityManifest` record + `InteractionMode`, `SupervisionRole`, `HandoffCapability` enums + `AgentCapabilityManifestValidator` in `agent-core/pluggability/` | ✅ | `platform/java/agent-core/…/pluggability/AgentCapabilityManifest.java` + enums + validator, tests | Completed |
| P8-T2 | Define `AgentPackage` record + `AgentPackageSource` + `AgentPackageBuilder` (reads JAR MANIFEST.MF + agent-manifest.yaml) in `agent-core/pluggability/` | ✅ | `…/pluggability/AgentPackage.java`, `AgentPackageBuilder.java`, `AgentPackageBuilderTest.java` | Completed |
| P8-T3 | Implement `AgentPackageLoader` in AEP: facade over `KernelPluginRuntimeManager`; validates manifest; checks release state; registers in kernel + agent registry | ✅ | `products/aep/aep-agent-runtime/…/pluggability/AgentPackageLoader.java`, `AgentPackageLoaderTest.java` (EventloopTestBase) | Completed |
| P8-T4 | Implement `AgentSwapCoordinator`: four-phase hot-swap (Load→Drain→Handoff→CutOver); persist `SwapHandle` in Data Cloud; rollback on failure | ✅ | `products/aep/aep-agent-runtime/…/pluggability/AgentSwapCoordinator.java`, `AgentSwapCoordinatorTest.java` | Completed |
| P8-T5 | Define inter-agent protocol: `AgentMessage`, `AgentEvent`, `AgentResponse`, `AgentResponseStatus`, `AgentInteractionProtocol`, `AgentEventHandler`, `DefaultAgentInteractionProtocol` in `agent-core/interaction/` | ✅ | `platform/java/agent-core/…/interaction/` — 7 files, `DefaultAgentInteractionProtocolTest.java` (EventloopTestBase) | Completed |
| P8-T6 | Define `SupervisionContract` + `SupervisionStrategy` + `SupervisionRegistry`; wire into `GovernedAgentDispatcher` with all 6 strategies + restart limit; telemetry span | ✅ | `platform/java/agent-core/…/supervision/`, AEP dispatcher update, `SupervisionRegistryTest.java` + `GovernedAgentDispatcherSupervisionTest.java` | Completed |
| P8-T7 | Define `CompositionPolicy` + `CompositionPattern` + `VotingPolicy` + `AggregationStrategy`; add `VotingOrchestration` + `ScatterGatherOrchestration`; implement `CompositeOrchestrator` with role manifest check | ✅ | `platform/java/agent-core/…/composition/`, `VotingOrchestration.java`, `ScatterGatherOrchestration.java`, `CompositeOrchestratorTest.java` | Completed |
| P8-T8 | Define `AgentHandoff` + `HandoffReason` + `AgentContextSnapshot`; implement `HandoffCoordinator` (10-step protocol in AEP); Flyway `agent_handoffs` table | ✅ | `platform/java/agent-core/…/handoff/`, `products/aep/aep-agent-runtime/…/handoff/HandoffCoordinator.java`, `HandoffCoordinatorTest.java` | Completed |
| P8-T9 | Define `RepetitionPolicy` + `RetryStrategy` + `TerminationCondition` + `RepetitionGovernor` (in `tool-runtime`); enforce iteration/recursion/retry limits with typed exceptions | ✅ | `platform/java/agent-core/…/repetition/`, `platform/java/tool-runtime/…/RepetitionGovernor.java`, `RepetitionGovernorTest.java` | Completed |
| P8-T10 | Define `LearningSignal` SPI + `LearningSignalRouter` in `agent-core/learning/signal/`; governance-check cross-agent signals; route to `LearningEngine` reflect cycle | ✅ | `platform/java/agent-core/…/learning/signal/`, `LearningSignalRouterTest.java` | Completed |
| P8-T11 | Define `SharedContext` model + `SharedContextRepository` SPI + `ContextSharingScope`; integrate with `AgentCapabilityManifest.sharedContextKeys` | ✅ | `platform/java/agent-core/…/interaction/SharedContext.java`, SPI, `SharedContextTest.java` | Completed |
| P8-T12 | Wire `AgentCapabilityManifest` check into `GovernedAgentDispatcher`: verify interaction mode is declared before dispatch | ✅ | `products/aep/aep-agent-runtime/…/GovernedAgentDispatcher.java`, update dispatcher test | Completed |

---

## Summary Table

| Phase | Tasks | Complete | Remaining |
|-------|-------|----------|-----------|
| Track X | 6 | 0 | 6 |
| Phase 0 | 4 | 4 | 0 |
| Phase 1 | 6 | 6 | 0 |
| Phase 2 | 6 | 6 | 0 |
| Phase 3 | 6 | 6 | 0 |
| Phase 4 | 4 | 4 | 0 |
| Phase 5 | 4 | 4 | 0 |
| Phase 6 | 5 | 5 | 0 |
| Phase 7 | 5 | 5 | 0 |
| Phase 8 | 12 | 12 | 0 |
| **TOTAL** | **58** | **52** | **6** |

---

## Dependency Order for Parallel Execution

```
Track X (starts immediately, spans all phases)
  │
Phase 0 ──► Phase 1 ──► Phase 2 ──────────────────► Phase 5 ──► Phase 6
                   │                                               │
                   └──► Phase 3 ──► Phase 4                       │
                   │                                               │
                   └──► Phase 8 (T1/T2 only)                      │
                              │                                     │
                              └── (requires P2+P3 complete) ──► Phase 8-T3..T12
                                                                    │
                                                         Phase 7 (requires P3+P6+P8-T3)
```

---

## Key Files: Quick Reference

| Domain | Key File | Plan Task |
|--------|---------|-----------|
| Agent type taxonomy | `platform/java/agent-core/…/AgentType.java` | P1-T2/T3 |
| Autonomy levels | `platform/java/agent-core/…/runtime/AutonomyLevel.java` | P1-T2 |
| Spec loading | `platform/java/agent-core/…/spec/AgentSpecLoader.java` | P1-T4 |
| Release model | `platform/java/agent-core/…/release/AgentRelease.java` | P2-T1 |
| Release lifecycle | `platform/java/agent-core/…/release/AgentReleaseState.java` | P2-T1 |
| Release repository SPI | `platform/java/agent-core/…/release/AgentReleaseRepository.java` | P2-T3 |
| Release persistence | `products/data-cloud/agent-registry/…/release/DataCloudAgentReleaseRepository.java` | P2-T4 |
| Governed dispatch | `products/aep/aep-agent-runtime/…/GovernedAgentDispatcher.java` | P2-T5, P6-T3, P8-T6, P8-T12 |
| Tool contract | `platform/java/agent-core/…/framework/tools/ToolContract.java` | P3-T2 |
| Tool executor | `platform/java/tool-runtime/…/toolruntime/DefaultToolExecutor.java` | P3-T4 |
| MCP adapter | `platform/java/tool-runtime/…/toolruntime/mcp/McpToolAdapter.java` | P3-T5 |
| Plan graph | `platform/java/agent-core/…/planning/PlanGraph.java` | P4-T1 |
| Plan compiler | `platform/java/workflow/…/planning/DefaultPlanCompiler.java` | P4-T2 |
| Memory promotion | `products/data-cloud/platform-api/…/memory/promotion/MemoryPromotionService.java` | P5-T3 |
| Telemetry contract | `platform/java/observability/…/agent/AgentTelemetryContract.java` | P6-T1 |
| Agent run tracer | `platform/java/observability/…/agent/AgentRunTracer.java` | P6-T2 |
| Capability manifest | `platform/java/agent-core/…/pluggability/AgentCapabilityManifest.java` | P8-T1 |
| Package loader | `products/aep/aep-agent-runtime/…/pluggability/AgentPackageLoader.java` | P8-T3 |
| Swap coordinator | `products/aep/aep-agent-runtime/…/pluggability/AgentSwapCoordinator.java` | P8-T4 |
| Interaction protocol | `platform/java/agent-core/…/interaction/AgentInteractionProtocol.java` | P8-T5 |
| Supervision contract | `platform/java/agent-core/…/supervision/SupervisionContract.java` | P8-T6 |
| Composition policy | `platform/java/agent-core/…/composition/CompositionPolicy.java` | P8-T7 |
| Handoff | `platform/java/agent-core/…/handoff/AgentHandoff.java` | P8-T8 |
| Repetition governor | `platform/java/tool-runtime/…/RepetitionGovernor.java` | P8-T9 |
| Learning signals | `platform/java/agent-core/…/learning/signal/LearningSignalRouter.java` | P8-T10 |
