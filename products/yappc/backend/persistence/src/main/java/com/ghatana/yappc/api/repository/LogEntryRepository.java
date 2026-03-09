/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.LogEntry;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for LogEntry persistence operations.
 *
 * @doc.type interface
 * @doc.purpose LogEntry repository for CRUD operations
 * @doc.layer repository
 * @doc.pattern Repository
 */
public interface LogEntryRepository {

    /**
     * Save a log entry.
     */
    Promise<LogEntry> save(LogEntry logEntry);

    /**
     * Save multiple log entries in batch.
     */
    Promise<List<LogEntry>> saveBatch(List<LogEntry> logEntries);

    /**
     * Find a log entry by ID.
     */
    Promise<LogEntry> findById(String tenantId, UUID id);

    /**
     * Find log entries by project.
     */
    Promise<List<LogEntry>> findByProject(String tenantId, String projectId, int limit);

    /**
     * Find log entries by service.
     */
    Promise<List<LogEntry>> findByService(String tenantId, String service, int limit);

    /**
     * Find log entries by level.
     */
    Promise<List<LogEntry>> findByLevel(String tenantId, LogEntry.LogLevel level, int limit);

    /**
     * Find error log entries.
     */
    Promise<List<LogEntry>> findErrors(String tenantId, String projectId, int limit);

    /**
     * Find log entries by trace ID.
     */
    Promise<List<LogEntry>> findByTraceId(String tenantId, String traceId);

    /**
     * Find log entries by request ID.
     */
    Promise<List<LogEntry>> findByRequestId(String tenantId, String requestId);

    /**
     * Find log entries by user.
     */
    Promise<List<LogEntry>> findByUser(String tenantId, String userId, int limit);

    /**
     * Search log entries by message pattern.
     */
    Promise<List<LogEntry>> searchByMessage(String tenantId, String query, int limit);

    /**
     * Find log entries within a time range.
     */
    Promise<List<LogEntry>> findByTimeRange(
        String tenantId,
        String projectId,
        Instant start,
        Instant end,
        int limit
    );

    /**
     * Count log entries by level.
     */
    Promise<Long> countByLevel(String tenantId, String projectId, LogEntry.LogLevel level);

    /**
     * Delete log entries before a timestamp.
     */
    Promise<Integer> deleteBefore(String tenantId, Instant before);

    /**
     * Delete a log entry.
     */
    Promise<Void> delete(String tenantId, UUID id);
}
