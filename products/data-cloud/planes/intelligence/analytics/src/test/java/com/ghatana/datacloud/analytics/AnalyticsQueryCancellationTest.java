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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Production tests for analytics query cancellation.
 *
 * <p>Verifies that queries can be cancelled via the distributed query tracker
 * and that cancelled queries do not return results.</p>
 *
 * @doc.type class
 * @doc.purpose Production tests for query cancellation
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Analytics Query Cancellation – production tests")
class AnalyticsQueryCancellationTest extends EventloopTestBase {

    @Test
    void engineCancelsRunningQuery() {
        DistributedQueryTracker tracker = new InMemoryQueryTracker();
        AnalyticsQueryEngine engine = new AnalyticsQueryEngine(null, tracker);
        String tenantId = "tenant-1";
        String queryText = "SELECT * FROM events";

        // Submit query
        QueryResult submitted = runPromise(() -> engine.submitQuery(tenantId, queryText, Map.of()));
        String queryId = submitted.getQueryId();

        // Cancel query
        Promise<DistributedQueryTracker.CancellationResult> cancelPromise =
            engine.cancelQuery(queryId, tenantId);
        DistributedQueryTracker.CancellationResult result = runPromise(() -> cancelPromise);

        assertThat(result.queryId()).isEqualTo(queryId);
        assertThat(result.message()).isNotBlank();
    }

    @Test
    void engineRejectsCancellationForDifferentTenant() {
        DistributedQueryTracker tracker = new InMemoryQueryTracker();
        AnalyticsQueryEngine engine = new AnalyticsQueryEngine(null, tracker);
        String tenantA = "tenant-a";
        String tenantB = "tenant-b";
        String queryText = "SELECT * FROM events";

        // Submit query for tenant A
        QueryResult submitted = runPromise(() -> engine.submitQuery(tenantA, queryText, Map.of()));
        String queryId = submitted.getQueryId();

        // Try to cancel from tenant B
        Promise<DistributedQueryTracker.CancellationResult> cancelPromise =
            engine.cancelQuery(queryId, tenantB);
        DistributedQueryTracker.CancellationResult result = runPromise(() -> cancelPromise);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).containsAnyOf("unauthorized", "not found", "already cleaned up");
    }

    @Test
    void engineChecksCancellationBeforeExecution() {
        DistributedQueryTracker tracker = new InMemoryQueryTracker();
        AnalyticsQueryEngine engine = new AnalyticsQueryEngine(null, tracker);
        String tenantId = "tenant-1";
        String queryText = "SELECT * FROM events";

        // Register query directly with tracker (simulate cancellation before execution)
        String queryId = "pre-cancelled-query";
        runPromise(() -> tracker.registerQuery(queryId, tenantId, queryText, Instant.now()));
        runPromise(() -> tracker.cancelQuery(queryId, tenantId));

        // Verify cancellation status
        Promise<Boolean> isCancelled = engine.isQueryCancelled(queryId);
        Boolean cancelled = runPromise(() -> isCancelled);

        assertThat(cancelled).isTrue();
    }

    @Test
    void engineHandlesCancellationWhenNoTracker() {
        // Engine with null tracker should use InMemoryQueryTracker by default
        AnalyticsQueryEngine engine = new AnalyticsQueryEngine(null, null);
        String tenantId = "tenant-1";
        String queryText = "SELECT * FROM events";

        // Submit query
        QueryResult submitted = runPromise(() -> engine.submitQuery(tenantId, queryText, Map.of()));
        String queryId = submitted.getQueryId();

        // Cancel should still work with default tracker
        Promise<DistributedQueryTracker.CancellationResult> cancelPromise =
            engine.cancelQuery(queryId, tenantId);
        DistributedQueryTracker.CancellationResult result = runPromise(() -> cancelPromise);

        assertThat(result.queryId()).isEqualTo(queryId);
        assertThat(result.message()).isNotBlank();
    }

    @Test
    void engineValidatesNullQueryId() {
        DistributedQueryTracker tracker = new InMemoryQueryTracker();
        AnalyticsQueryEngine engine = new AnalyticsQueryEngine(null, tracker);
        String tenantId = "tenant-1";

        assertThatThrownBy(() -> engine.cancelQuery(null, tenantId))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("queryId cannot be null");

        assertThatThrownBy(() -> engine.isQueryCancelled(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("queryId cannot be null");
    }

    @Test
    void engineValidatesNullTenantId() {
        DistributedQueryTracker tracker = new InMemoryQueryTracker();
        AnalyticsQueryEngine engine = new AnalyticsQueryEngine(null, tracker);
        String queryId = "query-1";

        assertThatThrownBy(() -> engine.cancelQuery(queryId, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tenantId cannot be null");
    }
}
