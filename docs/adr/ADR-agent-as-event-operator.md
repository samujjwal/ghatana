# ADR: Agent as Event Operator

**Status:** Accepted  
**Date:** 2026-05-23  
**Decision Makers:** AEP maintainers  
**Phase:** Adaptive event intelligence foundation

## Context

AEP needs agentic behavior without creating a separate "AI after detection" path. Pattern processing, learning, runtime replay, observability, security, and governance should all see agents through the same contract as other event operators.

## Decision

An agent is a special kind of `EventOperator`.

```text
AgentOperator extends EventOperator.
Agent operators can appear in PatternSpec and PipelineSpec wherever a normal event operator can appear.
Agent operators consume typed event context and emit typed event results.
Agent operators must follow the same validation, lifecycle, metrics, tracing, replay, uncertainty, governance, and security contracts as other operators.
```

The canonical agent operator kinds are:

```text
AGENT_PREDICATE
AGENT_ENRICH
AGENT_EXTRACT
AGENT_PATTERN_SYNTHESIS
AGENT_EXPLANATION
AGENT_REVIEW
AGENT_ACTION
AGENT_REFLECTION
```

## Rationale

This makes agent use seamless inside PatternSpec and PipelineSpec. It also prevents bypassing replay, uncertainty propagation, audit, policy, and operator catalog governance.

## Consequences

- Pattern examples must show agents inside `SEQ`, `AND`, `WINDOW`, and learning pipelines.
- Governance must distinguish inference-only agent operators from side-effecting agent operators.
- Side-effecting agent operators require tool policy, approval policy, idempotency, and audit events.
- Existing callback-style or detector-style agent integrations should be migrated behind `AgentOperator`.

## Alternatives Considered

1. Run agents only after an alert fires. Rejected because it makes agent behavior invisible to the operator graph and replay model.
2. Treat agents as external callbacks. Rejected because callbacks are hard to validate, govern, and replay.
3. Let every agent define its own payload contract. Rejected because typed input/output schemas are required for PatternSpec validation and governance.
