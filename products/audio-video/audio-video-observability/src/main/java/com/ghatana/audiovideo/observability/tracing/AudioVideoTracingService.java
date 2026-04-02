package com.ghatana.audiovideo.observability.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose OpenTelemetry distributed tracing for Audio-Video service operations
 * @doc.layer observability
 * @doc.pattern Service
 */
public class AudioVideoTracingService {

    private static final Logger log = LoggerFactory.getLogger(AudioVideoTracingService.class);

    private final Tracer tracer;
    private static final int SLA_THRESHOLD_MS = 50; // Audio-Video operations should be < 50ms

    public AudioVideoTracingService(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Start a span for gRPC streaming operation
     */
    public Span startGrpcStreamingSpan(String methodName, String tenantId) {
        String correlationId = getOrCreateCorrelationId();

        Span span = tracer.spanBuilder("audiovideo.grpc.streaming")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("method.name", methodName)
                .setAttribute("tenant.id", tenantId)
                .setAttribute("correlation.id", correlationId)
                .startSpan();

        MDC.put("methodName", methodName);
        MDC.put("correlationId", correlationId);

        return span;
    }

    /**
     * Start a span for video processing operation
     */
    public Span startVideoProcessingSpan(String processId, String tenantId) {
        String correlationId = getOrCreateCorrelationId();

        Span span = tracer.spanBuilder("audiovideo.video.processing")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("process.id", processId)
                .setAttribute("tenant.id", tenantId)
                .setAttribute("correlation.id", correlationId)
                .startSpan();

        MDC.put("processId", processId);

        return span;
    }

    /**
     * Start a span for audio processing operation
     */
    public Span startAudioProcessingSpan(String processId, String tenantId) {
        String correlationId = getOrCreateCorrelationId();

        Span span = tracer.spanBuilder("audiovideo.audio.processing")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("process.id", processId)
                .setAttribute("tenant.id", tenantId)
                .setAttribute("correlation.id", correlationId)
                .startSpan();

        MDC.put("processId", processId);

        return span;
    }

    /**
     * Start a span for synthesis operation
     */
    public Span startSynthesisSpan(String synthesisId, String tenantId) {
        String correlationId = getOrCreateCorrelationId();

        Span span = tracer.spanBuilder("audiovideo.synthesis")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("synthesis.id", synthesisId)
                .setAttribute("tenant.id", tenantId)
                .setAttribute("correlation.id", correlationId)
                .startSpan();

        MDC.put("synthesisId", synthesisId);

        return span;
    }

    /**
     * Record successful operation with SLA validation
     */
    public void recordSuccess(Span span, long durationMs) {
        if (span != null) {
            span.setStatus(StatusCode.OK);
            span.setAttribute("duration.ms", durationMs);

            if (durationMs > SLA_THRESHOLD_MS) {
                span.setAttribute("sla.warning", true);
                log.warn("Audio-Video operation exceeded SLA threshold: {}ms > {}ms",
                        durationMs, SLA_THRESHOLD_MS);
            }

            span.end();
            log.debug("Audio-Video operation completed successfully in {}ms", durationMs);
        }
    }

    /**
     * Record failed operation
     */
    public void recordError(Span span, Exception exception, long durationMs) {
        if (span != null) {
            span.setStatus(StatusCode.ERROR, exception.getMessage());
            span.recordException(exception);
            span.setAttribute("duration.ms", durationMs);
            span.setAttribute("error", true);
            span.setAttribute("error.type", exception.getClass().getName());
            span.end();

            log.error("Audio-Video operation failed after {}ms: {}",
                    durationMs, exception.getMessage(), exception);
        }
    }

    /**
     * Create a scope for a span
     */
    public Scope createScope(Span span) {
        return span.makeCurrent();
    }

    /**
     * Get or create correlation ID
     */
    private String getOrCreateCorrelationId() {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            MDC.put("correlationId", correlationId);
        }
        return correlationId;
    }

    /**
     * Clear all MDC context
     */
    public void clearContext() {
        MDC.clear();
    }
}
