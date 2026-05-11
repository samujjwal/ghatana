# ADR-021: Unified GAA Definition and Lifecycle

**Status:** Accepted  
**Date:** 2026-05-11  
**Decision Makers:** Platform Architecture Team

## Context

The repo had the right primitives for a uniform GAA platform, but specs, Java contracts, AEP runtime, Data Cloud registry, catalog tooling, docs, and YAPPC frontend contracts did not all enforce the same model.

## Decision

Every Ghatana agent is defined and executed through one substrate:

```text
AgentSpec / AgentDefinition
  -> AgentRelease
  -> GovernedAgentDispatcher
  -> GaaAgentExecutor
  -> AgentTurnPipeline
  -> type-specific strategy
  -> AgentResult + trace
  -> Memory / Learning / Evaluation / Promotion
```

The canonical computational taxonomy has exactly nine top-level types:

```text
DETERMINISTIC, PROBABILISTIC, STREAM_PROCESSOR, PLANNING, HYBRID,
ADAPTIVE, COMPOSITE, REACTIVE, CUSTOM
```

LLM is a `PROBABILISTIC` subtype, not a top-level type. Rule engines, policy engines, and pattern matchers are deterministic subtypes.

All executions use the operational lifecycle:

```text
ADMIT -> PERCEIVE -> REASON -> VERIFY -> ACT -> CAPTURE -> REFLECT -> COMPLETE
```

`PERCEIVE -> REASON -> ACT -> CAPTURE -> REFLECT` remains the cognitive lifecycle. `ADMIT`, `VERIFY`, and `COMPLETE` are platform runtime phases.

## Rules

- Runtime type parsing accepts only canonical names and registered custom types.
- Product roles and personas are not platform agent types.
- `TypedAgent.process()` is invoked by `GaaAgentExecutor` inside the governed lifecycle.
- `AgentResult` is the canonical execution envelope for release, spec, policy, memory, tool, phase trace, evidence, diagnostics, and rollback references.
- `SHADOW` releases may run internally but may not serve responses. `CANARY` and `ACTIVE` may serve responses.
- Type-specific validators give every `AgentType` runtime meaning before release validation.
- Learned artifacts require declared learning targets, provenance, evaluation evidence, promotion, and rollback references before activation.

## Consequences

Catalog specs, frontend contracts, dispatcher code, operator mapping, and docs must align with the canonical substrate. Repo-local tooling may fix specs, but production runtime does not accept alias-based type resolution.
