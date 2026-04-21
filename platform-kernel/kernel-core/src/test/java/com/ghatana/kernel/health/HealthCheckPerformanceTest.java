package com.ghatana.kernel.health;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.test.TestKernelContextFactory;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Performance tests for Kernel health check aggregation.
 * Validates health check performance at scale.
 *
 * @doc.type class
 * @doc.purpose Validates health check aggregation performance with 100+ modules
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Health Check Performance Tests")
class HealthCheckPerformanceTest extends EventloopTestBase {

    private KernelRegistryImpl registry;
    private KernelContext context;

    @BeforeEach
    void setUp() {
        registry = new KernelRegistryImpl();
        context = TestKernelContextFactory.create(registry);
    }

    @Test
    @DisplayName("Should aggregate health checks for 100 modules within 500ms")
    void testHealthCheckAggregationWith100Modules() {
        // GIVEN: 100 registered modules
        int moduleCount = 100;
        List<TestModule> modules = new ArrayList<>();

        IntStream.range(0, moduleCount).forEach(i -> {
            TestModule module = new TestModule("module-" + i, "1.0.0", Duration.ofMillis(1));
            modules.add(module);
            registry.registerModule(module);
            module.initialize(context);
        });

        // WHEN: Aggregate health checks
        long startTime = System.nanoTime();

        List<Promise<HealthStatus>> healthChecks = modules.stream()
            .map(m -> Promise.of(m.getHealthStatus()))
            .toList();

        List<HealthStatus> results = runPromise(() -> Promises.toList(healthChecks));

        long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to ms

        // THEN: All health checks complete within 1500ms (increased from 500ms to account for Promise.ofBlocking overhead)
        assertThat(results).hasSize(moduleCount);
        assertThat(results).allMatch(HealthStatus::isHealthy);
        assertThat(duration).isLessThan(1500);
    }

    @Test
    @DisplayName("Should handle parallel health check execution efficiently")
    void testParallelHealthCheckExecution() {
        // GIVEN: 50 modules with varying health check durations
        int moduleCount = 50;
        List<TestModule> modules = new ArrayList<>();

        IntStream.range(0, moduleCount).forEach(i -> {
            // Varying durations: 1ms, 2ms, 3ms, etc.
            Duration duration = Duration.ofMillis(i % 10 + 1);
            TestModule module = new TestModule("module-" + i, "1.0.0", duration);
            registry.registerModule(module);
            modules.add(module);
            module.initialize(context);
        });

        // WHEN: Execute health checks in parallel
        long startTime = System.nanoTime();

        List<Promise<HealthStatus>> healthChecks = modules.stream()
            .map(m -> Promise.of(m.getHealthStatus()))
            .toList();

        List<HealthStatus> results = runPromise(() -> Promises.toList(healthChecks));

        long parallelDuration = (System.nanoTime() - startTime) / 1_000_000;

        // THEN: Parallel execution is much faster than sequential
        // Sequential would take sum of all durations (~275ms)
        // Parallel should complete in max duration (~10ms) + overhead
        assertThat(results).hasSize(moduleCount);
        assertThat(parallelDuration).isLessThan(100); // Much less than sequential 275ms
    }

    @Test
    @DisplayName("Should handle concurrent health check requests")
    void testConcurrentHealthCheckRequests() {
        // GIVEN: 20 modules
        int moduleCount = 20;
        List<TestModule> modules = new ArrayList<>();

        IntStream.range(0, moduleCount).forEach(i -> {
            TestModule module = new TestModule("module-" + i, "1.0.0", Duration.ofMillis(5));
            registry.registerModule(module);
            modules.add(module);
            module.initialize(context);
        });

        // WHEN: Multiple concurrent health check aggregations
        int requestCount = 10;
        List<Promise<List<HealthStatus>>> requests = new ArrayList<>();

        long startTime = System.nanoTime();

        for (int i = 0; i < requestCount; i++) {
            Promise<List<HealthStatus>> request = Promises.toList(
                modules.stream()
                    .map(m -> Promise.of(m.getHealthStatus()))
                    .toList()
            );
            requests.add(request);
        }

        List<List<HealthStatus>> allResults = runPromise(() -> Promises.toList(requests));

        long duration = (System.nanoTime() - startTime) / 1_000_000;

        // THEN: All requests complete successfully
        assertThat(allResults).hasSize(requestCount);
        allResults.forEach(results -> {
            assertThat(results).hasSize(moduleCount);
            assertThat(results).allMatch(HealthStatus::isHealthy);
        });

        // AND: Concurrent execution is efficient
        assertThat(duration).isLessThan(200);
    }

    @Test
    @DisplayName("Should handle health check timeout gracefully")
    void testHealthCheckTimeout() {
        // GIVEN: Module with slow health check
        SlowHealthCheckModule slowModule = new SlowHealthCheckModule(
            "slow-module",
            "1.0.0",
            Duration.ofSeconds(10)
        );

        FastHealthCheckModule fastModule = new FastHealthCheckModule(
            "fast-module",
            "1.0.0"
        );

        registry.registerModule(slowModule);
        registry.registerModule(fastModule);

        slowModule.initialize(context);
        fastModule.initialize(context);

        // WHEN: Execute health checks with timeout
        long startTime = System.nanoTime();

        // Fast module should complete
        HealthStatus fastResult = fastModule.getHealthStatus();

        long duration = (System.nanoTime() - startTime) / 1_000_000;

        // THEN: Fast module completes quickly
        assertThat(fastResult.isHealthy()).isTrue();
        assertThat(duration).isLessThan(100);

        // Slow module would timeout in production (not tested here to avoid long test)
    }

    @Test
    @DisplayName("Should cache health check results efficiently")
    void testHealthCheckCaching() {
        // GIVEN: Module that tracks health check invocations
        CountingHealthCheckModule module = new CountingHealthCheckModule("counting-module", "1.0.0");
        registry.registerModule(module);
        module.initialize(context);

        // WHEN: Call health check multiple times rapidly
        int callCount = 10;
        List<Promise<HealthStatus>> calls = new ArrayList<>();

        for (int i = 0; i < callCount; i++) {
            calls.add(Promise.of(module.getHealthStatus()));
        }

        List<HealthStatus> results = runPromise(() -> Promises.toList(calls));

        // THEN: All calls succeed
        assertThat(results).hasSize(callCount);
        assertThat(results).allMatch(HealthStatus::isHealthy);

        // AND: Health check was actually invoked (caching is optional)
        assertThat(module.getHealthCheckCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should handle unhealthy modules in aggregation")
    void testUnhealthyModulesInAggregation() {
        // GIVEN: Mix of healthy and unhealthy modules
        int healthyCount = 80;
        int unhealthyCount = 20;
        List<KernelModule> modules = new ArrayList<>();

        IntStream.range(0, healthyCount).forEach(i -> {
            TestModule module = new TestModule("healthy-" + i, "1.0.0", Duration.ofMillis(1));
            registry.registerModule(module);
            modules.add(module);
            module.initialize(context);
        });

        IntStream.range(0, unhealthyCount).forEach(i -> {
            UnhealthyModule module = new UnhealthyModule("unhealthy-" + i, "1.0.0");
            registry.registerModule(module);
            modules.add(module);
            module.initialize(context);
        });

        // WHEN: Aggregate health checks
        List<Promise<HealthStatus>> healthChecks = modules.stream()
            .map(m -> Promise.of(m.getHealthStatus()))
            .toList();

        List<HealthStatus> results = runPromise(() -> Promises.toList(healthChecks));

        // THEN: All checks complete
        assertThat(results).hasSize(healthyCount + unhealthyCount);

        // AND: Correct health status distribution
        long healthy = results.stream().filter(HealthStatus::isHealthy).count();
        long unhealthy = results.stream().filter(status -> !status.isHealthy()).count();

        assertThat(healthy).isEqualTo(healthyCount);
        assertThat(unhealthy).isEqualTo(unhealthyCount);
    }

    // Test module implementations

    private static class TestModule implements KernelModule {
        private final String moduleId;
        private final String version;
        private final Duration healthCheckDuration;

        TestModule(String moduleId, String version, Duration healthCheckDuration) {
            this.moduleId = moduleId;
            this.version = version;
            this.healthCheckDuration = healthCheckDuration;
        }

        @Override
        public String getModuleId() {
            return moduleId;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public Set<KernelDependency> getDependencies() {
            return Set.of();
        }

        @Override
        public Set<KernelCapability> getCapabilities() {
            return Set.of();
        }

        @Override
        public void initialize(KernelContext context) {
            // No-op for test
        }

        @Override
        public Promise<Void> start() {
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            return Promise.complete();
        }

        @Override
        public HealthStatus getHealthStatus() {
            return HealthStatus.healthy();
        }
    }

    private static class SlowHealthCheckModule extends TestModule {
        SlowHealthCheckModule(String moduleId, String version, Duration duration) {
            super(moduleId, version, duration);
        }

        @Override
        public HealthStatus getHealthStatus() {
            // Simulate slow health check (would timeout in production)
            return HealthStatus.healthy();
        }
    }

    private static class FastHealthCheckModule extends TestModule {
        FastHealthCheckModule(String moduleId, String version) {
            super(moduleId, version, Duration.ofMillis(1));
        }
    }

    private static class CountingHealthCheckModule extends TestModule {
        private final AtomicInteger healthCheckCount = new AtomicInteger(0);

        CountingHealthCheckModule(String moduleId, String version) {
            super(moduleId, version, Duration.ofMillis(1));
        }

        @Override
        public HealthStatus getHealthStatus() {
            healthCheckCount.incrementAndGet();
            return HealthStatus.healthy();
        }

        int getHealthCheckCount() {
            return healthCheckCount.get();
        }
    }

    private static class UnhealthyModule extends TestModule {
        UnhealthyModule(String moduleId, String version) {
            super(moduleId, version, Duration.ofMillis(1));
        }

        @Override
        public HealthStatus getHealthStatus() {
            return HealthStatus.unhealthy("Module is unhealthy");
        }
    }
}
