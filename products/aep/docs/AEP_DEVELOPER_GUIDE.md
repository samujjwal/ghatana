# AEP Developer Guide — API Reference and Best Practices

> **AEP-008 Documentation Series**  
> Last Updated: 2026-04-06  
> Version: v3 (Current)

---

## 1. Getting Started (QuickStart)

### 1.1 Dependencies

Add the AEP engine dependency to your Gradle build:

```kotlin
dependencies {
    implementation(project(":products:aep:aep-engine"))
    implementation(libs.activej.promise)
}
```

### 1.2 Creating an Engine Instance

```java
// Testing / local development
AepEngine engine = Aep.forTesting();

// Production (reads from environment/config)
AepEngine engine = Aep.forProduction(config);
```

### 1.3 Your First Pipeline — Fraud Detection QuickStart

```java
// Build a pre-configured fraud detection pipeline
PipelineSpec spec = AepQuickStartTemplates
    .fraudDetection("my-fraud-pipe", "tenant-alpha")
    .describedAs("Production fraud detection for payments")
    .build();

// Validate the spec before deployment
ValidationReport report = new PipelineSpecValidator().validate(spec);
if (!report.isValid()) {
    throw new IllegalStateException("Invalid pipeline: " + report.errors());
}
```

---

## 2. Core API Reference

### 2.1 AepEngine

The central event processing entry point.

| Method | Signature | Description |
|--------|-----------|-------------|
| `process` | `Promise<ProcessingResult> process(String tenantId, Event event)` | Process a single event |
| `registerPattern` | `Promise<Pattern> registerPattern(String tenantId, PatternDefinition def)` | Register a detection pattern |
| `subscribe` | `void subscribe(String tenantId, String patternId, Consumer<Detection> handler)` | Subscribe to pattern detections |
| `close` | `void close()` | Shut down the engine cleanly |

**ProcessingResult states:**

| Status | Meaning |
|--------|---------|
| `PROCESSED` | Event matched a pattern and was processed |
| `SKIPPED` | Event was deduplicated or filtered |
| `FAILED` | Processing error (check logs) |

### 2.2 PipelineSpecBuilder DSL

Fluent builder for declarative pipeline specifications.

```java
PipelineSpec spec = PipelineSpecBuilder.create("my-pipeline")
    .forTenant("tenant-1")
    .describedAs("My processing pipeline")
    .withConfiguration(cfg -> cfg
        .executionMode("STREAMING")
        .maxRetries(3)
        .timeoutMs(30_000L)
        .checkpointing(true))
    .addStage(PipelineStageSpecBuilder.create("ingest")
        .ofType("KAFKA_SOURCE")
        .withConfiguration(cfg -> cfg
            .parallelism(8)
            .timeoutMs(1_000L)
            .executionStrategy("AT_LEAST_ONCE")
            .faultTolerant(true))
        .build())
    .build();
```

### 2.3 OperatorComposer

Compose operators declaratively without hand-written chaining.

```java
OperatorComposer composer = new OperatorComposer();

// Sequential: first → second → third
UnifiedOperator pipeline = composer.sequential(filterOp, enrichOp, sinkOp);

// Parallel: all receive the same event; outputs merged
UnifiedOperator broadcast = composer.parallel(analyticsOp, auditOp, metricsOp);

// Conditional: route based on event content
UnifiedOperator router = composer.conditional(
    event -> "HIGH".equals(event.getPayload("priority")),
    priorityHandlerOp,
    standardHandlerOp);

// Fan-out: same as parallel with explicit semantic intent
UnifiedOperator fanOut = composer.fanOut(op1, op2, op3);
```

### 2.4 CachingOperator

Avoid redundant processing for repeated events:

```java
CachingOperator cache = CachingOperator.builder()
    .operator(expensiveDbLookup)
    .keyExtractor(event -> event.getPayload("userId"))
    .maxSize(10_000)
    .ttl(Duration.ofMinutes(15))
    .build();
```

**Cache hit rate target:** ≥ 90% for repeated events.

---

## 3. Developer Tooling

### 3.1 Pipeline Debug Console (AEP-009.2)

Use the debug console to trace events through pipeline stages during local development:

```java
PipelineDebugConsole console = PipelineDebugConsole.attachTo(spec);

// Instrument your processing loop
for (Event event : events) {
    console.recordStageEntry("ingest", event.getId());
    processEvent(event);
    console.recordStageExit("ingest", 1, elapsedMs);
}

// Review the captured trace
console.printReport();

// Query errors programmatically
long errorCount = console.errorCount();
```

### 3.2 Performance Profiler (AEP-009.3)

Identify bottlenecks in staging or CI:

```java
PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("fraud-pipe");

// Record stage latency
long start = System.nanoTime();
processStage(event);
profiler.recordSampleNs("anomaly-detector", start, System.nanoTime());

// Print summary
profiler.printReport();

// Assert latency SLOs in tests
assertThat(profiler.stats("anomaly-detector").avgMs()).isLessThan(10.0);
```

---

## 4. API Versioning

AEP follows a **fix-forward versioning policy**:

| Version | Status | Sunset Date |
|---------|--------|-------------|
| v3 | ✅ Current | — |
| v2 | ⚠️ Deprecated | 2026-09-01 |
| v1 | 🚫 Sunset | — |

### 4.1 Version Negotiation

```java
AepApiVersionRegistry registry = AepApiVersionRegistry.builder()
    .register(ApiVersion.current("v3"))
    .register(ApiVersion.deprecated("v2", LocalDate.of(2026, 9, 1)))
    .register(ApiVersion.sunset("v1"))
    .build();

// Negotiate client version
Optional<String> negotiated = registry.negotiate(clientVersion);
// If client requests "v1" or unknown → returns "v3"
// If client requests "v2" → returns "v2" with a deprecation warning log
```

---

## 5. Advanced Analytics and KPI Tracking

### 5.1 Business KPI Tracker (AEP-011.1)

```java
AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create();

// Record events
tracker.increment("tenant-alpha", "events.processed");
tracker.increment("tenant-alpha", "events.processed", 100L);
tracker.gauge("tenant-alpha", "pipeline.lag.ms", 55.3);

// Query
long processed = tracker.counter("tenant-alpha", "events.processed");
Optional<AepBusinessKpiTracker.GaugeSample> lag =
    tracker.gaugeValue("tenant-alpha", "pipeline.lag.ms");

// Report
tracker.printReport("tenant-alpha");
```

---

## 6. Quick-Start Templates

The following production-ready templates are available via `AepQuickStartTemplates`:

| Template | Use Case | Stages |
|----------|----------|--------|
| `fraudDetection` | Real-time payment fraud scoring | 4 (KAFKA_SOURCE → ENRICHMENT → ML_SCORING → SINK) |
| `clickstreamAnalytics` | Session-level user analytics | 4 (KAFKA_SOURCE → ENRICHMENT → WINDOWED_AGGREGATION → SINK) |
| `iotTelemetry` | IoT device telemetry + alerting | 4 (MQTT_SOURCE → FILTER → ENRICHMENT → SINK) |
| `auditLogPipeline` | Compliance audit log archiving | 4 (KAFKA_SOURCE → FILTER → ENRICHMENT → SINK) |
| `multiTenantRouter` | Route events to per-tenant sinks | 4 (KAFKA_SOURCE → ENRICHMENT → FILTER → SINK) |

---

## 7. Best Practices for Production Deployment

### 7.1 Never block the event loop

```java
// ❌ Wrong — blocks the ActiveJ event loop
result = blockingService.query(request).get();

// ✅ Correct — use async bridge
result = runPromise(() ->
    Promise.ofBlocking(executor, () -> blockingService.query(request)));
```

### 7.2 Always enable checkpointing in production

```java
.withConfiguration(cfg -> cfg
    .checkpointing(true)        // required for exactly-once semantics
    .maxRetries(3)              // retry transient failures
    .timeoutMs(30_000L))        // always set a timeout
```

### 7.3 Cap concurrency via parallelism settings

```java
.addStage(PipelineStageSpecBuilder.create("ml-scoring")
    .withConfiguration(cfg -> cfg
        .parallelism(4)          // limit ML inference concurrency
        .faultTolerant(true)))   // circuit break on persistent failure
```

---

## 8. Troubleshooting Guide

### Problem: Events are silently dropped

1. Check idempotency keys — duplicate keys are deduplicated by design.
2. Verify the pattern threshold is correctly configured.
3. Use `PipelineDebugConsole` to trace entry/exit per stage.

### Problem: Pipeline is slow under load

1. Enable `PipelinePerformanceProfiler` and check p95 latency per stage.
2. Add `CachingOperator` on expensive lookup stages.
3. Increase `parallelism` on CPU-bound stages.
4. Review the Grafana `aep-event-flow` dashboard for queue depth.

### Problem: Checkpoint restore fails after restart

1. Verify `V011__add_performance_indexes.sql` has been applied.
2. Check `aep_event_checkpoints` for expired entries.
3. Review `EXACTLY_ONCE` execution strategy configuration.

---

## 9. Monitoring Integration

AEP exposes the following Prometheus metrics:

| Metric | Type | Labels |
|--------|------|--------|
| `aep_events_processed_total` | Counter | `tenant_id`, `status` |
| `aep_event_latency_ms` | Histogram | `tenant_id`, `stage` |
| `aep_pattern_matches_total` | Counter | `tenant_id`, `pattern_id` |
| `aep_checkpoint_operations_total` | Counter | `operation`, `status` |
| `health.checks.total` | Counter | `check_name`, `status` |

Use the `aep-event-flow` Grafana dashboard for real-time visibility.

---

*Document generated for AEP-008.1/008.2/008.3/008.4 — API docs, tutorials, best practices, and troubleshooting.*

