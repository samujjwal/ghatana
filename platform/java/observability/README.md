# platform/java/observability

Platform observability module. Provides metrics, distributed tracing, structured logging, health checks, and correlation context for all Ghatana services.

---

## Overview

Built on **Micrometer** (metrics) and **OpenTelemetry** (tracing), with ClickHouse as the trace storage backend. All services that depend on this module get:

- Prometheus-compatible metrics via `PrometheusMetricsExporter`
- Distributed tracing with span correlation (`CorrelationContext`, `TraceInfo`)
- Structured event-loop stall detection (`EbpfEventloopStallTracer`)
- Redis health probe integration

---

## Key Exports

| Class | Package | Purpose |
|-------|---------|---------|
| `PrometheusMetricsExporter` | `metrics` | Prometheus `/metrics` endpoint integration |
| `AgentExecutionMetrics` | `metrics` | Metrics for agent turn execution |
| `QueueMetrics` | `metrics` | Queue depth, lag, throughput metrics |
| `WALMetrics` | `metrics` | Write-ahead log metrics |
| `CorrelationContext` | root | Thread-local correlation ID propagation |
| `TraceInfo` | `trace` | Distributed trace info (trace ID, span ID) |
| `SpanData` / `SpanDataBuilder` | `trace` | Span creation and lifecycle |
| `TraceStorage` | `trace` | SPI for trace backends |
| `ClickHouseTraceStorage` | `clickhouse` | ClickHouse trace storage implementation |
| `BlockingExecutors` | `util` | Preconfigured executors for blocking I/O |
| `PromisesCompat` | `util` | ActiveJ `Promise.ofBlocking()` compatibility bridge |

---

## Feature Status

### ✅ Production-Ready

| Feature | Support |
|---------|---------|
| Prometheus metrics (`/metrics`) | Stable |
| OpenTelemetry trace export (OTLP) | Stable |
| ClickHouse trace storage | Stable |
| Correlation context propagation | Stable |
| Event-loop stall detection | Stable (best-effort via eBPF) |
| Redis health checks | Stable |

### ⚠️ Disabled / Experimental — DO NOT USE IN PRODUCTION

The following features are **disabled** because they depend on **ActiveJ DI** (`activej-inject` launcher integration), which is not API-stable in ActiveJ `6.0-rc2`:

| Feature | Status | Reason | Issue |
|---------|--------|--------|-------|
| `ObservabilityLauncher` (ActiveJ Launcher-based auto-bootstrap) | **DISABLED** | Requires `activej-inject` DI context that conflicts with our manual wiring | Re-enable when ActiveJ DI stabilizes |
| Automatic HTTP metrics endpoint registration via `Launcher` | **DISABLED** | Same root cause | Re-enable with `ObservabilityLauncher` |
| `@Monitored` AOP aspect (AspectJ-based method metrics) | **PROTOTYPE** | Functional but not covered by tests; may produce inaccurate histograms | Add tests before enabling |

> **Note to developers:** If you see references to `ObservabilityLauncher` or launcher-based wiring in older code, do **not** attempt to activate them. They will fail at startup. Use the direct constructor-injection approach described below.

---

## Usage

### Inject metrics via constructor

```java
// In your service class:
private final PrometheusMetricsExporter metricsExporter;
private final QueueMetrics queueMetrics;

public MyService(PrometheusMetricsExporter metricsExporter, QueueMetrics queueMetrics) {
    this.metricsExporter = metricsExporter;
    this.queueMetrics = queueMetrics;
}
```

### Record a span

```java
try (var span = traceInfo.startSpan("processEvent")) {
    // ... do work ...
    span.tag("tenantId", tenantId);
}
```

### Propagate correlation context

```java
CorrelationContext.set(correlationId);
try {
    // ... correlated work ...
} finally {
    CorrelationContext.clear();
}
```

---

## Dependencies

```
platform/java/core
platform/java/runtime
platform/java/config
platform/java/http
ActiveJ (promise, http, inject)
Micrometer (core, Prometheus registry)
OpenTelemetry (API, SDK, OTLP exporter)
Jedis (Redis health checks)
ClickHouse JDBC (trace storage)
AspectJ RT
```

---

## Upgrade Path for Disabled Features

When ActiveJ DI reaches a stable API (expected post `6.0-rc2`):

1. Re-enable `ObservabilityLauncher` and add integration tests
2. Wire `@Monitored` AOP aspect with full test coverage
3. Update this README to move those features to Production-Ready

Track in: [MED-004 SHARED_MODULES_AUDIT_REPORT.md](../../../../SHARED_MODULES_AUDIT_REPORT.md)
