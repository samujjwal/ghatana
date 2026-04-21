package com.ghatana.aep.server.http;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import org.jetbrains.annotations.Nullable;

/**
 * Request-scoped tracing header support for AEP HTTP responses.
 *
 * @doc.type class
 * @doc.purpose Applies correlation and W3C trace headers to HTTP response builders
 * @doc.layer product
 * @doc.pattern Utility
 */
final class RequestTraceSupport {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final String TRACESTATE_HEADER = "tracestate";
    private static final ThreadLocal<TraceHeaders> CURRENT = new ThreadLocal<>();

    private RequestTraceSupport() {
    }

    static void setCurrent(TraceHeaders traceHeaders) {
        CURRENT.set(traceHeaders);
    }

    static void clearCurrent() {
        CURRENT.remove();
    }

    static HttpResponse.Builder applyTo(HttpResponse.Builder builder) {
        TraceHeaders traceHeaders = CURRENT.get();
        if (traceHeaders == null) {
            return builder;
        }
        builder.withHeader(HttpHeaders.of(CORRELATION_ID_HEADER), traceHeaders.correlationId());
        builder.withHeader(HttpHeaders.of(TRACEPARENT_HEADER), traceHeaders.traceParent());
        if (traceHeaders.tracestate() != null && !traceHeaders.tracestate().isBlank()) {
            builder.withHeader(HttpHeaders.of(TRACESTATE_HEADER), traceHeaders.tracestate());
        }
        return builder;
    }

    static ResponseBuilder applyTo(ResponseBuilder builder) {
        TraceHeaders traceHeaders = CURRENT.get();
        if (traceHeaders == null) {
            return builder;
        }
        builder.header(CORRELATION_ID_HEADER, traceHeaders.correlationId());
        builder.header(TRACEPARENT_HEADER, traceHeaders.traceParent());
        if (traceHeaders.tracestate() != null && !traceHeaders.tracestate().isBlank()) {
            builder.header(TRACESTATE_HEADER, traceHeaders.tracestate());
        }
        return builder;
    }

    record TraceHeaders(String correlationId,
                        String traceId,
                        String spanId,
                        boolean sampled,
                        @Nullable String tracestate) {
        private String traceParent() {
            return "00-" + traceId + "-" + spanId + "-" + (sampled ? "01" : "00");
        }
    }
}