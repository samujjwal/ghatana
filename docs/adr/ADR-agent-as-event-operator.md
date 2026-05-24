# ADR: Agent as Event Operator

**Status:** Superseded by [ADR-agent-capability-event-operator](ADR-agent-capability-event-operator.md)  
**Date:** 2026-05-23  
**Decision Makers:** AEP maintainers  
**Phase:** Adaptive event intelligence foundation

## Context

AEP needs agentic behavior without creating a separate "AI after detection" path. Pattern processing, learning, runtime replay, observability, security, and governance should all see event-processing capabilities through the same execution contract as other event operators.

## Decision

This ADR originally chose to make an agent a special kind of `EventOperator`. That relationship is superseded. The current decision is that an agent is the root abstraction, and event processing is a capability exposed through `EventOperatorCapability`.

```text
EventOperatorCapability extends AgentCapability<EventContext<I>, EventOperatorResult<O>>.
EventOperatorCapability implements EventOperator<I, O>.
Capability bindings can appear in PatternSpec and PipelineSpec wherever an event-processing operator can appear.
Event operator capabilities consume typed event context and emit typed event results.
Event operator capabilities must follow validation, lifecycle, metrics, tracing, replay, uncertainty, governance, and security contracts.
```

The canonical event operator capability roles are:

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

This keeps agent use seamless inside PatternSpec and PipelineSpec while preserving the agent as the root abstraction. It also prevents bypassing replay, uncertainty propagation, audit, policy, and operator catalog governance.

## Consequences

- Pattern examples must bind capabilities inside `SEQ`, `AND`, `WINDOW`, and learning pipelines.
- Governance must distinguish inference-only capabilities from side-effecting capabilities.
- Side-effecting capabilities require tool policy, approval policy, idempotency, and audit events.
- Existing callback-style or detector-style agent integrations should be migrated behind `EventOperatorCapability`.

## Alternatives Considered

1. Run agents only after an alert fires. Rejected because it makes agent behavior invisible to the operator graph and replay model.
2. Treat agents as external callbacks. Rejected because callbacks are hard to validate, govern, and replay.
3. Let every agent define its own payload contract. Rejected because typed input/output schemas are required for PatternSpec validation and governance.
