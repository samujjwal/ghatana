# AEP Dissertation Traceability

**Status:** Target architecture with verified contract anchors  
**Owner:** AEP maintainers  
**Last reviewed:** 2026-05-23

## Purpose

This document maps the adaptive event stream processing foundation to AEP product modules and implementation checkpoints.

AEP modernizes the dissertation foundation with distributed stateful stream execution, probabilistic inference, online learning, neuro-symbolic reasoning, RAG-grounded agents, and governed tool use. These advancements extend typed event operators; they do not replace PatternSpec, EPL, operator graphs, or governed pattern lifecycle.

## Traceability Map

| Dissertation concept | AEP product concept | Current implementation anchor | Remaining work |
| --- | --- | --- | --- |
| Event model | `CanonicalEvent` | Verified contract in co-located operator-contracts module | Migrate legacy raw payload flows to canonical envelopes |
| Event context | `EventContext` | Verified contract with tenant, bindings, time, uncertainty, replay, and match state | Require every runtime operator path to consume it |
| Event preprocessing | Filter, transform, enrich, extract operators | Operator kinds and agent enrichment/extraction contracts exist | Runtime adapter must normalize preprocessing stages into operator DAG nodes |
| EPL and pattern language | PatternSpec / EPL | PatternSpec docs, JSON Schema, validator, examples, and deterministic contract compiler exist | Full EPL text parser and runtime DAG execution |
| Formal operators | `EventOperator` | Unified validation, compilation, and process contract exists | Migrate legacy operators to the unified contract |
| Temporal semantics | Time model, watermarks, late-event policy, replay | Time/replay context and late-event evaluator exist | Runtime watermark, checkpoint, and partial-match integration |
| Pattern detection | Pattern runtime graph | `CompiledPattern` runtime graph metadata exists | Executable graph runner and stateful partial-match engine |
| Uncertainty | `UncertaintyContext` and propagation | Uncertainty dimensions and propagation helpers exist | Calibrated production thresholds and operator-wide enforcement |
| Predictive patterns | `ACTIVE` lifecycle patterns | Lifecycle states and transition policy exist | Pattern registry persistence, deployment, rollback, and active runtime binding |
| Recommended patterns | `RECOMMENDED` lifecycle patterns and `pattern.suggested` | Recommendation policy emits `pattern.suggested` and blocks direct activation | Mining, scoring, shadow evaluation, and review workflow integration |
| Pattern exploration | Event group discovery | Documented roadmap | Online/offline correlated event group mining |
| Pattern extraction | Candidate PatternSpec synthesis | `AgentPatternSynthesisOperator` contract requires `pattern.suggested` | Similarity extraction runtime and schema validation for generated specs |
| Pattern learning | Learning-to-recommendation loop | Pattern score, candidate, suggestion, and recommendation contracts exist | Online learning services, score history, and review packet generation |
| Pattern adaptation | Governed lifecycle transitions | Lifecycle policy prevents unsafe direct promotion | Shadow deployment and governed promotion/retirement automation |
| Expert feedback | Review events and review operators | `AgentReviewOperator` contract blocks high-risk self-approval | Human review queues and auditable review event persistence |
| Agents | `EventOperatorCapability` | Agent capability contracts and canonical event-operator capability roles exist | Runtime-wide migration from callbacks/detectors to capability-bound DAG nodes |
| Side-effecting action | `AgentActionOperator` | Approval, tool, audit, and idempotency checks exist at contract level | Tool execution gateway, policy engine integration, and audit event persistence |
| Event storage substrate | EventCloud SPI | Append, tail, replay, checkpoint, watermark, offset, subscription, and partial-match SPI contracts exist | Data-Cloud storage bridge behind the AEP SPI |

## Layer Placement

- Stream runtime: EventCloud SPI, PatternSpec compiler, operator DAG runtime, checkpointing, watermarks, and replay.
- Probabilistic inference: `UncertaintyContext`, operator propagation rules, model confidence, retrieval confidence, calibration, and evidence.
- Online learning: correlated event groups, pattern extraction, score history, recommended patterns, and shadow evaluation.
- Neuro-symbolic reasoning: PatternSpec/EPL plus typed `EventOperatorCapability` bindings and validated output schemas.
- RAG-grounded agents: retrieval policy, evidence confidence, prompt/retrieval snapshots, replay policy, and typed capability outputs.
- Governed tool use: `AGENT_ACTION` capability role, tool policy, approval policy, audit policy, idempotency keys, and side-effect profiles.

## Coherence Rules

- GenAI and agents participate through typed `EventOperatorCapability` bindings.
- Learning emits recommended or shadow pattern events; it does not mutate active rules directly.
- Human and expert review are represented as events and review operators.
- EventCloud and adaptive event semantics are AEP-owned.
- Data-Cloud may provide governed storage plugins behind stable AEP SPI boundaries, but it must not expose CEP, PatternSpec, EPL, or adaptive event semantics.
