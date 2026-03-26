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
 * <p>These tests run against {@link DataCloud#forTesting()} (fully in-memory,
 * no I/O), which establishes the <em>framework overhead</em> baseline — the
 * minimum latency attributable to the Data-Cloud API layer itself before any
 * network, disk, or serialisation cost is added by a real storage backend.
 *
 * <h2>Purpose</h2>
 * <ul>
 *   <li>Catch regressions early: if the framework layer gets slower (e.g.
 *       unnecessary object allocation, lock contention in InMemoryEntityStore),
 *       these tests will fail before a single byte hits the network.</li>
 *   <li>Document expected SLAs for each operation tier so that downstream
 *       teams can set realistic latency budgets for real connectors.</li>
 * </ul>
 *
 * <h2>Measurement methodology</h2>
 * Each test follows the stop-the-world microbenchmark discipline:
 * <ol>
 *   <li>Warmup phase ({@value #WARMUP_OPS} operations) to let JIT compile the hot paths.</li>
 *   <li>Measurement phase ({@value #MEASURE_OPS} operations) collected into a
 *       {@code long[]} of nanosecond wall-clock latencies per call.</li>
 *   <li>Array is sorted in-place; P50/P95/P99 are read at fixed indices.</li>
 *   <li>Assertions use generous bounds calibrated for the in-memory path with
 *       headroom for GC pauses and slow CI runners.</li>
 * </ol>
 *
 * <p><strong>Note</strong>: For production latency SLAs against real backends
 * (PostgreSQL, ClickHouse, etc.) see the {@code @Tag("integration")} tests in
 * {@code src/integrationTest/}.
 *
 * @doc.type class
 * @doc.purpose Performance baseline tests measuring framework-layer overhead
 * @doc.layer product
 * @doc.pattern Benchmark, EventloopTestBase
 */
@Tag("performance")
@DisplayName("Data-Cloud Storage: In-Memory Performance Baselines")
class StoragePerformanceBaselineTest extends EventloopTestBase {

    // ── Benchmark configuration ───────────────────────────────────────────────

    /** Number of warmup iterations before measurement (allows JIT to stabilise). */
    private static final int WARMUP_OPS = 200;

    /** Number of measured operations per test scenario. */
    private static final int MEASURE_OPS = 1_000;

    /** Fixed test tenant identifier — no cross-tenant ambiguity. */
    private static final String TENANT = "perf-baseline-tenant";

    /** Fixed collection name for all baseline tests. */
    private static final String COLLECTION = "perf-baseline-collection";

    // ── SLA thresholds (nanoseconds) ─────────────────────────────────────────

    /**
     * P99 latency SLA for {@code save} on in-memory store.
     * 5 ms is extremely generous for pure in-memory work; any regression
     * above this indicates a structural problem in the API layer.
     */
    private static final long SAVE_P99_THRESHOLD_NS = 5_000_000L;     // 5 ms

    /**
     * P99 latency SLA for {@code findById} on in-memory store.
     * Point lookups in a ConcurrentHashMap should stay well under 1 ms; 3 ms
     * is the CI-adjusted upper bound.
     */
    private static final long FIND_P99_THRESHOLD_NS = 3_000_000L;     // 3 ms

    /**
     * P99 latency SLA for a full-collection {@code query} on in-memory store.
     * Scans the entire ConcurrentHashMap + applies criteria; 10 ms is sufficient
     * headroom for 1 000-element in-memory collections on resource-constrained CI.
     */
    private static final long QUERY_P99_THRESHOLD_NS = 10_000_000L;   // 10 ms

    // ── Test state ────────────────────────────────────────────────────────────

    private DataCloudClient client;

    @BeforeEach
    void setUpClient() {
        client = DataCloud.forTesting();
    }

    @AfterEach
    void tearDownClient() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    // =========================================================================
    //  save() baseline
    // =========================================================================

    @Test
    @DisplayName("save() P99 must stay within in-memory SLA")
    void saveShouldMeetP99LatencySla() {
        // ── Warmup ────────────────────────────────────────────────────────────
        for (int i = 0; i < WARMUP_OPS; i++) {
            final int idx = i;
            runPromise(() -> client.save(TENANT, COLLECTION,
                    Map.of("warmup", true, "seq", idx)));
        }

        // ── Measure ───────────────────────────────────────────────────────────
        long[] latencies = new long[MEASURE_OPS];
        for (int i = 0; i < MEASURE_OPS; i++) {
            final Map<String, Object> data = Map.of("key", "baseline", "seq", i, "value", UUID.randomUUID().toString());
            long t0 = System.nanoTime();
            runPromise(() -> client.save(TENANT, COLLECTION, data));
            latencies[i] = System.nanoTime() - t0;
        }

        // ── Assertions ────────────────────────────────────────────────────────
        long p50 = percentile(latencies, 50);
        long p95 = percentile(latencies, 95);
        long p99 = percentile(latencies, 99);

        logPercentiles("save", p50, p95, p99, SAVE_P99_THRESHOLD_NS);

        assertThat(p99)
                .as("save() P99 latency (%d µs) must be < %d µs SLA",
                        toMicros(p99), toMicros(SAVE_P99_THRESHOLD_NS))
                .isLessThan(SAVE_P99_THRESHOLD_NS);
    }

    // =========================================================================
    //  findById() baseline
    // =========================================================================

    @Test
    @DisplayName("findById() P99 must stay within in-memory SLA")
    void findByIdShouldMeetP99LatencySla() {
        // Pre-populate 200 entities so findById has something to scan over
        String[] savedIds = new String[200];
        for (int i = 0; i < 200; i++) {
            final int idx = i;
            Entity saved = runPromise(() -> client.save(TENANT, COLLECTION,
                    Map.of("type", "findById-baseline", "seq", idx)));
            savedIds[i] = saved.id();
        }

        // ── Warmup ────────────────────────────────────────────────────────────
        for (int i = 0; i < WARMUP_OPS; i++) {
            final String id = savedIds[i % savedIds.length];
            runPromise(() -> client.findById(TENANT, COLLECTION, id));
        }

        // ── Measure ───────────────────────────────────────────────────────────
        long[] latencies = new long[MEASURE_OPS];
        for (int i = 0; i < MEASURE_OPS; i++) {
            final String id = savedIds[i % savedIds.length];
            long t0 = System.nanoTime();
            Optional<Entity> result = runPromise(() -> client.findById(TENANT, COLLECTION, id));
            latencies[i] = System.nanoTime() - t0;

            // Sanity: every record must be found (guard against store bug mid-test)
            assertThat(result).isPresent();
        }

        // ── Assertions ────────────────────────────────────────────────────────
        long p50 = percentile(latencies, 50);
        long p95 = percentile(latencies, 95);
        long p99 = percentile(latencies, 99);

        logPercentiles("findById", p50, p95, p99, FIND_P99_THRESHOLD_NS);

        assertThat(p99)
                .as("findById() P99 latency (%d µs) must be < %d µs SLA",
                        toMicros(p99), toMicros(FIND_P99_THRESHOLD_NS))
                .isLessThan(FIND_P99_THRESHOLD_NS);
    }

    // =========================================================================
    //  query() baseline
    // =========================================================================

    @Test
    @DisplayName("query() P99 must stay within in-memory SLA")
    void queryShouldMeetP99LatencySla() {
        // Pre-populate 500 entities of two types for the query to scan
        for (int i = 0; i < 500; i++) {
            final int idx = i;
            final String type = (i % 2 == 0) ? "even" : "odd";
            runPromise(() -> client.save(TENANT, COLLECTION,
                    Map.of("type", type, "seq", idx)));
        }

        DataCloudClient.Query evenQuery = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.eq("type", "even"))
                .limit(500)
                .build();

        // ── Warmup ────────────────────────────────────────────────────────────
        for (int i = 0; i < WARMUP_OPS; i++) {
            runPromise(() -> client.query(TENANT, COLLECTION, evenQuery));
        }

        // ── Measure ───────────────────────────────────────────────────────────
        long[] latencies = new long[MEASURE_OPS];
        for (int i = 0; i < MEASURE_OPS; i++) {
            long t0 = System.nanoTime();
            List<Entity> results = runPromise(() -> client.query(TENANT, COLLECTION, evenQuery));
            latencies[i] = System.nanoTime() - t0;

            // Sanity: results must be non-null (query must not error)
            assertThat(results).isNotNull();
        }

        // ── Assertions ────────────────────────────────────────────────────────
        long p50 = percentile(latencies, 50);
        long p95 = percentile(latencies, 95);
        long p99 = percentile(latencies, 99);

        logPercentiles("query", p50, p95, p99, QUERY_P99_THRESHOLD_NS);

        assertThat(p99)
                .as("query() P99 latency (%d µs) must be < %d µs SLA",
                        toMicros(p99), toMicros(QUERY_P99_THRESHOLD_NS))
                .isLessThan(QUERY_P99_THRESHOLD_NS);
    }

    // =========================================================================
    //  Throughput baseline
    // =========================================================================

    @Test
    @DisplayName("save() throughput must exceed 500 ops/s on in-memory store")
    void saveThroughputShouldExceedMinimum() {
        // Warmup
        for (int i = 0; i < WARMUP_OPS; i++) {
            final int idx = i;
            runPromise(() -> client.save(TENANT, "throughput-collection",
                    Map.of("warmup", true, "seq", idx)));
        }

        int throughputOps = 2_000;
        long startNs = System.nanoTime();
        for (int i = 0; i < throughputOps; i++) {
            final int idx = i;
            runPromise(() -> client.save(TENANT, "throughput-collection",
                    Map.of("key", "value", "seq", idx)));
        }
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;

        double actualOpsPerSec = (double) throughputOps / durationMs * 1_000.0;
        double minimumOpsPerSec = 500.0;  // modest lower bound — even slow CI can sustain this

        System.out.printf(
                "[Performance] save() throughput: %.0f ops/s (%d ops in %d ms)%n",
                actualOpsPerSec, throughputOps, durationMs);

        assertThat(actualOpsPerSec)
                .as("save() throughput (%.0f ops/s) must exceed %.0f ops/s minimum",
                        actualOpsPerSec, minimumOpsPerSec)
                .isGreaterThan(minimumOpsPerSec);
    }

    // =========================================================================
    //  Percentile calculation helpers
    // =========================================================================

    /**
     * Computes a percentile from an unsorted array of nanosecond latencies.
     *
     * <p>Uses the nearest-rank method: {@code ceil(p/100 * N)} — broadly
     * equivalent to Java's {@code DescriptiveStatistics.getPercentile} for
     * large N, without the extra dependency.
     *
     * @param nanosArr raw nanosecond latency samples (modified in-place by sorting)
     * @param p        percentile (0–100, inclusive)
     * @return latency value at the requested percentile, in nanoseconds
     */
    private static long percentile(long[] nanosArr, int p) {
        long[] sorted = nanosArr.clone();
        Arrays.sort(sorted);
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        index = Math.max(0, Math.min(index, sorted.length - 1));
        return sorted[index];
    }

    private static long toMicros(long nanos) {
        return nanos / 1_000;
    }

    private static void logPercentiles(String op, long p50, long p95, long p99, long sla) {
        System.out.printf(
                "[Performance] %s() — P50=%4d µs  P95=%4d µs  P99=%4d µs  (SLA P99<%d µs)%n",
                op, toMicros(p50), toMicros(p95), toMicros(p99), toMicros(sla));
    }
}
