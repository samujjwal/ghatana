# AEP End-to-End Agentic Implementation Plan

**Status:** Target implementation plan  
**Owner:** AEP maintainers  
**Boundary:** Agents expose EventOperator capabilities; Data-Cloud is storage substrate only.

## Architecture Invariant

Every participant in event intelligence must be represented as either:

1. An event.
2. An `EventOperator`.
3. An operator result.
4. Governed metadata.

Agents are root abstractions. Event processing is exposed through `EventOperatorCapability` bindings. Human review is represented as events and review capabilities. Tool calls are represented as governed action capabilities.

## End-to-End Flow

1. Register schemas, operators, agents, model policies, tool policies, and lifecycle policies.
2. Author PatternSpec with standard operators and optional `capabilityRef` bindings.
3. Compile PatternSpec into a deterministic operator DAG.
4. Bind EventCloud sources, sinks, checkpoints, replay policy, and tenant isolation.
5. Execute the DAG through the unified EventOperator runtime.
6. Emit typed operator results, PatternMatch events, uncertainty evidence, and lifecycle events.
7. Feed matches, misses, outcomes, expert feedback, and agent review into learning.
8. Emit `pattern.suggested` for candidate patterns rather than mutating active rules directly.
9. Promote only through governance policy, review, and auditable lifecycle events.

## PatternSpec Example with Agent Capability

```yaml
pattern:
  operator: SEQ
  within: PT30M
  operands:
    - event: deploy.started
    - operator: TIMES
      event: service.error_rate_elevated
      min: 3
      within: PT10M
    - operator: AGENT_PREDICATE
      capabilityRef: agents/sre-risk-assessor@1.0.0
      outputSchema: RiskDecision
      condition: "$output.riskScore >= 0.85"
```

## Agentic Execution and Learning

Agentic execution is not an external callback model. It is implemented through `EventOperatorCapability` bindings in PatternSpec and PipelineSpec.

Agent outputs are typed operator results or typed events. Pattern learning may synthesize candidate PatternSpecs, but those outputs emit `pattern.suggested` and enter lifecycle governance. Learning does not directly mutate active rules unless an explicit policy allows auto-promotion.

## Action Governance

Action execution is policy-gated:

- Inference-only operators may run in replay and shadow modes.
- Read-only tool use requires tool policy and audit events.
- Proposed actions emit recommendation or action-request events.
- Side-effecting actions require approval policy, idempotency key, audit event, and rollback or compensation metadata.
