/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a distributed trace.
 *
 * @doc.type class
 * @doc.purpose Trace domain entity for distributed tracing
 * @doc.layer domain
 * @doc.pattern Entity
 */
public class Trace {

    private UUID id;
    private String tenantId;
    private String projectId;
    private String traceId;
    private String name;
    private String service;
    private String operation;
    private TraceStatus status;
    private List<Span> spans;
    private Instant startTime;
    private Instant endTime;
    private long durationMs;
    private String userId;
    private String requestId;
    private Map<String, String> tags;
    private Map<String, Object> metadata;

    public Trace() {
        this.id = UUID.randomUUID();
        this.traceId = UUID.randomUUID().toString().replace("-", "");
        this.status = TraceStatus.IN_PROGRESS;
        this.spans = new ArrayList<>();
        this.tags = new HashMap<>();
        this.metadata = new HashMap<>();
        this.startTime = Instant.now();
    }

    // ========== Enums ==========

    public enum TraceStatus {
        IN_PROGRESS,
        COMPLETED,
        ERROR
    }

    public enum SpanKind {
        SERVER,
        CLIENT,
        PRODUCER,
        CONSUMER,
        INTERNAL
    }

    // ========== Nested Classes ==========

    public static class Span {
        private String spanId;
        private String parentSpanId;
        private String name;
        private String service;
        private String operation;
        private SpanKind kind;
        private TraceStatus status;
        private Instant startTime;
        private Instant endTime;
        private long durationMs;
        private List<SpanEvent> events;
        private List<SpanLink> links;
        private Map<String, String> tags;
        private String errorMessage;
        private String stackTrace;

        public Span() {
            this.spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            this.status = TraceStatus.IN_PROGRESS;
            this.events = new ArrayList<>();
            this.links = new ArrayList<>();
            this.tags = new HashMap<>();
            this.startTime = Instant.now();
        }

        public void complete() {
            this.endTime = Instant.now();
            this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
            if (status == TraceStatus.IN_PROGRESS) {
                this.status = TraceStatus.COMPLETED;
            }
        }

        public void fail(String errorMessage, String stackTrace) {
            this.status = TraceStatus.ERROR;
            this.errorMessage = errorMessage;
            this.stackTrace = stackTrace;
            complete();
        }

        public String getSpanId() { return spanId; }
        public void setSpanId(String spanId) { this.spanId = spanId; }

        public String getParentSpanId() { return parentSpanId; }
        public void setParentSpanId(String parentSpanId) { this.parentSpanId = parentSpanId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getService() { return service; }
        public void setService(String service) { this.service = service; }

        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }

        public SpanKind getKind() { return kind; }
        public void setKind(SpanKind kind) { this.kind = kind; }

        public TraceStatus getStatus() { return status; }
        public void setStatus(TraceStatus status) { this.status = status; }

        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }

        public Instant getEndTime() { return endTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }

        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

        public List<SpanEvent> getEvents() { return events; }
        public void setEvents(List<SpanEvent> events) { this.events = events; }

        public List<SpanLink> getLinks() { return links; }
        public void setLinks(List<SpanLink> links) { this.links = links; }

        public Map<String, String> getTags() { return tags; }
        public void setTags(Map<String, String> tags) { this.tags = tags; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public String getStackTrace() { return stackTrace; }
        public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }
    }

    public static class SpanEvent {
        private String name;
        private Instant timestamp;
        private Map<String, String> attributes;

        public SpanEvent() {
            this.attributes = new HashMap<>();
            this.timestamp = Instant.now();
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

        public Map<String, String> getAttributes() { return attributes; }
        public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
    }

    public static class SpanLink {
        private String traceId;
        private String spanId;
        private Map<String, String> attributes;

        public SpanLink() {
            this.attributes = new HashMap<>();
        }

        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }

        public String getSpanId() { return spanId; }
        public void setSpanId(String spanId) { this.spanId = spanId; }

        public Map<String, String> getAttributes() { return attributes; }
        public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
    }

    // ========== Domain Methods ==========

    public Span createSpan(String name, SpanKind kind) {
        Span span = new Span();
        span.setName(name);
        span.setKind(kind);
        spans.add(span);
        return span;
    }

    public Span createChildSpan(String name, String parentSpanId, SpanKind kind) {
        Span span = createSpan(name, kind);
        span.setParentSpanId(parentSpanId);
        return span;
    }

    public void complete() {
        this.endTime = Instant.now();
        this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        
        boolean hasError = spans.stream().anyMatch(s -> s.getStatus() == TraceStatus.ERROR);
        this.status = hasError ? TraceStatus.ERROR : TraceStatus.COMPLETED;
    }

    public Span getRootSpan() {
        return spans.stream()
            .filter(s -> s.getParentSpanId() == null)
            .findFirst()
            .orElse(null);
    }

    public long getTotalDuration() {
        if (durationMs > 0) return durationMs;
        if (endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
        return java.time.Duration.between(startTime, Instant.now()).toMillis();
    }

    // ========== Getters and Setters ==========

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public TraceStatus getStatus() { return status; }
    public void setStatus(TraceStatus status) { this.status = status; }

    public List<Span> getSpans() { return spans; }
    public void setSpans(List<Span> spans) { this.spans = spans; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trace)) return false;
        Trace trace = (Trace) o;
        return Objects.equals(id, trace.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
