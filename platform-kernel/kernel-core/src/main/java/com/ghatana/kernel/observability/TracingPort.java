package com.ghatana.kernel.observability;

import java.util.Map;

/**
 * Port for distributed tracing in the kernel.
 *
 * <p>Provides a generic interface for distributed tracing that can be implemented
 * by various backends (OpenTelemetry, Jaeger, Zipkin, etc.).</p>
 *
 * @doc.type interface
 * @doc.purpose Kernel port for distributed tracing
 * @doc.layer core
 * @doc.pattern Port
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface TracingPort {

    /**
     * Starts a new span with the given name.
     *
     * @param spanName the span name
     * @return span instance
     */
    Span startSpan(String spanName);

    /**
     * Starts a new span with the given name and parent span.
     *
     * @param spanName the span name
     * @param parent the parent span
     * @return span instance
     */
    Span startSpan(String spanName, Span parent);

    /**
     * Gets the current active span.
     *
     * @return current span or null if no active span
     */
    Span getCurrentSpan();

    /**
     * Adds a key-value attribute to the current span.
     *
     * @param key the attribute key
     * @param value the attribute value
     */
    void addAttribute(String key, String value);

    /**
     * Adds a key-value attribute to the current span.
     *
     * @param key the attribute key
     * @param value the attribute value
     */
    void addAttribute(String key, long value);

    /**
     * Adds a key-value attribute to the current span.
     *
     * @param key the attribute key
     * @param value the attribute value
     */
    void addAttribute(String key, double value);

    /**
     * Adds a key-value attribute to the current span.
     *
     * @param key the attribute key
     * @param value the attribute value
     */
    void addAttribute(String key, boolean value);

    /**
     * Adds multiple attributes to the current span.
     *
     * @param attributes the attributes to add
     */
    void addAttributes(Map<String, Object> attributes);

    /**
     * Records an event on the current span.
     *
     * @param eventName the event name
     * @param attributes optional event attributes
     */
    void addEvent(String eventName, Map<String, Object> attributes);

    /**
     * Records an exception on the current span.
     *
     * @param throwable the exception
     */
    void recordException(Throwable throwable);

    /**
     * Sets the span status.
     *
     * @param status the span status
     */
    void setStatus(SpanStatus status);

    /**
     * Sets the span status with a description.
     *
     * @param status the span status
     * @param description the status description
     */
    void setStatus(SpanStatus status, String description);

    /**
     * Represents a distributed trace span.
     */
    interface Span {
        /**
         * Gets the span context (trace ID, span ID).
         *
         * @return span context
         */
        SpanContext getContext();

        /**
         * Ends the span.
         */
        void end();

        /**
         * Ends the span with a specific end time.
         *
         * @param endTimestamp the end timestamp in milliseconds
         */
        void end(long endTimestamp);

        /**
         * Checks if the span is recording.
         *
         * @return true if recording
         */
        boolean isRecording();

        /**
         * Makes the span the current active span.
         */
        void makeCurrent();
    }

    /**
     * Span context containing trace and span IDs.
     */
    record SpanContext(String traceId, String spanId) {
        public SpanContext {
            if (traceId == null || traceId.isBlank()) {
                throw new IllegalArgumentException("traceId must not be blank");
            }
            if (spanId == null || spanId.isBlank()) {
                throw new IllegalArgumentException("spanId must not be blank");
            }
        }
    }

    /**
     * Span status.
     */
    enum SpanStatus {
        UNSET,
        OK,
        ERROR
    }
}
