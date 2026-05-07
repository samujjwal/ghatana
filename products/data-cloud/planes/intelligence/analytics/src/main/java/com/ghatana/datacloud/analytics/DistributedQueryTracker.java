/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Distributed query tracker for analytics query cancellation.
 *
 * <p>This interface provides the contract for tracking running analytics queries
 * across distributed deployments and supporting cancellation operations. Implementations
 * must be thread-safe and support concurrent access from multiple nodes.</p>
 *
 * <p><b>Required Operations:</b></p>
 * <ul>
 *   <li>Register a query when it starts execution</li>
 *   <li>Cancel a running query by ID</li>
 *   <li>Query the cancellation status of a query</li>
 *   <li>Cleanup completed/failed queries</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Distributed query tracking and cancellation
 * @doc.layer core
 * @doc.pattern Repository, Tracker
 */
public interface DistributedQueryTracker {

    /**
     * Registers a query as running in the distributed tracker.
     *
     * @param queryId unique query identifier
     * @param tenantId tenant identifier for isolation
     * @param queryText the query text (for debugging/auditing)
     * @param submittedAt when the query was submitted
     * @return promise completing when registration is successful
     */
    Promise<Void> registerQuery(String queryId, String tenantId, String queryText, Instant submittedAt);

    /**
     * Cancels a running query by ID.
     *
     * <p>If the query is not found or already completed, this operation
     * should return success without error (idempotent).</p>
     *
     * @param queryId unique query identifier
     * @param tenantId tenant identifier for authorization
     * @return promise completing with cancellation status
     */
    Promise<CancellationResult> cancelQuery(String queryId, String tenantId);

    /**
     * Checks whether a query has been cancelled.
     *
     * @param queryId unique query identifier
     * @return promise completing with cancellation status
     */
    Promise<Boolean> isCancelled(String queryId);

    /**
     * Marks a query as completed or failed for cleanup.
     *
     * @param queryId unique query identifier
     * @param status final status (COMPLETED, FAILED, CANCELLED)
     * @return promise completing when cleanup is successful
     */
    Promise<Void> markComplete(String queryId, String status);

    /**
     * Result of a cancellation attempt.
     */
    record CancellationResult(
        boolean success,
        String queryId,
        String message,
        Instant cancelledAt
    ) {
        public static CancellationResult cancelled(String queryId) {
            return new CancellationResult(true, queryId, "Query cancelled successfully", Instant.now());
        }

        public static CancellationResult alreadyComplete(String queryId) {
            return new CancellationResult(true, queryId, "Query already completed", Instant.now());
        }

        public static CancellationResult notFound(String queryId) {
            return new CancellationResult(false, queryId, "Query not found or already cleaned up", Instant.now());
        }

        public static CancellationResult unauthorized(String queryId) {
            return new CancellationResult(false, queryId, "Tenant mismatch - unauthorized", Instant.now());
        }
    }
}

/**
 * In-memory implementation of DistributedQueryTracker for single-process deployments.
 *
 * <p>This implementation uses ConcurrentHashMap for thread-safe in-memory tracking.
 * It is suitable for development and single-process production deployments. For
 * distributed deployments, use a database-backed implementation.</p>
 *
 * @doc.type class
 * @doc.purpose In-memory query tracking for single-process deployments
 * @doc.layer core
 * @doc.pattern Tracker
 */
class InMemoryQueryTracker implements DistributedQueryTracker {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryQueryTracker.class);

    private static final int CLEANUP_INTERVAL_SECONDS = 300; // 5 minutes
    private static final int QUERY_RETENTION_SECONDS = 3600; // 1 hour

    private final Map<String, QueryInfo> activeQueries = new ConcurrentHashMap<>();
    private final Set<String> cancelledQueries = ConcurrentHashMap.newKeySet();
    private final MetricsCollector metrics;
    private final Tracer tracer;

    private static record QueryInfo(
        String queryId,
        String tenantId,
        String queryText,
        Instant submittedAt,
        Instant registeredAt
    ) {}

    public InMemoryQueryTracker() {
        this(null, null);
    }

    public InMemoryQueryTracker(MetricsCollector metrics, Tracer tracer) {
        this.metrics = metrics != null ? metrics : MetricsCollector.create();
        this.tracer = tracer != null ? tracer : io.opentelemetry.api.OpenTelemetry.noop().getTracer("query-tracker");
        // Start cleanup thread
        Thread cleanupThread = new Thread(this::cleanupLoop, "query-tracker-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    @Override
    public Promise<Void> registerQuery(String queryId, String tenantId, String queryText, Instant submittedAt) {
        MDC.put("queryId", queryId);
        MDC.put("tenantId", tenantId);
        
        Span span = tracer.spanBuilder("query.tracker.register")
            .setAttribute("query_id", queryId)
            .setAttribute("tenant_id", tenantId)
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            QueryInfo info = new QueryInfo(queryId, tenantId, queryText, submittedAt, Instant.now());
            activeQueries.put(queryId, info);
            metrics.incrementCounter("analytics.query.tracker.registered",
                "tenant_id", tenantId);
            logger.debug("Registered query: queryId={}, tenantId={}", queryId, tenantId);
            span.setStatus(StatusCode.OK);
            return Promise.of(null);
        } catch (Exception e) {
            logger.error("Failed to register query: queryId={}", queryId, e);
            Map<String, String> errorTags = new HashMap<>();
            errorTags.put("tenant_id", tenantId);
            metrics.recordError("analytics.query.tracker.register_failed", e, errorTags);
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            return Promise.ofException(e);
        } finally {
            span.end();
            MDC.remove("queryId");
            MDC.remove("tenantId");
        }
    }

    @Override
    public Promise<DistributedQueryTracker.CancellationResult> cancelQuery(String queryId, String tenantId) {
        MDC.put("queryId", queryId);
        MDC.put("tenantId", tenantId);
        
        Span span = tracer.spanBuilder("query.tracker.cancel")
            .setAttribute("query_id", queryId)
            .setAttribute("tenant_id", tenantId)
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            QueryInfo info = activeQueries.get(queryId);
            if (info == null) {
                logger.debug("Cancel requested for unknown query: queryId={}", queryId);
                metrics.incrementCounter("analytics.query.tracker.cancel.not_found",
                    "tenant_id", tenantId);
                span.setStatus(StatusCode.ERROR, "NOT_FOUND");
                return Promise.of(DistributedQueryTracker.CancellationResult.notFound(queryId));
            }

            if (!info.tenantId().equals(tenantId)) {
                logger.warn("Cancel requested with tenant mismatch for query: queryId={} (expected: {}, actual: {})",
                    queryId, info.tenantId(), tenantId);
                metrics.incrementCounter("analytics.query.tracker.cancel.unauthorized",
                    "tenant_id", tenantId);
                span.setStatus(StatusCode.ERROR, "UNAUTHENTICATED");
                return Promise.of(DistributedQueryTracker.CancellationResult.unauthorized(queryId));
            }

            cancelledQueries.add(queryId);
            metrics.incrementCounter("analytics.query.tracker.cancelled",
                "tenant_id", tenantId);
            logger.info("Cancelled query: queryId={}, tenantId={}", queryId, tenantId);
            span.setStatus(StatusCode.OK);
            return Promise.of(DistributedQueryTracker.CancellationResult.cancelled(queryId));
        } catch (Exception e) {
            logger.error("Failed to cancel query: queryId={}", queryId, e);
            Map<String, String> cancelErrorTags = new HashMap<>();
            cancelErrorTags.put("tenant_id", tenantId);
            metrics.recordError("analytics.query.tracker.cancel_failed", e, cancelErrorTags);
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            return Promise.ofException(e);
        } finally {
            span.end();
            MDC.remove("queryId");
            MDC.remove("tenantId");
        }
    }

    @Override
    public Promise<Boolean> isCancelled(String queryId) {
        MDC.put("queryId", queryId);
        
        Span span = tracer.spanBuilder("query.tracker.check_cancelled")
            .setAttribute("query_id", queryId)
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            boolean cancelled = cancelledQueries.contains(queryId);
            span.setAttribute("cancelled", cancelled);
            span.setStatus(StatusCode.OK);
            return Promise.of(cancelled);
        } catch (Exception e) {
            logger.error("Failed to check cancellation status for query: queryId={}", queryId, e);
            metrics.recordError("analytics.query.tracker.check_cancelled_failed", e, Map.of());
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            return Promise.ofException(e);
        } finally {
            span.end();
            MDC.remove("queryId");
        }
    }

    @Override
    public Promise<Void> markComplete(String queryId, String status) {
        MDC.put("queryId", queryId);
        MDC.put("status", status);
        
        Span span = tracer.spanBuilder("query.tracker.mark_complete")
            .setAttribute("query_id", queryId)
            .setAttribute("status", status)
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            activeQueries.remove(queryId);
            cancelledQueries.remove(queryId);
            metrics.incrementCounter("analytics.query.tracker.completed",
                "status", status);
            logger.debug("Marked query complete: queryId={}, status={}", queryId, status);
            span.setStatus(StatusCode.OK);
            return Promise.of(null);
        } catch (Exception e) {
            logger.error("Failed to mark query complete: queryId={}", queryId, e);
            Map<String, String> markCompleteErrorTags = new HashMap<>();
            markCompleteErrorTags.put("status", status);
            metrics.recordError("analytics.query.tracker.mark_complete_failed", e, markCompleteErrorTags);
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            return Promise.ofException(e);
        } finally {
            span.end();
            MDC.remove("queryId");
            MDC.remove("status");
        }
    }

    private void cleanupLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(CLEANUP_INTERVAL_SECONDS * 1000L);
                cleanupStaleQueries();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void cleanupStaleQueries() {
        Instant cutoff = Instant.now().minusSeconds(QUERY_RETENTION_SECONDS);
        AtomicInteger removedCount = new AtomicInteger(0);
        Instant beforeCleanup = Instant.now();

        activeQueries.entrySet().removeIf(entry -> {
            boolean stale = entry.getValue().registeredAt().isBefore(cutoff);
            if (stale) {
                cancelledQueries.remove(entry.getKey());
                logger.debug("Cleaned up stale query: queryId={}", entry.getKey());
                removedCount.incrementAndGet();
            }
            return stale;
        });

        int finalRemovedCount = removedCount.get();
        if (finalRemovedCount > 0) {
            metrics.incrementCounter("analytics.query.tracker.cleanup",
                "count", String.valueOf(finalRemovedCount));
            logger.info("Cleaned up {} stale queries in {}ms", finalRemovedCount,
                java.time.Duration.between(beforeCleanup, Instant.now()).toMillis());
        }
    }
}
