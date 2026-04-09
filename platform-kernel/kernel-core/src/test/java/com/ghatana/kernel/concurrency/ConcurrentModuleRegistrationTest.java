package com.ghatana.kernel.concurrency;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for concurrent module registration in Kernel registry.
 * Validates thread-safety and race condition handling.
 *
 * @doc.type class
 * @doc.purpose Validates thread-safe concurrent module registration
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Concurrent Module Registration Tests")
class ConcurrentModuleRegistrationTest extends EventloopTestBase {

    private KernelRegistryImpl registry;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        registry = new KernelRegistryImpl();
        executorService = Executors.newFixedThreadPool(100);
    }

    @Test
    @DisplayName("Should handle 100 concurrent module registrations")
    void testConcurrentModuleRegistration() throws Exception {
        // GIVEN: 100 modules to register concurrently
        int moduleCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(moduleCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        // WHEN: Register all modules concurrently
        IntStream.range(0, moduleCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    TestModule module = new TestModule("module-" + i, "1.0.0");
                    registry.registerModule(module);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    completionLatch.countDown();
                }
            });
        });

        startLatch.countDown(); // Start all threads simultaneously
        boolean completed = completionLatch.await(10, TimeUnit.SECONDS);

        // THEN: All modules registered successfully
        assertThat(completed).isTrue();
        assertThat(exceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(moduleCount);
        assertThat(registry.getAllModules()).hasSize(moduleCount);
    }

    @Test
    @DisplayName("Should prevent duplicate module registration under concurrent load")
    void testConcurrentDuplicateRegistration() throws Exception {
        // GIVEN: Multiple threads trying to register the same module
        int threadCount = 50;
        String moduleId = "duplicate-module";
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // WHEN: Multiple threads try to register the same module
        IntStream.range(0, threadCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    TestModule module = new TestModule(moduleId, "1.0.0");
                    registry.registerModule(module);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    if (e.getMessage().contains("already registered")) {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Unexpected exception
                } finally {
                    completionLatch.countDown();
                }
            });
        });

        startLatch.countDown();
        completionLatch.await(10, TimeUnit.SECONDS);

        // THEN: Only one registration succeeded, others failed with correct error
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);
        assertThat(registry.getAllModules()).hasSize(1);
    }

    @Test
    @DisplayName("Should handle concurrent capability registration")
    void testConcurrentCapabilityRegistration() throws Exception {
        // GIVEN: 100 modules with capabilities to register concurrently
        int moduleCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(moduleCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // WHEN: Register modules with capabilities concurrently
        IntStream.range(0, moduleCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    TestModuleWithCapability module = new TestModuleWithCapability(
                        "module-" + i,
                        "1.0.0",
                        "capability-" + i
                    );
                    registry.registerModule(module);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Should not happen
                } finally {
                    completionLatch.countDown();
                }
            });
        });

        startLatch.countDown();
        completionLatch.await(10, TimeUnit.SECONDS);

        // THEN: All modules and capabilities registered
        assertThat(successCount.get()).isEqualTo(moduleCount);
        assertThat(registry.getAllModules()).hasSize(moduleCount);
        assertThat(registry.getAllCapabilities()).hasSize(moduleCount);
    }

    @Test
    @DisplayName("Should handle concurrent dependency resolution")
    void testConcurrentDependencyResolution() throws Exception {
        // GIVEN: Modules with dependencies
        TestModule moduleA = new TestModule("module-a", "1.0.0");
        TestModule moduleB = new TestModule("module-b", "1.0.0");
        TestModule moduleC = new TestModule("module-c", "1.0.0");

        moduleC.addDependency(new KernelDependency("module-b", "1.0.0", KernelDependency.DependencyType.MODULE, false));
        moduleB.addDependency(new KernelDependency("module-a", "1.0.0", KernelDependency.DependencyType.MODULE, false));

        registry.registerModule(moduleA);
        registry.registerModule(moduleB);
        registry.registerModule(moduleC);

        // WHEN: Multiple threads resolve dependencies concurrently
        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        List<List<KernelModule>> results = new CopyOnWriteArrayList<>();

        IntStream.range(0, threadCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    List<KernelModule> resolved = registry.resolveDependencies(moduleC);
                    results.add(resolved);
                } catch (Exception e) {
                    // Should not happen
                } finally {
                    completionLatch.countDown();
                }
            });
        });

        startLatch.countDown();
        completionLatch.await(10, TimeUnit.SECONDS);

        // THEN: All resolutions return consistent results
        assertThat(results).hasSize(threadCount);
        results.forEach(resolved -> {
            assertThat(resolved).containsExactlyInAnyOrder(moduleA, moduleB);
        });
    }

    @Test
    @DisplayName("Should handle concurrent module unregistration")
    void testConcurrentModuleUnregistration() throws Exception {
        // GIVEN: 100 registered modules
        int moduleCount = 100;
        IntStream.range(0, moduleCount).forEach(i -> {
            TestModule module = new TestModule("module-" + i, "1.0.0");
            registry.registerModule(module);
        });

        assertThat(registry.getAllModules()).hasSize(moduleCount);

        // WHEN: Unregister all modules concurrently
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(moduleCount);
        AtomicInteger successCount = new AtomicInteger(0);

        IntStream.range(0, moduleCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    boolean removed = registry.unregisterModule("module-" + i);
                    if (removed) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Should not happen
                } finally {
                    completionLatch.countDown();
                }
            });
        });

        startLatch.countDown();
        completionLatch.await(10, TimeUnit.SECONDS);

        // THEN: All modules unregistered successfully
        assertThat(successCount.get()).isEqualTo(moduleCount);
        assertThat(registry.getAllModules()).isEmpty();
    }

    @Test
    @DisplayName("Should handle race condition in capability registration")
    void testRaceConditionInCapabilityRegistration() throws Exception {
        // GIVEN: Multiple modules providing the same capability
        int moduleCount = 10;
        String capabilityId = "shared-capability";
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(moduleCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // WHEN: Register modules with same capability concurrently
        IntStream.range(0, moduleCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    TestModuleWithCapability module = new TestModuleWithCapability(
                        "module-" + i,
                        "1.0.0",
                        capabilityId
                    );
                    registry.registerModule(module);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Should not happen
                } finally {
                    completionLatch.countDown();
                }
            });
        });

        startLatch.countDown();
        completionLatch.await(10, TimeUnit.SECONDS);

        // THEN: All modules registered, capability count is correct
        assertThat(successCount.get()).isEqualTo(moduleCount);
        assertThat(registry.getAllModules()).hasSize(moduleCount);

        // Multiple modules can provide the same capability
        List<KernelModule> modulesWithCapability = registry.getModulesByCapability(
            createTestCapability(capabilityId)
        );
        assertThat(modulesWithCapability).hasSize(moduleCount);
    }

    /**
     * Test module implementation.
     */
    private static class TestModule implements KernelModule {
        private final String moduleId;
        private final String version;
        private final List<KernelDependency> dependencies = new ArrayList<>();

        TestModule(String moduleId, String version) {
            this.moduleId = moduleId;
            this.version = version;
        }

        void addDependency(KernelDependency dependency) {
            dependencies.add(dependency);
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
            return new java.util.HashSet<>(dependencies);
        }

        @Override
        public Set<KernelCapability> getCapabilities() {
            return Set.of();
        }

        @Override
        public void initialize(com.ghatana.kernel.context.KernelContext context) {
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

    /**
     * Test module with capability.
     */
    private static class TestModuleWithCapability extends TestModule {
        private final KernelCapability capability;

        TestModuleWithCapability(String moduleId, String version, String capabilityId) {
            super(moduleId, version);
            this.capability = createTestCapability(capabilityId);
        }

        @Override
        public Set<KernelCapability> getCapabilities() {
            return Set.of(capability);
        }
    }

    private static KernelCapability createTestCapability(String capabilityId) {
        return new KernelCapability(
            capabilityId,
            "Test Capability",
            "Test capability for unit tests",
            KernelCapability.CapabilityType.BUSINESS_LOGIC,
            java.util.Map.of()
        );
    }
}
