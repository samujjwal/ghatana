/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.domain.Trace;
import com.ghatana.yappc.api.domain.Trace.*;
import com.ghatana.yappc.api.repository.TraceRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;

/**
 * Service for managing distributed traces.
 *
 * @doc.type class
 * @doc.purpose Business logic for trace operations
 * @doc.layer service
 * @doc.pattern Service
 */
public class TraceService {

    private static final Logger logger = LoggerFactory.getLogger(TraceService.class);

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final TraceRepository repository;
    private final AuditService auditService;

    @Inject
    public TraceService(TraceRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /**
     * Starts a new trace.
     */
    public Promise<Trace> startTrace(String tenantId, StartTraceInput input) {
        Trace trace = new Trace();
        trace.setTenantId(tenantId);
        trace.setProjectId(input.projectId());
        trace.setName(input.name());
        trace.setService(input.service());
        trace.setOperation(input.operation());
        trace.setUserId(input.userId());
        trace.setRequestId(input.requestId());
        if (input.tags() != null) {
            trace.setTags(input.tags());
        }

        // Create root span
        Span rootSpan = trace.createSpan(input.name(), SpanKind.SERVER);
        rootSpan.setService(input.service());
        rootSpan.setOperation(input.operation());

        return repository.save(trace);
    }

    /**
     * Adds a span to a trace.
     */
    public Promise<Trace> addSpan(String tenantId, String traceId, AddSpanInput input) {
        return repository.findByTraceId(tenantId, traceId)
            .then(trace -> {
                if (trace == null) {
                    return Promise.ofException(new IllegalArgumentException("Trace not found"));
                }

                Span span = trace.createChildSpan(input.name(), input.parentSpanId(), input.kind());
                span.setService(input.service());
                span.setOperation(input.operation());
                if (input.tags() != null) {
                    span.setTags(input.tags());
                }

                return repository.save(trace);
            });
    }

    /**
     * Completes a span.
     */
    public Promise<Trace> completeSpan(String tenantId, String traceId, String spanId, CompleteSpanInput input) {
        return repository.findByTraceId(tenantId, traceId)
            .then(trace -> {
                if (trace == null) {
                    return Promise.ofException(new IllegalArgumentException("Trace not found"));
                }

                Span span = trace.getSpans().stream()
                    .filter(s -> s.getSpanId().equals(spanId))
                    .findFirst()
                    .orElse(null);

                if (span == null) {
                    return Promise.ofException(new IllegalArgumentException("Span not found"));
                }

                if (input.error() != null && input.error()) {
                    span.fail(input.errorMessage(), input.stackTrace());
                } else {
                    span.complete();
                }

                return repository.save(trace);
            });
    }

    /**
     * Completes a trace.
     */
    public Promise<Trace> completeTrace(String tenantId, String traceId) {
        return repository.findByTraceId(tenantId, traceId)
            .then(trace -> {
                if (trace == null) {
                    return Promise.ofException(new IllegalArgumentException("Trace not found"));
                }

                trace.complete();

                return repository.save(trace);
            });
    }

    /**
     * Gets a trace by ID.
     */
    public Promise<Optional<Trace>> getTrace(String tenantId, UUID traceId) {
        return repository.findById(tenantId, traceId)
            .map(t -> Optional.ofNullable(t));
    }

    /**
     * Gets a trace by trace ID string.
     */
    public Promise<Optional<Trace>> getTraceByTraceId(String tenantId, String traceId) {
        return repository.findByTraceId(tenantId, traceId)
            .map(t -> Optional.ofNullable(t));
    }

    /**
     * Lists traces for a project.
     */
    public Promise<List<Trace>> listProjectTraces(String tenantId, String projectId, Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        return repository.findByProject(tenantId, projectId, effectiveLimit);
    }

    /**
     * Lists traces by service.
     */
    public Promise<List<Trace>> listServiceTraces(String tenantId, String service, Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        return repository.findByService(tenantId, service, effectiveLimit);
    }

    /**
     * Lists traces by operation.
     */
    public Promise<List<Trace>> listOperationTraces(
            String tenantId, 
            String service, 
            String operation, 
            Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        return repository.findByOperation(tenantId, service, operation, effectiveLimit);
    }

    /**
     * Lists error traces.
     */
    public Promise<List<Trace>> listErrorTraces(String tenantId, String projectId, Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        return repository.findErrors(tenantId, projectId, effectiveLimit);
    }

    /**
     * Lists slow traces.
     */
    public Promise<List<Trace>> listSlowTraces(
            String tenantId, 
            String projectId, 
            long minDurationMs,
            Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        return repository.findSlowTraces(tenantId, projectId, minDurationMs, effectiveLimit);
    }

    /**
     * Lists traces by user.
     */
    public Promise<List<Trace>> listUserTraces(String tenantId, String userId, Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        return repository.findByUser(tenantId, userId, effectiveLimit);
    }

    /**
     * Gets traces by request ID.
     */
    public Promise<List<Trace>> getTracesByRequest(String tenantId, String requestId) {
        return repository.findByRequestId(tenantId, requestId);
    }

    /**
     * Gets traces within a time range.
     */
    public Promise<List<Trace>> getTracesByTimeRange(
            String tenantId, 
            String projectId, 
            Instant start, 
            Instant end,
            Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        return repository.findByTimeRange(tenantId, projectId, start, end, effectiveLimit);
    }

    /**
     * Gets trace statistics.
     */
    public Promise<TraceStats> getTraceStatistics(String tenantId, String projectId, String service) {
        return repository.findByProject(tenantId, projectId, 1000)
            .then(traces -> repository.getAverageDuration(tenantId, projectId, service)
                .map(avgDuration -> {
                    TraceStats stats = new TraceStats();
                    
                    long total = traces.size();
                    long errors = traces.stream()
                        .filter(t -> t.getStatus() == TraceStatus.ERROR)
                        .count();
                    long completed = traces.stream()
                        .filter(t -> t.getStatus() == TraceStatus.COMPLETED)
                        .count();
                    
                    stats.setTotalTraces(total);
                    stats.setErrorCount(errors);
                    stats.setCompletedCount(completed);
                    stats.setAverageDurationMs(avgDuration != null ? avgDuration : 0.0);
                    
                    if (total > 0) {
                        stats.setErrorRate((double) errors / total * 100);
                    }
                    
                    // Calculate p95 duration
                    List<Long> durations = traces.stream()
                        .filter(t -> t.getDurationMs() > 0)
                        .map(Trace::getDurationMs)
                        .sorted()
                        .toList();
                    
                    if (!durations.isEmpty()) {
                        int p95Index = (int) Math.ceil(durations.size() * 0.95) - 1;
                        stats.setP95DurationMs(durations.get(Math.max(0, p95Index)));
                    }
                    
                    return stats;
                }));
    }

    /**
     * Cleans up old traces.
     */
    public Promise<Integer> cleanupOldTraces(String tenantId, Instant before) {
        logger.info("Cleaning up traces before: {} for tenant: {}", before, tenantId);
        return repository.deleteBefore(tenantId, before);
    }

    /**
     * Deletes a trace.
     */
    public Promise<Void> deleteTrace(String tenantId, UUID traceId) {
        return repository.delete(tenantId, traceId);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    // ========== Input Records ==========

    public record StartTraceInput(
        String projectId,
        String name,
        String service,
        String operation,
        String userId,
        String requestId,
        Map<String, String> tags
    ) {}

    public record AddSpanInput(
        String name,
        String parentSpanId,
        SpanKind kind,
        String service,
        String operation,
        Map<String, String> tags
    ) {}

    public record CompleteSpanInput(
        Boolean error,
        String errorMessage,
        String stackTrace
    ) {}

    // ========== Stats Classes ==========

    public static class TraceStats {
        private long totalTraces;
        private long errorCount;
        private long completedCount;
        private double errorRate;
        private double averageDurationMs;
        private long p95DurationMs;

        public long getTotalTraces() { return totalTraces; }
        public void setTotalTraces(long totalTraces) { this.totalTraces = totalTraces; }

        public long getErrorCount() { return errorCount; }
        public void setErrorCount(long errorCount) { this.errorCount = errorCount; }

        public long getCompletedCount() { return completedCount; }
        public void setCompletedCount(long completedCount) { this.completedCount = completedCount; }

        public double getErrorRate() { return errorRate; }
        public void setErrorRate(double errorRate) { this.errorRate = errorRate; }

        public double getAverageDurationMs() { return averageDurationMs; }
        public void setAverageDurationMs(double averageDurationMs) { this.averageDurationMs = averageDurationMs; }

        public long getP95DurationMs() { return p95DurationMs; }
        public void setP95DurationMs(long p95DurationMs) { this.p95DurationMs = p95DurationMs; }
    }
}
