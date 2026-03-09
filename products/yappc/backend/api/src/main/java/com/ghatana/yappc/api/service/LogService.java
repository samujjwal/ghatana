/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.domain.LogEntry;
import com.ghatana.yappc.api.domain.LogEntry.*;
import com.ghatana.yappc.api.repository.LogEntryRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;

/**
 * Service for managing log entries.
 *
 * @doc.type class
 * @doc.purpose Business logic for log operations
 * @doc.layer service
 * @doc.pattern Service
 */
public class LogService {

    private static final Logger logger = LoggerFactory.getLogger(LogService.class);

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final LogEntryRepository repository;
    private final AuditService auditService;

    @Inject
    public LogService(LogEntryRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /**
     * Records a log entry.
     */
    public Promise<LogEntry> recordLog(String tenantId, RecordLogInput input) {
        LogEntry log = new LogEntry();
        log.setTenantId(tenantId);
        log.setProjectId(input.projectId());
        log.setService(input.service());
        log.setInstance(input.instance());
        log.setLevel(input.level());
        log.setMessage(input.message());
        log.setLogger(input.logger());
        log.setThread(input.thread());
        log.setTraceId(input.traceId());
        log.setSpanId(input.spanId());
        log.setRequestId(input.requestId());
        log.setUserId(input.userId());
        if (input.context() != null) {
            log.setContext(input.context());
        }
        log.setStackTrace(input.stackTrace());
        log.setTimestamp(input.timestamp() != null ? input.timestamp() : Instant.now());

        return repository.save(log);
    }

    /**
     * Records multiple log entries in batch.
     */
    public Promise<List<LogEntry>> recordLogs(String tenantId, List<RecordLogInput> inputs) {
        List<LogEntry> logs = inputs.stream()
            .map(input -> {
                LogEntry log = new LogEntry();
                log.setTenantId(tenantId);
                log.setProjectId(input.projectId());
                log.setService(input.service());
                log.setInstance(input.instance());
                log.setLevel(input.level());
                log.setMessage(input.message());
                log.setLogger(input.logger());
                log.setThread(input.thread());
                log.setTraceId(input.traceId());
                log.setSpanId(input.spanId());
                log.setRequestId(input.requestId());
                log.setUserId(input.userId());
                if (input.context() != null) {
                    log.setContext(input.context());
                }
                log.setStackTrace(input.stackTrace());
                log.setTimestamp(input.timestamp() != null ? input.timestamp() : Instant.now());
                return log;
            })
            .toList();

        return repository.saveBatch(logs);
    }

    /**
     * Gets a log entry by ID.
     */
    public Promise<Optional<LogEntry>> getLogEntry(String tenantId, UUID logId) {
        return repository.findById(tenantId, logId)
            .map(l -> Optional.ofNullable(l));
    }

    /**
     * Lists log entries for a project.
     */
    public Promise<List<LogEntry>> listProjectLogs(String tenantId, String projectId, Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        return repository.findByProject(tenantId, projectId, effectiveLimit);
    }

    /**
     * Lists log entries by service.
     */
    public Promise<List<LogEntry>> listServiceLogs(String tenantId, String service, Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        return repository.findByService(tenantId, service, effectiveLimit);
    }

    /**
     * Lists log entries by level.
     */
    public Promise<List<LogEntry>> listLogsByLevel(String tenantId, LogLevel level, Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        return repository.findByLevel(tenantId, level, effectiveLimit);
    }

    /**
     * Lists error logs for a project.
     */
    public Promise<List<LogEntry>> listErrorLogs(String tenantId, String projectId, Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        return repository.findErrors(tenantId, projectId, effectiveLimit);
    }

    /**
     * Gets logs by trace ID.
     */
    public Promise<List<LogEntry>> getLogsByTrace(String tenantId, String traceId) {
        return repository.findByTraceId(tenantId, traceId);
    }

    /**
     * Gets logs by request ID.
     */
    public Promise<List<LogEntry>> getLogsByRequest(String tenantId, String requestId) {
        return repository.findByRequestId(tenantId, requestId);
    }

    /**
     * Gets logs by user.
     */
    public Promise<List<LogEntry>> getLogsByUser(String tenantId, String userId, Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        return repository.findByUser(tenantId, userId, effectiveLimit);
    }

    /**
     * Searches logs by message pattern.
     */
    public Promise<List<LogEntry>> searchLogs(String tenantId, String query, Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        return repository.searchByMessage(tenantId, query, effectiveLimit);
    }

    /**
     * Gets logs within a time range.
     */
    public Promise<List<LogEntry>> getLogsByTimeRange(
            String tenantId, 
            String projectId, 
            Instant start, 
            Instant end,
            Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        return repository.findByTimeRange(tenantId, projectId, start, end, effectiveLimit);
    }

    /**
     * Gets log level counts for a project.
     */
    public Promise<LogLevelStats> getLogLevelStats(String tenantId, String projectId) {
        return repository.countByLevel(tenantId, projectId, LogLevel.ERROR)
            .then(errorCount -> repository.countByLevel(tenantId, projectId, LogLevel.WARN)
                .then(warnCount -> repository.countByLevel(tenantId, projectId, LogLevel.INFO)
                    .map(infoCount -> {
                        LogLevelStats stats = new LogLevelStats();
                        stats.setErrorCount(errorCount);
                        stats.setWarnCount(warnCount);
                        stats.setInfoCount(infoCount);
                        return stats;
                    })));
    }

    /**
     * Cleans up old logs.
     */
    public Promise<Integer> cleanupOldLogs(String tenantId, Instant before) {
        logger.info("Cleaning up logs before: {} for tenant: {}", before, tenantId);
        return repository.deleteBefore(tenantId, before);
    }

    /**
     * Deletes a log entry.
     */
    public Promise<Void> deleteLogEntry(String tenantId, UUID logId) {
        return repository.delete(tenantId, logId);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    // ========== Input Records ==========

    public record RecordLogInput(
        String projectId,
        String service,
        String instance,
        LogLevel level,
        String message,
        String logger,
        String thread,
        String traceId,
        String spanId,
        String requestId,
        String userId,
        Map<String, String> context,
        String stackTrace,
        Instant timestamp
    ) {}

    // ========== Stats Classes ==========

    public static class LogLevelStats {
        private long errorCount;
        private long warnCount;
        private long infoCount;

        public long getErrorCount() { return errorCount; }
        public void setErrorCount(long errorCount) { this.errorCount = errorCount; }

        public long getWarnCount() { return warnCount; }
        public void setWarnCount(long warnCount) { this.warnCount = warnCount; }

        public long getInfoCount() { return infoCount; }
        public void setInfoCount(long infoCount) { this.infoCount = infoCount; }
    }
}
