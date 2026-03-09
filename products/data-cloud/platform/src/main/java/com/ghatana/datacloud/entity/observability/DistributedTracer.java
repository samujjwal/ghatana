package com.ghatana.datacloud.entity.observability;

import io.activej.promise.Promise;

/**
 * Port interface for distributed tracing operations.
 *
 * <p><b>Purpose</b><br>
 * Abstracts OpenTelemetry/Jaeger tracing implementation from domain logic.
 * Enables span creation, propagation, and recording with Promise-based async.
 *
 * <p><b>Responsibilities</b><br>
 * - Create new spans for operations
 * - Record span attributes (tags)
 * - Set span status and end times
 * - Propagate trace context across async boundaries
 * - Export spans to tracing backend
 * - Handle sampling decisions
 *
 * <p><b>Implementation Notes</b><br>
 * All methods return Promise for integration with ActiveJ async model.
 * Implementations must be thread-safe for concurrent span creation.
 * Multi-tenant spans must always include tenant ID.
 *
 * @see Span
 * @see TraceContext
 * @doc.type interface
 * @doc.purpose Port interface for distributed tracing
 * @doc.layer domain
 * @doc.pattern Port
 */
public interface DistributedTracer {

  /**
   * Creates a new span as a child of current trace context.
   *
   * @param operationName name of the operation (max 500 chars)
   * @param tenantId tenant ID for multi-tenant isolation
   * @return Promise of newly created Span
   * @throws IllegalArgumentException if operationName blank or tenantId blank
   */
  Promise<Span> startSpan(String operationName, String tenantId);

  /**
   * Creates a new span as a child of specified parent span.
   *
   * @param parentSpan the parent span (not null)
   * @param operationName name of the operation
   * @return Promise of newly created child Span
   */
  Promise<Span> startChildSpan(Span parentSpan, String operationName);

  /**
   * Ends (records) a span with current time and specified status.
   *
   * @param span the span to end (not null)
   * @param status the final status of the span
   * @return Promise of void on completion
   */
  Promise<Void> endSpan(Span span, SpanStatus status);

  /**
   * Records an attribute on a span.
   *
   * @param span the span to update
   * @param key attribute key
   * @param value attribute value
   * @return Promise of updated Span
   */
  Promise<Span> recordAttribute(Span span, String key, Object value);

  /**
   * Records an event (note) on a span.
   *
   * @param span the span to update
   * @param eventName the event name
   * @param attributes optional attributes map (may be null)
   * @return Promise of void on completion
   */
  Promise<Void> recordEvent(Span span, String eventName,
      java.util.Map<String, Object> attributes);

  /**
   * Extracts trace context from a carrier (e.g., HTTP headers).
   *
   * @param carrier the carrier map (e.g., HTTP headers)
   * @param tenantId tenant ID to associate with extracted context
   * @return Promise of TraceContext, or empty context if not present
   */
  Promise<TraceContext> extractContext(java.util.Map<String, String> carrier,
      String tenantId);

  /**
   * Injects trace context into a carrier (e.g., HTTP headers).
   *
   * @param context the trace context to inject
   * @param carrier the target carrier map (modified in place)
   * @return Promise of void on completion
   */
  Promise<Void> injectContext(TraceContext context,
      java.util.Map<String, String> carrier);

  /**
   * Returns current active trace context, or empty context if none.
   *
   * @param tenantId tenant ID for the context
   * @return Promise of current TraceContext
   */
  Promise<TraceContext> getCurrentContext(String tenantId);

  /**
   * Returns true if tracer is healthy and ready to record spans.
   *
   * @return Promise of health status
   */
  Promise<Boolean> isHealthy();

  /**
   * Flushes any pending spans to the tracing backend.
   *
   * @return Promise of void on completion (may take time depending on backend)
   */
  Promise<Void> flush();

  /**
   * Result of batch span export operation.
   */
  class ExportResult {
    private final int successCount;
    private final int errorCount;
    private final java.util.List<String> errors;

    /**
     * Creates export result.
     *
     * @param successCount number of successfully exported spans
     * @param errorCount number of spans that failed export
     * @param errors list of error messages
     */
    public ExportResult(int successCount, int errorCount,
        java.util.List<String> errors) {
      this.successCount = successCount;
      this.errorCount = errorCount;
      this.errors = java.util.Collections.unmodifiableList(
          new java.util.ArrayList<>(errors));
    }

    public int getSuccessCount() {
      return successCount;
    }

    public int getErrorCount() {
      return errorCount;
    }

    public java.util.List<String> getErrors() {
      return errors;
    }

    @Override
    public String toString() {
      return "ExportResult{" + "successCount=" + successCount +
          ", errorCount=" + errorCount + ", errors=" + errors + '}';
    }
  }
}
