package com.ghatana.kernel.test.unit;

import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.registry.KernelRegistry;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link KernelRegistry}.
 *
 * <p>Uses {@link EventloopTestBase} for async Promise-based testing following
 * the platform testing conventions.</p>
 *
 * @doc.type class
 * @doc.purpose Kernel registry unit tests with async Promise verification
 * @doc.layer test
 * @doc.pattern Unit Test
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@DisplayName("KernelRegistry Tests [GH-90000]")
class KernelRegistryTest extends EventloopTestBase {

    private KernelRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new KernelRegistryImpl(); // GH-90000
    }

    @Test
    @DisplayName("should register and retrieve module by ID [GH-90000]")
    void testModuleRegistration() { // GH-90000
        // Given
        KernelModule module = new TestKernelModule("test-module", "1.0.0"); // GH-90000

        // When
        registry.registerModule(module); // GH-90000

        // Then
        var retrieved = registry.getModule("test-module [GH-90000]");
        assertThat(retrieved).isPresent(); // GH-90000
        assertThat(retrieved.get()).isEqualTo(module); // GH-90000
    }

    @Test
    @DisplayName("should return empty optional for unknown module [GH-90000]")
    void testUnknownModule() { // GH-90000
        // When/Then
        var result = registry.getModule("unknown-module [GH-90000]");
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("should resolve dependencies in correct order [GH-90000]")
    void testDependencyResolution() { // GH-90000
        // Given
        KernelModule moduleA = new TestKernelModule("module-a", "1.0.0"); // GH-90000
        KernelModule moduleB = new TestKernelModule("module-b", "1.0.0", "module-a"); // GH-90000

        registry.registerModule(moduleA); // GH-90000
        registry.registerModule(moduleB); // GH-90000

        // When
        var dependencies = registry.resolveDependencies(moduleB); // GH-90000

        // Then
        assertThat(dependencies).hasSize(1); // GH-90000
        assertThat(dependencies.get(0).getModuleId()).isEqualTo("module-a [GH-90000]");
    }

    @Test
    @DisplayName("should detect missing dependencies [GH-90000]")
    void testMissingDependency() { // GH-90000
        // Given
        KernelModule module = new TestKernelModule("module-with-deps", "1.0.0", "missing-dependency"); // GH-90000

        // When/Then
        assertThatThrownBy(() -> registry.registerModule(module)) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("missing-dependency [GH-90000]");
    }

    @Test
    @DisplayName("should start module lifecycle successfully [GH-90000]")
    void testModuleLifecycle() { // GH-90000
        // Given
        TestKernelModule module = new TestKernelModule("lifecycle-test", "1.0.0"); // GH-90000
        registry.registerModule(module); // GH-90000

        // When - runPromise blocks the Eventloop correctly
        runPromise(() -> module.start()); // GH-90000

        // Then
        assertThat(module.isStarted()).isTrue(); // GH-90000

        // And stop
        runPromise(() -> module.stop()); // GH-90000
        assertThat(module.isStopped()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should find modules by capability [GH-90000]")
    void testModulesByCapability() { // GH-90000
        // Given
        KernelModule module = new TestKernelModule("capability-module", "1.0.0"); // GH-90000
        registry.registerModule(module); // GH-90000
        registry.registerCapability(TestKernelModule.TEST_CAPABILITY); // GH-90000

        // When
        var modules = registry.getModulesByCapability(TestKernelModule.TEST_CAPABILITY); // GH-90000

        // Then
        assertThat(modules).isNotEmpty(); // GH-90000
    }

    // ==================== Test Fixtures ====================

    /**
     * Test implementation of KernelModule for unit testing.
     */
    static class TestKernelModule implements KernelModule {
        static final com.ghatana.kernel.descriptor.KernelCapability TEST_CAPABILITY =
            new com.ghatana.kernel.descriptor.KernelCapability( // GH-90000
                "test.capability", "Test Capability", "For testing",
                com.ghatana.kernel.descriptor.KernelCapability.CapabilityType.DATA_MANAGEMENT,
                java.util.Map.of() // GH-90000
            );

        private final String moduleId;
        private final String version;
        private final java.util.Set<String> dependencies;
        private volatile boolean started = false;
        private volatile boolean stopped = false;

        TestKernelModule(String moduleId, String version, String... dependencies) { // GH-90000
            this.moduleId = moduleId;
            this.version = version;
            this.dependencies = java.util.Set.of(dependencies); // GH-90000
        }

        @Override
        public String getModuleId() { return moduleId; } // GH-90000

        @Override
        public String getVersion() { return version; } // GH-90000

        @Override
        public java.util.Set<com.ghatana.kernel.descriptor.KernelCapability> getCapabilities() { // GH-90000
            return java.util.Set.of(TEST_CAPABILITY); // GH-90000
        }

        @Override
        public java.util.Set<com.ghatana.kernel.descriptor.KernelDependency> getDependencies() { // GH-90000
            return dependencies.stream() // GH-90000
                .map(dep -> new com.ghatana.kernel.descriptor.KernelDependency(dep, "1.0.0", // GH-90000
                    com.ghatana.kernel.descriptor.KernelDependency.DependencyType.MODULE, false))
                .collect(java.util.stream.Collectors.toSet()); // GH-90000
        }

        @Override
        public void initialize(com.ghatana.kernel.context.KernelContext context) { // GH-90000
            // No-op for test
        }

        @Override
        public Promise<Void> start() { // GH-90000
            started = true;
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Void> stop() { // GH-90000
            stopped = true;
            started = false;
            return Promise.complete(); // GH-90000
        }

        @Override
        public HealthStatus getHealthStatus() { // GH-90000
            return started
            ? HealthStatus.healthy("Test module healthy [GH-90000]")
            : HealthStatus.unhealthy("Test module not started [GH-90000]");
        }

        boolean isStarted() { return started; } // GH-90000
        boolean isStopped() { return stopped; } // GH-90000
    }
}
