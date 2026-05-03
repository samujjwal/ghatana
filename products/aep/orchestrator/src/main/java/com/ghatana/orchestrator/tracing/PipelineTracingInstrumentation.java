/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.tracing;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

/**
 * Tracing instrumentation for pipeline execution.
 *
 * Provides span creation and trace context propagation for pipeline operations.
 *
 * @doc.type class
 * @doc.purpose Distributed tracing instrumentation for pipeline execution
 * @doc.layer product
 * @doc.pattern Instrumentation
 */
public final class PipelineTracingInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(PipelineTracingInstrumentation.class);

    private PipelineTracingInstrumentation() {
        // Utility class
    }

    /**
     * Request trace context for distributed tracing.
     *
     * @doc.type record
     * @doc.purpose Holds trace context information (correlation ID, trace ID, span ID)
     * @doc.layer product
     */
    public record RequestTraceContext(
        String correlationId,
        String traceId,
        String spanId,
        boolean sampled,
        String tracestate
    ) {

    /**
     * Creates a span for pipeline execution.
     */
    public static PipelineSpan createPipelineSpan(
        String pipelineId,
        String pipelineName,
        String tenantId,
        Map<String, Object> parameters
    ) {
        PipelineSpan span = new PipelineSpan(
            "aep.pipeline.execute",
            Map.of(
                "pipeline.id", pipelineId,
                "pipeline.name", pipelineName,
                "tenant.id", tenantId,
                "parameter.count", parameters.size()
            )
        );
        
        span.event("pipeline.started", Map.of("timestamp", Instant.now().toString()));
        log.debug("Created pipeline execution span: pipelineId={}, tenantId={}", pipelineId, tenantId);
        
        return span;
    }

    /**
     * Creates a span for operator execution.
     */
    public static OperatorSpan createOperatorSpan(
        String operatorId,
        String operatorType,
        String pipelineId,
        RequestTraceContext parentContext
    ) {
        OperatorSpan span = new OperatorSpan(
            "aep.operator.execute",
            Map.of(
                "operator.id", operatorId,
                "operator.type", operatorType,
                "pipeline.id", pipelineId
            ),
            parentContext
        );
        
        span.event("operator.started", Map.of("timestamp", Instant.now().toString()));
        log.debug("Created operator execution span: operatorId={}, type={}", operatorId, operatorType);
        
        return span;
    }

    /**
     * Records checkpoint creation in span.
     */
    public static void recordCheckpointCreated(
        PipelineSpan span,
        String checkpointId,
        String checkpointType
    ) {
        span.event("checkpoint.created", Map.of(
            "checkpoint.id", checkpointId,
            "checkpoint.type", checkpointType,
            "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Records operator completion in span.
     */
    public static void recordOperatorCompleted(
        OperatorSpan span,
        boolean success,
        long durationMs,
        int eventCount
    ) {
        span.attribute("result.success", success);
        span.attribute("processing.time.ms", durationMs);
        span.attribute("event.count", eventCount);
        
        span.event("operator.completed", Map.of(
            "duration.ms", durationMs,
            "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Records pipeline completion in span.
     */
    public static void recordPipelineCompleted(
        PipelineSpan span,
        String runId,
        String status,
        long durationMs,
        int operatorCount
    ) {
        span.attribute("run.id", runId);
        span.attribute("run.status", status);
        span.attribute("duration.ms", durationMs);
        span.attribute("operator.count", operatorCount);
        
        span.event("pipeline.completed", Map.of(
            "duration.ms", durationMs,
            "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Records error in span.
     */
    public static void recordError(Span span, Exception error) {
        span.recordException(error);
        span.status("ERROR", error.getMessage());
        log.error("Recorded error in span: {}", error.getMessage());
    }

    /**
     * Pipeline span implementation.
     */
    public static class PipelineSpan implements Span {
        private final String name;
        private final Map<String, Object> attributes;
        private final Map<String, Object> events = new java.util.LinkedHashMap<>();
        private String status = "OK";
        private String statusDescription;
        private boolean ended = false;

        PipelineSpan(String name, Map<String, Object> attributes) {
            this.name = name;
            this.attributes = new java.util.HashMap<>(attributes);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.copyOf(attributes);
        }

        @Override
        public void attribute(String key, Object value) {
            if (!ended) {
                attributes.put(key, value);
            }
        }

        @Override
        public void event(String eventName, Map<String, Object> attributes) {
            if (!ended) {
                events.put(eventName, Map.copyOf(attributes));
            }
        }

        @Override
        public void recordException(Exception error) {
            if (!ended) {
                attribute("error.type", error.getClass().getSimpleName());
                attribute("error.message", error.getMessage());
                attribute("error.stacktrace", java.util.Arrays.stream(error.getStackTrace())
                    .limit(10)
                    .map(StackTraceElement::toString)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse(""));
            }
        }

        @Override
        public void status(String status, String description) {
            if (!ended) {
                this.status = status;
                this.statusDescription = description;
            }
        }

        @Override
        public void end() {
            if (!ended) {
                ended = true;
                log.debug("Span ended: name={}, status={}", name, status);
                // In production, this would export to tracing backend
            }
        }

        @Override
        public boolean isEnded() {
            return ended;
        }

        public Map<String, Object> getEvents() {
            return Map.copyOf(events);
        }
    }

    /**
     * Operator span implementation with parent context.
     */
    public static class OperatorSpan extends PipelineSpan {
        private final RequestTraceContext parentContext;

        OperatorSpan(String name, Map<String, Object> attributes, RequestTraceContext parentContext) {
            super(name, attributes);
            this.parentContext = parentContext;
            if (parentContext != null) {
                attribute("parent.trace.id", parentContext.traceId());
                attribute("parent.span.id", parentContext.spanId());
                attribute("correlation.id", parentContext.correlationId());
            }
        }

        public RequestTraceContext getParentContext() {
            return parentContext;
        }
    }

    /**
     * Span interface.
     */
    public interface Span {
        String getName();
        Map<String, Object> getAttributes();
        void attribute(String key, Object value);
        void event(String eventName, Map<String, Object> attributes);
        void recordException(Exception error);
        void status(String status, String description);
        void end();
        boolean isEnded();
    }
    }
}
