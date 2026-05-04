# Kernel Platform - Detailed Implementation Plan

## Overview

**Product**: Platform Kernel (`platform/java/kernel`)  
**Current Status**: Production-Ready Core Platform Module (~85% test coverage)  
**Target**: 100% test coverage with performance benchmarking  
**Timeline**: Week 2 of comprehensive implementation plan

---

## Current State Analysis

### Source Files Inventory (32 files)

#### Core Kernel Components

| Component              | Files | Test Status      |
| ---------------------- | ----- | ---------------- |
| Registry               | 2     | Well tested      |
| Context                | 2     | Well tested      |
| Descriptors            | 2     | Well tested      |
| Lifecycle              | 1     | Well tested      |
| Adapters               | 17    | Partially tested |
| AI Framework           | 4     | Partially tested |
| Boundary/Communication | 4     | Partially tested |

### Test Files Inventory (29 files)

#### Existing Test Coverage

```
Unit Tests:
- KernelRegistryTest.java
- KernelDescriptorTest.java
- KernelCapabilityTest.java
- DefaultKernelContextTest.java
- TypedDataSerializerTest.java

Integration Tests:
- KernelLifecycleIntegrationTest.java
- KernelModuleIntegrationTest.java
- AIFrameworkIntegrationTest.java
- SecurityFrameworkIntegrationTest.java
- ObservabilityFrameworkIntegrationTest.java
- ContractValidationIntegrationTest.java
- KernelCrossProductEventIntegrationTest.java

Registry Tests:
- KernelRegistryImplTest.java
- KernelRegistryBoundaryClasspathTest.java

Validation Tests:
- KernelPurityValidationTest.java
- KernelPurityEnforcementTest.java
- KernelArchitectureDriftTest.java
- RegulatoryComplianceTest.java

End-to-End Tests:
- KernelEndToEndTest.java

Performance Tests:
- KernelPerformanceBenchmark.java

Contract Tests:
- KernelAdapterClientContractTest.java
- CrossScopeAuditServiceTest.java
- ContractValidatorValidationResultTest.java
```

### Coverage Gaps Identified

1. **Concurrency Testing** (Critical)
   - Concurrent module registration
   - Race conditions in capability discovery
   - Thread-safety of registry operations

2. **Edge Cases** (High Priority)
   - Circular dependency detection
   - Partial initialization failure handling
   - Graceful shutdown with hanging modules

3. **Performance at Scale** (Medium Priority)
   - Health check aggregation with 100+ modules
   - Event bus throughput under load
   - Dependency resolution latency with complex graphs

4. **Adapter Testing** (Medium Priority)
   - AepKernelAdapter edge cases
   - DataCloudKernelAdapter error handling
   - Cross-product communication failure scenarios

---

## Implementation Tasks

### Task 1: Concurrency Test Suite

**File**: `src/test/java/com/ghatana/kernel/concurrency/KernelConcurrencyTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Validates thread-safety of kernel registry operations
 * @doc.layer platform
 * @doc.scenario concurrent access
 */
@DisplayName("Kernel Registry Concurrency Tests")
class KernelConcurrencyTest extends EventloopTestBase {

    private KernelRegistry registry;
    private ExecutorService executor;

    @BeforeEach
    void setup() {
        registry = KernelRegistry.create();
        executor = Executors.newFixedThreadPool(10);
    }

    /**
     * @doc.test_type stress
     * @doc.coverage line, branch
     */
    @Test
    void testConcurrentModuleRegistration() {
        int moduleCount = 100;
        CountDownLatch latch = new CountDownLatch(moduleCount);
        List<Future<KernelModuleDescriptor>> futures = new ArrayList<>();

        for (int i = 0; i < moduleCount; i++) {
            final int index = i;
            Future<KernelModuleDescriptor> future = executor.submit(() -> {
                try {
                    TestModule module = new TestModule("module-" + index);
                    KernelModuleDescriptor descriptor = KernelModuleDescriptor.builder()
                        .id("module-" + index)
                        .name("Test Module " + index)
                        .version("1.0.0")
                        .capabilities(Set.of("capability-" + index))
                        .build();

                    return runPromise(() -> registry.register(descriptor, module));
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        awaitLatch(latch, 30, TimeUnit.SECONDS);

        // Verify all registrations succeeded
        assertThat(futures).allMatch(f -> {
            try {
                return f.get(5, TimeUnit.SECONDS) != null;
            } catch (Exception e) {
                return false;
            }
        });

        // Verify registry state
        assertThat(registry.getAllModules()).hasSize(moduleCount);
    }

    /**
     * @doc.test_type stress
     * @doc.coverage line, branch, path
     */
    @Test
    void testConcurrentCapabilityDiscovery() {
        // Setup modules with overlapping capabilities
        Set<String> sharedCapability = Set.of("shared-capability");

        List<KernelModuleDescriptor> descriptors = IntStream.range(0, 50)
            .mapToObj(i -> KernelModuleDescriptor.builder()
                .id("cap-module-" + i)
                .name("Capability Module " + i)
                .version("1.0.0")
                .capabilities(i % 2 == 0 ? sharedCapability : Set.of("unique-" + i))
                .build())
            .toList();

        CountDownLatch registerLatch = new CountDownLatch(descriptors.size());
        CountDownLatch discoverLatch = new CountDownLatch(100);

        // Register modules concurrently
        descriptors.forEach(desc -> executor.submit(() -> {
            try {
                runPromise(() -> registry.register(desc, new TestModule(desc.id())));
            } finally {
                registerLatch.countDown();
            }
        }));

        awaitLatch(registerLatch, 10, TimeUnit.SECONDS);

        // Concurrent capability discovery
        List<Future<Set<KernelModuleDescriptor>>> discoveryFutures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Future<Set<KernelModuleDescriptor>> future = executor.submit(() -> {
                try {
                    return runPromise(() -> registry.findByCapability("shared-capability"));
                } finally {
                    discoverLatch.countDown();
                }
            });
            discoveryFutures.add(future);
        }

        awaitLatch(discoverLatch, 30, TimeUnit.SECONDS);

        // All discoveries should return same result
        Set<KernelModuleDescriptor> expected = registry.findByCapability("shared-capability");
        assertThat(discoveryFutures).allMatch(f -> {
            try {
                return f.get(5, TimeUnit.SECONDS).equals(expected);
            } catch (Exception e) {
                return false;
            }
        });
    }

    /**
     * @doc.test_type stress
     * @doc.coverage path
     */
    @Test
    void testDependencyResolutionUnderConcurrentLoad() {
        // Create module graph with dependencies
        KernelModuleDescriptor root = createDescriptor("root", Set.of(), Set.of());
        List<KernelModuleDescriptor> children = IntStream.range(0, 20)
            .mapToObj(i -> createDescriptor("child-" + i, Set.of(), Set.of("root")))
            .toList();
        List<KernelModuleDescriptor> grandchildren = IntStream.range(0, 40)
            .mapToObj(i -> createDescriptor("grandchild-" + i, Set.of(),
                Set.of("child-" + (i % 20))))
            .toList();

        CountDownLatch latch = new CountDownLatch(61);
        List<Future<?>> futures = new ArrayList<>();

        // Register all concurrently
        Stream.of(Stream.of(root), children.stream(), grandchildren.stream())
            .flatMap(Function.identity())
            .forEach(desc -> {
                Future<?> future = executor.submit(() -> {
                    try {
                        runPromise(() -> registry.register(desc, new TestModule(desc.id())));
                    } finally {
                        latch.countDown();
                    }
                });
                futures.add(future);
            });

        awaitLatch(latch, 30, TimeUnit.SECONDS);

        // Resolve dependencies concurrently
        List<Future<List<KernelModuleDescriptor>>> resolutionFutures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Future<List<KernelModuleDescriptor>> future = executor.submit(() ->
                runPromise(() -> registry.resolveDependencies("grandchild-" + new Random().nextInt(40)))
            );
            resolutionFutures.add(future);
        }

        // Verify all resolutions succeed
        assertThat(resolutionFutures).allSatisfy(f -> {
            try {
                List<KernelModuleDescriptor> resolved = f.get(5, TimeUnit.SECONDS);
                assertThat(resolved).isNotNull();
                assertThat(resolved.size()).isGreaterThanOrEqualTo(2); // At least parent and grandparent
            } catch (Exception e) {
                fail("Resolution failed: " + e.getMessage());
            }
        });
    }

    private KernelModuleDescriptor createDescriptor(String id, Set<String> capabilities, Set<String> dependencies) {
        return KernelModuleDescriptor.builder()
            .id(id)
            .name("Test " + id)
            .version("1.0.0")
            .capabilities(capabilities)
            .dependencies(dependencies)
            .build();
    }

    private void awaitLatch(CountDownLatch latch, long timeout, TimeUnit unit) {
        try {
            assertThat(latch.await(timeout, unit)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted");
        }
    }

    @AfterEach
    void cleanup() {
        executor.shutdown();
    }
}
```

**Additional Concurrency Tests:**

1. `ConcurrentLifecycleTest.java` - Tests concurrent start/stop operations
2. `ConcurrentEventBusTest.java` - Tests concurrent event publishing/subscribing
3. `ConcurrentAdapterTest.java` - Tests concurrent adapter client operations

---

### Task 2: Circular Dependency Detection

**File**: `src/test/java/com/ghatana/kernel/dependency/CircularDependencyDetectionTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Validates circular dependency detection and handling
 * @doc.layer platform
 * @doc.scenario error condition
 */
@DisplayName("Circular Dependency Detection Tests")
class CircularDependencyDetectionTest extends EventloopTestBase {

    private KernelRegistry registry;

    @BeforeEach
    void setup() {
        registry = KernelRegistry.create();
    }

    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testSimpleCircularDependency() {
        // A -> B -> A
        KernelModuleDescriptor moduleA = createDescriptorWithDeps("module-A", Set.of("module-B"));
        KernelModuleDescriptor moduleB = createDescriptorWithDeps("module-B", Set.of("module-A"));

        runPromise(() -> registry.register(moduleA, new TestModule("module-A")));
        runPromise(() -> registry.register(moduleB, new TestModule("module-B")));

        assertThatThrownBy(() ->
            runPromise(() -> registry.resolveDependencies("module-A"))
        )
            .isInstanceOf(KernelException.class)
            .hasMessageContaining("circular dependency detected")
            .hasMessageContaining("module-A")
            .hasMessageContaining("module-B");
    }

    /**
     * @doc.test_type negative
     * @doc.coverage path
     */
    @Test
    void testComplexCircularDependency() {
        // A -> B -> C -> D -> B (cycle through B)
        KernelModuleDescriptor moduleA = createDescriptorWithDeps("module-A", Set.of("module-B"));
        KernelModuleDescriptor moduleB = createDescriptorWithDeps("module-B", Set.of("module-C"));
        KernelModuleDescriptor moduleC = createDescriptorWithDeps("module-C", Set.of("module-D"));
        KernelModuleDescriptor moduleD = createDescriptorWithDeps("module-D", Set.of("module-B")); // Creates cycle

        runPromise(() -> registry.register(moduleA, new TestModule("module-A")));
        runPromise(() -> registry.register(moduleB, new TestModule("module-B")));
        runPromise(() -> registry.register(moduleC, new TestModule("module-C")));
        runPromise(() -> registry.register(moduleD, new TestModule("module-D")));

        assertThatThrownBy(() ->
            runPromise(() -> registry.resolveAllDependencies())
        )
            .isInstanceOf(KernelException.class)
            .hasMessageContaining("circular dependency");
    }

    /**
     * @doc.test_type negative
     * @doc.coverage path
     */
    @Test
    void testSelfDependency() {
        // A -> A (self-reference)
        KernelModuleDescriptor moduleA = createDescriptorWithDeps("module-A", Set.of("module-A"));

        runPromise(() -> registry.register(moduleA, new TestModule("module-A")));

        assertThatThrownBy(() ->
            runPromise(() -> registry.resolveDependencies("module-A"))
        )
            .isInstanceOf(KernelException.class)
            .hasMessageContaining("circular dependency")
            .hasMessageContaining("self-reference");
    }

    /**
     * @doc.test_type positive
     * @doc.coverage path
     */
    @Test
    void testDiamondDependencyPattern() {
        //     A
        //    / \
        //   B   C
        //    \ /
        //     D
        // This is NOT circular - should resolve correctly

        KernelModuleDescriptor moduleA = createDescriptorWithDeps("module-A", Set.of());
        KernelModuleDescriptor moduleB = createDescriptorWithDeps("module-B", Set.of("module-A"));
        KernelModuleDescriptor moduleC = createDescriptorWithDeps("module-C", Set.of("module-A"));
        KernelModuleDescriptor moduleD = createDescriptorWithDeps("module-D", Set.of("module-B", "module-C"));

        runPromise(() -> registry.register(moduleA, new TestModule("module-A")));
        runPromise(() -> registry.register(moduleB, new TestModule("module-B")));
        runPromise(() -> registry.register(moduleC, new TestModule("module-C")));
        runPromise(() -> registry.register(moduleD, new TestModule("module-D")));

        List<KernelModuleDescriptor> resolved = runPromise(() ->
            registry.resolveDependencies("module-D")
        );

        // Should contain D, B, C, A (order matters for dependencies)
        assertThat(resolved).extracting(KernelModuleDescriptor::id)
            .containsExactly("module-D", "module-B", "module-C", "module-A");
    }

    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testCircularDependencyWithOptionalDeps() {
        // Test with optional dependencies that form cycle
        KernelModuleDescriptor moduleA = createDescriptorWithOptionalDeps("module-A",
            Set.of("module-B"), Set.of("module-C"));
        KernelModuleDescriptor moduleB = createDescriptorWithDeps("module-B", Set.of("module-C"));
        KernelModuleDescriptor moduleC = createDescriptorWithDeps("module-C", Set.of("module-A"));

        runPromise(() -> registry.register(moduleA, new TestModule("module-A")));
        runPromise(() -> registry.register(moduleB, new TestModule("module-B")));
        runPromise(() -> registry.register(moduleC, new TestModule("module-C")));

        assertThatThrownBy(() ->
            runPromise(() -> registry.resolveDependencies("module-A"))
        )
            .isInstanceOf(KernelException.class)
            .hasMessageContaining("circular dependency");
    }

    private KernelModuleDescriptor createDescriptorWithDeps(String id, Set<String> deps) {
        return KernelModuleDescriptor.builder()
            .id(id)
            .name("Test " + id)
            .version("1.0.0")
            .dependencies(deps)
            .build();
    }

    private KernelModuleDescriptor createDescriptorWithOptionalDeps(String id,
            Set<String> required, Set<String> optional) {
        return KernelModuleDescriptor.builder()
            .id(id)
            .name("Test " + id)
            .version("1.0.0")
            .dependencies(required)
            .optionalDependencies(optional)
            .build();
    }
}
```

**Note**: If circular dependency detection is not implemented, this requires a feature implementation in `KernelRegistryImpl`:

```java
// Implementation addition to KernelRegistryImpl
private void detectCircularDependencies(Map<String, Set<String>> dependencyGraph) {
    Set<String> visiting = new HashSet<>();
    Set<String> visited = new HashSet<>();

    for (String module : dependencyGraph.keySet()) {
        if (!visited.contains(module)) {
            dfsDetectCycle(module, dependencyGraph, visiting, visited, new ArrayList<>());
        }
    }
}

private void dfsDetectCycle(String current, Map<String, Set<String>> graph,
        Set<String> visiting, Set<String> visited, List<String> path) {
    visiting.add(current);
    path.add(current);

    for (String dependency : graph.getOrDefault(current, Set.of())) {
        if (visiting.contains(dependency)) {
            int cycleStart = path.indexOf(dependency);
            List<String> cycle = path.subList(cycleStart, path.size());
            throw new KernelException("Circular dependency detected: " + String.join(" -> ", cycle) + " -> " + dependency);
        }

        if (!visited.contains(dependency)) {
            dfsDetectCycle(dependency, graph, visiting, visited, path);
        }
    }

    path.remove(path.size() - 1);
    visiting.remove(current);
    visited.add(current);
}
```

---

### Task 3: Lifecycle Edge Cases

**File**: `src/test/java/com/ghatana/kernel/lifecycle/KernelLifecycleEdgeCaseTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Validates kernel lifecycle edge cases and error handling
 * @doc.layer platform
 * @doc.scenario error condition, edge case
 */
@DisplayName("Kernel Lifecycle Edge Case Tests")
class KernelLifecycleEdgeCaseTest extends EventloopTestBase {

    private KernelContext context;
    private KernelRegistry registry;

    @BeforeEach
    void setup() {
        registry = KernelRegistry.create();
        context = DefaultKernelContext.create(registry);
    }

    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testPartialInitializationFailure() {
        // Create modules where one fails to initialize
        FailingModule failingModule = new FailingModule("failing-module", true, false);
        HealthyModule healthyModule1 = new HealthyModule("healthy-1");
        HealthyModule healthyModule2 = new HealthyModule("healthy-2");

        registerAll(failingModule, healthyModule1, healthyModule2);

        // Attempt initialization
        KernelInitializationResult result = runPromise(() -> context.initialize());

        // Verify partial failure handling
        assertThat(result.status()).isEqualTo(InitializationStatus.PARTIAL_FAILURE);
        assertThat(result.successfulModules()).containsExactlyInAnyOrder("healthy-1", "healthy-2");
        assertThat(result.failedModules()).containsExactly("failing-module");
        assertThat(result.failureReason("failing-module"))
            .contains("Initialization failed");

        // Healthy modules should be initialized
        assertThat(healthyModule1.isInitialized()).isTrue();
        assertThat(healthyModule2.isInitialized()).isTrue();
        assertThat(failingModule.isInitialized()).isFalse();
    }

    /**
     * @doc.test_type stress
     * @doc.coverage branch, path
     */
    @Test
    void testGracefulShutdownWithHangingModules() {
        HangingModule hangingModule = new HangingModule("hanging-module", 5000);
        HealthyModule healthyModule = new HealthyModule("healthy-module");

        registerAll(hangingModule, healthyModule);
        runPromise(() -> context.initialize()).assertComplete();

        // Start shutdown with timeout
        long startTime = System.currentTimeMillis();
        KernelShutdownResult result = runPromise(() ->
            context.shutdown(Duration.ofSeconds(2))
        );
        long shutdownTime = System.currentTimeMillis() - startTime;

        // Should complete within timeout (not wait for hanging module)
        assertThat(shutdownTime).isLessThan(3000);
        assertThat(result.status()).isEqualTo(ShutdownStatus.TIMEOUT);
        assertThat(result.timedOutModules()).contains("hanging-module");
        assertThat(result.successfullyStoppedModules()).contains("healthy-module");
    }

    /**
     * @doc.test_type positive
     * @doc.coverage path
     */
    @Test
    void testStartStopStartCycle() {
        HealthyModule module = new HealthyModule("restartable-module");
        registerAll(module);

        // First cycle
        runPromise(() -> context.initialize()).assertComplete();
        assertThat(module.getInitCount()).isEqualTo(1);
        assertThat(module.getStartCount()).isEqualTo(1);

        runPromise(() -> context.shutdown()).assertComplete();
        assertThat(module.getStopCount()).isEqualTo(1);

        // Second cycle - should be allowed
        runPromise(() -> context.initialize()).assertComplete();
        assertThat(module.getInitCount()).isEqualTo(2);
        assertThat(module.getStartCount()).isEqualTo(2);

        runPromise(() -> context.shutdown()).assertComplete();
        assertThat(module.getStopCount()).isEqualTo(2);
    }

    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testInitializationTimeout() {
        SlowModule slowModule = new SlowModule("slow-module", 10000);
        registerAll(slowModule);

        KernelInitializationResult result = runPromise(() ->
            context.initialize(Duration.ofSeconds(2))
        );

        assertThat(result.status()).isEqualTo(InitializationStatus.TIMEOUT);
        assertThat(result.timedOutModules()).contains("slow-module");
    }

    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testModuleDependencyFailureCascade() {
        // A depends on B, B depends on C, C fails
        // Should fail A and B as well

        FailingModule failingModule = new FailingModule("module-C", true, false);
        DependentModule moduleB = new DependentModule("module-B", Set.of("module-C"));
        DependentModule moduleA = new DependentModule("module-A", Set.of("module-B"));

        registerAll(moduleA, moduleB, failingModule);

        KernelInitializationResult result = runPromise(() -> context.initialize());

        assertThat(result.status()).isEqualTo(InitializationStatus.PARTIAL_FAILURE);
        assertThat(result.failedModules()).contains("module-C");
        assertThat(result.skippedModules())
            .contains("module-B", "module-A"); // Skipped due to dependency failure
    }

    // Helper classes for testing
    private static class FailingModule extends TestKernelModule {
        private final boolean failInit;
        private final boolean failStart;

        FailingModule(String id, boolean failInit, boolean failStart) {
            super(id);
            this.failInit = failInit;
            this.failStart = failStart;
        }

        @Override
        public Promise<Void> initialize() {
            if (failInit) {
                return Promise.ofException(new KernelException("Initialization failed for " + id()));
            }
            return Promise.complete();
        }

        @Override
        public Promise<Void> start() {
            if (failStart) {
                return Promise.ofException(new KernelException("Start failed for " + id()));
            }
            return Promise.complete();
        }
    }

    private static class HangingModule extends TestKernelModule {
        private final long hangDurationMs;

        HangingModule(String id, long hangDurationMs) {
            super(id);
            this.hangDurationMs = hangDurationMs;
        }

        @Override
        public Promise<Void> stop() {
            return Promise.ofBlocking(() -> {
                Thread.sleep(hangDurationMs);
                return null;
            });
        }
    }

    // Additional helper methods...
    private void registerAll(KernelModule... modules) {
        for (KernelModule module : modules) {
            KernelModuleDescriptor desc = createDescriptor(module.id());
            runPromise(() -> registry.register(desc, module));
        }
    }
}
```

---

### Task 4: Health Check Performance

**File**: `src/test/java/com/ghatana/kernel/health/HealthCheckPerformanceTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Validates health check performance at scale
 * @doc.layer platform
 * @doc.scenario performance
 */
@DisplayName("Health Check Performance Tests")
class HealthCheckPerformanceTest extends EventloopTestBase {

    private KernelRegistry registry;
    private KernelContext context;

    @BeforeEach
    void setup() {
        registry = KernelRegistry.create();
        context = DefaultKernelContext.create(registry);
    }

    /**
     * @doc.test_type performance
     * @doc.coverage path
     */
    @Test
    void testHealthCheckWith100Modules() {
        // Create 100 modules with varying health check latencies
        List<KernelModule> modules = IntStream.range(0, 100)
            .mapToObj(i -> new HealthCheckableModule("module-" + i, i % 10))
            .collect(Collectors.toList());

        modules.forEach(m -> {
            KernelModuleDescriptor desc = createDescriptor(m.id());
            runPromise(() -> registry.register(desc, m));
        });

        runPromise(() -> context.initialize()).assertComplete();

        // Measure health check aggregation time
        long startTime = System.nanoTime();
        KernelHealth health = runPromise(() -> context.checkHealth());
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        // Should complete in reasonable time (not sequential sum of all checks)
        assertThat(durationMs).isLessThan(500); // 500ms max for 100 modules
        assertThat(health.status()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(health.moduleHealth()).hasSize(100);
    }

    /**
     * @doc.test_type performance
     * @doc.coverage path
     */
    @Test
    void testParallelHealthCheckAggregation() {
        // Ensure health checks run in parallel, not sequentially
        List<SlowHealthCheckModule> modules = IntStream.range(0, 20)
            .mapToObj(i -> new SlowHealthCheckModule("slow-module-" + i, 100))
            .collect(Collectors.toList());

        modules.forEach(m -> {
            KernelModuleDescriptor desc = createDescriptor(m.id());
            runPromise(() -> registry.register(desc, m));
        });

        runPromise(() -> context.initialize()).assertComplete();

        long startTime = System.nanoTime();
        runPromise(() -> context.checkHealth());
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        // If run sequentially: 20 * 100ms = 2000ms
        // If run in parallel: ~100ms + overhead
        assertThat(durationMs).isLessThan(500); // Must be parallel
    }

    /**
     * @doc.test_type stress
     * @doc.coverage path
     */
    @Test
    void testHealthCheckUnderConcurrentLoad() {
        // Simulate multiple health check requests concurrently
        IntStream.range(0, 50).forEach(i -> {
            KernelModuleDescriptor desc = createDescriptor("concurrent-module-" + i);
            runPromise(() -> registry.register(desc, new HealthyModule("concurrent-module-" + i)));
        });

        runPromise(() -> context.initialize()).assertComplete();

        // Fire 100 concurrent health check requests
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(100);
        List<Future<KernelHealth>> futures = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            Future<KernelHealth> future = executor.submit(() -> {
                try {
                    return runPromise(() -> context.checkHealth());
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted");
        }

        // All should succeed
        assertThat(futures).allMatch(f -> {
            try {
                KernelHealth health = f.get(5, TimeUnit.SECONDS);
                return health.status() == HealthStatus.HEALTHY;
            } catch (Exception e) {
                return false;
            }
        });

        executor.shutdown();
    }

    private static class SlowHealthCheckModule extends TestKernelModule {
        private final long checkDurationMs;

        SlowHealthCheckModule(String id, long checkDurationMs) {
            super(id);
            this.checkDurationMs = checkDurationMs;
        }

        @Override
        public Promise<ModuleHealth> checkHealth() {
            return Promise.ofBlocking(() -> {
                Thread.sleep(checkDurationMs);
                return ModuleHealth.healthy();
            });
        }
    }
}
```

---

### Task 5: Adapter Comprehensive Tests

**File**: `src/test/java/com/ghatana/kernel/adapter/DataCloudKernelAdapterTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Comprehensive tests for DataCloud kernel adapter
 * @doc.layer platform
 * @doc.scenario happy path, error condition
 */
@DisplayName("DataCloud Kernel Adapter Tests")
class DataCloudKernelAdapterTest extends EventloopTestBase {

    private DataCloudKernelAdapter adapter;
    private MockDataCloudService mockService;

    @BeforeEach
    void setup() {
        mockService = new MockDataCloudService();
        adapter = new DataCloudKernelAdapterImpl(mockService);
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testDataReadSuccess() {
        DataReadRequest request = DataReadRequest.builder()
            .dataset("patients")
            .recordId("patient-123")
            .build();

        mockService.setupSuccessResponse("patient-123", Map.of(
            "id", "patient-123",
            "name", "John Doe",
            "age", 45
        ));

        DataResult result = runPromise(() -> adapter.read(request));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("id", "patient-123");
        assertThat(result.data()).containsEntry("name", "John Doe");
    }

    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testDataReadNotFound() {
        DataReadRequest request = DataReadRequest.builder()
            .dataset("patients")
            .recordId("non-existent")
            .build();

        mockService.setupNotFoundResponse("non-existent");

        DataResult result = runPromise(() -> adapter.read(request));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(result.errorMessage()).contains("not found");
    }

    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testDataReadServiceUnavailable() {
        DataReadRequest request = DataReadRequest.builder()
            .dataset("patients")
            .recordId("patient-123")
            .build();

        mockService.setupServiceUnavailable();

        assertThatThrownBy(() -> runPromise(() -> adapter.read(request)))
            .isInstanceOf(KernelAdapterException.class)
            .hasMessageContaining("service unavailable");
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testDataWriteWithTransaction() {
        TransactionHandle txn = adapter.beginTransaction();

        try {
            DataWriteRequest request1 = DataWriteRequest.builder()
                .dataset("patients")
                .record(Map.of("id", "patient-456", "name", "Jane Doe"))
                .transaction(txn)
                .build();

            DataWriteRequest request2 = DataWriteRequest.builder()
                .dataset("appointments")
                .record(Map.of("patientId", "patient-456", "date", "2026-04-10"))
                .transaction(txn)
                .build();

            DataResult result1 = runPromise(() -> adapter.write(request1));
            DataResult result2 = runPromise(() -> adapter.write(request2));

            assertThat(result1.success()).isTrue();
            assertThat(result2.success()).isTrue();

            runPromise(() -> adapter.commit(txn)).assertComplete();

        } catch (Exception e) {
            runPromise(() -> adapter.rollback(txn)).assertComplete();
            throw e;
        }
    }

    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testTransactionRollbackOnFailure() {
        TransactionHandle txn = adapter.beginTransaction();

        DataWriteRequest request = DataWriteRequest.builder()
            .dataset("patients")
            .record(Map.of("id", "patient-789"))
            .transaction(txn)
            .build();

        mockService.setupWriteFailure("patient-789");

        assertThatThrownBy(() -> {
            runPromise(() -> adapter.write(request));
            runPromise(() -> adapter.commit(txn));
        });

        // Verify rollback was called
        assertThat(mockService.wasRollbackCalled(txn)).isTrue();
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line
     */
    @Test
    void testQueryWithFiltering() {
        DataQueryRequest request = DataQueryRequest.builder()
            .dataset("patients")
            .filter(Filter.builder()
                .field("age")
                .operator(FilterOperator.GREATER_THAN)
                .value(30)
                .build())
            .sort(Sort.by("name", SortOrder.ASCENDING))
            .limit(10)
            .build();

        mockService.setupQueryResponse(List.of(
            Map.of("id", "p1", "name", "Alice", "age", 45),
            Map.of("id", "p2", "name", "Bob", "age", 50)
        ));

        QueryResult result = runPromise(() -> adapter.query(request));

        assertThat(result.success()).isTrue();
        assertThat(result.records()).hasSize(2);
        assertThat(result.records().get(0)).containsEntry("name", "Alice");
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line
     */
    @Test
    void testStreamingDataRead() {
        DataStreamRequest request = DataStreamRequest.builder()
            .dataset("large_dataset")
            .batchSize(100)
            .build();

        List<Map<String, Object>> batches = List.of(
            IntStream.range(0, 100).mapToObj(i -> Map.of("id", "r" + i)).collect(Collectors.toList()),
            IntStream.range(100, 200).mapToObj(i -> Map.of("id", "r" + i)).collect(Collectors.toList())
        );

        mockService.setupStreamingResponse(batches);

        DataStream stream = runPromise(() -> adapter.stream(request));

        List<Map<String, Object>> allRecords = new ArrayList<>();
        stream.forEach(allRecords::addAll);

        assertThat(allRecords).hasSize(200);
    }

    // Additional test cases for edge cases...
}
```

---

## Performance Benchmark Suite

**File**: `src/test/java/com/ghatana/kernel/performance/KernelBenchmarkSuite.java`

```java
/**
 * @doc.type benchmark
 * @doc.purpose JMH benchmarks for kernel performance characteristics
 * @doc.layer platform
 */
@State(Scope.Benchmark)
public class KernelBenchmarkSuite {

    private KernelRegistry registry;
    private Eventloop eventloop;

    @Setup
    public void setup() {
        eventloop = Eventloop.create();
        registry = KernelRegistry.create();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void testSingleModuleRegistration() {
        KernelModuleDescriptor descriptor = KernelModuleDescriptor.builder()
            .id("test-module")
            .name("Test Module")
            .version("1.0.0")
            .build();

        eventloop.submit(() -> registry.register(descriptor, new TestModule("test-module")));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void testDependencyResolution() {
        // Setup complex dependency graph
        List<KernelModuleDescriptor> descriptors = createComplexDependencyGraph(50);

        eventloop.submit(() -> {
            descriptors.forEach(desc ->
                registry.register(desc, new TestModule(desc.id()))
            );
            return registry.resolveAllDependencies();
        });
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void testEventPublishingThroughput() {
        KernelEventBus eventBus = new KernelEventBus(eventloop);

        eventloop.submit(() -> {
            IntStream.range(0, 1000).forEach(i ->
                eventBus.publish("test-event", Map.of("index", i))
            );
        });
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void testHealthCheckLatency() {
        // Setup 100 modules
        IntStream.range(0, 100).forEach(i -> {
            KernelModuleDescriptor desc = KernelModuleDescriptor.builder()
                .id("health-module-" + i)
                .name("Health Module " + i)
                .build();
            registry.register(desc, new TestModule("health-module-" + i));
        });

        KernelContext context = DefaultKernelContext.create(registry);
        eventloop.submit(() -> context.checkHealth());
    }

    private List<KernelModuleDescriptor> createComplexDependencyGraph(int size) {
        List<KernelModuleDescriptor> descriptors = new ArrayList<>();

        // Root
        descriptors.add(createDescriptor("root", Set.of()));

        // Children
        for (int i = 0; i < size / 2; i++) {
            descriptors.add(createDescriptor("child-" + i, Set.of("root")));
        }

        // Grandchildren with multiple parents
        for (int i = size / 2; i < size; i++) {
            Set<String> deps = Set.of(
                "child-" + (i % (size / 2)),
                "child-" + ((i + 1) % (size / 2))
            );
            descriptors.add(createDescriptor("grandchild-" + i, deps));
        }

        return descriptors;
    }

    private KernelModuleDescriptor createDescriptor(String id, Set<String> deps) {
        return KernelModuleDescriptor.builder()
            .id(id)
            .name("Benchmark " + id)
            .version("1.0.0")
            .dependencies(deps)
            .build();
    }
}
```

---

## Test Execution Plan

### Week 2 Schedule

| Day   | Focus Area                                     | Deliverables                                     |
| ----- | ---------------------------------------------- | ------------------------------------------------ |
| Day 1 | Concurrency tests                              | `KernelConcurrencyTest.java`                     |
| Day 2 | Concurrency + Circular dependency              | `Concurrent*Test.java` files                     |
| Day 3 | Circular dependency implementation + Lifecycle | `CircularDependencyDetectionTest.java`           |
| Day 4 | Lifecycle edge cases                           | `KernelLifecycleEdgeCaseTest.java`               |
| Day 5 | Health check + Adapter tests                   | `HealthCheckPerformanceTest.java`, adapter tests |
| Day 6 | Performance benchmarks                         | JMH benchmark suite                              |
| Day 7 | Coverage verification                          | JaCoCo 100% coverage report                      |

### Coverage Targets

| Component     | Current | Target   |
| ------------- | ------- | -------- |
| Registry      | 90%     | 100%     |
| Context       | 85%     | 100%     |
| Descriptors   | 95%     | 100%     |
| Adapters      | 70%     | 100%     |
| AI Framework  | 75%     | 100%     |
| Boundary      | 80%     | 100%     |
| Communication | 78%     | 100%     |
| **Overall**   | **85%** | **100%** |

---

## Success Criteria

1. **Line Coverage**: 100% of executable lines covered
2. **Branch Coverage**: 100% of branches covered
3. **Test Count**: 29 → 40+ test files
4. **Concurrency**: All race conditions tested
5. **Performance**: Benchmarks establish baselines
6. **Documentation**: All tests have `@doc.*` annotations

---

## Appendix: Implementation Checklist

- [ ] Create `KernelConcurrencyTest.java`
- [ ] Create `ConcurrentLifecycleTest.java`
- [ ] Create `ConcurrentEventBusTest.java`
- [ ] Create `CircularDependencyDetectionTest.java`
- [ ] Implement circular dependency detection in `KernelRegistryImpl`
- [ ] Create `KernelLifecycleEdgeCaseTest.java`
- [ ] Create `HealthCheckPerformanceTest.java`
- [ ] Verify parallel health check execution
- [ ] Create `DataCloudKernelAdapterTest.java`
- [ ] Create `AepKernelAdapterTest.java`
- [ ] Create JMH benchmark suite
- [ ] Run JaCoCo coverage report
- [ ] Address any remaining coverage gaps
- [ ] Document performance baselines
- [ ] Update `TEST_COVERAGE_REPORT.md`

---

_Document Version: 1.0_  
_Last Updated: April 4, 2026_
