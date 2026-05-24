# Agentic Event Processor Planned Features

**Status:** Planned roadmap  
**Owner:** AEP maintainers  
**Last reviewed:** 2026-05-23

## Phase 0 - Build and Documentation Coherence

- Verified: canonical ADRs exist for Data-Cloud/AEP/EventCloud boundaries, `AgentOperator`, and adaptive ESP foundation.
- Verified: AEP, Data-Cloud, EventCloud, and agent-as-operator status language has been normalized across the core docs and specs.
- Verified: co-located AEP contract modules build and pass focused tests.
- Verified: product-boundary architecture tests enforce Data-Cloud-to-AEP and AEP-contract-to-Data-Cloud implementation dependency rules.
- Remaining: make standalone `products/aep` build wiring explicit or document it as intentionally docs/contracts-only.
- Remaining: keep production-readiness claims tied to passing compile, test, architecture, and runtime verification gates.
- Verified: dissertation-to-AEP traceability map exists with implementation anchors and remaining runtime work.

## Phase 1 - Unified Event and Operator Foundation

- Verified: `CanonicalEvent` envelope.
- Verified: `EventContext`.
- Verified: `EventOperator` contract.
- Verified: `AgentOperator` subtype.
- Verified: `OperatorCatalog` metadata for operator kind, agent kind, side-effect profile, replay profile, schema, cost, security, owner, and version.
- Verified: operator catalog runtime admission rejects unknown or unapproved operators, requires declared tool policy metadata for side-effecting agent operators, and is enforced by the co-located pipeline stage executor when using `UnifiedOperatorCatalog`.
- Verified: time model contracts and late-event policy evaluation.
- Verified: uncertainty model contracts and propagation helpers.
- Remaining: migrate all legacy runtime operators to the unified `EventOperator` execution path.

## Phase 2 - PatternSpec / EPL

- Verified: PatternSpec documentation, JSON Schema, examples, structural validator, operator-shape validation, and deterministic contract compiler.
- Verified: agent operators are accepted as valid PatternSpec expression nodes when required schemas and policies are present.
- Verified: production patterns require explicit time, uncertainty, lifecycle, replay, and output event metadata.
- Verified: compiler emits deterministic `CompiledPattern` runtime graph metadata.
- Remaining: implement a formal text grammar/parser for EPL syntax.
- Verified: contract-level PatternSpec graphs adapt to executable pipeline DAG shape.
- Remaining: connect adapted DAGs to production EventPipeline execution.
- Remaining: broaden validation tests to cover parser errors, schema compatibility, and deeper operator type compatibility.

## Phase 3 - EventCloud and Runtime

- Verified: EventCloud SPI contracts for append, tail, replay, subscription, checkpoint, partial-match state, offset, partition, tenant, watermark, and dead-letter metadata.
- Verified: EventCloud SPI contract tests cover interface-level behavior and boundary expectations.
- Verified: PatternSpec-to-pipeline adapter produces a DAG with EventCloud source/sink stages and agent operator stages.
- Verified: Data-Cloud-backed `EventCloudStore` bridge persists canonical AEP events through the Data-Cloud event log behind the AEP SPI.
- Verified: EventCloud checkpoint and partial-match stores persist through provider-backed AEP state events, including restart-readable checkpoints and delete tombstones.
- Remaining: wire replay, tailing, watermark, late-event handling, partial-match state, and checkpointing into the production runtime.
- Remaining: connect adapted PatternSpec pipeline DAGs to production EventPipeline execution.

## Phase 4 - Learning and Adaptation

- Verified: recommendation contracts emit `pattern.suggested` and block direct activation by learning outputs.
- Verified: lifecycle policy and registry include recommended, approved, active, degraded, retired, rollback, stored state checks, and auditable lifecycle events.
- Verified: shadow evaluation contracts compute false positives, false negatives, precision, recall, matched outcomes, and review packets without enabling side effects.
- Verified: correlated event group mining contracts discover bounded correlation groups and report support/correlation/search-space reduction metrics.
- Verified: similarity-based pattern extraction contracts emit ranked candidate PatternSpecs that validate before review.
- Verified: explainable pattern scoring contracts record candidate and shadow-evaluation score history without activating production patterns.
- Remaining: event pattern exploration beyond correlation-id grouping.
- Remaining: shadow deployment orchestration and metrics persistence.
- Remaining: persist lifecycle registry state behind the production pattern registry and wire expert, human, and agent feedback into lifecycle governance.

## Phase 5 - Agentic Event Intelligence

- Verified: `AgentPredicateOperator`.
- Verified: `AgentActionOperator`.
- Verified: `AgentActionOperator` emits canonical action audit events for failed, requested, and executed action outcomes.
- Verified: `AgentEnrichmentOperator`.
- Verified: `AgentExtractOperator`.
- Verified: `AgentPatternSynthesisOperator`.
- Verified: `AgentExplanationOperator`.
- Verified: `AgentReviewOperator`.
- Verified: `AgentReflectionOperator`.
- Verified: replay-safe agent execution records, prompt snapshots, retrieval snapshots, tool-call records, output records, replay policy, and replay planner.
- Remaining: full runtime integration for replay-safe agent execution.
- Remaining: tool, memory, model, retrieval, guardrail, and human-review governance in the runtime.

## Phase 5A - Remaining Agent Operator Types

- Migrate existing detector/callback integrations to the concrete agent operator implementations.
- Add emitted-event contract tests for every agent operator kind that emits events; inference-only agent operators may return typed operator results without side-effect events.
- Add runtime replay and audit integration tests for every agent operator kind.
- Verified: contract-level catalog approval checks for side-effecting agent operators are connected to the co-located pipeline stage execution path.
- Remaining: connect catalog approval checks to any other production runtime lookup paths.

## Phase 6 - Production Hardening

- Tenant isolation.
- Security and privacy.
- Audit.
- Verified: Data-Cloud field-level encryption/redaction helper exists for sensitive payload fields.
- Remaining: wire field-level encryption and redaction through all sensitive storage and audit export paths.
- Verified: canonical AEP operator, pattern, agent, replay, EventCloud lag, and DLQ metric facade exists over `MetricsCollector`.
- Remaining: wire the canonical metrics facade through every production operator and runtime path.
- Performance.
- Chaos tests.
- Regression gates.

## Implementation Order

Do not implement agent features before the core operator model is stable.

1. Canonical docs and ADRs.
2. Build health for AEP and Data-Cloud.
3. Product-boundary architecture tests.
4. `CanonicalEvent` and `EventContext`.
5. `EventOperator` contract.
6. `AgentOperator` contract.
7. PatternSpec schema and compiler.
8. EventCloud SPI.
9. Pattern lifecycle.
10. Learning and adaptation operators.
11. Agent operators.
12. Replay-safe agent execution.
13. Governance, observability, and security hardening.
14. Cross-product adoption examples.
