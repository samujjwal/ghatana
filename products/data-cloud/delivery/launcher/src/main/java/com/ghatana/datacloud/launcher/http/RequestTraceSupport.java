package com.ghatana.datacloud.launcher.http;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Request-scoped trace header support for Data Cloud HTTP responses.
 *
 * <p>I3: Ensures every response includes request/correlation ID, async job creation
 * stores request/correlation ID, and SSE/WebSocket messages include correlation or event IDs.
 *
 * @doc.type class
 * @doc.purpose Propagates request and W3C trace headers through HTTP response builders
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class RequestTraceSupport {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final String PARENT_SPAN_ID_HEADER = "X-Parent-Span-Id";
    private static final String EVENT_ID_HEADER = "X-Event-ID";
    private static final ThreadLocal<TraceHeaders> CURRENT = new ThreadLocal<>();

    private RequestTraceSupport() {
    }

    /**
     * I3: Set current trace context with separate correlation ID.
     */
    public static void setCurrent(TraceHeaders traceHeaders) {
        CURRENT.set(traceHeaders);
    }

    /**
     * I3: Set current trace context with auto-generated correlation ID if not provided.
     */
    public static void setCurrent(String requestId, String traceId, String spanId,
                                 @Nullable String correlationId,
                                 @Nullable String parentSpanId, boolean sampled) {
        String effectiveCorrelationId = correlationId != null ? correlationId : requestId;
        CURRENT.set(new TraceHeaders(requestId, effectiveCorrelationId, traceId, spanId, parentSpanId, sampled));
    }

    public static void clearCurrent() {
        CURRENT.remove();
    }

    /**
     * I3: Get current trace context for async job creation.
     */
    @Nullable
    public static TraceHeaders getCurrent() {
        return CURRENT.get();
    }

    /**
     * I3: Get correlation ID for async job storage.
     */
    @Nullable
    public static String getCorrelationId() {
        TraceHeaders headers = CURRENT.get();
        return headers != null ? headers.correlationId() : null;
    }

    /**
     * I3: Get request ID for logging.
     */
    @Nullable
    public static String getRequestId() {
        TraceHeaders headers = CURRENT.get();
        return headers != null ? headers.requestId() : null;
    }

    /**
     * I3: Apply trace headers to HTTP response builder.
     */
    public static HttpResponse.Builder applyTo(HttpResponse.Builder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("builder must not be null");
        }
        TraceHeaders traceHeaders = CURRENT.get();
        if (traceHeaders == null) {
            return builder;
        }
        builder.withHeader(HttpHeaders.of(REQUEST_ID_HEADER), traceHeaders.requestId());
        builder.withHeader(HttpHeaders.of(CORRELATION_ID_HEADER), traceHeaders.correlationId());
        builder.withHeader(HttpHeaders.of(TRACEPARENT_HEADER), traceHeaders.traceParent());
        String parentSpanId = traceHeaders.parentSpanId();
        if (parentSpanId != null && !parentSpanId.isBlank()) {
            builder.withHeader(HttpHeaders.of(PARENT_SPAN_ID_HEADER), parentSpanId);
        }
        return builder;
    }

    /**
     * I3: Create SSE event headers with correlation and event ID.
     */
    public static Map<String, String> createSseHeaders(@Nullable String eventId) {
        TraceHeaders traceHeaders = CURRENT.get();
        Map<String, String> headers = new java.util.HashMap<>();
        if (traceHeaders != null) {
            headers.put(REQUEST_ID_HEADER, traceHeaders.requestId());
            headers.put(CORRELATION_ID_HEADER, traceHeaders.correlationId());
        }
        if (eventId != null && !eventId.isBlank()) {
            headers.put(EVENT_ID_HEADER, eventId);
        }
        return headers;
    }

    /**
     * I3: Create WebSocket message metadata with correlation ID.
     */
    public static WebSocketTraceMetadata createWebSocketMetadata(@Nullable String messageId) {
        TraceHeaders traceHeaders = CURRENT.get();
        String correlationId = traceHeaders != null ? traceHeaders.correlationId() : UUID.randomUUID().toString();
        String effectiveMessageId = messageId != null ? messageId : UUID.randomUUID().toString();
        return new WebSocketTraceMetadata(correlationId, effectiveMessageId);
    }

    /**
     * I3: Trace headers record with separate correlation ID.
     */
    public record TraceHeaders(String requestId,
                               String correlationId,
                               String traceId,
                               String spanId,
                               @Nullable String parentSpanId,
                               boolean sampled) {
        private String traceParent() {
            return "00-" + traceId + "-" + spanId + "-" + (sampled ? "01" : "00");
        }
    }

    /**
     * I3: WebSocket trace metadata for message correlation.
     */
    public record WebSocketTraceMetadata(String correlationId, String messageId) {
    }
}