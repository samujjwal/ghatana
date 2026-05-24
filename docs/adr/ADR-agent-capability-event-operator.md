# ADR: Agent Capability Event Operator

**Status:** Accepted

## Context

AEP previously described an agent as a first-class `EventOperator`. That made event processing look like the parent type of an agent and blurred the boundary between the platform agent abstraction and AEP operator execution.

The canonical agent model already starts from `TypedAgent<I, O>` and `AgentDescriptor`. AEP needs to bind PatternSpec and PipelineSpec to executable event-processing behavior without requiring every agent to be an operator.

## Decision

An agent is the root abstraction. Event processing is a capability an agent can expose.

The canonical AEP contract is `EventOperatorCapability<I, O>`, which extends `AgentCapability<EventContext<I>, EventOperatorResult<O>>` and implements `EventOperator<I, O>`.

AEP PatternSpec and PipelineSpec bind to capability references such as:

```yaml
capabilityRef: agents/sre-risk-assessor@1.0.0/capabilities/agent_predicate
```

External or non-AEP agent systems integrate by publishing capability metadata through `ExternalAgentProvider` and executing through a `RemoteCapabilityInvocationClient`.

## Consequences

- Agents do not inherit from event operators.
- Event-operator behavior remains executable inside AEP graphs.
- Side-effecting capabilities must declare tool policy, approval policy, audit policy, replay/idempotency policy, and observability metadata.
- Data-Cloud may persist capability metadata and audit evidence, but AEP owns `EventOperatorCapability` semantics.
