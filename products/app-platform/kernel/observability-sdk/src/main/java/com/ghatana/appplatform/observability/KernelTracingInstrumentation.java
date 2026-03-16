/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Provides OpenTelemetry tracing instrumentation for kernel services (STORY-K06-009).
 *
 * <p>Wraps service invocations in an OTel span and exports them to the configured OTLP
 * endpoint (Jaeger in the local environment, Grafana Tempo / Datadog in production).
 *
 * <p>Configuration in {@code application.properties}:
 * <pre>
 *   otel.service.name=appplatform-kernel
 *   otel.exporter.otlp.endpoint=http://jaeger:4317
 *   otel.traces.sampler=parentbased_traceidratio
 *   otel.traces.sampler.arg=0.1
 * </pre>
 *
 * @doc.type  class
 * @doc.purpose OTel span creation and attribute tagging for kernel service calls (K06-009)
 * @doc.layer kernel
 * @doc.pattern Adapter
 */
public final class KernelTracingInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(KernelTracingInstrumentation.class);

    private static final String INSTRUMENTATION_SCOPE = "com.ghatana.appplatform.kernel";

    private final Tracer tracer;

    public KernelTracingInstrumentation() {
        // Tracer obtained from the global OTel instance configured by the agent or SDK init
        this.tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE, "1.0.0");
    }

    /** Package-visible constructor for injecting a custom Tracer in tests. */
    KernelTracingInstrumentation(Tracer tracer) {
        this.tracer = Objects.requireNonNull(tracer, "tracer");
    }

    /**
     * Executes {@code operation} within a new child span.
     *
     * @param spanName        span name (verb + object, e.g. {@code "IAM.authorize"})
     * @param kind            span kind (SERVER, CLIENT, INTERNAL, etc.)
     * @param tenantId        tenant context tag (may be null)
     * @param jurisdiction    jurisdiction context tag (may be null)
     * @param operation       the operation to instrument
     * @param <T>             return type
     * @return the operation result
     */
    public <T> T trace(String spanName, SpanKind kind,
                        String tenantId, String jurisdiction,
                        Supplier<T> operation) {
        Objects.requireNonNull(spanName,  "spanName");
        Objects.requireNonNull(operation, "operation");

        Span span = tracer.spanBuilder(spanName)
                .setParent(Context.current())
                .setSpanKind(kind != null ? kind : SpanKind.INTERNAL)
                .startSpan();

        if (tenantId    != null) span.setAttribute("tenant.id",     tenantId);
        if (jurisdiction != null) span.setAttribute("jurisdiction",   jurisdiction);

        try (Scope ignored = span.makeCurrent()) {
            T result = operation.get();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Executes a void operation within a new child span.
     */
    public void traceVoid(String spanName, SpanKind kind, String tenantId,
                           String jurisdiction, Runnable operation) {
        trace(spanName, kind, tenantId, jurisdiction, () -> {
            operation.run();
            return null;
        });
    }

    /** Records an additional attribute on the current active span. */
    public static void addSpanAttribute(String key, String value) {
        Span current = Span.current();
        if (current.isRecording()) {
            current.setAttribute(key, value);
        }
    }

    /** Marks the current span as errored with the given exception. */
    public static void recordError(Throwable e) {
        Span current = Span.current();
        if (current.isRecording()) {
            current.setStatus(StatusCode.ERROR, e.getMessage());
            current.recordException(e);
        }
    }
}
