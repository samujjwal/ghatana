# AEP Observability

**Status:** Target standard  
**Owner:** AEP maintainers

## Required Metrics

```text
aep.events.ingested.total
aep.operator.processed.total
aep.operator.latency
aep.pattern.matches.total
aep.pattern.confidence
aep.pattern.shadow.false_positive
aep.agent.invocations.total
aep.agent.tool_calls.total
aep.agent.replay.mode
aep.eventcloud.lag
aep.eventcloud.dlq.total
```

## Requirements

- Every operator emits metrics.
- Agent calls are traceable.
- Pattern match has correlation ID.
- Replay has trace context.
- Agent spans include model version, prompt hash, retrieval snapshot ID, tool-call IDs, guardrail outcome, and replay mode.
