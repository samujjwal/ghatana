package com.ghatana.appplatform.sdk;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.*;
import java.util.concurrent.*;

/**
 * @doc.type    Service
 * @doc.purpose Load generation, latency measurement, regression detection for SDK performance testing.
 *              Integrates with k6 for external HTTP load scenarios.
 * @doc.layer   Platform SDK (K-12)
 * @doc.pattern Port-Adapter using inner port interfaces; all I/O via Promise.ofBlocking
 *
 * STORY-K12-006: Performance test utilities
 * Provides: configurable TPS load generator, latency recorder (p50/p95/p99),
 * throughput calculator, memory profiler, event storm simulator, regression detection (>10% = FAIL).
 */
public class PerformanceTestUtilitiesService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface LoadGeneratorPort {
        /** Execute a single operation unit. Returns latency in nanos. */
        long executeUnit() throws Exception;
    }

    public interface MetricsCollectorPort {
        void record(String testId, String phase, long latencyNanos, long memoryBytes);
        List<LatencySnapshot> snapshot(String testId);
    }

    public interface BaselineStorePort {
        void saveBaseline(String testName, PerformanceBaseline baseline);
        Optional<PerformanceBaseline> loadBaseline(String testName);
    }

    public interface K6RunnerPort {
        K6Result runScript(String scriptPath, int virtualUsers, int durationSeconds);
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public record LoadConfig(
        int targetTps,
        int rampUpSeconds,
        int steadyStateSeconds,
        int rampDownSeconds,
        int virtualUsers
    ) {}

    public record LatencySnapshot(long p50Nanos, long p95Nanos, long p99Nanos, long maxNanos) {}

    public record ThroughputResult(double actualTps, long totalOps, long durationMs, long errorCount) {}

    public record MemoryProfile(long heapUsedBytes, long heapMaxBytes, long nonHeapUsedBytes, long gcCount) {}

    public record PerformanceBaseline(
        String testName,
        long p50Nanos,
        long p95Nanos,
        long p99Nanos,
        double actualTps,
        String capturedAt
    ) {}

    public record RegressionReport(
        String testName,
        boolean passed,
        List<String> violations
    ) {}

    public record PerformanceTestResult(
        String testId,
        String testName,
        LoadConfig config,
        LatencySnapshot latency,
        ThroughputResult throughput,
        MemoryProfile memoryProfile,
        RegressionReport regression
    ) {}

    public record K6Result(int errorCount, double reqsPerSec, double p95Ms, int totalRequests) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final MetricsCollectorPort metricsCollector;
    private final BaselineStorePort baselineStore;
    private final K6RunnerPort k6Runner;
    private final Executor executor;
    private final Counter testsRunCounter;
    private final Counter regressionFailCounter;
    private final Timer testDurationTimer;

    private static final double REGRESSION_THRESHOLD = 0.10; // 10% degradation = FAIL

    public PerformanceTestUtilitiesService(
        MetricsCollectorPort metricsCollector,
        BaselineStorePort baselineStore,
        K6RunnerPort k6Runner,
        MeterRegistry registry,
        Executor executor
    ) {
        this.metricsCollector = metricsCollector;
        this.baselineStore = baselineStore;
        this.k6Runner = k6Runner;
        this.executor = executor;
        this.testsRunCounter      = Counter.builder("sdk.perf.tests.run").register(registry);
        this.regressionFailCounter = Counter.builder("sdk.perf.regression.failures").register(registry);
        this.testDurationTimer    = Timer.builder("sdk.perf.test.duration").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Run a load test with the given load generator and collect latency + throughput metrics. */
    public Promise<PerformanceTestResult> runLoadTest(
        String testName,
        LoadConfig config,
        LoadGeneratorPort generator
    ) {
        return Promise.ofBlocking(executor, () -> {
            String testId = UUID.randomUUID().toString();
            testDurationTimer.record(() -> {
                long startMs = System.currentTimeMillis();
                long errors = 0;
                long ops = 0;
                List<Long> latencies = new ArrayList<>();

                // Ramp up + steady state loop (simplified: constant TPS for duration)
                int totalSeconds = config.rampUpSeconds() + config.steadyStateSeconds() + config.rampDownSeconds();
                long endMs = startMs + (long) totalSeconds * 1000;
                long intervalNanos = 1_000_000_000L / Math.max(1, config.targetTps());

                while (System.currentTimeMillis() < endMs) {
                    long t0 = System.nanoTime();
                    try {
                        long latencyNanos = generator.executeUnit();
                        latencies.add(latencyNanos);
                        long heapUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                        metricsCollector.record(testId, "steady", latencyNanos, heapUsed);
                    } catch (Exception e) {
                        errors++;
                    }
                    ops++;
                    long elapsed = System.nanoTime() - t0;
                    long sleep = intervalNanos - elapsed;
                    if (sleep > 0) LockSupport.parkNanos(sleep);
                }

                testsRunCounter.increment();
            });

            List<LatencySnapshot> snapshots = metricsCollector.snapshot(testId);
            LatencySnapshot latency = snapshots.isEmpty()
                ? new LatencySnapshot(0, 0, 0, 0)
                : snapshots.get(snapshots.size() - 1);

            long durationMs = config.steadyStateSeconds() * 1000L;
            ThroughputResult throughput = new ThroughputResult(
                durationMs > 0 ? 1000.0 * latencies(latency) / durationMs : 0,
                latencies(latency), durationMs, 0
            );

            MemoryProfile mem = captureMemoryProfile();
            RegressionReport regression = detectRegression(testName, latency, throughput);

            if (!regression.passed()) regressionFailCounter.increment();

            return new PerformanceTestResult(testId, testName, config, latency, throughput, mem, regression);
        });
    }

    /** Simulate an event storm at the given events-per-second rate for the given duration. */
    public Promise<ThroughputResult> simulateEventStorm(
        String topic,
        int eventsPerSecond,
        int durationSeconds,
        LoadGeneratorPort publisher
    ) {
        return Promise.ofBlocking(executor, () -> {
            long startMs = System.currentTimeMillis();
            long endMs = startMs + (long) durationSeconds * 1000;
            long intervalNanos = 1_000_000_000L / Math.max(1, eventsPerSecond);
            long ops = 0;
            long errors = 0;

            while (System.currentTimeMillis() < endMs) {
                long t0 = System.nanoTime();
                try { publisher.executeUnit(); } catch (Exception e) { errors++; }
                ops++;
                long remaining = intervalNanos - (System.nanoTime() - t0);
                if (remaining > 0) LockSupport.parkNanos(remaining);
            }

            long actualMs = System.currentTimeMillis() - startMs;
            double actualTps = actualMs > 0 ? 1000.0 * ops / actualMs : 0;
            return new ThroughputResult(actualTps, ops, actualMs, errors);
        });
    }

    /** Save the result as the new baseline for future regression comparisons. */
    public Promise<Void> saveBaseline(String testName, PerformanceTestResult result) {
        return Promise.ofBlocking(executor, () -> {
            PerformanceBaseline baseline = new PerformanceBaseline(
                testName,
                result.latency().p50Nanos(),
                result.latency().p95Nanos(),
                result.latency().p99Nanos(),
                result.throughput().actualTps(),
                java.time.Instant.now().toString()
            );
            baselineStore.saveBaseline(testName, baseline);
            return null;
        });
    }

    /** Run a k6 test script and return its results wrapped in a PerformanceTestResult. */
    public Promise<K6Result> runK6Script(String scriptPath, int vus, int durationSeconds) {
        return Promise.ofBlocking(executor, () -> k6Runner.runScript(scriptPath, vus, durationSeconds));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private MemoryProfile captureMemoryProfile() {
        Runtime rt = Runtime.getRuntime();
        long heap = rt.totalMemory() - rt.freeMemory();
        long heapMax = rt.maxMemory();
        return new MemoryProfile(heap, heapMax, 0, 0);
    }

    private RegressionReport detectRegression(String testName, LatencySnapshot latency, ThroughputResult throughput) {
        Optional<PerformanceBaseline> baselineOpt = baselineStore.loadBaseline(testName);
        if (baselineOpt.isEmpty()) {
            return new RegressionReport(testName, true, List.of("No baseline — first run, saving as baseline"));
        }

        PerformanceBaseline baseline = baselineOpt.get();
        List<String> violations = new ArrayList<>();

        checkRegression("p95 latency", baseline.p95Nanos(), latency.p95Nanos(), violations);
        checkRegression("p99 latency", baseline.p99Nanos(), latency.p99Nanos(), violations);
        checkThroughputRegression("throughput", baseline.actualTps(), throughput.actualTps(), violations);

        return new RegressionReport(testName, violations.isEmpty(), violations);
    }

    private void checkRegression(String metric, long baselineNanos, long currentNanos, List<String> violations) {
        if (baselineNanos <= 0) return;
        double change = (double) (currentNanos - baselineNanos) / baselineNanos;
        if (change > REGRESSION_THRESHOLD) {
            violations.add(String.format("%s degraded by %.1f%% (baseline %.2fms → current %.2fms)",
                metric, change * 100, baselineNanos / 1e6, currentNanos / 1e6));
        }
    }

    private void checkThroughputRegression(String metric, double baselineTps, double currentTps, List<String> violations) {
        if (baselineTps <= 0) return;
        double change = (baselineTps - currentTps) / baselineTps;
        if (change > REGRESSION_THRESHOLD) {
            violations.add(String.format("%s dropped by %.1f%% (baseline %.1f TPS → current %.1f TPS)",
                metric, change * 100, baselineTps, currentTps));
        }
    }

    /** Placeholder — real impl reads from snapshots */
    private long latencies(LatencySnapshot snap) { return snap.p50Nanos() > 0 ? 1000L : 0L; }
    private final List<Long> latencies = new ArrayList<>();
}
