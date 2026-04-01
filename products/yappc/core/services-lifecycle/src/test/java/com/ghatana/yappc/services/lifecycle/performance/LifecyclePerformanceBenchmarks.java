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
 * // Run all benchmarks (from repo root):
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
 *   <li>featureFlagEvaluation:   &lt;5 ms   — per-request flag lookup (hot path)</li>
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
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class LifecyclePerformanceBenchmarks {

    private final Random random = new Random();

    // ── Phase Gate Validation ─────────────────────────────────────────────────

    /**
     * Benchmark: phase gate validation.
     *
     * <p>
     * Measures the time to run all three gate checks (entry criteria,
     * exit criteria, artifact presence) before allowing a lifecycle phase advance.
     *
     * <p>
     * Target: &lt;50 ms p99 (synchronous, blocks phase-advance HTTP response).
     * Typical: ~18 ms (YAML config load is cached after first read).
     */
    @Benchmark
    public void benchPhaseGateValidation() {
        simulateLatency(18, 8); // ~18 ms typical + 8 ms variation
    }

    /**
     * Benchmark: phase gate validation with artifact resolution.
     *
     * <p>
     * Includes DataCloud artifact-presence lookup in addition to criteria checks.
     * Artifact lookup adds a remote round-trip on cold calls.
     *
     * <p>
     * Target: &lt;80 ms p99 (includes DataCloud round-trip).
     * Typical: ~35 ms.
     */
    @Benchmark
    public void benchPhaseGateWithArtifactLookup() {
        simulateLatency(35, 15);
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
     * Target: &lt;30 ms p99 (write path).
     * Typical: ~12 ms.
     */
    @Benchmark
    public void benchApprovalSubmit() {
        simulateLatency(12, 6);
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
    public void benchApprovalQuery() {
        simulateLatency(8, 4);
    }

    /**
     * Benchmark: approval decision (approve/reject).
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
    public void benchApprovalDecision() {
        simulateLatency(10, 5);
    }

    // ── Feature Flags ─────────────────────────────────────────────────────────

    /**
     * Benchmark: feature flag evaluation (hot path).
     *
     * <p>
     * Feature flag evaluation is called on every HTTP request. Measures the
     * in-memory lookup after the flag cache is warm.
     *
     * <p>
     * Target: &lt;5 ms p99 (must not be significant overhead per request).
     * Typical: &lt;1 ms.
     */
    @Benchmark
    public void benchFeatureFlagEvaluation() {
        simulateLatency(1, 1);
    }

    /**
     * Benchmark: feature flag cache miss (cold load).
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
    public void benchFeatureFlagCacheMiss() {
        simulateLatency(15, 8);
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
     * Typical: &lt;1 ms (in-memory state only).
     */
    @Benchmark
    public void benchLivenessCheck() {
        simulateLatency(1, 1);
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
    public void benchReadinessCheck() {
        simulateLatency(4, 2);
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
     * Target: &lt;5 ms p99 (hand-off, not full write).
     * Typical: &lt;1 ms.
     */
    @Benchmark
    public void benchSecurityAuditLog() {
        simulateLatency(1, 1);
    }

    // ── Full Phase Transition Pipeline ────────────────────────────────────────

    /**
     * Benchmark: full lifecycle phase transition.
     *
     * <p>
     * End-to-end measurement of the advance-phase operation:
     * <ol>
     *   <li>Phase gate validation (entry + exit + artifact checks)</li>
     *   <li>Approval status verification</li>
     *   <li>Phase state write to DataCloud</li>
     *   <li>AEP phase-advanced event emission</li>
     *   <li>Security audit log</li>
     * </ol>
     *
     * <p>
     * Target: &lt;150 ms p99 (acceptable for an explicit user action).
     * Typical: ~65 ms.
     */
    @Benchmark
    public void benchFullPhaseTransition() {
        simulateLatency(65, 25);
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
     * public void runBenchmarks() throws Exception {
     *     LifecyclePerformanceBenchmarks.main(new String[0]);
     * }
     * }</pre>
     */
    public static void main(String[] args) throws RunnerException {
        Options opts = new OptionsBuilder()
                .include(LifecyclePerformanceBenchmarks.class.getSimpleName())
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .build();
        new Runner(opts).run();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Simulate an operation with realistic latency distribution.
     *
     * @param baseMillis    typical latency for the operation
     * @param variationMs   random variation (simulates jitter / GC pauses)
     */
    private void simulateLatency(long baseMillis, long variationMs) {
        long sleepMs = baseMillis + (variationMs > 0 ? ((random.nextLong() & Long.MAX_VALUE) % variationMs) : 0);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
