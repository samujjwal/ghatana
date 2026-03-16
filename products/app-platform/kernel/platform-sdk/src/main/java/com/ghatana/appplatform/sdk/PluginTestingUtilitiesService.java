package com.ghatana.appplatform.sdk;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Provides an isolated sandbox for plugin unit testing: mock dependencies,
 *              sandbox violation simulation, and plugin benchmarking.
 * @doc.layer   Platform SDK (K-12)
 * @doc.pattern Port-Adapter with inner interfaces; Promise.ofBlocking for all I/O
 *
 * STORY-K12-008: Plugin testing utilities
 * Provides: TestSandbox for isolated execution, mock market data / rules engine,
 * SandboxViolationSimulator, and per-plugin benchmark runner.
 */
public class PluginTestingUtilitiesService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface SandboxRuntimePort {
        /** Load and run a plugin JAR in isolation. Returns stdout/stderr. */
        SandboxRunResult run(SandboxExecutionRequest request) throws Exception;
        /** Terminate a running sandbox by ID. */
        void terminate(String sandboxId) throws Exception;
    }

    public interface MockDependencyPort {
        /** Register a mock response for a given dependency method call. */
        void registerMock(String dependency, String method, Object responsePayload);
        /** Record all calls made to a dependency during a sandbox run. */
        List<RecordedCall> getCalls(String sandboxId, String dependency);
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public record SandboxExecutionRequest(
        String sandboxId,
        String pluginJarPath,
        String entryClass,
        Map<String, Object> inputContext,
        List<String> allowedDependencies,
        long timeoutMs
    ) {}

    public record SandboxRunResult(
        String sandboxId,
        boolean success,
        Object output,
        String stdout,
        String stderr,
        long executionNanos,
        long peakMemoryBytes,
        List<String> violations
    ) {}

    public record RecordedCall(String sandboxId, String dependency, String method, Object args, long calledAtNanos) {}

    public enum ViolationType { NETWORK_ACCESS, FILESYSTEM_ACCESS, SYSCALL_DENIED, MEMORY_EXCEEDED, CPU_EXCEEDED, TIMEOUT }

    public record ViolationSimulationResult(ViolationType type, boolean detectedByRuntime, long detectedAtNanos) {}

    public record BenchmarkResult(
        String pluginJarPath,
        int iterations,
        long p50Nanos, long p95Nanos, long p99Nanos,
        long minNanos, long maxNanos,
        long peakMemoryBytes
    ) {}

    public record TestAssertion(String label, boolean passed, String detail) {}

    public record PluginTestReport(
        String pluginJarPath,
        String runId,
        boolean allPassed,
        List<SandboxRunResult> runs,
        List<TestAssertion> assertions,
        BenchmarkResult benchmark
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final SandboxRuntimePort sandboxRuntime;
    private final MockDependencyPort mockDependency;
    private final Executor executor;
    private final Counter testsRunCounter;
    private final Counter violationsDetectedCounter;

    public PluginTestingUtilitiesService(
        SandboxRuntimePort sandboxRuntime,
        MockDependencyPort mockDependency,
        MeterRegistry registry,
        Executor executor
    ) {
        this.sandboxRuntime = sandboxRuntime;
        this.mockDependency = mockDependency;
        this.executor = executor;
        this.testsRunCounter         = Counter.builder("sdk.plugin.tests.run").register(registry);
        this.violationsDetectedCounter = Counter.builder("sdk.plugin.violations.detected").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Execute a plugin in an isolated sandbox with the provided input context and
     * pre-registered mock dependencies.  Returns the full run result including any violations.
     */
    public Promise<SandboxRunResult> runInSandbox(
        String pluginJarPath,
        String entryClass,
        Map<String, Object> input,
        List<String> allowedDeps,
        long timeoutMs
    ) {
        return Promise.ofBlocking(executor, () -> {
            String sandboxId = UUID.randomUUID().toString();
            SandboxExecutionRequest req = new SandboxExecutionRequest(
                sandboxId, pluginJarPath, entryClass, input, allowedDeps, timeoutMs
            );
            SandboxRunResult result = sandboxRuntime.run(req);
            testsRunCounter.increment();
            violationsDetectedCounter.increment(result.violations().size());
            return result;
        });
    }

    /**
     * Register a mock for a dependency method so that sandbox runs use it instead of
     * the real adapter.
     */
    public Promise<Void> registerMock(String dependency, String method, Object responsePayload) {
        return Promise.ofBlocking(executor, () -> {
            mockDependency.registerMock(dependency, method, responsePayload);
            return null;
        });
    }

    /**
     * Trigger a simulated sandbox violation and verify the runtime detects it.
     */
    public Promise<ViolationSimulationResult> simulateViolation(
        String sandboxId,
        ViolationType type
    ) {
        return Promise.ofBlocking(executor, () -> {
            long before = System.nanoTime();
            // Fire violation-inducing input based on type
            Map<String, Object> violationInput = Map.of("_simulate_violation", type.name());
            SandboxExecutionRequest req = new SandboxExecutionRequest(
                sandboxId, "", "", violationInput, List.of(), 5000L
            );
            SandboxRunResult result = sandboxRuntime.run(req);
            boolean detected = result.violations().stream()
                .anyMatch(v -> v.contains(type.name()));
            if (detected) violationsDetectedCounter.increment();
            return new ViolationSimulationResult(type, detected, System.nanoTime() - before);
        });
    }

    /**
     * Benchmark a plugin over N iterations, collecting latency percentiles and peak memory.
     */
    public Promise<BenchmarkResult> benchmark(
        String pluginJarPath,
        String entryClass,
        Map<String, Object> input,
        int iterations
    ) {
        return Promise.ofBlocking(executor, () -> {
            List<Long> latencies = new ArrayList<>(iterations);
            long peakMem = 0;

            for (int i = 0; i < iterations; i++) {
                String sandboxId = UUID.randomUUID().toString();
                SandboxExecutionRequest req = new SandboxExecutionRequest(
                    sandboxId, pluginJarPath, entryClass, input, List.of(), 30000L
                );
                SandboxRunResult res = sandboxRuntime.run(req);
                latencies.add(res.executionNanos());
                if (res.peakMemoryBytes() > peakMem) peakMem = res.peakMemoryBytes();
            }

            Collections.sort(latencies);
            return new BenchmarkResult(
                pluginJarPath,
                iterations,
                percentile(latencies, 50),
                percentile(latencies, 95),
                percentile(latencies, 99),
                latencies.isEmpty() ? 0L : latencies.get(0),
                latencies.isEmpty() ? 0L : latencies.get(latencies.size() - 1),
                peakMem
            );
        });
    }

    /**
     * Run a full test report: multiple runs + assertions + benchmark.
     */
    public Promise<PluginTestReport> runTestReport(
        String pluginJarPath,
        String entryClass,
        List<Map<String, Object>> testInputs,
        List<TestAssertion> staticAssertions,
        int benchmarkIterations
    ) {
        return Promise.ofBlocking(executor, () -> {
            String runId = UUID.randomUUID().toString();
            List<SandboxRunResult> runs = new ArrayList<>();

            for (Map<String, Object> input : testInputs) {
                String sandboxId = UUID.randomUUID().toString();
                SandboxExecutionRequest req = new SandboxExecutionRequest(
                    sandboxId, pluginJarPath, entryClass, input, List.of(), 30000L
                );
                runs.add(sandboxRuntime.run(req));
            }

            // Auto-assertions from run results
            List<TestAssertion> assertions = new ArrayList<>(staticAssertions);
            long failedRuns = runs.stream().filter(r -> !r.success()).count();
            assertions.add(new TestAssertion("All runs succeed", failedRuns == 0,
                failedRuns + " run(s) failed"));
            assertions.add(new TestAssertion("No sandbox violations",
                runs.stream().allMatch(r -> r.violations().isEmpty()),
                "Violations found in some runs"));

            boolean allPassed = assertions.stream().allMatch(TestAssertion::passed);

            // Benchmark
            BenchmarkResult benchmark = new BenchmarkResult(pluginJarPath, 0, 0, 0, 0, 0, 0, 0);
            if (benchmarkIterations > 0) {
                Map<String, Object> benchInput = testInputs.isEmpty() ? Map.of() : testInputs.get(0);
                benchmark = (BenchmarkResult) benchmark(pluginJarPath, entryClass, benchInput, benchmarkIterations).get();
            }

            return new PluginTestReport(pluginJarPath, runId, allPassed, runs, assertions, benchmark);
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
}
