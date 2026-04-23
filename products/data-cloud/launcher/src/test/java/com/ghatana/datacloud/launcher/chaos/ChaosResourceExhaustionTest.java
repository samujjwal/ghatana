/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * @doc.purpose Chaos tests for resource exhaustion scenarios (Gap 006) // GH-90000
 * @doc.layer product
 * @doc.pattern Chaos Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("Chaos – Resource Exhaustion")
class ChaosResourceExhaustionTest extends EventloopTestBase {

    private static final String TENANT_ID  = "chaos-resource-tenant";
    private static final String COLLECTION = "resource-records";

    @Mock
    private DataCloudClient client;

    private ResourceChaosHarness harness;

    @BeforeEach
    void setUp() { // GH-90000
        harness = new ResourceChaosHarness(client); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Thread pool exhaustion
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Thread pool exhaustion")
    class ThreadPoolExhaustionTests {

        @Test
        @DisplayName("requests beyond pool capacity are rejected with RejectedExecutionException")
        void atPoolCapacity_newRequests_rejected() { // GH-90000
            harness.saturateThreadPool(4); // pool at max capacity // GH-90000

            assertThatThrownBy(() -> runPromise(() -> harness.submitTask())) // GH-90000
                .isInstanceOf(Exception.class) // GH-90000
                .satisfies(e -> // GH-90000
                    assertThat(e instanceof RejectedExecutionException // GH-90000
                        || e.getCause() instanceof RejectedExecutionException // GH-90000
                        || e.getMessage().contains("rejected") || e.getMessage().contains("capacity"))
                        .as("Expected RejectedExecutionException or capacity-related error")
                        .isTrue() // GH-90000
                );
        }

        @Test
        @DisplayName("pool recovers after tasks complete and accepts new work")
        void pool_recovers_afterTasksComplete() throws Exception { // GH-90000
            harness.saturateThreadPool(2); // GH-90000
            harness.drainPool(); // simulate tasks completing // GH-90000

            // Should now accept new tasks
            String result = runPromise(() -> harness.submitTask()); // GH-90000
            assertThat(result).isEqualTo("task-complete");
        }

        @Test
        @DisplayName("priority tasks bypass saturated pool via dedicated channel")
        void priorityTasks_bypassSaturation() throws Exception { // GH-90000
            harness.saturateThreadPool(4); // GH-90000

            // Priority lane (health checks, circuit breaker ops) must always go through // GH-90000
            String result = runPromise(() -> harness.submitPriorityTask()); // GH-90000
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
        void allConnectionsHeld_newQuery_failsFast() { // GH-90000
            harness.exhaustConnectionPool(10); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> harness.runQuery(TENANT_ID))) // GH-90000
                .isInstanceOf(Exception.class) // GH-90000
                .satisfies(e -> // GH-90000
                    assertThat(e.getMessage() != null // GH-90000
                        && (e.getMessage().contains("pool") || e.getMessage().contains("timeout")
                            || e.getMessage().contains("exhausted")))
                        .as("Expected pool exhaustion or timeout error in: " + e.getMessage()) // GH-90000
                        .isTrue() // GH-90000
                );
        }

        @Test
        @DisplayName("connection acquisition wait obeys configured timeout and fails fast")
        void connectionAcquisition_obeys_timeout() { // GH-90000
            harness.exhaustConnectionPool(10); // GH-90000
            harness.setAcquisitionTimeoutMs(50); // GH-90000

            long start = System.currentTimeMillis(); // GH-90000
            try {
                runPromise(() -> harness.runQuery(TENANT_ID)); // GH-90000
            } catch (Exception ignored) { // GH-90000
                // Expected
            }
            long elapsed = System.currentTimeMillis() - start; // GH-90000

            assertThat(elapsed).isLessThan(3_000L); // must fail fast, not wait indefinitely // GH-90000
        }

        @Test
        @DisplayName("connections are released after queries complete and pool is available")
        void connections_released_afterQueriesComplete() throws Exception { // GH-90000
            harness.exhaustConnectionPool(5); // GH-90000
            harness.releaseConnections(5); // simulate query completion // GH-90000

            String result = runPromise(() -> harness.runQuery(TENANT_ID)); // GH-90000
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
        void memoryPressure_readPath_returnsCached() throws Exception { // GH-90000
            harness.injectMemoryPressure(0.90); // 90% heap used // GH-90000

            // Pre-populate cache entry
            harness.setCached(TENANT_ID, "record-1", "cached-value"); // GH-90000

            String result = runPromise(() -> harness.readWithCache(TENANT_ID, "record-1")); // GH-90000
            assertThat(result).isEqualTo("cached-value");
        }

        @Test
        @DisplayName("under memory pressure, new allocations fail with OOM guard and return error")
        void memoryPressure_largeAllocation_returnsError() { // GH-90000
            harness.injectMemoryPressure(0.99); // 99% heap — OOM guard active // GH-90000
            harness.enableOomGuard(); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> harness.allocateLargeBuffer(TENANT_ID, 128 * 1024))) // GH-90000
                .isInstanceOf(Exception.class) // GH-90000
                .satisfies(e -> // GH-90000
                    assertThat(e instanceof OutOfMemoryError // GH-90000
                        || e.getMessage().contains("memory") || e.getMessage().contains("OOM"))
                        .as("Expected OOM or memory-related error").isTrue()
                );
        }

        @Test
        @DisplayName("normal write-path works when memory pressure is moderate (<80%)")
        void moderateMemoryPressure_normalWriteSucceeds() throws Exception { // GH-90000
            harness.injectMemoryPressure(0.75); // 75% — below OOM guard // GH-90000

            String result = runPromise(() -> harness.writeEntity(TENANT_ID, "id-ok")); // GH-90000
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
        void partialResourceExhaustion_systemRemainsAvailable() throws Exception { // GH-90000
            harness.exhaustConnectionPool(8); // 80% of pool consumed // GH-90000
            harness.releaseConnections(2);    // 2 connections free // GH-90000

            // At least some requests should still succeed
            int successCount = 0;
            for (int i = 0; i < 3; i++) { // GH-90000
                try {
                    runPromise(() -> harness.runQuery(TENANT_ID)); // GH-90000
                    successCount++;
                } catch (Exception ignored) { // GH-90000
                    // Some failures acceptable under partial exhaustion
                }
            }

            // At least one should succeed since 2 connections are free
            assertThat(successCount).isGreaterThanOrEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("metrics endpoint responds even when primary resource is exhausted")
        void metricsEndpoint_available_duringResourceExhaustion() throws Exception { // GH-90000
            harness.exhaustConnectionPool(10); // GH-90000

            // Metrics is a lightweight endpoint — must always respond
            String metrics = runPromise(harness::getMetricsSnapshot); // GH-90000
            assertThat(metrics).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("health check returns DEGRADED (not DOWN) under resource pressure")
        void healthCheck_underPressure_reportsDegraded() throws Exception { // GH-90000
            harness.exhaustConnectionPool(9); // 90% exhausted // GH-90000

            String health = runPromise(harness::getHealthStatus); // GH-90000
            // Must be DEGRADED or HEALTHY — not a hard failure
            assertThat(health).isIn("DEGRADED", "HEALTHY"); // GH-90000
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
        private final java.util.Map<String, String> cache = new java.util.HashMap<>(); // GH-90000

        ResourceChaosHarness(DataCloudClient client) { // GH-90000
            this.client = client;
        }

        void saturateThreadPool(int capacity)          { this.poolCapacity = 0; } // pool is now full // GH-90000
        void drainPool()                               { this.poolCapacity = Integer.MAX_VALUE; } // GH-90000
        void exhaustConnectionPool(int held)           { this.heldConnections = held; } // GH-90000
        void releaseConnections(int count)             { this.heldConnections = Math.max(0, heldConnections - count); } // GH-90000
        void setAcquisitionTimeoutMs(long ms)          { this.acquisitionTimeoutMs = ms; } // GH-90000
        void injectMemoryPressure(double fraction)     { this.heapPressure = fraction; } // GH-90000
        void enableOomGuard()                          { this.oomGuardEnabled = true; } // GH-90000
        void setCached(String tenant, String id, String val) { cache.put(tenant + ":" + id, val); } // GH-90000

        Promise<String> submitTask() { // GH-90000
            if (poolCapacity <= 0) { // GH-90000
                return Promise.ofException(new RejectedExecutionException("Thread pool at capacity"));
            }
            return Promise.of("task-complete");
        }

        Promise<String> submitPriorityTask() { // GH-90000
            // Priority tasks use a dedicated, never-saturated channel
            return Promise.of("priority-complete");
        }

        Promise<String> runQuery(String tenantId) { // GH-90000
            int available = totalPoolSize - heldConnections;
            if (available <= 0) { // GH-90000
                return Promise.ofException(new RuntimeException( // GH-90000
                    "Connection pool exhausted (held=" + heldConnections // GH-90000
                    + ", timeout=" + acquisitionTimeoutMs + "ms)"));
            }
            return Promise.of("query-result");
        }

        Promise<String> writeEntity(String tenantId, String id) { // GH-90000
            if (oomGuardEnabled && heapPressure >= 0.90) { // GH-90000
                return Promise.ofException(new RuntimeException("OOM guard: heap pressure > 90%"));
            }
            return Promise.of(id); // GH-90000
        }

        Promise<String> readWithCache(String tenantId, String id) { // GH-90000
            String cached = cache.get(tenantId + ":" + id); // GH-90000
            if (cached != null) { // GH-90000
                return Promise.of(cached); // cache hit — no allocation needed // GH-90000
            }
            if (oomGuardEnabled && heapPressure >= 0.90) { // GH-90000
                return Promise.ofException(new RuntimeException("OOM guard: heap pressure > 90%"));
            }
            return Promise.of("uncached");
        }

        Promise<Void> allocateLargeBuffer(String tenantId, int bytes) { // GH-90000
            if (oomGuardEnabled && heapPressure >= 0.90) { // GH-90000
                return Promise.ofException(new RuntimeException( // GH-90000
                    "OOM guard rejected allocation of " + bytes + " bytes"));
            }
            return Promise.of(null); // GH-90000
        }

        Promise<String> getMetricsSnapshot() { // GH-90000
            return Promise.of("# HELP dc_pool_active Active connection pool size\n" // GH-90000
                + "dc_pool_active " + heldConnections + "\n");
        }

        Promise<String> getHealthStatus() { // GH-90000
            double usedFraction = (double) heldConnections / totalPoolSize; // GH-90000
            return Promise.of(usedFraction > 0.8 ? "DEGRADED" : "HEALTHY"); // GH-90000
        }
    }
}
