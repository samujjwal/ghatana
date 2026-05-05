/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import com.ghatana.platform.observability.MetricsCollector;

import java.util.Map;
import java.util.Objects;

/**
 * Observability facade for analytics query metrics.
 *
 * <p>Provides domain-specific metrics methods for analytics query operations across
 * all query types (SELECT, AGGREGATE, TIMESERIES, JOIN). All analytics metrics
 * follow the {@code analytics.query.*} namespace convention.</p>
 *
 * <h2>Metric Categories</h2>
 * <ul>
 *   <li><b>Submission metrics:</b> analytics.query.submitted, analytics.query.submitted.duration</li>
 *   <li><b>Execution metrics:</b> analytics.query.executed, analytics.query.execution.duration</li>
 *   <li><b>Completion metrics:</b> analytics.query.completed, analytics.query.failed, analytics.query.cancelled</li>
 *   <li><b>Cache metrics:</b> analytics.query.cache.hit, analytics.query.cache.miss</li>
 *   <li><b>Resource metrics:</b> analytics.query.rows.returned, analytics.query.cost.estimated</li>
 * </ul>
 *
 * <h2>Standard Tags</h2>
 * <ul>
 *   <li>{@code query_type} — SELECT, AGGREGATE, TIMESERIES, JOIN</li>
 *   <li>{@code tenant_id} — Tenant for multi-tenant isolation</li>
 *   <li>{@code status} — success, failure, cancelled</li>
 *   <li>{@code data_source} — Collection or data source name</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Observability facade for analytics query metrics
 * @doc.layer product
 * @doc.pattern Facade
 */
public class AnalyticsMetrics {

    // ════════════════════════════════════════════════════════════════
    // Metric name constants
    // ════════════════════════════════════════════════════════════════

    public static final String QUERY_SUBMITTED = "analytics.query.submitted";
    public static final String QUERY_SUBMITTED_DURATION_MS = "analytics.query.submitted.duration";
    public static final String QUERY_EXECUTED = "analytics.query.executed";
    public static final String QUERY_EXECUTION_DURATION_MS = "analytics.query.execution.duration";
    public static final String QUERY_COMPLETED = "analytics.query.completed";
    public static final String QUERY_FAILED = "analytics.query.failed";
    public static final String QUERY_CANCELLED = "analytics.query.cancelled";
    public static final String QUERY_CACHE_HIT = "analytics.query.cache.hit";
    public static final String QUERY_CACHE_MISS = "analytics.query.cache.miss";
    public static final String QUERY_ROWS_RETURNED = "analytics.query.rows.returned";
    public static final String QUERY_COST_ESTIMATED = "analytics.query.cost.estimated";
    public static final String QUERY_ACTIVE = "analytics.query.active";

    // ════════════════════════════════════════════════════════════════
    // Tag key constants
    // ════════════════════════════════════════════════════════════════

    public static final String TAG_QUERY_TYPE = "query_type";
    public static final String TAG_TENANT_ID = "tenant_id";
    public static final String TAG_STATUS = "status";
    public static final String TAG_DATA_SOURCE = "data_source";
    public static final String TAG_ERROR_TYPE = "error_type";
    public static final String TAG_QUERY_ID = "query_id";

    // ════════════════════════════════════════════════════════════════
    // Fields
    // ════════════════════════════════════════════════════════════════

    private final MetricsCollector collector;

    /**
     * Creates an AnalyticsMetrics facade wrapping the given MetricsCollector.
     *
     * @param collector the underlying metrics collector (must not be null)
     */
    public AnalyticsMetrics(MetricsCollector collector) {
        this.collector = Objects.requireNonNull(collector, "MetricsCollector must not be null");
    }

    // ════════════════════════════════════════════════════════════════
    // Submission metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records a query submission event.
     *
     * @param queryType the type of query (e.g., "SELECT", "AGGREGATE")
     * @param tenantId  the tenant ID
     * @param queryId   the query ID
     */
    public void recordQuerySubmitted(String queryType, String tenantId, String queryId) {
        collector.incrementCounter(QUERY_SUBMITTED,
                TAG_QUERY_TYPE, queryType,
                TAG_TENANT_ID, tenantId,
                TAG_QUERY_ID, queryId);
        collector.incrementCounter(QUERY_ACTIVE,
                TAG_TENANT_ID, tenantId);
    }

    /**
     * Records query submission duration.
     *
     * @param queryType   the type of query
     * @param tenantId    the tenant ID
     * @param durationMs submission duration in milliseconds
     */
    public void recordSubmissionDuration(String queryType, String tenantId, long durationMs) {
        collector.recordTimer(QUERY_SUBMITTED_DURATION_MS, durationMs,
                TAG_QUERY_TYPE, queryType,
                TAG_TENANT_ID, tenantId);
    }

    // ════════════════════════════════════════════════════════════════
    // Execution metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records a query execution event.
     *
     * @param queryType  the type of query
     * @param tenantId   the tenant ID
     * @param dataSource the data source name
     */
    public void recordQueryExecuted(String queryType, String tenantId, String dataSource) {
        collector.incrementCounter(QUERY_EXECUTED,
                TAG_QUERY_TYPE, queryType,
                TAG_TENANT_ID, tenantId,
                TAG_DATA_SOURCE, dataSource);
    }

    /**
     * Records query execution duration.
     *
     * @param queryType   the type of query
     * @param tenantId    the tenant ID
     * @param dataSource the data source name
     * @param durationMs  execution duration in milliseconds
     */
    public void recordExecutionDuration(String queryType, String tenantId, String dataSource, long durationMs) {
        collector.recordTimer(QUERY_EXECUTION_DURATION_MS, durationMs,
                TAG_QUERY_TYPE, queryType,
                TAG_TENANT_ID, tenantId,
                TAG_DATA_SOURCE, dataSource);
    }

    // ════════════════════════════════════════════════════════════════
    // Completion metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records a successful query completion.
     *
     * @param queryType the type of query
     * @param tenantId  the tenant ID
     * @param rowCount  the number of rows returned
     */
    public void recordQueryCompleted(String queryType, String tenantId, int rowCount) {
        collector.incrementCounter(QUERY_COMPLETED,
                TAG_QUERY_TYPE, queryType,
                TAG_TENANT_ID, tenantId,
                TAG_STATUS, "success");
        collector.increment(QUERY_ROWS_RETURNED, rowCount,
                Map.of(TAG_QUERY_TYPE, queryType, TAG_TENANT_ID, tenantId));
        collector.increment(QUERY_ACTIVE, -1.0,
                Map.of(TAG_TENANT_ID, tenantId, TAG_STATUS, "completed")); // Decrement active count
    }

    /**
     * Records a failed query.
     *
     * @param queryType the type of query
     * @param tenantId  the tenant ID
     * @param error     the exception that caused failure
     */
    public void recordQueryFailed(String queryType, String tenantId, Exception error) {
        collector.incrementCounter(QUERY_FAILED,
                TAG_QUERY_TYPE, queryType,
                TAG_TENANT_ID, tenantId,
                TAG_STATUS, "failure",
                TAG_ERROR_TYPE, error.getClass().getSimpleName());
        collector.recordError(QUERY_FAILED, error,
                Map.of(TAG_QUERY_TYPE, queryType, TAG_TENANT_ID, tenantId));
        collector.increment(QUERY_ACTIVE, -1.0,
                Map.of(TAG_TENANT_ID, tenantId, TAG_STATUS, "failed")); // Decrement active count
    }

    /**
     * Records a cancelled query.
     *
     * @param queryType the type of query
     * @param tenantId  the tenant ID
     */
    public void recordQueryCancelled(String queryType, String tenantId) {
        collector.incrementCounter(QUERY_CANCELLED,
                TAG_QUERY_TYPE, queryType,
                TAG_TENANT_ID, tenantId,
                TAG_STATUS, "cancelled");
        collector.increment(QUERY_ACTIVE, -1.0,
                Map.of(TAG_TENANT_ID, tenantId, TAG_STATUS, "cancelled")); // Decrement active count
    }

    // ════════════════════════════════════════════════════════════════
    // Cache metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records a cache hit.
     *
     * @param queryType the type of query
     * @param tenantId  the tenant ID
     */
    public void recordCacheHit(String queryType, String tenantId) {
        collector.incrementCounter(QUERY_CACHE_HIT,
                TAG_QUERY_TYPE, queryType,
                TAG_TENANT_ID, tenantId);
    }

    /**
     * Records a cache miss.
     *
     * @param queryType the type of query
     * @param tenantId  the tenant ID
     */
    public void recordCacheMiss(String queryType, String tenantId) {
        collector.incrementCounter(QUERY_CACHE_MISS,
                TAG_QUERY_TYPE, queryType,
                TAG_TENANT_ID, tenantId);
    }

    // ════════════════════════════════════════════════════════════════
    // Resource metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records estimated query cost.
     *
     * @param queryType the type of query
     * @param tenantId  the tenant ID
     * @param cost      the estimated cost
     */
    public void recordEstimatedCost(String queryType, String tenantId, double cost) {
        collector.recordConfidenceScore(QUERY_COST_ESTIMATED, cost);
    }

    /**
     * Returns the underlying MetricsCollector.
     *
     * @return the metrics collector
     */
    public MetricsCollector getCollector() {
        return collector;
    }
}
