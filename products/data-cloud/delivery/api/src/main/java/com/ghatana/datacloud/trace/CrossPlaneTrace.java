/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.trace;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pass 9: Cross-plane trace model for Data Cloud + AEP + agents + media.
 *
 * <p>Provides unified tracing across:
 * <ul>
 *   <li>Data Cloud (entity storage, connectors, pipelines)</li>
 *   <li>AEP (Action Plane - agent orchestration, pattern execution)</li>
 *   <li>Agents (tool calls, memory writes, approvals)</li>
 *   <li>Media (processing jobs, transcription, vision analysis)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Cross-plane distributed tracing for observability
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class CrossPlaneTrace {

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String operationId;
    private final String tenantId;
    private final PlaneType sourcePlane;
    private final PlaneType targetPlane;
    private final String serviceName;
    private final String operationName;
    private final TraceStatus status;
    private final Instant startTime;
    private final Instant endTime;
    private final long durationMs;
    private final Map<String, Object> attributes;
    private final List<TraceEvent> events;
    private final String correlationId;
    private final String userId;
    private final String resourceType;
    private final String resourceId;

    private CrossPlaneTrace(Builder builder) {
        this.traceId = Objects.requireNonNull(builder.traceId, "traceId required");
        this.spanId = Objects.requireNonNull(builder.spanId, "spanId required");
        this.parentSpanId = builder.parentSpanId;
        this.operationId = builder.operationId;
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId required");
        this.sourcePlane = Objects.requireNonNull(builder.sourcePlane, "sourcePlane required");
        this.targetPlane = builder.targetPlane;
        this.serviceName = Objects.requireNonNull(builder.serviceName, "serviceName required");
        this.operationName = Objects.requireNonNull(builder.operationName, "operationName required");
        this.status = builder.status != null ? builder.status : TraceStatus.PENDING;
        this.startTime = builder.startTime != null ? builder.startTime : Instant.now();
        this.endTime = builder.endTime;
        this.durationMs = builder.durationMs;
        this.attributes = builder.attributes != null ? Map.copyOf(builder.attributes) : Map.of();
        this.events = builder.events != null ? List.copyOf(builder.events) : List.of();
        this.correlationId = builder.correlationId;
        this.userId = builder.userId;
        this.resourceType = builder.resourceType;
        this.resourceId = builder.resourceId;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new root trace with generated IDs.
     */
    public static CrossPlaneTrace createRoot(
            String tenantId,
            PlaneType sourcePlane,
            String serviceName,
            String operationName) {
        String traceId = "trace-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String spanId = "span-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return builder()
                .traceId(traceId)
                .spanId(spanId)
                .tenantId(tenantId)
                .sourcePlane(sourcePlane)
                .serviceName(serviceName)
                .operationName(operationName)
                .status(TraceStatus.PENDING)
                .build();
    }

    // Getters
    public String traceId() { return traceId; }
    public String spanId() { return spanId; }
    public String parentSpanId() { return parentSpanId; }
    public String operationId() { return operationId; }
    public String tenantId() { return tenantId; }
    public PlaneType sourcePlane() { return sourcePlane; }
    public PlaneType targetPlane() { return targetPlane; }
    public String serviceName() { return serviceName; }
    public String operationName() { return operationName; }
    public TraceStatus status() { return status; }
    public Instant startTime() { return startTime; }
    public Instant endTime() { return endTime; }
    public long durationMs() { return durationMs; }
    public Map<String, Object> attributes() { return attributes; }
    public List<TraceEvent> events() { return events; }
    public String correlationId() { return correlationId; }
    public String userId() { return userId; }
    public String resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }

    /**
     * Checks if this trace has completed.
     */
    public boolean isComplete() {
        return status == TraceStatus.COMPLETED ||
               status == TraceStatus.FAILED ||
               status == TraceStatus.CANCELLED;
    }

    /**
     * Creates a child span from this trace.
     */
    public CrossPlaneTrace createChildSpan(
            PlaneType targetPlane,
            String serviceName,
            String operationName) {
        String childSpanId = "span-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return builder()
                .traceId(this.traceId)
                .spanId(childSpanId)
                .parentSpanId(this.spanId)
                .tenantId(this.tenantId)
                .sourcePlane(this.sourcePlane)
                .targetPlane(targetPlane)
                .serviceName(serviceName)
                .operationName(operationName)
                .correlationId(this.correlationId)
                .userId(this.userId)
                .status(TraceStatus.PENDING)
                .build();
    }

    /**
     * Plane types for cross-plane tracing.
     */
    public enum PlaneType {
        DATA_CLOUD,
        ACTION_PLANE,
        AGENT_RUNTIME,
        MEDIA_PROCESSING,
        CONNECTOR,
        WORKFLOW,
        EXTERNAL
    }

    /**
     * Trace status enumeration.
     */
    public enum TraceStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        TIMEOUT
    }

    /**
     * Trace event record.
     */
    public record TraceEvent(
            String eventType,
            String description,
            Instant timestamp,
            Map<String, Object> metadata
    ) {
        public TraceEvent {
            if (metadata == null) {
                metadata = Map.of();
            }
        }
    }

    /**
     * Builder for CrossPlaneTrace.
     */
    public static class Builder {
        private String traceId;
        private String spanId;
        private String parentSpanId;
        private String operationId;
        private String tenantId;
        private PlaneType sourcePlane;
        private PlaneType targetPlane;
        private String serviceName;
        private String operationName;
        private TraceStatus status;
        private Instant startTime;
        private Instant endTime;
        private long durationMs;
        private Map<String, Object> attributes;
        private List<TraceEvent> events;
        private String correlationId;
        private String userId;
        private String resourceType;
        private String resourceId;

        private Builder() {}

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder spanId(String spanId) {
            this.spanId = spanId;
            return this;
        }

        public Builder parentSpanId(String parentSpanId) {
            this.parentSpanId = parentSpanId;
            return this;
        }

        public Builder operationId(String operationId) {
            this.operationId = operationId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder sourcePlane(PlaneType sourcePlane) {
            this.sourcePlane = sourcePlane;
            return this;
        }

        public Builder targetPlane(PlaneType targetPlane) {
            this.targetPlane = targetPlane;
            return this;
        }

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public Builder status(TraceStatus status) {
            this.status = status;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder events(List<TraceEvent> events) {
            this.events = events;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public CrossPlaneTrace build() {
            return new CrossPlaneTrace(this);
        }
    }
}
