# AEP Tracing Instrumentation Guide

**Version:** 1.0.0  
**Last Updated:** 2026-05-02  
**Maintainer:** AEP Platform Team

---

## Overview

This guide provides instructions for adding distributed tracing instrumentation to AEP components. Tracing enables observability of request flows across services, operators, and infrastructure components.

---

## Existing Tracing Infrastructure

### Request Trace Context

AEP uses `RequestTraceContext` to propagate trace information across components:

```java
private record RequestTraceContext(
    String correlationId,
    String traceId,
    String spanId,
    boolean sampled,
    String tracestate
)
```

### Tracing Interceptors

- **HTTP:** `RequestTraceSupport` - Adds tracing headers to HTTP responses
- **gRPC:** `TracingServerInterceptor` - Propagates trace context in gRPC calls

### Trace Headers

- `X-Correlation-Id`: Request correlation identifier
- `traceparent`: W3C trace context format
- `tracestate`: Vendor-specific trace state

---

## Tracing Best Practices

### 1. Span Naming

Use descriptive, hierarchical span names:

```java
// Good
Span.builder("aep.pipeline.execute")
Span.builder("aep.agent.process")
Span.builder("aep.learning.reflect")

// Avoid
Span.builder("execute")
Span.builder("process")
Span.builder("run")
```

### 2. Span Attributes

Add meaningful attributes to spans:

```java
span.attribute("tenant.id", tenantId);
span.attribute("pipeline.id", pipelineId);
span.attribute("pipeline.version", version);
span.attribute("operator.type", operatorType);
span.attribute("event.count", eventCount);
```

### 3. Span Events

Record important events during span execution:

```java
span.event("pipeline.started", Map.of("timestamp", Instant.now()));
span.event("checkpoint.created", Map.of("checkpoint.id", checkpointId));
span.event("operator.completed", Map.of("operator.id", operatorId));
```

### 4. Error Handling

Record errors in spans:

```java
try {
    // operation
} catch (Exception e) {
    span.recordException(e);
    span.status(Status.ERROR, e.getMessage());
    throw e;
}
```

### 5. Trace Propagation

Propagate trace context across service boundaries:

```java
// HTTP
request.addHeader("traceparent", traceContext.traceParent());
request.addHeader("X-Correlation-Id", traceContext.correlationId());

// gRPC
metadata.put(Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER), 
            traceContext.traceParent());
```

---

## Instrumentation Examples

### Pipeline Execution Tracing

```java
/**
 * Pipeline execution with tracing
 */
public Promise<PipelineRun> executePipeline(
    Pipeline pipeline,
    Map<String, Object> parameters
) {
    Span span = Span.builder("aep.pipeline.execute")
        .attribute("pipeline.id", pipeline.getId())
        .attribute("pipeline.name", pipeline.getName())
        .attribute("tenant.id", pipeline.getTenantId())
        .attribute("parameter.count", parameters.size())
        .start();
    
    span.event("pipeline.started", Map.of("timestamp", Instant.now()));
    
    return pipelineExecutor.execute(pipeline, parameters)
        .whenComplete((result, error) -> {
            if (error == null) {
                span.attribute("run.id", result.getRunId());
                span.attribute("run.status", result.getStatus());
                span.event("pipeline.completed", Map.of(
                    "duration.ms", result.getDurationMs()
                ));
                span.status(Status.OK);
            } else {
                span.recordException(error);
                span.status(Status.ERROR, error.getMessage());
            }
            span.end();
        });
}
```

### Agent Processing Tracing

```java
/**
 * Agent processing with tracing
 */
public Promise<AgentResult> processEvent(
    Agent agent,
    Event event,
    AgentContext context
) {
    Span span = Span.builder("aep.agent.process")
        .parent(context.getTraceContext())
        .attribute("agent.id", agent.getId())
        .attribute("agent.type", agent.getType())
        .attribute("event.type", event.getType())
        .attribute("tenant.id", context.getTenantId())
        .start();
    
    span.event("agent.started", Map.of("timestamp", Instant.now()));
    
    return agent.process(context, event)
        .whenComplete((result, error) -> {
            if (error == null) {
                span.attribute("result.success", result.isSuccess());
                span.attribute("result.output.count", 
                    result.getOutput() != null ? 1 : 0);
                span.event("agent.completed", Map.of(
                    "processing.time.ms", result.getProcessingTimeMs()
                ));
                span.status(Status.OK);
            } else {
                span.recordException(error);
                span.status(Status.ERROR, error.getMessage());
            }
            span.end();
        });
}
```

### Learning Reflection Tracing

```java
/**
 * Learning reflection with tracing
 */
public Promise<ReflectionResult> triggerReflection(
    String tenantId,
    ReflectionConfig config
) {
    Span span = Span.builder("aep.learning.reflect")
        .attribute("tenant.id", tenantId)
        .attribute("reflection.type", config.getType())
        .attribute("episode.count", config.getEpisodeCount())
        .start();
    
    span.event("reflection.started", Map.of("timestamp", Instant.now()));
    
    return learningEngine.reflect(tenantId, config)
        .whenComplete((result, error) -> {
            if (error == null) {
                span.attribute("policies.learned", result.getPolicyCount());
                span.attribute("episodes.processed", result.getEpisodeCount());
                span.attribute("reflection.duration.ms", result.getDurationMs());
                span.event("reflection.completed", Map.of(
                    "new.policies", result.getNewPolicyCount()
                ));
                span.status(Status.OK);
            } else {
                span.recordException(error);
                span.status(Status.ERROR, error.getMessage());
            }
            span.end();
        });
}
```

### Governance Operations Tracing

```java
/**
 * Kill switch activation with tracing
 */
public Promise<KillSwitchResult> activateKillSwitch(
    String tenantId,
    String reason,
    String incidentId
) {
    Span span = Span.builder("aep.governance.kill-switch.activate")
        .attribute("tenant.id", tenantId)
        .attribute("incident.id", incidentId)
        .attribute("reason", reason)
        .start();
    
    span.event("kill-switch.activation.started", Map.of(
        "timestamp", Instant.now()
    ));
    
    return governanceManager.activateKillSwitch(tenantId, reason, incidentId)
        .whenComplete((result, error) -> {
            if (error == null) {
                span.attribute("activated", result.isActivated());
                span.attribute("previous.state", result.getPreviousState());
                span.event("kill-switch.activated", Map.of(
                    "operator", result.getOperator()
                ));
                span.status(Status.OK);
            } else {
                span.recordException(error);
                span.status(Status.ERROR, error.getMessage());
            }
            span.end();
        });
}
```

---

## Critical Paths to Instrument

### High Priority

1. **Pipeline Execution Flow**
   - Pipeline start/stop
   - Operator execution
   - Checkpoint creation/restore
   - Error handling

2. **Agent Processing**
   - Agent initialization
   - Event processing
   - Tool invocation
   - Memory operations

3. **Learning Operations**
   - Reflection triggers
   - Policy learning
   - Episode processing
   - Model updates

4. **Governance Operations**
   - Kill switch activation/deactivation
   - Degradation mode changes
   - Compliance checks
   - Audit logging

### Medium Priority

5. **Data Ingestion**
   - HTTP webhook processing
   - Kafka message consumption
   - Event validation
   - Backpressure handling

6. **API Operations**
   - REST endpoint handling
   - gRPC service calls
   - SSE connections
   - Batch operations

7. **Database Operations**
   - Query execution
   - Transaction commits
   - Cache operations
   - Connection pool operations

### Low Priority

8. **Background Tasks**
   - Scheduled jobs
   - Cleanup operations
   - Health checks
   - Metrics collection

---

## Tracing Configuration

### Enable Tracing

```bash
# Command line
java -jar aep-server.jar --enable-tracing

# Environment variable
export AEP_ENABLE_TRACING=true
```

### Trace Sampling

```java
// Configure sampling rate
TraceConfig config = TraceConfig.builder()
    .sampler(Sampler.probability(0.1)) // 10% sampling
    .build();
```

### Export Configuration

```java
// Configure trace exporter (e.g., Jaeger, Zipkin, OTLP)
TraceExporter exporter = OtlpGrpcExporter.builder()
    .setEndpoint("http://jaeger:4317")
    .build();
```

---

## Tracing Metrics

### Key Metrics to Track

- **Span Count:** Total number of spans created
- **Span Duration:** Distribution of span durations
- **Error Rate:** Percentage of spans with errors
- **Trace Completeness:** Percentage of complete traces
- **Sampling Rate:** Effective sampling rate

### Metrics Integration

```java
// Record tracing metrics
Metrics.counter("tracing.spans.created", 
    Tags.of("component", "pipeline")).increment();
Metrics.timer("tracing.span.duration",
    Tags.of("operation", "execute")).record(duration, TimeUnit.MILLISECONDS);
```

---

## Troubleshooting

### Missing Traces

**Symptoms:** Traces not appearing in tracing backend

**Solutions:**
1. Verify tracing is enabled: `--enable-tracing`
2. Check sampling rate
3. Verify exporter configuration
4. Check network connectivity to tracing backend
5. Review logs for exporter errors

### Incomplete Traces

**Symptoms:** Traces missing spans or incomplete

**Solutions:**
1. Verify trace context propagation
2. Check for async operations without context propagation
3. Review span lifecycle (ensure spans are ended)
4. Check for exceptions that prevent span completion

### High Overhead

**Symptoms:** Tracing causing performance degradation

**Solutions:**
1. Reduce sampling rate
2. Use conditional tracing for high-volume operations
3. Optimize span attribute collection
4. Use efficient exporters (e.g., batch exporters)

---

## Testing

### Unit Tests

```java
@Test
void shouldCreateSpanWithCorrectAttributes() {
    Span span = Span.builder("test.operation")
        .attribute("test.attr", "value")
        .start();
    
    assertThat(span.getAttribute("test.attr")).isEqualTo("value");
    span.end();
}
```

### Integration Tests

```java
@Test
void shouldPropagateTraceContextAcrossServices() {
    RequestTraceContext context = new RequestTraceContext(
        "corr-123", "trace-456", "span-789", true, null
    );
    
    // Make service call with context
    ServiceResult result = service.call(context);
    
    // Verify trace context in result
    assertThat(result.getTraceContext().getTraceId())
        .isEqualTo(context.getTraceId());
}
```

---

## Documentation Updates

When adding tracing to a component:

1. Update component documentation to mention tracing
2. Add tracing configuration to deployment docs
3. Update runbooks with tracing troubleshooting steps
4. Add tracing metrics to monitoring dashboards

---

## References

- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
- [OpenTelemetry Specification](https://opentelemetry.io/docs/reference/specification/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [AEP Observability Guide](../observability/README.md)

---

**Document Version:** 1.0.0  
**Last Updated:** 2026-05-02  
**Maintained By:** AEP Platform Team
