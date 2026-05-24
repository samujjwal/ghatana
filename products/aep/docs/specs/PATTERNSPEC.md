# PatternSpec

**Status:** Target specification  
**Owner:** AEP maintainers
**Schema:** `products/aep/contracts/schemas/pattern-spec.schema.json`
**Current structural validator:** `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/pattern/spec/PatternSpecValidator.java`

PatternSpec is the canonical declarative event pattern language for AEP.

It supports:

- Event references.
- `AND`, `OR`, `NOT`, `SEQ`.
- `WITHIN`, `TIMES`, `REPEAT`.
- `WINDOW`.
- `ABSENCE`.
- `FILTER`.
- `AGENT_PREDICATE`.
- `AGENT_ENRICH`.
- `AGENT_EXTRACT`.
- `AGENT_REVIEW`.
- `AGENT_ACTION`.
- `AGENT_REFLECTION`.
- Uncertainty policy.
- Time policy.
- Replay policy.
- Lifecycle policy.
- Output event definition.

## Required Top-Level Sections

```yaml
apiVersion:
kind:
metadata:
semantics:
pattern:
emit:
actions:
lifecycle:
governance:
observability:
```

## Grammar

```text
PatternExpr :=
    EventRef
  | AND(PatternExpr*)
  | OR(PatternExpr*)
  | SEQ(PatternExpr*)
  | NOT(PatternExpr)
  | WITHIN(PatternExpr, Duration)
  | TIMES(PatternExpr, Bounds)
  | WINDOW(PatternExpr, WindowSpec)
  | ABSENCE(EventRef, WindowSpec)
  | FILTER(PatternExpr, Predicate)
  | AGENT_PREDICATE(EventOperatorCapabilityRef)
  | AGENT_ENRICH(EventOperatorCapabilityRef)
  | AGENT_EXTRACT(EventOperatorCapabilityRef)
  | AGENT_PATTERN_SYNTHESIS(EventOperatorCapabilityRef)
  | AGENT_EXPLANATION(EventOperatorCapabilityRef)
  | AGENT_REVIEW(EventOperatorCapabilityRef)
  | AGENT_ACTION(EventOperatorCapabilityRef)
  | AGENT_REFLECTION(EventOperatorCapabilityRef)
```

## Production Requirements

- Agent capability roles are valid `PatternExpr` nodes when bound by `capabilityRef`.
- Output event schema is mandatory.
- Time and uncertainty semantics are mandatory for production patterns.
- Pattern lifecycle metadata is mandatory.
- Side-effecting operators must declare governance and approval policy.
- Pattern compiler must reject unknown operators.
- Pattern compiler must reject agent capability roles without `capabilityRef` and output schemas.

## Example

```yaml
apiVersion: aep.ghatana.io/v1
kind: PatternSpec
metadata:
  name: risky-deploy-detection
  tenantId: tenant-a
semantics:
  timeMode: event_time
  allowedLateness: PT2M
  uncertaintyPolicy:
    minPatternConfidence: 0.85
pattern:
  operator: SEQ
  within: PT30M
  operands:
    - event: deploy.started
    - operator: WINDOW
      window:
        type: sliding
        size: PT10M
      pattern:
        operator: TIMES
        event: service.error_rate_elevated
        min: 3
    - operator: AGENT_PREDICATE
      agentRef: agents/sre-risk-assessor@1.0.0
      inputSchema: DeployRiskContext
      outputSchema: RiskDecision
      replayPolicy:
        mode: recorded_output
      condition: "$output.riskScore >= 0.85"
emit:
  eventType: incident.risk_detected
  outputSchema: IncidentRiskDetected
lifecycle:
  state: SHADOW
governance:
  owner: sre-platform
  reviewPolicy: human_required_for_promotion
observability:
  metrics: true
  tracing: true
```

## Agent in AND Example

```yaml
pattern:
  operator: AND
  operands:
    - event: payment.failed
    - operator: AGENT_ENRICH
      agentRef: agents/fraud-context-enricher@1.0.0
      inputSchema: PaymentFailureContext
      outputSchema: FraudContext
    - operator: AGENT_PREDICATE
      agentRef: agents/fraud-risk-reviewer@1.0.0
      inputSchema: FraudContext
      outputSchema: FraudDecision
      condition: "$output.decision == 'REVIEW'"
```

## Learning Pipeline Example

```yaml
pipeline:
  operator: SEQ
  operands:
    - event: event_group.discovered
    - operator: AGENT_PATTERN_SYNTHESIS
      agentRef: agents/pattern-synthesizer@1.0.0
      inputSchema: DiscoveredEventGroup
      outputSchema: CandidatePatternSpec
    - operator: AGENT_REVIEW
      agentRef: agents/pattern-reviewer@1.0.0
      inputSchema: CandidatePatternSpec
      outputSchema: PatternReviewRecommendation
emit:
  eventType: pattern.suggested
  outputSchema: PatternSuggested
```
