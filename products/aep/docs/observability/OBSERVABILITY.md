# AEP Observability

**Status:** Partially implemented standard
**Owner:** AEP maintainers

## Current Implementation

The co-located AEP contract module includes `AepOperatorMetrics`, a non-throwing facade over the platform `MetricsCollector`. It centralizes canonical metric names and tags for event ingestion, operator processing, pattern matches, agent calls, replay mode, EventCloud lag, and EventCloud DLQ counters. Full runtime adoption is still pending.

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
