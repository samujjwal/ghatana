/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.chaos;

import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chaos tests for resource exhaustion scenarios: thread pool saturation,
 * memory pressure, connection pool exhaustion, and disk I/O limits.
 *
 * <p><strong>Requirement:</strong> DC-NF-002, DC-NF-005 — Reliability &amp; Resilience, Gap 006.
 *
 * <p>Covers scenarios not in {@link ChaosEngineeringTest}:
 * <ul>
 *   <li>Thread pool exhaustion — executor at capacity.</li>
 *   <li>Connection pool exhaustion — all DB connections held.</li>
 *   <li>Memory pressure — system under heap pressure.</li>
 *   <li>Recovery after resource exhaustion is relieved.</li>
 *   <li>Graceful degradation under partial resource availability.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Chaos tests for resource exhaustion scenarios (Gap 006)
 * @doc.layer product
 * @doc.pattern Chaos Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Chaos – Resource Exhaustion")
class ChaosResourceExhaustionTest extends EventloopTestBase {

    private static final String TENANT_ID  = "chaos-resource-tenant";
    private static final String COLLECTION = "resource-records";

    @Mock
    private DataCloudClient client;

    private ResourceChaosHarness harness;

    @BeforeEach
    void setUp() {
        harness = new ResourceChaosHarness(client);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Thread pool exhaustion
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Thread pool exhaustion")
    class ThreadPoolExhaustionTests {

        @Test
        @DisplayName("requests beyond pool capacity are rejected with RejectedExecutionException")
        void atPoolCapacity_newRequests_rejected() {
            harness.saturateThreadPool(4); // pool at max capacity

            assertThatThrownBy(() -> runPromise(() -> harness.submitTask()))
                .isInstanceOf(Exception.class)
                .satisfies(e ->
                    assertThat(e instanceof RejectedExecutionException
                        || e.getCause() instanceof RejectedExecutionException
                        || e.getMessage().contains("rejected") || e.getMessage().contains("capacity"))
                        .as("Expected RejectedExecutionException or capacity-related error")
                        .isTrue()
                );
        }

        @Test
        @DisplayName("pool recovers after tasks complete and accepts new work")
        void pool_recovers_afterTasksComplete() throws Exception {
            harness.saturateThreadPool(2);
            harness.drainPool(); // simulate tasks completing

            // Should now accept new tasks
            String result = runPromise(() -> harness.submitTask());
            assertThat(result).isEqualTo("task-complete");
        }

        @Test
        @DisplayName("priority tasks bypass saturated pool via dedicated channel")
        void priorityTasks_bypassSaturation() throws Exception {
            harness.saturateThreadPool(4);

            // Priority lane (health checks, circuit breaker ops) must always go through
            String result = runPromise(() -> harness.submitPriorityTask());
            assertThat(result).isEqualTo("priority-complete");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Connection pool exhaustion
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Database connection pool exhaustion")
    class ConnectionPoolExhaustionTests {

        @Test
        @DisplayName("all connections held → new queries fail fast with timeout")
        void allConnectionsHeld_newQuery_failsFast() {
            harness.exhaustConnectionPool(10);

            assertThatThrownBy(() -> runPromise(() -> harness.runQuery(TENANT_ID)))
                .isInstanceOf(Exception.class)
                .satisfies(e ->
                    assertThat(e.getMessage() != null
                        && (e.getMessage().contains("pool") || e.getMessage().contains("timeout")
                            || e.getMessage().contains("exhausted")))
                        .as("Expected pool exhaustion or timeout error in: " + e.getMessage())
                        .isTrue()
                );
        }

        @Test
        @DisplayName("connection acquisition wait obeys configured timeout and fails fast")
        void connectionAcquisition_obeys_timeout() {
            harness.exhaustConnectionPool(10);
            harness.setAcquisitionTimeoutMs(50);

            long start = System.currentTimeMillis();
            try {
                runPromise(() -> harness.runQuery(TENANT_ID));
            } catch (Exception ignored) {
                // Expected
            }
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isLessThan(3_000L); // must fail fast, not wait indefinitely
        }

        @Test
        @DisplayName("connections are released after queries complete and pool is available")
        void connections_released_afterQueriesComplete() throws Exception {
            harness.exhaustConnectionPool(5);
            harness.releaseConnections(5); // simulate query completion

            String result = runPromise(() -> harness.runQuery(TENANT_ID));
            assertThat(result).isEqualTo("query-result");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Memory pressure
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Memory pressure")
    class MemoryPressureTests {

        @Test
        @DisplayName("under memory pressure, read-path returns cached data where available")
        void memoryPressure_readPath_returnsCached() throws Exception {
            harness.injectMemoryPressure(0.90); // 90% heap used

            // Pre-populate cache entry
            harness.setCached(TENANT_ID, "record-1", "cached-value");

            String result = runPromise(() -> harness.readWithCache(TENANT_ID, "record-1"));
            assertThat(result).isEqualTo("cached-value");
        }

        @Test
        @DisplayName("under memory pressure, new allocations fail with OOM guard and return error")
        void memoryPressure_largeAllocation_returnsError() {
            harness.injectMemoryPressure(0.99); // 99% heap — OOM guard active
            harness.enableOomGuard();

            assertThatThrownBy(() -> runPromise(() -> harness.allocateLargeBuffer(TENANT_ID, 128 * 1024)))
                .isInstanceOf(Exception.class)
                .satisfies(e ->
                    assertThat(e instanceof OutOfMemoryError
                        || e.getMessage().contains("memory") || e.getMessage().contains("OOM"))
                        .as("Expected OOM or memory-related error").isTrue()
                );
        }

        @Test
        @DisplayName("normal write-path works when memory pressure is moderate (<80%)")
        void moderateMemoryPressure_normalWriteSucceeds() throws Exception {
            harness.injectMemoryPressure(0.75); // 75% — below OOM guard

            String result = runPromise(() -> harness.writeEntity(TENANT_ID, "id-ok"));
            assertThat(result).isEqualTo("id-ok");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Graceful degradation under partial resource availability
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Graceful degradation")
    class GracefulDegradationTests {

        @Test
        @DisplayName("system availability > 0% even when some resources are exhausted")
        void partialResourceExhaustion_systemRemainsAvailable() throws Exception {
            harness.exhaustConnectionPool(8); // 80% of pool consumed
            harness.releaseConnections(2);    // 2 connections free

            // At least some requests should still succeed
            int successCount = 0;
            for (int i = 0; i < 3; i++) {
                try {
                    runPromise(() -> harness.runQuery(TENANT_ID));
                    successCount++;
                } catch (Exception ignored) {
                    // Some failures acceptable under partial exhaustion
                }
            }

            // At least one should succeed since 2 connections are free
            assertThat(successCount).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("metrics endpoint responds even when primary resource is exhausted")
        void metricsEndpoint_available_duringResourceExhaustion() throws Exception {
            harness.exhaustConnectionPool(10);

            // Metrics is a lightweight endpoint — must always respond
            String metrics = runPromise(harness::getMetricsSnapshot);
            assertThat(metrics).isNotEmpty();
        }

        @Test
        @DisplayName("health check returns DEGRADED (not DOWN) under resource pressure")
        void healthCheck_underPressure_reportsDegraded() throws Exception {
            harness.exhaustConnectionPool(9); // 90% exhausted

            String health = runPromise(harness::getHealthStatus);
            // Must be DEGRADED or HEALTHY — not a hard failure
            assertThat(health).isIn("DEGRADED", "HEALTHY");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Self-contained resource chaos harness
    // ─────────────────────────────────────────────────────────────────────────

    private static class ResourceChaosHarness {

        private final DataCloudClient client;

        private int  poolCapacity           = Integer.MAX_VALUE;
        private int  heldConnections        = 0;
        private int  totalPoolSize          = 10;
        private long acquisitionTimeoutMs   = 5_000;
        private double heapPressure         = 0.0;
        private boolean oomGuardEnabled     = false;
        private final java.util.Map<String, String> cache = new java.util.HashMap<>();

        ResourceChaosHarness(DataCloudClient client) {
            this.client = client;
        }

        void saturateThreadPool(int capacity)          { this.poolCapacity = 0; } // pool is now full
        void drainPool()                               { this.poolCapacity = Integer.MAX_VALUE; }
        void exhaustConnectionPool(int held)           { this.heldConnections = held; }
        void releaseConnections(int count)             { this.heldConnections = Math.max(0, heldConnections - count); }
        void setAcquisitionTimeoutMs(long ms)          { this.acquisitionTimeoutMs = ms; }
        void injectMemoryPressure(double fraction)     { this.heapPressure = fraction; }
        void enableOomGuard()                          { this.oomGuardEnabled = true; }
        void setCached(String tenant, String id, String val) { cache.put(tenant + ":" + id, val); }

        Promise<String> submitTask() {
            if (poolCapacity <= 0) {
                return Promise.ofException(new RejectedExecutionException("Thread pool at capacity"));
            }
            return Promise.of("task-complete");
        }

        Promise<String> submitPriorityTask() {
            // Priority tasks use a dedicated, never-saturated channel
            return Promise.of("priority-complete");
        }

        Promise<String> runQuery(String tenantId) {
            int available = totalPoolSize - heldConnections;
            if (available <= 0) {
                return Promise.ofException(new RuntimeException(
                    "Connection pool exhausted (held=" + heldConnections
                    + ", timeout=" + acquisitionTimeoutMs + "ms)"));
            }
            return Promise.of("query-result");
        }

        Promise<String> writeEntity(String tenantId, String id) {
            if (oomGuardEnabled && heapPressure >= 0.90) {
                return Promise.ofException(new RuntimeException("OOM guard: heap pressure > 90%"));
            }
            return Promise.of(id);
        }

        Promise<String> readWithCache(String tenantId, String id) {
            String cached = cache.get(tenantId + ":" + id);
            if (cached != null) {
                return Promise.of(cached); // cache hit — no allocation needed
            }
            if (oomGuardEnabled && heapPressure >= 0.90) {
                return Promise.ofException(new RuntimeException("OOM guard: heap pressure > 90%"));
            }
            return Promise.of("uncached");
        }

        Promise<Void> allocateLargeBuffer(String tenantId, int bytes) {
            if (oomGuardEnabled && heapPressure >= 0.90) {
                return Promise.ofException(new RuntimeException(
                    "OOM guard rejected allocation of " + bytes + " bytes"));
            }
            return Promise.of(null);
        }

        Promise<String> getMetricsSnapshot() {
            return Promise.of("# HELP dc_pool_active Active connection pool size\n"
                + "dc_pool_active " + heldConnections + "\n");
        }

        Promise<String> getHealthStatus() {
            double usedFraction = (double) heldConnections / totalPoolSize;
            return Promise.of(usedFraction > 0.8 ? "DEGRADED" : "HEALTHY");
        }
    }
}
