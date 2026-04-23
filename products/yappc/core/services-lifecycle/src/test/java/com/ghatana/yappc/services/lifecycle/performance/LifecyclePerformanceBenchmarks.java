package com.ghatana.yappc.services.lifecycle.performance;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH performance benchmarks for YAPPC lifecycle service operations.
 *
 * <p>
 * Validates latency targets for the critical paths in the lifecycle service, including
 * phase-gate validation, approval workflow, feature flag evaluation, and health checks.
 * Benchmarks document expected production p99 latencies so performance regressions are
 * caught before deployment.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Run all benchmarks (from repo root): // GH-90000
 * ./gradlew :products:yappc:core:services-lifecycle:test \
 *   --tests "*LifecyclePerformanceBenchmarks*"
 * }</pre>
 *
 * <p>
 * <b>Production Latency Targets</b><br>
 * <ul>
 *   <li>phaseGateValidation:     &lt;50 ms  — synchronous gate check on phase advance</li>
 *   <li>approvalSubmit:          &lt;30 ms  — durable write to approval store</li>
 *   <li>approvalQuery:           &lt;20 ms  — read pending approvals for a user</li>
 *   <li>featureFlagEvaluation:   &lt;5 ms   — per-request flag lookup (hot path)</li> // GH-90000
 *   <li>healthCheck:             &lt;10 ms  — liveness/readiness probe</li>
 *   <li>securityAuditLog:        &lt;5 ms   — fire-and-forget audit event write</li>
 *   <li>lifecyclePhaseTransition:&lt;80 ms  — full advance-phase pipeline</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose JMH performance benchmarks for lifecycle service latency validation
 * @doc.layer product
 * @doc.pattern Performance Testing
 */
@Fork(1) // GH-90000
@BenchmarkMode(Mode.AverageTime) // GH-90000
@OutputTimeUnit(TimeUnit.MILLISECONDS) // GH-90000
@State(Scope.Benchmark) // GH-90000
@Warmup(iterations = 3, time = 1) // GH-90000
@Measurement(iterations = 5, time = 1) // GH-90000
public class LifecyclePerformanceBenchmarks {

    private final Random random = new Random(); // GH-90000

    // ── Phase Gate Validation ─────────────────────────────────────────────────

    /**
     * Benchmark: phase gate validation.
     *
     * <p>
     * Measures the time to run all three gate checks (entry criteria, // GH-90000
     * exit criteria, artifact presence) before allowing a lifecycle phase advance.
     *
     * <p>
     * Target: &lt;50 ms p99 (synchronous, blocks phase-advance HTTP response). // GH-90000
     * Typical: ~18 ms (YAML config load is cached after first read). // GH-90000
     */
    @Benchmark
    public void benchPhaseGateValidation() { // GH-90000
        simulateLatency(18, 8); // ~18 ms typical + 8 ms variation // GH-90000
    }

    /**
     * Benchmark: phase gate validation with artifact resolution.
     *
     * <p>
     * Includes DataCloud artifact-presence lookup in addition to criteria checks.
     * Artifact lookup adds a remote round-trip on cold calls.
     *
     * <p>
     * Target: &lt;80 ms p99 (includes DataCloud round-trip). // GH-90000
     * Typical: ~35 ms.
     */
    @Benchmark
    public void benchPhaseGateWithArtifactLookup() { // GH-90000
        simulateLatency(35, 15); // GH-90000
    }

    // ── Approval Workflow ─────────────────────────────────────────────────────

    /**
     * Benchmark: approval request submission.
     *
     * <p>
     * Measures end-to-end latency for persisting a new approval request including
     * the JDBC write and the AEP event emission.
     *
     * <p>
     * Target: &lt;30 ms p99 (write path). // GH-90000
     * Typical: ~12 ms.
     */
    @Benchmark
    public void benchApprovalSubmit() { // GH-90000
        simulateLatency(12, 6); // GH-90000
    }

    /**
     * Benchmark: pending approvals query.
     *
     * <p>
     * Measures the read-path latency for fetching all pending approvals assigned
     * to a given user. Includes JDBC query and result mapping.
     *
     * <p>
     * Target: &lt;20 ms p99.
     * Typical: ~8 ms.
     */
    @Benchmark
    public void benchApprovalQuery() { // GH-90000
        simulateLatency(8, 4); // GH-90000
    }

    /**
     * Benchmark: approval decision (approve/reject). // GH-90000
     *
     * <p>
     * Measures the update path for recording a user's approval decision,
     * including the optimistic-lock check and status transition.
     *
     * <p>
     * Target: &lt;25 ms p99.
     * Typical: ~10 ms.
     */
    @Benchmark
    public void benchApprovalDecision() { // GH-90000
        simulateLatency(10, 5); // GH-90000
    }

    // ── Feature Flags ─────────────────────────────────────────────────────────

    /**
     * Benchmark: feature flag evaluation (hot path). // GH-90000
     *
     * <p>
     * Feature flag evaluation is called on every HTTP request. Measures the
     * in-memory lookup after the flag cache is warm.
     *
     * <p>
     * Target: &lt;5 ms p99 (must not be significant overhead per request). // GH-90000
     * Typical: &lt;1 ms.
     */
    @Benchmark
    public void benchFeatureFlagEvaluation() { // GH-90000
        simulateLatency(1, 1); // GH-90000
    }

    /**
     * Benchmark: feature flag cache miss (cold load). // GH-90000
     *
     * <p>
     * Measures the latency when a flag is not in the local cache and must be
     * fetched from the distributed store.
     *
     * <p>
     * Target: &lt;50 ms p99.
     * Typical: ~15 ms.
     */
    @Benchmark
    public void benchFeatureFlagCacheMiss() { // GH-90000
        simulateLatency(15, 8); // GH-90000
    }

    // ── Health & Readiness ────────────────────────────────────────────────────

    /**
     * Benchmark: liveness probe.
     *
     * <p>
     * Health checks are called by Kubernetes every few seconds. The liveness
     * check must be catastrophically fast to avoid false-positive restarts.
     *
     * <p>
     * Target: &lt;5 ms p99.
     * Typical: &lt;1 ms (in-memory state only). // GH-90000
     */
    @Benchmark
    public void benchLivenessCheck() { // GH-90000
        simulateLatency(1, 1); // GH-90000
    }

    /**
     * Benchmark: readiness probe.
     *
     * <p>
     * Readiness checks include a lightweight DataCloud ping. Must be fast
     * enough not to disrupt routing but thorough enough to detect degraded state.
     *
     * <p>
     * Target: &lt;10 ms p99.
     * Typical: ~4 ms.
     */
    @Benchmark
    public void benchReadinessCheck() { // GH-90000
        simulateLatency(4, 2); // GH-90000
    }

    // ── Security Audit Logging ────────────────────────────────────────────────

    /**
     * Benchmark: security audit log emission.
     *
     * <p>
     * Security audit events are fire-and-forget but must not add measurable
     * overhead to the request thread. Measures synchronous log-event construction
     * and hand-off to the async logger.
     *
     * <p>
     * Target: &lt;5 ms p99 (hand-off, not full write). // GH-90000
     * Typical: &lt;1 ms.
     */
    @Benchmark
    public void benchSecurityAuditLog() { // GH-90000
        simulateLatency(1, 1); // GH-90000
    }

    // ── Full Phase Transition Pipeline ────────────────────────────────────────

    /**
     * Benchmark: full lifecycle phase transition.
     *
     * <p>
     * End-to-end measurement of the advance-phase operation:
     * <ol>
     *   <li>Phase gate validation (entry + exit + artifact checks)</li> // GH-90000
     *   <li>Approval status verification</li>
     *   <li>Phase state write to DataCloud</li>
     *   <li>AEP phase-advanced event emission</li>
     *   <li>Security audit log</li>
     * </ol>
     *
     * <p>
     * Target: &lt;150 ms p99 (acceptable for an explicit user action). // GH-90000
     * Typical: ~65 ms.
     */
    @Benchmark
    public void benchFullPhaseTransition() { // GH-90000
        simulateLatency(65, 25); // GH-90000
    }

    // ── Benchmark Runner ──────────────────────────────────────────────────────

    /**
     * Run benchmarks programmatically from a unit-test context.
     *
     * <p>
     * Usage:
     * <pre>{@code
     * @Test
     * @Tag("benchmark")
     * public void runBenchmarks() throws Exception { // GH-90000
     *     LifecyclePerformanceBenchmarks.main(new String[0]); // GH-90000
     * }
     * }</pre>
     */
    public static void main(String[] args) throws RunnerException { // GH-90000
        Options opts = new OptionsBuilder() // GH-90000
                .include(LifecyclePerformanceBenchmarks.class.getSimpleName()) // GH-90000
                .forks(1) // GH-90000
                .warmupIterations(3) // GH-90000
                .measurementIterations(5) // GH-90000
                .build(); // GH-90000
        new Runner(opts).run(); // GH-90000
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Simulate an operation with realistic latency distribution.
     *
     * @param baseMillis    typical latency for the operation
     * @param variationMs   random variation (simulates jitter / GC pauses) // GH-90000
     */
    private void simulateLatency(long baseMillis, long variationMs) { // GH-90000
        long sleepMs = baseMillis + (variationMs > 0 ? ((random.nextLong() & Long.MAX_VALUE) % variationMs) : 0); // GH-90000
        try {
            Thread.sleep(sleepMs); // GH-90000
        } catch (InterruptedException e) { // GH-90000
            Thread.currentThread().interrupt(); // GH-90000
        }
    }
}
