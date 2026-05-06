package com.ghatana.kernel.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenTelemetry implementation of TracingPort.
 *
 * @doc.type class
 * @doc.purpose OpenTelemetry tracing provider implementation
 * @doc.layer core
 * @doc.pattern Adapter
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class OpenTelemetryTracingProvider implements TracingPort {

    private final Tracer tracer;
    private final ThreadLocal<Span> currentSpan = new ThreadLocal<>();

    public OpenTelemetryTracingProvider(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("ghatana.kernel", "1.0.0");
    }

    @Override
    public Span startSpan(String spanName) {
        SpanBuilder builder = tracer.spanBuilder(spanName);
        io.opentelemetry.api.trace.Span otelSpan = builder.startSpan();
        return new OpenTelemetrySpanAdapter(otelSpan);
    }

    @Override
    public Span startSpan(String spanName, Span parent) {
        SpanBuilder builder = tracer.spanBuilder(spanName);
        if (parent != null && parent instanceof OpenTelemetrySpanAdapter adapter) {
            builder.setParent(Context.root().with(adapter.getOtelSpan()));
        }
        io.opentelemetry.api.trace.Span otelSpan = builder.startSpan();
        return new OpenTelemetrySpanAdapter(otelSpan);
    }

    @Override
    public Span getCurrentSpan() {
        io.opentelemetry.api.trace.Span otelSpan = io.opentelemetry.api.trace.Span.current();
        if (otelSpan == null || !otelSpan.isRecording()) {
            return null;
        }
        return new OpenTelemetrySpanAdapter(otelSpan);
    }

    @Override
    public void addAttribute(String key, String value) {
        io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.current();
        if (span != null) {
            span.setAttribute(key, value);
        }
    }

    @Override
    public void addAttribute(String key, long value) {
        io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.current();
        if (span != null) {
            span.setAttribute(key, value);
        }
    }

    @Override
    public void addAttribute(String key, double value) {
        io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.current();
        if (span != null) {
            span.setAttribute(key, value);
        }
    }

    @Override
    public void addAttribute(String key, boolean value) {
        io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.current();
        if (span != null) {
            span.setAttribute(key, value);
        }
    }

    @Override
    public void addAttributes(Map<String, Object> attributes) {
        io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.current();
        if (span != null) {
            attributes.forEach((key, value) -> {
                if (value instanceof String) {
                    span.setAttribute(key, (String) value);
                } else if (value instanceof Long) {
                    span.setAttribute(key, (Long) value);
                } else if (value instanceof Double) {
                    span.setAttribute(key, (Double) value);
                } else if (value instanceof Boolean) {
                    span.setAttribute(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    span.setAttribute(key, (Integer) value);
                }
            });
        }
    }

    @Override
    public void addEvent(String eventName, Map<String, Object> attributes) {
        io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.current();
        if (span != null) {
            if (attributes != null && !attributes.isEmpty()) {
                span.addEvent(eventName, toOtelAttributes(attributes));
            } else {
                span.addEvent(eventName);
            }
        }
    }

    @Override
    public void recordException(Throwable throwable) {
        io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.current();
        if (span != null) {
            span.recordException(throwable);
        }
    }

    @Override
    public void setStatus(SpanStatus status) {
        io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.current();
        if (span != null) {
            span.setStatus(toOtelStatus(status));
        }
    }

    @Override
    public void setStatus(SpanStatus status, String description) {
        io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.current();
        if (span != null) {
            span.setStatus(toOtelStatus(status), description);
        }
    }

    private io.opentelemetry.api.trace.StatusCode toOtelStatus(SpanStatus status) {
        return switch (status) {
            case OK -> StatusCode.OK;
            case ERROR -> StatusCode.ERROR;
            case UNSET -> StatusCode.UNSET;
        };
    }

    private io.opentelemetry.api.common.Attributes toOtelAttributes(Map<String, Object> attributes) {
        io.opentelemetry.api.common.AttributesBuilder builder = io.opentelemetry.api.common.Attributes.builder();
        attributes.forEach((key, value) -> {
            if (value instanceof String) {
                builder.put(key, (String) value);
            } else if (value instanceof Long) {
                builder.put(key, (Long) value);
            } else if (value instanceof Double) {
                builder.put(key, (Double) value);
            } else if (value instanceof Boolean) {
                builder.put(key, (Boolean) value);
            } else if (value instanceof Integer) {
                builder.put(key, (Integer) value);
            }
        });
        return builder.build();
    }

    /**
     * Adapter for OpenTelemetry Span.
     */
    private static class OpenTelemetrySpanAdapter implements Span {
        private final io.opentelemetry.api.trace.Span otelSpan;

        OpenTelemetrySpanAdapter(io.opentelemetry.api.trace.Span otelSpan) {
            this.otelSpan = otelSpan;
        }

        io.opentelemetry.api.trace.Span getOtelSpan() {
            return otelSpan;
        }

        @Override
        public SpanContext getContext() {
            String traceId = otelSpan.getSpanContext().getTraceId();
            String spanId = otelSpan.getSpanContext().getSpanId();
            return new SpanContext(traceId, spanId);
        }

        @Override
        public void end() {
            otelSpan.end();
        }

        @Override
        public void end(long endTimestamp) {
            // Note: OpenTelemetry Span.end(long) requires Instant, not long
            // For simplicity, we just end the span without custom timestamp
            otelSpan.end();
        }

        @Override
        public boolean isRecording() {
            return otelSpan.isRecording();
        }

        @Override
        public void makeCurrent() {
            otelSpan.makeCurrent();
        }
    }
}
