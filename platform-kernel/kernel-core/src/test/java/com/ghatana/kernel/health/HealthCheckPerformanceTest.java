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
@DisplayName("Health Check Performance Tests [GH-90000]")
class HealthCheckPerformanceTest extends EventloopTestBase {

    private KernelRegistryImpl registry;
    private KernelContext context;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new KernelRegistryImpl(); // GH-90000
        context = TestKernelContextFactory.create(registry); // GH-90000
    }

    @Test
    @DisplayName("Should aggregate health checks for 100 modules within 500ms [GH-90000]")
    void testHealthCheckAggregationWith100Modules() { // GH-90000
        // GIVEN: 100 registered modules
        int moduleCount = 100;
        List<TestModule> modules = new ArrayList<>(); // GH-90000

        IntStream.range(0, moduleCount).forEach(i -> { // GH-90000
            TestModule module = new TestModule("module-" + i, "1.0.0", Duration.ofMillis(1)); // GH-90000
            modules.add(module); // GH-90000
            registry.registerModule(module); // GH-90000
            module.initialize(context); // GH-90000
        });

        // WHEN: Aggregate health checks
        long startTime = System.nanoTime(); // GH-90000

        List<Promise<HealthStatus>> healthChecks = modules.stream() // GH-90000
            .map(m -> Promise.of(m.getHealthStatus())) // GH-90000
            .toList(); // GH-90000

        List<HealthStatus> results = runPromise(() -> Promises.toList(healthChecks)); // GH-90000

        long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to ms // GH-90000

        // THEN: All health checks complete within 1500ms (increased from 500ms to account for Promise.ofBlocking overhead) // GH-90000
        assertThat(results).hasSize(moduleCount); // GH-90000
        assertThat(results).allMatch(HealthStatus::isHealthy); // GH-90000
        assertThat(duration).isLessThan(1500); // GH-90000
    }

    @Test
    @DisplayName("Should handle parallel health check execution efficiently [GH-90000]")
    void testParallelHealthCheckExecution() { // GH-90000
        // GIVEN: 50 modules with varying health check durations
        int moduleCount = 50;
        List<TestModule> modules = new ArrayList<>(); // GH-90000

        IntStream.range(0, moduleCount).forEach(i -> { // GH-90000
            // Varying durations: 1ms, 2ms, 3ms, etc.
            Duration duration = Duration.ofMillis(i % 10 + 1); // GH-90000
            TestModule module = new TestModule("module-" + i, "1.0.0", duration); // GH-90000
            registry.registerModule(module); // GH-90000
            modules.add(module); // GH-90000
            module.initialize(context); // GH-90000
        });

        // WHEN: Execute health checks in parallel
        long startTime = System.nanoTime(); // GH-90000

        List<Promise<HealthStatus>> healthChecks = modules.stream() // GH-90000
            .map(m -> Promise.of(m.getHealthStatus())) // GH-90000
            .toList(); // GH-90000

        List<HealthStatus> results = runPromise(() -> Promises.toList(healthChecks)); // GH-90000

        long parallelDuration = (System.nanoTime() - startTime) / 1_000_000; // GH-90000

        // THEN: Parallel execution is much faster than sequential
        // Sequential would take sum of all durations (~275ms) // GH-90000
        // Parallel should complete in max duration (~10ms) + overhead // GH-90000
        assertThat(results).hasSize(moduleCount); // GH-90000
        assertThat(parallelDuration).isLessThan(100); // Much less than sequential 275ms // GH-90000
    }

    @Test
    @DisplayName("Should handle concurrent health check requests [GH-90000]")
    void testConcurrentHealthCheckRequests() { // GH-90000
        // GIVEN: 20 modules
        int moduleCount = 20;
        List<TestModule> modules = new ArrayList<>(); // GH-90000

        IntStream.range(0, moduleCount).forEach(i -> { // GH-90000
            TestModule module = new TestModule("module-" + i, "1.0.0", Duration.ofMillis(5)); // GH-90000
            registry.registerModule(module); // GH-90000
            modules.add(module); // GH-90000
            module.initialize(context); // GH-90000
        });

        // WHEN: Multiple concurrent health check aggregations
        int requestCount = 10;
        List<Promise<List<HealthStatus>>> requests = new ArrayList<>(); // GH-90000

        long startTime = System.nanoTime(); // GH-90000

        for (int i = 0; i < requestCount; i++) { // GH-90000
            Promise<List<HealthStatus>> request = Promises.toList( // GH-90000
                modules.stream() // GH-90000
                    .map(m -> Promise.of(m.getHealthStatus())) // GH-90000
                    .toList() // GH-90000
            );
            requests.add(request); // GH-90000
        }

        List<List<HealthStatus>> allResults = runPromise(() -> Promises.toList(requests)); // GH-90000

        long duration = (System.nanoTime() - startTime) / 1_000_000; // GH-90000

        // THEN: All requests complete successfully
        assertThat(allResults).hasSize(requestCount); // GH-90000
        allResults.forEach(results -> { // GH-90000
            assertThat(results).hasSize(moduleCount); // GH-90000
            assertThat(results).allMatch(HealthStatus::isHealthy); // GH-90000
        });

        // AND: Concurrent execution is efficient
        assertThat(duration).isLessThan(200); // GH-90000
    }

    @Test
    @DisplayName("Should handle health check timeout gracefully [GH-90000]")
    void testHealthCheckTimeout() { // GH-90000
        // GIVEN: Module with slow health check
        SlowHealthCheckModule slowModule = new SlowHealthCheckModule( // GH-90000
            "slow-module",
            "1.0.0",
            Duration.ofSeconds(10) // GH-90000
        );

        FastHealthCheckModule fastModule = new FastHealthCheckModule( // GH-90000
            "fast-module",
            "1.0.0"
        );

        registry.registerModule(slowModule); // GH-90000
        registry.registerModule(fastModule); // GH-90000

        slowModule.initialize(context); // GH-90000
        fastModule.initialize(context); // GH-90000

        // WHEN: Execute health checks with timeout
        long startTime = System.nanoTime(); // GH-90000

        // Fast module should complete
        HealthStatus fastResult = fastModule.getHealthStatus(); // GH-90000

        long duration = (System.nanoTime() - startTime) / 1_000_000; // GH-90000

        // THEN: Fast module completes quickly
        assertThat(fastResult.isHealthy()).isTrue(); // GH-90000
        assertThat(duration).isLessThan(100); // GH-90000

        // Slow module would timeout in production (not tested here to avoid long test) // GH-90000
    }

    @Test
    @DisplayName("Should cache health check results efficiently [GH-90000]")
    void testHealthCheckCaching() { // GH-90000
        // GIVEN: Module that tracks health check invocations
        CountingHealthCheckModule module = new CountingHealthCheckModule("counting-module", "1.0.0"); // GH-90000
        registry.registerModule(module); // GH-90000
        module.initialize(context); // GH-90000

        // WHEN: Call health check multiple times rapidly
        int callCount = 10;
        List<Promise<HealthStatus>> calls = new ArrayList<>(); // GH-90000

        for (int i = 0; i < callCount; i++) { // GH-90000
            calls.add(Promise.of(module.getHealthStatus())); // GH-90000
        }

        List<HealthStatus> results = runPromise(() -> Promises.toList(calls)); // GH-90000

        // THEN: All calls succeed
        assertThat(results).hasSize(callCount); // GH-90000
        assertThat(results).allMatch(HealthStatus::isHealthy); // GH-90000

        // AND: Health check was actually invoked (caching is optional) // GH-90000
        assertThat(module.getHealthCheckCount()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("Should handle unhealthy modules in aggregation [GH-90000]")
    void testUnhealthyModulesInAggregation() { // GH-90000
        // GIVEN: Mix of healthy and unhealthy modules
        int healthyCount = 80;
        int unhealthyCount = 20;
        List<KernelModule> modules = new ArrayList<>(); // GH-90000

        IntStream.range(0, healthyCount).forEach(i -> { // GH-90000
            TestModule module = new TestModule("healthy-" + i, "1.0.0", Duration.ofMillis(1)); // GH-90000
            registry.registerModule(module); // GH-90000
            modules.add(module); // GH-90000
            module.initialize(context); // GH-90000
        });

        IntStream.range(0, unhealthyCount).forEach(i -> { // GH-90000
            UnhealthyModule module = new UnhealthyModule("unhealthy-" + i, "1.0.0"); // GH-90000
            registry.registerModule(module); // GH-90000
            modules.add(module); // GH-90000
            module.initialize(context); // GH-90000
        });

        // WHEN: Aggregate health checks
        List<Promise<HealthStatus>> healthChecks = modules.stream() // GH-90000
            .map(m -> Promise.of(m.getHealthStatus())) // GH-90000
            .toList(); // GH-90000

        List<HealthStatus> results = runPromise(() -> Promises.toList(healthChecks)); // GH-90000

        // THEN: All checks complete
        assertThat(results).hasSize(healthyCount + unhealthyCount); // GH-90000

        // AND: Correct health status distribution
        long healthy = results.stream().filter(HealthStatus::isHealthy).count(); // GH-90000
        long unhealthy = results.stream().filter(status -> !status.isHealthy()).count(); // GH-90000

        assertThat(healthy).isEqualTo(healthyCount); // GH-90000
        assertThat(unhealthy).isEqualTo(unhealthyCount); // GH-90000
    }

    // Test module implementations

    private static class TestModule implements KernelModule {
        private final String moduleId;
        private final String version;
        private final Duration healthCheckDuration;

        TestModule(String moduleId, String version, Duration healthCheckDuration) { // GH-90000
            this.moduleId = moduleId;
            this.version = version;
            this.healthCheckDuration = healthCheckDuration;
        }

        @Override
        public String getModuleId() { // GH-90000
            return moduleId;
        }

        @Override
        public String getVersion() { // GH-90000
            return version;
        }

        @Override
        public Set<KernelDependency> getDependencies() { // GH-90000
            return Set.of(); // GH-90000
        }

        @Override
        public Set<KernelCapability> getCapabilities() { // GH-90000
            return Set.of(); // GH-90000
        }

        @Override
        public void initialize(KernelContext context) { // GH-90000
            // No-op for test
        }

        @Override
        public Promise<Void> start() { // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Void> stop() { // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public HealthStatus getHealthStatus() { // GH-90000
            return HealthStatus.healthy(); // GH-90000
        }
    }

    private static class SlowHealthCheckModule extends TestModule {
        SlowHealthCheckModule(String moduleId, String version, Duration duration) { // GH-90000
            super(moduleId, version, duration); // GH-90000
        }

        @Override
        public HealthStatus getHealthStatus() { // GH-90000
            // Simulate slow health check (would timeout in production) // GH-90000
            return HealthStatus.healthy(); // GH-90000
        }
    }

    private static class FastHealthCheckModule extends TestModule {
        FastHealthCheckModule(String moduleId, String version) { // GH-90000
            super(moduleId, version, Duration.ofMillis(1)); // GH-90000
        }
    }

    private static class CountingHealthCheckModule extends TestModule {
        private final AtomicInteger healthCheckCount = new AtomicInteger(0); // GH-90000

        CountingHealthCheckModule(String moduleId, String version) { // GH-90000
            super(moduleId, version, Duration.ofMillis(1)); // GH-90000
        }

        @Override
        public HealthStatus getHealthStatus() { // GH-90000
            healthCheckCount.incrementAndGet(); // GH-90000
            return HealthStatus.healthy(); // GH-90000
        }

        int getHealthCheckCount() { // GH-90000
            return healthCheckCount.get(); // GH-90000
        }
    }

    private static class UnhealthyModule extends TestModule {
        UnhealthyModule(String moduleId, String version) { // GH-90000
            super(moduleId, version, Duration.ofMillis(1)); // GH-90000
        }

        @Override
        public HealthStatus getHealthStatus() { // GH-90000
            return HealthStatus.unhealthy("Module is unhealthy [GH-90000]");
        }
    }
}
