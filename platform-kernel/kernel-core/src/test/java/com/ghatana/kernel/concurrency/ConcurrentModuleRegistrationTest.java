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
    void setUp() { // GH-90000
        registry = new KernelRegistryImpl(); // GH-90000
        executorService = Executors.newFixedThreadPool(100); // GH-90000
    }

    @Test
    @DisplayName("Should handle 100 concurrent module registrations")
    void testConcurrentModuleRegistration() throws Exception { // GH-90000
        // GIVEN: 100 modules to register concurrently
        int moduleCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1); // GH-90000
        CountDownLatch completionLatch = new CountDownLatch(moduleCount); // GH-90000
        AtomicInteger successCount = new AtomicInteger(0); // GH-90000
        List<Exception> exceptions = new CopyOnWriteArrayList<>(); // GH-90000

        // WHEN: Register all modules concurrently
        IntStream.range(0, moduleCount).forEach(i -> { // GH-90000
            executorService.submit(() -> { // GH-90000
                try {
                    startLatch.await(); // Wait for all threads to be ready // GH-90000
                    TestModule module = new TestModule("module-" + i, "1.0.0"); // GH-90000
                    registry.registerModule(module); // GH-90000
                    successCount.incrementAndGet(); // GH-90000
                } catch (Exception e) { // GH-90000
                    exceptions.add(e); // GH-90000
                } finally {
                    completionLatch.countDown(); // GH-90000
                }
            });
        });

        startLatch.countDown(); // Start all threads simultaneously // GH-90000
        boolean completed = completionLatch.await(10, TimeUnit.SECONDS); // GH-90000

        // THEN: All modules registered successfully
        assertThat(completed).isTrue(); // GH-90000
        assertThat(exceptions).isEmpty(); // GH-90000
        assertThat(successCount.get()).isEqualTo(moduleCount); // GH-90000
        assertThat(registry.getAllModules()).hasSize(moduleCount); // GH-90000
    }

    @Test
    @DisplayName("Should prevent duplicate module registration under concurrent load")
    void testConcurrentDuplicateRegistration() throws Exception { // GH-90000
        // GIVEN: Multiple threads trying to register the same module
        int threadCount = 50;
        String moduleId = "duplicate-module";
        CountDownLatch startLatch = new CountDownLatch(1); // GH-90000
        CountDownLatch completionLatch = new CountDownLatch(threadCount); // GH-90000
        AtomicInteger successCount = new AtomicInteger(0); // GH-90000
        AtomicInteger failureCount = new AtomicInteger(0); // GH-90000

        // WHEN: Multiple threads try to register the same module
        IntStream.range(0, threadCount).forEach(i -> { // GH-90000
            executorService.submit(() -> { // GH-90000
                try {
                    startLatch.await(); // GH-90000
                    TestModule module = new TestModule(moduleId, "1.0.0"); // GH-90000
                    registry.registerModule(module); // GH-90000
                    successCount.incrementAndGet(); // GH-90000
                } catch (IllegalStateException e) { // GH-90000
                    if (e.getMessage().contains("already registered")) {
                        failureCount.incrementAndGet(); // GH-90000
                    }
                } catch (Exception e) { // GH-90000
                    // Unexpected exception
                } finally {
                    completionLatch.countDown(); // GH-90000
                }
            });
        });

        startLatch.countDown(); // GH-90000
        completionLatch.await(10, TimeUnit.SECONDS); // GH-90000

        // THEN: Only one registration succeeded, others failed with correct error
        assertThat(successCount.get()).isEqualTo(1); // GH-90000
        assertThat(failureCount.get()).isEqualTo(threadCount - 1); // GH-90000
        assertThat(registry.getAllModules()).hasSize(1); // GH-90000
    }

    @Test
    @DisplayName("Should handle concurrent capability registration")
    void testConcurrentCapabilityRegistration() throws Exception { // GH-90000
        // GIVEN: 100 modules with capabilities to register concurrently
        int moduleCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1); // GH-90000
        CountDownLatch completionLatch = new CountDownLatch(moduleCount); // GH-90000
        AtomicInteger successCount = new AtomicInteger(0); // GH-90000

        // WHEN: Register modules with capabilities concurrently
        IntStream.range(0, moduleCount).forEach(i -> { // GH-90000
            executorService.submit(() -> { // GH-90000
                try {
                    startLatch.await(); // GH-90000
                    TestModuleWithCapability module = new TestModuleWithCapability( // GH-90000
                        "module-" + i,
                        "1.0.0",
                        "capability-" + i
                    );
                    registry.registerModule(module); // GH-90000
                    successCount.incrementAndGet(); // GH-90000
                } catch (Exception e) { // GH-90000
                    // Should not happen
                } finally {
                    completionLatch.countDown(); // GH-90000
                }
            });
        });

        startLatch.countDown(); // GH-90000
        completionLatch.await(10, TimeUnit.SECONDS); // GH-90000

        // THEN: All modules and capabilities registered
        assertThat(successCount.get()).isEqualTo(moduleCount); // GH-90000
        assertThat(registry.getAllModules()).hasSize(moduleCount); // GH-90000
        assertThat(registry.getAllCapabilities()).hasSize(moduleCount); // GH-90000
    }

    @Test
    @DisplayName("Should handle concurrent dependency resolution")
    void testConcurrentDependencyResolution() throws Exception { // GH-90000
        // GIVEN: Modules with dependencies
        TestModule moduleA = new TestModule("module-a", "1.0.0"); // GH-90000
        TestModule moduleB = new TestModule("module-b", "1.0.0"); // GH-90000
        TestModule moduleC = new TestModule("module-c", "1.0.0"); // GH-90000

        moduleC.addDependency(new KernelDependency("module-b", "1.0.0", KernelDependency.DependencyType.MODULE, false)); // GH-90000
        moduleB.addDependency(new KernelDependency("module-a", "1.0.0", KernelDependency.DependencyType.MODULE, false)); // GH-90000

        registry.registerModule(moduleA); // GH-90000
        registry.registerModule(moduleB); // GH-90000
        registry.registerModule(moduleC); // GH-90000

        // WHEN: Multiple threads resolve dependencies concurrently
        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1); // GH-90000
        CountDownLatch completionLatch = new CountDownLatch(threadCount); // GH-90000
        List<List<KernelModule>> results = new CopyOnWriteArrayList<>(); // GH-90000

        IntStream.range(0, threadCount).forEach(i -> { // GH-90000
            executorService.submit(() -> { // GH-90000
                try {
                    startLatch.await(); // GH-90000
                    List<KernelModule> resolved = registry.resolveDependencies(moduleC); // GH-90000
                    results.add(resolved); // GH-90000
                } catch (Exception e) { // GH-90000
                    // Should not happen
                } finally {
                    completionLatch.countDown(); // GH-90000
                }
            });
        });

        startLatch.countDown(); // GH-90000
        completionLatch.await(10, TimeUnit.SECONDS); // GH-90000

        // THEN: All resolutions return consistent results
        assertThat(results).hasSize(threadCount); // GH-90000
        results.forEach(resolved -> { // GH-90000
            assertThat(resolved).containsExactlyInAnyOrder(moduleA, moduleB); // GH-90000
        });
    }

    @Test
    @DisplayName("Should handle concurrent module unregistration")
    void testConcurrentModuleUnregistration() throws Exception { // GH-90000
        // GIVEN: 100 registered modules
        int moduleCount = 100;
        IntStream.range(0, moduleCount).forEach(i -> { // GH-90000
            TestModule module = new TestModule("module-" + i, "1.0.0"); // GH-90000
            registry.registerModule(module); // GH-90000
        });

        assertThat(registry.getAllModules()).hasSize(moduleCount); // GH-90000

        // WHEN: Unregister all modules concurrently
        CountDownLatch startLatch = new CountDownLatch(1); // GH-90000
        CountDownLatch completionLatch = new CountDownLatch(moduleCount); // GH-90000
        AtomicInteger successCount = new AtomicInteger(0); // GH-90000

        IntStream.range(0, moduleCount).forEach(i -> { // GH-90000
            executorService.submit(() -> { // GH-90000
                try {
                    startLatch.await(); // GH-90000
                    boolean removed = registry.unregisterModule("module-" + i); // GH-90000
                    if (removed) { // GH-90000
                        successCount.incrementAndGet(); // GH-90000
                    }
                } catch (Exception e) { // GH-90000
                    // Should not happen
                } finally {
                    completionLatch.countDown(); // GH-90000
                }
            });
        });

        startLatch.countDown(); // GH-90000
        completionLatch.await(10, TimeUnit.SECONDS); // GH-90000

        // THEN: All modules unregistered successfully
        assertThat(successCount.get()).isEqualTo(moduleCount); // GH-90000
        assertThat(registry.getAllModules()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should handle race condition in capability registration")
    void testRaceConditionInCapabilityRegistration() throws Exception { // GH-90000
        // GIVEN: Multiple modules providing the same capability
        int moduleCount = 10;
        String capabilityId = "shared-capability";
        CountDownLatch startLatch = new CountDownLatch(1); // GH-90000
        CountDownLatch completionLatch = new CountDownLatch(moduleCount); // GH-90000
        AtomicInteger successCount = new AtomicInteger(0); // GH-90000

        // WHEN: Register modules with same capability concurrently
        IntStream.range(0, moduleCount).forEach(i -> { // GH-90000
            executorService.submit(() -> { // GH-90000
                try {
                    startLatch.await(); // GH-90000
                    TestModuleWithCapability module = new TestModuleWithCapability( // GH-90000
                        "module-" + i,
                        "1.0.0",
                        capabilityId
                    );
                    registry.registerModule(module); // GH-90000
                    successCount.incrementAndGet(); // GH-90000
                } catch (Exception e) { // GH-90000
                    // Should not happen
                } finally {
                    completionLatch.countDown(); // GH-90000
                }
            });
        });

        startLatch.countDown(); // GH-90000
        completionLatch.await(10, TimeUnit.SECONDS); // GH-90000

        // THEN: All modules registered, capability count is correct
        assertThat(successCount.get()).isEqualTo(moduleCount); // GH-90000
        assertThat(registry.getAllModules()).hasSize(moduleCount); // GH-90000

        // Multiple modules can provide the same capability
        List<KernelModule> modulesWithCapability = registry.getModulesByCapability( // GH-90000
            createTestCapability(capabilityId) // GH-90000
        );
        assertThat(modulesWithCapability).hasSize(moduleCount); // GH-90000
    }

    /**
     * Test module implementation.
     */
    private static class TestModule implements KernelModule {
        private final String moduleId;
        private final String version;
        private final List<KernelDependency> dependencies = new ArrayList<>(); // GH-90000

        TestModule(String moduleId, String version) { // GH-90000
            this.moduleId = moduleId;
            this.version = version;
        }

        void addDependency(KernelDependency dependency) { // GH-90000
            dependencies.add(dependency); // GH-90000
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
            return new java.util.HashSet<>(dependencies); // GH-90000
        }

        @Override
        public Set<KernelCapability> getCapabilities() { // GH-90000
            return Set.of(); // GH-90000
        }

        @Override
        public void initialize(com.ghatana.kernel.context.KernelContext context) { // GH-90000
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

    /**
     * Test module with capability.
     */
    private static class TestModuleWithCapability extends TestModule {
        private final KernelCapability capability;

        TestModuleWithCapability(String moduleId, String version, String capabilityId) { // GH-90000
            super(moduleId, version); // GH-90000
            this.capability = createTestCapability(capabilityId); // GH-90000
        }

        @Override
        public Set<KernelCapability> getCapabilities() { // GH-90000
            return Set.of(capability); // GH-90000
        }
    }

    private static KernelCapability createTestCapability(String capabilityId) { // GH-90000
        return new KernelCapability( // GH-90000
            capabilityId,
            "Test Capability",
            "Test capability for unit tests",
            KernelCapability.CapabilityType.BUSINESS_LOGIC,
            java.util.Map.of() // GH-90000
        );
    }
}
