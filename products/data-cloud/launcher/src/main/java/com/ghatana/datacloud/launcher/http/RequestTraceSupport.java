package com.ghatana.datacloud.launcher.http;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import org.jetbrains.annotations.Nullable;

/**
 * Request-scoped trace header support for Data Cloud HTTP responses.
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
    private static final ThreadLocal<TraceHeaders> CURRENT = new ThreadLocal<>();

    private RequestTraceSupport() {
    }

    public static void setCurrent(TraceHeaders traceHeaders) {
        CURRENT.set(traceHeaders);
    }

    public static void clearCurrent() {
        CURRENT.remove();
    }

    public static HttpResponse.Builder applyTo(HttpResponse.Builder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("builder must not be null");
        }
        TraceHeaders traceHeaders = CURRENT.get();
        if (traceHeaders == null) {
            return builder;
        }
        builder.withHeader(HttpHeaders.of(REQUEST_ID_HEADER), traceHeaders.requestId());
        builder.withHeader(HttpHeaders.of(CORRELATION_ID_HEADER), traceHeaders.requestId());
        builder.withHeader(HttpHeaders.of(TRACEPARENT_HEADER), traceHeaders.traceParent());
        String parentSpanId = traceHeaders.parentSpanId();
        if (parentSpanId != null && !parentSpanId.isBlank()) {
            builder.withHeader(HttpHeaders.of(PARENT_SPAN_ID_HEADER), parentSpanId);
        }
        return builder;
    }

    public record TraceHeaders(String requestId,
                               String traceId,
                               String spanId,
                               @Nullable String parentSpanId,
                               boolean sampled) {
        private String traceParent() {
            return "00-" + traceId + "-" + spanId + "-" + (sampled ? "01" : "00");
        }
    }
}