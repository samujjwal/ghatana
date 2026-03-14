package com.ghatana.appplatform.observability;

import java.util.UUID;

/**
 * Propagates OpenTelemetry-compatible W3C TraceContext headers ({@code traceparent},
 * {@code tracestate}) across HTTP requests and Kafka message headers.
 *
 * <p>Trace context is stored in {@link StructuredLogContext} so it flows into logs.
 *
 * <p>Reference: <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context spec</a>
 *
 * @doc.type class
 * @doc.purpose W3C TraceContext propagation for distributed tracing (STORY-K06-002)
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class TraceContextPropagator {

    private static final String VERSION = "00";
    private static final String FLAGS = "01"; // sampled

    public static final String HEADER_TRACEPARENT = "traceparent";
    public static final String HEADER_TRACESTATE  = "tracestate";

    public static final String CTX_TRACE_ID = "traceId";
    public static final String CTX_SPAN_ID  = "spanId";

    private TraceContextPropagator() {}

    /**
     * Start a new root trace. Generates a new traceId and spanId, stores them in
     * {@link StructuredLogContext}, and returns the {@code traceparent} header value.
     *
     * @return W3C traceparent header value, e.g. {@code 00-<traceId>-<spanId>-01}
     */
    public static String startTrace() {
        String traceId = generateTraceId();
        String spanId  = generateSpanId();
        StructuredLogContext.put(CTX_TRACE_ID, traceId);
        StructuredLogContext.put(CTX_SPAN_ID,  spanId);
        return toTraceparent(traceId, spanId);
    }

    /**
     * Continue an existing trace from an incoming {@code traceparent} header.
     * Generates a new spanId (child span) while preserving the traceId.
     *
     * @param incomingTraceparent incoming W3C traceparent header value
     * @return new traceparent with same traceId and fresh spanId
     */
    public static String continueTrace(String incomingTraceparent) {
        if (incomingTraceparent == null || incomingTraceparent.isBlank()) {
            return startTrace();
        }
        String[] parts = incomingTraceparent.split("-");
        if (parts.length < 4) return startTrace(); // malformed

        String traceId = parts[1];
        String spanId  = generateSpanId();
        StructuredLogContext.put(CTX_TRACE_ID, traceId);
        StructuredLogContext.put(CTX_SPAN_ID,  spanId);
        return toTraceparent(traceId, spanId);
    }

    /** Return the current trace ID from context, or null if no trace active. */
    public static String currentTraceId() {
        return StructuredLogContext.get(CTX_TRACE_ID);
    }

    /** Return the current span ID from context, or null if no trace active. */
    public static String currentSpanId() {
        return StructuredLogContext.get(CTX_SPAN_ID);
    }

    // ── Format Helpers ────────────────────────────────────────────────────────

    private static String toTraceparent(String traceId, String spanId) {
        return VERSION + "-" + traceId + "-" + spanId + "-" + FLAGS;
    }

    /** Generate a 32-char (128-bit) hex trace ID. */
    private static String generateTraceId() {
        UUID uuid = UUID.randomUUID();
        return String.format("%016x%016x",
            uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }

    /** Generate a 16-char (64-bit) hex span ID. */
    private static String generateSpanId() {
        return String.format("%016x", UUID.randomUUID().getMostSignificantBits());
    }
}
