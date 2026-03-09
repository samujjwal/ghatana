/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.Trace;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for Trace persistence operations.
 *
 * @doc.type interface
 * @doc.purpose Trace repository for CRUD operations
 * @doc.layer repository
 * @doc.pattern Repository
 */
public interface TraceRepository {

    /**
     * Save a trace.
     */
    Promise<Trace> save(Trace trace);

    /**
     * Find a trace by internal ID.
     */
    Promise<Trace> findById(String tenantId, UUID id);

    /**
     * Find a trace by trace ID.
     */
    Promise<Trace> findByTraceId(String tenantId, String traceId);

    /**
     * Find traces by project.
     */
    Promise<List<Trace>> findByProject(String tenantId, String projectId, int limit);

    /**
     * Find traces by service.
     */
    Promise<List<Trace>> findByService(String tenantId, String service, int limit);

    /**
     * Find traces by operation.
     */
    Promise<List<Trace>> findByOperation(String tenantId, String service, String operation, int limit);

    /**
     * Find traces by status.
     */
    Promise<List<Trace>> findByStatus(String tenantId, Trace.TraceStatus status, int limit);

    /**
     * Find error traces.
     */
    Promise<List<Trace>> findErrors(String tenantId, String projectId, int limit);

    /**
     * Find traces by user.
     */
    Promise<List<Trace>> findByUser(String tenantId, String userId, int limit);

    /**
     * Find traces by request ID.
     */
    Promise<List<Trace>> findByRequestId(String tenantId, String requestId);

    /**
     * Find slow traces (duration above threshold).
     */
    Promise<List<Trace>> findSlowTraces(String tenantId, String projectId, long minDurationMs, int limit);

    /**
     * Find traces within a time range.
     */
    Promise<List<Trace>> findByTimeRange(
        String tenantId,
        String projectId,
        Instant start,
        Instant end,
        int limit
    );

    /**
     * Find traces by tags.
     */
    Promise<List<Trace>> findByTag(String tenantId, String tagKey, String tagValue, int limit);

    /**
     * Count traces by status.
     */
    Promise<Long> countByStatus(String tenantId, String projectId, Trace.TraceStatus status);

    /**
     * Get average duration for traces.
     */
    Promise<Double> getAverageDuration(String tenantId, String projectId, String service);

    /**
     * Delete traces before a timestamp.
     */
    Promise<Integer> deleteBefore(String tenantId, Instant before);

    /**
     * Delete a trace.
     */
    Promise<Void> delete(String tenantId, UUID id);
}
