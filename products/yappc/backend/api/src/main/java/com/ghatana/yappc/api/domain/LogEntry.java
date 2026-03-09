/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a log entry.
 *
 * @doc.type class
 * @doc.purpose LogEntry domain entity for operations logging
 * @doc.layer domain
 * @doc.pattern Entity
 */
public class LogEntry {

    private UUID id;
    private String tenantId;
    private String projectId;
    private String service;
    private String instance;
    private LogLevel level;
    private String message;
    private String logger;
    private String thread;
    private String traceId;
    private String spanId;
    private String requestId;
    private String userId;
    private Map<String, String> context;
    private String stackTrace;
    private Instant timestamp;
    private Map<String, Object> metadata;

    public LogEntry() {
        this.id = UUID.randomUUID();
        this.context = new HashMap<>();
        this.metadata = new HashMap<>();
        this.timestamp = Instant.now();
    }

    // ========== Enums ==========

    public enum LogLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        FATAL
    }

    // ========== Domain Methods ==========

    public boolean isError() {
        return level == LogLevel.ERROR || level == LogLevel.FATAL;
    }

    public boolean hasException() {
        return stackTrace != null && !stackTrace.isEmpty();
    }

    public boolean hasTracing() {
        return traceId != null && !traceId.isEmpty();
    }

    // ========== Getters and Setters ==========

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getInstance() { return instance; }
    public void setInstance(String instance) { this.instance = instance; }

    public LogLevel getLevel() { return level; }
    public void setLevel(LogLevel level) { this.level = level; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getLogger() { return logger; }
    public void setLogger(String logger) { this.logger = logger; }

    public String getThread() { return thread; }
    public void setThread(String thread) { this.thread = thread; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getSpanId() { return spanId; }
    public void setSpanId(String spanId) { this.spanId = spanId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Map<String, String> getContext() { return context; }
    public void setContext(Map<String, String> context) { this.context = context; }

    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LogEntry)) return false;
        LogEntry logEntry = (LogEntry) o;
        return Objects.equals(id, logEntry.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
