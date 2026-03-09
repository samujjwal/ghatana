# ADR-007: Micrometer + OpenTelemetry Dual Observability Stack

**Status:** Accepted  
**Date:** 2026-01-30  
**Decision Makers:** Platform Team  
**Phase:** 4 — Production Hardening  

## Context

Production systems require three pillars of observability: metrics, tracing, and structured logging. The platform must support standard observability backends (Prometheus, Grafana, Jaeger) without coupling business logic to specific vendors.

## Decision

Adopt a **dual-stack observability model**:

| Pillar | Library | Version | Export |
|--------|---------|---------|--------|
| **Metrics** | Micrometer | 1.12.4 | Prometheus + OTLP |
| **Tracing** | OpenTelemetry | 1.31.0 | OTLP (Jaeger/Zipkin compatible) |
| **Logging** | SLF4J + Log4j2 | 2.x | Structured JSON via MDC |

**Architecture:**

```
Business Code → MetricsCollector (interface) → SimpleMetricsCollector → MeterRegistry → Prometheus/OTLP
Business Code → TracingProvider (interface) → OpenTelemetryTracingProvider → OTLP Exporter
Business Code → SLF4J Logger → TraceIdMdcFilter → Log4j2 → Structured JSON
```

**Domain-specific metrics facades:**
- `PipelineMetrics` — 14 metrics (execution counts/duration/errors, stage metrics, checkpoint metrics)
- `AgentMetrics` — 13 metrics (processing, decisions, confidence, lifecycle, adaptive/rule-based)
- `DataCloudMetrics` — 20+ metrics (operations, entity/event/search metrics, plugin health, RBAC, rate limiting)

**Key interface — `MetricsCollector`:**
- `increment(name, value, tags)` — counter
- `recordTimer(name, durationMs, tags)` — histogram/timer
- `recordGauge(name, value)` — gauge
- `recordConfidenceScore(name, score)` — distribution summary
- `recordError(name, exception, tags)` — error counter with exception classification
- `getMeterRegistry()` — escape hatch for direct Micrometer access

**Implementations:**
- `SimpleMetricsCollector` — production (wraps real MeterRegistry)
- `NoopMetricsCollector` — testing (zero overhead)

## Rationale

- **Micrometer** is the de-facto Java metrics standard, with native Prometheus and OTLP support
- **OpenTelemetry** is the emerging standard for distributed tracing
- **`MetricsCollector` interface** decouples domain logic from Micrometer API, enabling testing with NoopMetricsCollector
- **Domain facades** (PipelineMetrics, AgentMetrics) prevent metric name sprawl and enforce naming conventions
- All metric names use `snake_case` dot-separated hierarchy: `pipeline.execution.duration`, `agent.process.count`
- Tag keys use `snake_case`: `tenant_id`, `agent_type`, `pipeline_id`

## Consequences

- Two metric systems coexist: `DataCloudMetrics` uses raw `MeterRegistry` directly; newer facades use `MetricsCollector` — eventual migration needed
- `MetricsCollector` does not expose all Micrometer features (e.g., `DistributionStatisticConfig`) — `getMeterRegistry()` is the escape hatch
- Tracing propagation across async boundaries (ActiveJ Promise chains) requires manual context transfer
- 85 observability tests verify metric recording, naming conventions, and multi-tenant isolation

## Alternatives Considered

1. **OpenTelemetry only (metrics + tracing)** — rejected; Micrometer has richer Java ecosystem integration
2. **Custom metrics library** — rejected; NIH; Micrometer covers all use cases
3. **Dropwizard Metrics** — rejected; Micrometer is the successor with better dimensional model
