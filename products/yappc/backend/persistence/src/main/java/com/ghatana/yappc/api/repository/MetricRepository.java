/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.Metric;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repository interface for Metric persistence operations.
 *
 * @doc.type interface
 * @doc.purpose Metric repository for CRUD operations
 * @doc.layer repository
 * @doc.pattern Repository
 */
public interface MetricRepository {

    /**
     * Save a metric.
     */
    Promise<Metric> save(Metric metric);

    /**
     * Find a metric by ID.
     */
    Promise<Metric> findById(String tenantId, UUID id);

    /**
     * Find metrics by project.
     */
    Promise<List<Metric>> findByProject(String tenantId, String projectId);

    /**
     * Find metrics by name.
     */
    Promise<List<Metric>> findByName(String tenantId, String name);

    /**
     * Find metrics by name pattern.
     */
    Promise<List<Metric>> findByNamePattern(String tenantId, String namePattern);

    /**
     * Find metrics by type.
     */
    Promise<List<Metric>> findByType(String tenantId, Metric.MetricType type);

    /**
     * Find metrics by tags.
     */
    Promise<List<Metric>> findByTags(String tenantId, Map<String, String> tags);

    /**
     * Add data points to a metric.
     */
    Promise<Metric> addDataPoints(String tenantId, UUID metricId, List<Metric.DataPoint> dataPoints);

    /**
     * Query data points within a time range.
     */
    Promise<List<Metric.DataPoint>> queryDataPoints(
        String tenantId,
        UUID metricId,
        Instant start,
        Instant end
    );

    /**
     * Delete a metric.
     */
    Promise<Void> delete(String tenantId, UUID id);

    /**
     * Delete old data points.
     */
    Promise<Integer> deleteDataPointsBefore(String tenantId, Instant before);

    /**
     * Check if a metric exists.
     */
    Promise<Boolean> exists(String tenantId, UUID id);
}
