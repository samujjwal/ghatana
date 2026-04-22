package com.ghatana.datacloud;

import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance baseline tests for the Data-Cloud in-memory stack.
 *
 * <p>These tests run against {@link DataCloud#forTesting()} (fully in-memory, // GH-90000
 * no I/O), which establishes the <em>framework overhead</em> baseline — the
 * minimum latency attributable to the Data-Cloud API layer itself before any
 * network, disk, or serialisation cost is added by a real storage backend.
 *
 * <h2>Purpose</h2>
 * <ul>
 *   <li>Catch regressions early: if the framework layer gets slower (e.g. // GH-90000
 *       unnecessary object allocation, lock contention in InMemoryEntityStore),
 *       these tests will fail before a single byte hits the network.</li>
 *   <li>Document expected SLAs for each operation tier so that downstream
 *       teams can set realistic latency budgets for real connectors.</li>
 * </ul>
 *
 * <h2>Measurement methodology</h2>
 * Each test follows the stop-the-world microbenchmark discipline:
 * <ol>
 *   <li>Warmup phase ({@value #WARMUP_OPS} operations) to let JIT compile the hot paths.</li> // GH-90000
 *   <li>Measurement phase ({@value #MEASURE_OPS} operations) collected into a // GH-90000
 *       {@code long[]} of nanosecond wall-clock latencies per call.</li>
 *   <li>Array is sorted in-place; P50/P95/P99 are read at fixed indices.</li>
 *   <li>Assertions use generous bounds calibrated for the in-memory path with
 *       headroom for GC pauses and slow CI runners.</li>
 * </ol>
 *
 * <p><strong>Note</strong>: For production latency SLAs against real backends
 * (PostgreSQL, ClickHouse, etc.) see the {@code @Tag("integration [GH-90000]")} tests in
 * {@code src/integrationTest/}.
 *
 * @doc.type class
 * @doc.purpose Performance baseline tests measuring framework-layer overhead
 * @doc.layer product
 * @doc.pattern Benchmark, EventloopTestBase
 */
@Tag("performance [GH-90000]")
@DisplayName("Data-Cloud Storage: In-Memory Performance Baselines [GH-90000]")
class StoragePerformanceBaselineTest extends EventloopTestBase {

    // ── Benchmark configuration ───────────────────────────────────────────────

    /** Number of warmup iterations before measurement (allows JIT to stabilise). */ // GH-90000
    private static final int WARMUP_OPS = 200;

    /** Number of measured operations per test scenario. */
    private static final int MEASURE_OPS = 1_000;

    /** Fixed test tenant identifier — no cross-tenant ambiguity. */
    private static final String TENANT = "perf-baseline-tenant";

    /** Fixed collection name for all baseline tests. */
    private static final String COLLECTION = "perf-baseline-collection";

    // ── SLA thresholds (nanoseconds) ───────────────────────────────────────── // GH-90000

    /**
     * P99 latency SLA for {@code save} on in-memory store.
     * 5 ms is extremely generous for pure in-memory work; any regression
     * above this indicates a structural problem in the API layer.
     */
    private static final long SAVE_P99_THRESHOLD_NS = 5_000_000L;     // 5 ms

    /**
     * P99 latency SLA for {@code findById} on in-memory store.
     * The lookup itself is O(1), but this benchmark measures the full DataCloud client // GH-90000
     * path including Promise/eventloop handoff and test harness overhead. Empirical CI
     * runs show occasional spikes above 3 ms without indicating a storage regression, so
     * 5 ms is the stable upper bound for the framework-layer baseline.
     */
    private static final long FIND_P99_THRESHOLD_NS = 5_000_000L;     // 5 ms

    /**
     * P99 latency SLA for a full-collection {@code query} on in-memory store.
     * Scans the entire ConcurrentHashMap + applies criteria; 10 ms is sufficient
     * headroom for 1 000-element in-memory collections on resource-constrained CI.
     */
    private static final long QUERY_P99_THRESHOLD_NS = 10_000_000L;   // 10 ms

    // ── Test state ────────────────────────────────────────────────────────────

    private DataCloudClient client;

    @BeforeEach
    void setUpClient() { // GH-90000
        client = DataCloud.forTesting(); // GH-90000
    }

    @AfterEach
    void tearDownClient() throws Exception { // GH-90000
        if (client != null) { // GH-90000
            client.close(); // GH-90000
        }
    }

    // =========================================================================
    //  save() baseline // GH-90000
    // =========================================================================

    @Test
    @DisplayName("save() P99 must stay within in-memory SLA [GH-90000]")
    void saveShouldMeetP99LatencySla() { // GH-90000
        // ── Warmup ────────────────────────────────────────────────────────────
        for (int i = 0; i < WARMUP_OPS; i++) { // GH-90000
            final int idx = i;
            runPromise(() -> client.save(TENANT, COLLECTION, // GH-90000
                    Map.of("warmup", true, "seq", idx))); // GH-90000
        }

        // ── Measure ───────────────────────────────────────────────────────────
        long[] latencies = new long[MEASURE_OPS];
        for (int i = 0; i < MEASURE_OPS; i++) { // GH-90000
            final Map<String, Object> data = Map.of("key", "baseline", "seq", i, "value", UUID.randomUUID().toString()); // GH-90000
            long t0 = System.nanoTime(); // GH-90000
            runPromise(() -> client.save(TENANT, COLLECTION, data)); // GH-90000
            latencies[i] = System.nanoTime() - t0; // GH-90000
        }

        // ── Assertions ────────────────────────────────────────────────────────
        long p50 = percentile(latencies, 50); // GH-90000
        long p95 = percentile(latencies, 95); // GH-90000
        long p99 = percentile(latencies, 99); // GH-90000

        logPercentiles("save", p50, p95, p99, SAVE_P99_THRESHOLD_NS); // GH-90000

        assertThat(p99) // GH-90000
                .as("save() P99 latency (%d µs) must be < %d µs SLA", // GH-90000
                        toMicros(p99), toMicros(SAVE_P99_THRESHOLD_NS)) // GH-90000
                .isLessThan(SAVE_P99_THRESHOLD_NS); // GH-90000
    }

    // =========================================================================
    //  findById() baseline // GH-90000
    // =========================================================================

    @Test
    @DisplayName("findById() P99 must stay within in-memory SLA [GH-90000]")
    void findByIdShouldMeetP99LatencySla() { // GH-90000
        // Pre-populate 200 entities so findById has something to scan over
        String[] savedIds = new String[200];
        for (int i = 0; i < 200; i++) { // GH-90000
            final int idx = i;
            Entity saved = runPromise(() -> client.save(TENANT, COLLECTION, // GH-90000
                    Map.of("type", "findById-baseline", "seq", idx))); // GH-90000
            savedIds[i] = saved.id(); // GH-90000
        }

        // ── Warmup ────────────────────────────────────────────────────────────
        for (int i = 0; i < WARMUP_OPS; i++) { // GH-90000
            final String id = savedIds[i % savedIds.length];
            runPromise(() -> client.findById(TENANT, COLLECTION, id)); // GH-90000
        }

        // ── Measure ───────────────────────────────────────────────────────────
        long[] latencies = new long[MEASURE_OPS];
        for (int i = 0; i < MEASURE_OPS; i++) { // GH-90000
            final String id = savedIds[i % savedIds.length];
            long t0 = System.nanoTime(); // GH-90000
            Optional<Entity> result = runPromise(() -> client.findById(TENANT, COLLECTION, id)); // GH-90000
            latencies[i] = System.nanoTime() - t0; // GH-90000

            // Sanity: every record must be found (guard against store bug mid-test) // GH-90000
            assertThat(result).isPresent(); // GH-90000
        }

        // ── Assertions ────────────────────────────────────────────────────────
        long p50 = percentile(latencies, 50); // GH-90000
        long p95 = percentile(latencies, 95); // GH-90000
        long p99 = percentile(latencies, 99); // GH-90000

        logPercentiles("findById", p50, p95, p99, FIND_P99_THRESHOLD_NS); // GH-90000

        assertThat(p99) // GH-90000
                .as("findById() P99 latency (%d µs) must be < %d µs SLA", // GH-90000
                        toMicros(p99), toMicros(FIND_P99_THRESHOLD_NS)) // GH-90000
                .isLessThan(FIND_P99_THRESHOLD_NS); // GH-90000
    }

    // =========================================================================
    //  query() baseline // GH-90000
    // =========================================================================

    @Test
    @DisplayName("query() P99 must stay within in-memory SLA [GH-90000]")
    void queryShouldMeetP99LatencySla() { // GH-90000
        // Pre-populate 500 entities of two types for the query to scan
        for (int i = 0; i < 500; i++) { // GH-90000
            final int idx = i;
            final String type = (i % 2 == 0) ? "even" : "odd"; // GH-90000
            runPromise(() -> client.save(TENANT, COLLECTION, // GH-90000
                    Map.of("type", type, "seq", idx))); // GH-90000
        }

        DataCloudClient.Query evenQuery = DataCloudClient.Query.builder() // GH-90000
                .filter(DataCloudClient.Filter.eq("type", "even")) // GH-90000
                .limit(500) // GH-90000
                .build(); // GH-90000

        // ── Warmup ────────────────────────────────────────────────────────────
        for (int i = 0; i < WARMUP_OPS; i++) { // GH-90000
            runPromise(() -> client.query(TENANT, COLLECTION, evenQuery)); // GH-90000
        }

        // ── Measure ───────────────────────────────────────────────────────────
        long[] latencies = new long[MEASURE_OPS];
        for (int i = 0; i < MEASURE_OPS; i++) { // GH-90000
            long t0 = System.nanoTime(); // GH-90000
            List<Entity> results = runPromise(() -> client.query(TENANT, COLLECTION, evenQuery)); // GH-90000
            latencies[i] = System.nanoTime() - t0; // GH-90000

            // Sanity: results must be non-null (query must not error) // GH-90000
            assertThat(results).isNotNull(); // GH-90000
        }

        // ── Assertions ────────────────────────────────────────────────────────
        long p50 = percentile(latencies, 50); // GH-90000
        long p95 = percentile(latencies, 95); // GH-90000
        long p99 = percentile(latencies, 99); // GH-90000

        logPercentiles("query", p50, p95, p99, QUERY_P99_THRESHOLD_NS); // GH-90000

        assertThat(p99) // GH-90000
                .as("query() P99 latency (%d µs) must be < %d µs SLA", // GH-90000
                        toMicros(p99), toMicros(QUERY_P99_THRESHOLD_NS)) // GH-90000
                .isLessThan(QUERY_P99_THRESHOLD_NS); // GH-90000
    }

    // =========================================================================
    //  Throughput baseline
    // =========================================================================

    @Test
    @DisplayName("save() throughput must exceed 500 ops/s on in-memory store [GH-90000]")
    void saveThroughputShouldExceedMinimum() { // GH-90000
        // Warmup
        for (int i = 0; i < WARMUP_OPS; i++) { // GH-90000
            final int idx = i;
            runPromise(() -> client.save(TENANT, "throughput-collection", // GH-90000
                    Map.of("warmup", true, "seq", idx))); // GH-90000
        }

        int throughputOps = 2_000;
        long startNs = System.nanoTime(); // GH-90000
        for (int i = 0; i < throughputOps; i++) { // GH-90000
            final int idx = i;
            runPromise(() -> client.save(TENANT, "throughput-collection", // GH-90000
                    Map.of("key", "value", "seq", idx))); // GH-90000
        }
        long durationMs = (System.nanoTime() - startNs) / 1_000_000; // GH-90000

        double actualOpsPerSec = (double) throughputOps / durationMs * 1_000.0; // GH-90000
        double minimumOpsPerSec = 500.0;  // modest lower bound — even slow CI can sustain this

        System.out.printf( // GH-90000
                "[Performance] save() throughput: %.0f ops/s (%d ops in %d ms)%n", // GH-90000
                actualOpsPerSec, throughputOps, durationMs);

        assertThat(actualOpsPerSec) // GH-90000
                .as("save() throughput (%.0f ops/s) must exceed %.0f ops/s minimum", // GH-90000
                        actualOpsPerSec, minimumOpsPerSec)
                .isGreaterThan(minimumOpsPerSec); // GH-90000
    }

    // =========================================================================
    //  Percentile calculation helpers
    // =========================================================================

    /**
     * Computes a percentile from an unsorted array of nanosecond latencies.
     *
     * <p>Uses the nearest-rank method: {@code ceil(p/100 * N)} — broadly // GH-90000
     * equivalent to Java's {@code DescriptiveStatistics.getPercentile} for
     * large N, without the extra dependency.
     *
     * @param nanosArr raw nanosecond latency samples (modified in-place by sorting) // GH-90000
     * @param p        percentile (0–100, inclusive) // GH-90000
     * @return latency value at the requested percentile, in nanoseconds
     */
    private static long percentile(long[] nanosArr, int p) { // GH-90000
        long[] sorted = nanosArr.clone(); // GH-90000
        Arrays.sort(sorted); // GH-90000
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1; // GH-90000
        index = Math.max(0, Math.min(index, sorted.length - 1)); // GH-90000
        return sorted[index];
    }

    private static long toMicros(long nanos) { // GH-90000
        return nanos / 1_000;
    }

    private static void logPercentiles(String op, long p50, long p95, long p99, long sla) { // GH-90000
        System.out.printf( // GH-90000
                "[Performance] %s() — P50=%4d µs  P95=%4d µs  P99=%4d µs  (SLA P99<%d µs)%n", // GH-90000
                op, toMicros(p50), toMicros(p95), toMicros(p99), toMicros(sla)); // GH-90000
    }
}
