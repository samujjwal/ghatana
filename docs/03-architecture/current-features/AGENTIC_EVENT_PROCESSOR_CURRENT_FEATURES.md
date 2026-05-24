# Agentic Event Processor Current Features

**Status:** Current reality summary  
**Owner:** AEP maintainers  
**Last reviewed:** 2026-05-23

## Current Reality Summary

AEP currently has strong foundations for event processing, pipelines, operators, state, ingestion, and learning concepts. However, the adaptive event intelligence vision is not complete until the following are implemented and verified:

1. Unified operator model for validation and execution.
2. `EventOperatorCapability` as an agent capability for event processing.
3. PatternSpec/EPL with formal time, operator, and uncertainty semantics.
4. Predictive, recommended, and shadow pattern lifecycle.
5. Pattern registry and governance.
6. Learning outputs connected to pattern recommendation and promotion.
7. Consistent observability through `MetricsCollector` and tracing.
8. Replay-safe execution for agent capabilities.
9. CI, build, and test gates for AEP.

## Verified Implemented

- AEP-named runtime and contracts exist in the repository.
- Data-Cloud has event, storage, contract, UI, and AEP integration areas used by AEP-compatible runtime surfaces.
- Existing AEP runtime code includes operators, pipeline/runtime services, agent registry integration, event bridge code, learning concepts, metrics, tests, and Data-Cloud-backed persistence adapters.
- `CanonicalEvent`, `EventContext`, event time context, replay context, uncertainty context, pattern match, and partial-match contracts exist in the co-located AEP operator-contracts module.
- `EventOperator` defines the unified validation, compilation, and execution contract for AEP operators.
- `EventOperatorCapability` extends `AgentCapability` and implements the event-operator execution contract; canonical agent capability roles and side-effect classification are contract-tested.
- `AgentPredicateOperator`, `AgentEnrichmentOperator`, `AgentExtractOperator`, `AgentPatternSynthesisOperator`, `AgentExplanationOperator`, `AgentReviewOperator`, `AgentActionOperator`, and `AgentReflectionOperator` exist as contract-level operator implementations.
- Contract-level agent capabilities include replay behavior, schema validation, typed outputs, confidence propagation, pattern suggestion guardrails, review guardrails, approval, tool, audit, and idempotency checks where applicable.
- `AgentActionOperator` emits canonical `action.failed`, `action.requested`, and `action.executed` audit events around governed action execution.
- EventCloud SPI contracts exist for append, tail, replay, subscription, checkpoint, partial-match state, offset, partition, tenant, watermark, and dead-letter metadata.
- Data-Cloud-backed `EventCloudStore` bridge exists in the AEP event-bridge module; it uses Data-Cloud event-log persistence behind the AEP SPI without putting PatternSpec or CEP semantics into Data-Cloud storage modules.
- EventCloud checkpoint and partial-match stores are persisted as AEP-owned state events through the Data-Cloud event log, including restart-readable checkpoints and partial-match delete tombstones.
- PatternSpec schema, structural validation, operator-shape validation, and deterministic contract compiler exist for core operators, nested patterns, agent capability references, production semantics, and side-effect governance checks.
- A PatternSpec-to-pipeline adapter exists at the contract layer; it converts compiled patterns into a DAG with EventCloud source/sink stages and agent capability stages.
- Pattern lifecycle transition policy, lifecycle event service, and lifecycle registry exist; stored lifecycle state rejects invalid transitions and recommended patterns cannot become active without approval.
- Learning recommendation contracts emit `pattern.suggested` events and prevent direct activation from learning outputs.
- Correlated event group mining contracts discover correlation-id groups inside bounded windows and report support, correlation, and search-space reduction metrics.
- Similarity-based pattern extraction contracts convert correlated event groups into ranked candidate PatternSpecs that validate before review.
- Pattern scoring contracts retain score history, recalculate explainable score snapshots from shadow evaluation metrics, and keep scoring separate from activation.
- Shadow deployment evaluation contracts compute false positives, false negatives, precision, recall, matched outcomes, and review packets while enforcing no production side effects.
- `AepOperatorMetrics` centralizes canonical AEP event, operator, pattern, agent, replay, EventCloud lag, and DLQ metric names over the shared `MetricsCollector`.
- Agent replay records, prompt snapshots, retrieval snapshots, tool-call records, output records, replay policy, and replay planner exist in the co-located agent runtime module.
- Operator catalog admission policy can reject unknown or unapproved operators, side-effecting agent capabilities require declared tool policy metadata, and the pipeline stage executor checks admission before invoking operators from `UnifiedOperatorCatalog`.
- Product-boundary architecture tests verify foundational Data-Cloud planes do not depend on AEP implementation packages, and AEP EventCloud/PatternSpec contracts do not depend on Data-Cloud implementation packages.
- Focused AEP contract, replay, lifecycle, learning, and product-boundary tests pass for the co-located implementation.
- Data-Cloud runtime-composition includes a field-level AES-GCM encryption/redaction helper for sensitive payload fields; full connector/application write-path adoption remains pending.

## Partially Implemented

- Event processing, pattern, and pipeline execution foundations remain split between legacy runtime surfaces and the new unified operator contracts.
- Agent registry and agent execution integration.
- EventCloud-compatible adapter code; the SPI, Data-Cloud-backed EventCloud storage bridge, provider-backed checkpoint/partial-match state events, and PatternSpec-to-pipeline DAG adapter exist, but full provider contract coverage still needs hardening.
- Governance, human review, audit, and runtime truth surfaces.
- Learning and analytics services; correlated mining, similarity extraction, scoring history, recommendation, lifecycle registry, and shadow evaluation contracts exist, but deployment orchestration is not fully wired.
- Observability and run ledger concepts; the metric names, facade, and trace expectations are documented, but every production operator is not yet consistently instrumented through one runtime standard.

## Target Architecture

The canonical target is adaptive event intelligence:

- Unified `EventOperator` runtime adoption across all pattern, pipeline, learning, and agent execution paths.
- PatternSpec/EPL compiler that emits deterministic runtime plans.
- EventCloud runtime backed by an implementation-neutral SPI and optional Data-Cloud persistence plugins.
- Learning-to-recommended-pattern loop with shadow evaluation, review, promotion, rollback, and audit events.
- Agent event-processing behavior represented only as capability-bound DAG nodes, never as out-of-band detector callbacks.

## Agent Capability Current State

Agents are first-class at the contract layer: `EventOperatorCapability` exposes event processing as an `AgentCapability`, agent capability roles are catalogable, and predicate/action capability roles are covered by focused tests. Existing runtime integrations must still be refactored so every agent interaction flows through capability-bound DAG nodes and emits typed events or typed results.

## Known Gaps

- Agent callbacks and detector-style flows must be normalized into capability-bound DAG nodes.
- Current documents and module names still contain Data-Cloud/AEP boundary drift.
- Pattern learning must emit recommended or shadow patterns instead of mutating active runtime behavior directly.
- Side-effecting agent execution has contract-level approval, replay, tool, audit, idempotency, and catalog admission checks, but those controls must still be enforced consistently across all production runtime entrypoints.
- PatternSpec contract compilation, operator-shape validation, and pipeline DAG adaptation exist, but full EPL grammar parsing, deeper operator compatibility checking, schema compatibility checking, and runtime operator execution are still pending.
- Build and CI truth should be treated as a P0 gate before production-readiness claims; the co-located modules pass focused checks, but standalone `products/aep` Gradle wiring is not yet established.
