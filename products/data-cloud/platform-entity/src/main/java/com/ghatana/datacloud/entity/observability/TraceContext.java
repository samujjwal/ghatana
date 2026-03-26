package com.ghatana.datacloud.entity.observability;

import java.util.Objects;

/**
 * Immutable value object representing OpenTelemetry trace context.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates trace ID, span ID, and trace flags for distributed tracing
 * context propagation across service boundaries. Used to maintain trace
 * continuity in async Promise chains.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * TraceContext context = TraceContext.builder()
 *     .traceId("550e8400-e29b-41d4-a716-446655440000")
 *     .spanId("f47ac10b-58cc-4372-a567-0e02b2c3d479")
 *     .traceFlags(TraceFlags.SAMPLED)
 *     .build();
 *
 * // Extract and propagate context
 * String traceId = context.getTraceId();
 * String spanId = context.getSpanId();
 * boolean sampled = context.isSampled();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable — safe for concurrent access and ThreadLocal storage.
 *
 * @see Span
 * @see TraceFlags
 * @doc.type record
 * @doc.purpose Distributed trace context for propagation
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public final class TraceContext {

  private final String traceId;
  private final String spanId;
  private final TraceFlags traceFlags;
  private final String tenantId;

  private TraceContext(Builder builder) {
    this.traceId = Objects.requireNonNull(builder.traceId, "traceId required");
    this.spanId = Objects.requireNonNull(builder.spanId, "spanId required");
    this.traceFlags =
        builder.traceFlags != null ? builder.traceFlags : TraceFlags.NOT_SAMPLED;
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
    if (tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId cannot be blank");
    }
  }

  /** Returns the trace ID. */
  public String getTraceId() {
    return traceId;
  }

  /** Returns the span ID. */
  public String getSpanId() {
    return spanId;
  }

  /** Returns the trace flags. */
  public TraceFlags getTraceFlags() {
    return traceFlags;
  }

  /** Returns true if this trace is sampled. */
  public boolean isSampled() {
    return traceFlags == TraceFlags.SAMPLED;
  }

  /** Returns the tenant ID for this trace context. */
  public String getTenantId() {
    return tenantId;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for TraceContext with fluent API.
   */
  public static final class Builder {
    private String traceId;
    private String spanId;
    private TraceFlags traceFlags;
    private String tenantId;

    public Builder traceId(String traceId) {
      this.traceId = traceId;
      return this;
    }

    public Builder spanId(String spanId) {
      this.spanId = spanId;
      return this;
    }

    public Builder traceFlags(TraceFlags traceFlags) {
      this.traceFlags = traceFlags;
      return this;
    }

    public Builder sampled(boolean sampled) {
      this.traceFlags =
          sampled ? TraceFlags.SAMPLED : TraceFlags.NOT_SAMPLED;
      return this;
    }

    public Builder tenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public TraceContext build() {
      return new TraceContext(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TraceContext)) return false;
    TraceContext context = (TraceContext) o;
    return Objects.equals(traceId, context.traceId)
        && Objects.equals(spanId, context.spanId)
        && Objects.equals(tenantId, context.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(traceId, spanId, tenantId);
  }

  @Override
  public String toString() {
    return "TraceContext{"
        + "traceId='"
        + traceId
        + '\''
        + ", spanId='"
        + spanId
        + '\''
        + ", sampled="
        + isSampled()
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
