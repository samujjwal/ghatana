package com.ghatana.datacloud.entity.observability;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable value object representing a distributed tracing span.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates OpenTelemetry span data including trace ID, span ID, parent span ID,
 * operation name, start/end times, status, and attributes for distributed tracing
 * and observability.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Span span = Span.builder()
 *     .traceId("550e8400-e29b-41d4-a716-446655440000")
 *     .spanId("f47ac10b-58cc-4372-a567-0e02b2c3d479")
 *     .operationName("collection.create")
 *     .startTime(Instant.now())
 *     .attribute("tenant.id", "tenant-123")
 *     .attribute("collection.type", "documents")
 *     .build();
 *
 * span = span.withEndTime(Instant.now());
 * span = span.withStatus(SpanStatus.OK);
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable — safe for concurrent access.
 *
 * <p><b>Performance</b><br>
 * Builder amortizes cost of repeated immutable creation. Attributes map is
 * defensively copied to maintain immutability.
 *
 * @see SpanStatus
 * @see TraceContext
 * @doc.type record
 * @doc.purpose Distributed tracing span representation
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public final class Span {

  private final String traceId;
  private final String spanId;
  private final String parentSpanId;
  private final String operationName;
  private final Instant startTime;
  private final Instant endTime;
  private final SpanStatus status;
  private final Map<String, Object> attributes;
  private final String tenantId;

  private Span(Builder builder) {
    this.traceId = Objects.requireNonNull(builder.traceId, "traceId required");
    this.spanId = Objects.requireNonNull(builder.spanId, "spanId required");
    this.parentSpanId = builder.parentSpanId;
    this.operationName =
        Objects.requireNonNull(builder.operationName, "operationName required");
    this.startTime = Objects.requireNonNull(builder.startTime, "startTime required");
    this.endTime = builder.endTime;
    this.status = builder.status != null ? builder.status : SpanStatus.UNSET;
    this.attributes =
        Collections.unmodifiableMap(new HashMap<>(builder.attributes));
    this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId required");
    validate();
  }

  private void validate() {
    if (traceId.isBlank()) {
      throw new IllegalArgumentException("traceId cannot be blank");
    }
    if (spanId.isBlank()) {
      throw new IllegalArgumentException("spanId cannot be blank");
    }
    if (operationName.isBlank()) {
      throw new IllegalArgumentException("operationName cannot be blank");
    }
    if (operationName.length() > 500) {
      throw new IllegalArgumentException(
          "operationName exceeds max length 500: " + operationName.length());
    }
    if (tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId cannot be blank");
    }
    if (endTime != null && endTime.isBefore(startTime)) {
      throw new IllegalArgumentException("endTime cannot be before startTime");
    }
  }

  /** Returns the trace ID this span belongs to. */
  public String getTraceId() {
    return traceId;
  }

  /** Returns the unique ID for this span. */
  public String getSpanId() {
    return spanId;
  }

  /** Returns the parent span ID, or null if this is root span. */
  public String getParentSpanId() {
    return parentSpanId;
  }

  /** Returns the operation/function name this span represents. */
  public String getOperationName() {
    return operationName;
  }

  /** Returns the instant this span started. */
  public Instant getStartTime() {
    return startTime;
  }

  /** Returns the instant this span ended, or null if still running. */
  public Instant getEndTime() {
    return endTime;
  }

  /** Returns the status of this span. */
  public SpanStatus getStatus() {
    return status;
  }

  /** Returns immutable map of span attributes. */
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  /** Returns the tenant ID associated with this span. */
  public String getTenantId() {
    return tenantId;
  }

  /** Returns duration in milliseconds, or -1 if span still running. */
  public long getDurationMillis() {
    if (endTime == null) {
      return -1;
    }
    return endTime.toEpochMilli() - startTime.toEpochMilli();
  }

  /** Returns true if span is still in-flight (no end time). */
  public boolean isActive() {
    return endTime == null;
  }

  /** Returns a new Span with the end time set. */
  public Span withEndTime(Instant endTime) {
    return new Builder()
        .traceId(this.traceId)
        .spanId(this.spanId)
        .parentSpanId(this.parentSpanId)
        .operationName(this.operationName)
        .startTime(this.startTime)
        .endTime(endTime)
        .status(this.status)
        .tenantId(this.tenantId)
        .attributes(this.attributes)
        .build();
  }

  /** Returns a new Span with the status set. */
  public Span withStatus(SpanStatus status) {
    return new Builder()
        .traceId(this.traceId)
        .spanId(this.spanId)
        .parentSpanId(this.parentSpanId)
        .operationName(this.operationName)
        .startTime(this.startTime)
        .endTime(this.endTime)
        .status(status)
        .tenantId(this.tenantId)
        .attributes(this.attributes)
        .build();
  }

  /** Returns a new Span with an additional attribute. */
  public Span withAttribute(String key, Object value) {
    Map<String, Object> newAttributes = new HashMap<>(this.attributes);
    newAttributes.put(key, value);
    return new Builder()
        .traceId(this.traceId)
        .spanId(this.spanId)
        .parentSpanId(this.parentSpanId)
        .operationName(this.operationName)
        .startTime(this.startTime)
        .endTime(this.endTime)
        .status(this.status)
        .tenantId(this.tenantId)
        .attributes(newAttributes)
        .build();
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for Span with fluent API.
   */
  public static final class Builder {
    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String operationName;
    private Instant startTime;
    private Instant endTime;
    private SpanStatus status;
    private final Map<String, Object> attributes = new HashMap<>();
    private String tenantId;

    public Builder traceId(String traceId) {
      this.traceId = traceId;
      return this;
    }

    public Builder spanId(String spanId) {
      this.spanId = spanId;
      return this;
    }

    public Builder parentSpanId(String parentSpanId) {
      this.parentSpanId = parentSpanId;
      return this;
    }

    public Builder operationName(String operationName) {
      this.operationName = operationName;
      return this;
    }

    public Builder startTime(Instant startTime) {
      this.startTime = startTime;
      return this;
    }

    public Builder endTime(Instant endTime) {
      this.endTime = endTime;
      return this;
    }

    public Builder status(SpanStatus status) {
      this.status = status;
      return this;
    }

    public Builder attribute(String key, Object value) {
      this.attributes.put(key, value);
      return this;
    }

    public Builder attributes(Map<String, Object> attributes) {
      this.attributes.putAll(attributes);
      return this;
    }

    public Builder tenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Span build() {
      return new Span(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Span)) return false;
    Span span = (Span) o;
    return Objects.equals(traceId, span.traceId)
        && Objects.equals(spanId, span.spanId)
        && Objects.equals(tenantId, span.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(traceId, spanId, tenantId);
  }

  @Override
  public String toString() {
    return "Span{"
        + "traceId='"
        + traceId
        + '\''
        + ", spanId='"
        + spanId
        + '\''
        + ", operationName='"
        + operationName
        + '\''
        + ", status="
        + status
        + ", durationMillis="
        + getDurationMillis()
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
