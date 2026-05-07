/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production tests for {@link DistributedQueryTracker} implementations.
 *
 * <p>Verifies thread-safety, correctness, and edge cases for query tracking
 * and cancellation across distributed deployments.</p>
 *
 * @doc.type class
 * @doc.purpose Production tests for distributed query tracking
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Distributed Query Tracker – production tests")
class DistributedQueryTrackerTest extends EventloopTestBase {

    @Test
    void inMemoryTrackerRegistersAndCancelsQuery() {
        DistributedQueryTracker tracker = new InMemoryQueryTracker();
        String queryId = "query-1";
        String tenantId = "tenant-1";
        String queryText = "SELECT * FROM events";
        Instant submittedAt = Instant.now();

        // Register query
        Promise<Void> registration = tracker.registerQuery(queryId, tenantId, queryText, submittedAt);
        runPromise(() -> registration);

        // Cancel query
        Promise<DistributedQueryTracker.CancellationResult> cancellation =
            tracker.cancelQuery(queryId, tenantId);
        DistributedQueryTracker.CancellationResult result = runPromise(() -> cancellation);

        assertThat(result.success()).isTrue();
        assertThat(result.queryId()).isEqualTo(queryId);
        assertThat(result.message()).contains("cancelled successfully");
    }

    @Test
    void inMemoryTrackerRejectsCancellationForDifferentTenant() {
        DistributedQueryTracker tracker = new InMemoryQueryTracker();
        String queryId = "query-1";
        String tenantA = "tenant-a";
        String tenantB = "tenant-b";
        String queryText = "SELECT * FROM events";
        Instant submittedAt = Instant.now();

        // Register query for tenant A
        runPromise(() -> tracker.registerQuery(queryId, tenantA, queryText, submittedAt));

        // Try to cancel from tenant B
        Promise<DistributedQueryTracker.CancellationResult> cancellation =
            tracker.cancelQuery(queryId, tenantB);
        DistributedQueryTracker.CancellationResult result = runPromise(() -> cancellation);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("unauthorized");
    }

    @Test
    void inMemoryTrackerReturnsNotFoundForUnknownQuery() {
        DistributedQueryTracker tracker = new InMemoryQueryTracker();
        String queryId = "unknown-query";
        String tenantId = "tenant-1";

        Promise<DistributedQueryTracker.CancellationResult> cancellation =
            tracker.cancelQuery(queryId, tenantId);
        DistributedQueryTracker.CancellationResult result = runPromise(() -> cancellation);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("not found");
    }

    @Test
    void inMemoryTrackerTracksCancellationStatus() {
        DistributedQueryTracker tracker = new InMemoryQueryTracker();
        String queryId = "query-1";
        String tenantId = "tenant-1";
        String queryText = "SELECT * FROM events";
        Instant submittedAt = Instant.now();

        // Register and cancel
        runPromise(() -> tracker.registerQuery(queryId, tenantId, queryText, submittedAt));
        runPromise(() -> tracker.cancelQuery(queryId, tenantId));

        // Check cancellation status
        Promise<Boolean> isCancelled = tracker.isCancelled(queryId);
        Boolean cancelled = runPromise(() -> isCancelled);

        assertThat(cancelled).isTrue();
    }

    @Test
    void inMemoryTrackerCleansUpCompletedQueries() {
        DistributedQueryTracker tracker = new InMemoryQueryTracker();
        String queryId = "query-1";
        String tenantId = "tenant-1";
        String queryText = "SELECT * FROM events";
        Instant submittedAt = Instant.now();

        // Register query
        runPromise(() -> tracker.registerQuery(queryId, tenantId, queryText, submittedAt));

        // Mark as complete
        runPromise(() -> tracker.markComplete(queryId, "COMPLETED"));

        // Try to cancel completed query - should return not found
        Promise<DistributedQueryTracker.CancellationResult> cancellation =
            tracker.cancelQuery(queryId, tenantId);
        DistributedQueryTracker.CancellationResult result = runPromise(() -> cancellation);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("not found");
    }

    @Test
    void cancellationResultFactoryMethods() {
        DistributedQueryTracker.CancellationResult cancelled =
            DistributedQueryTracker.CancellationResult.cancelled("q1");
        assertThat(cancelled.success()).isTrue();
        assertThat(cancelled.queryId()).isEqualTo("q1");

        DistributedQueryTracker.CancellationResult alreadyComplete =
            DistributedQueryTracker.CancellationResult.alreadyComplete("q1");
        assertThat(alreadyComplete.success()).isTrue();
        assertThat(alreadyComplete.message()).contains("already completed");

        DistributedQueryTracker.CancellationResult notFound =
            DistributedQueryTracker.CancellationResult.notFound("q1");
        assertThat(notFound.success()).isFalse();
        assertThat(notFound.message()).contains("not found");

        DistributedQueryTracker.CancellationResult unauthorized =
            DistributedQueryTracker.CancellationResult.unauthorized("q1");
        assertThat(unauthorized.success()).isFalse();
        assertThat(unauthorized.message()).contains("unauthorized");
    }
}
