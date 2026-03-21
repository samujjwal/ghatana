package com.ghatana.kernel.test.unit;

import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.registry.KernelRegistry;
import com.ghatana.kernel.registry.KernelRegistryImpl;
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
@DisplayName("KernelRegistry Tests")
class KernelRegistryTest extends EventloopTestBase {

    private KernelRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new KernelRegistryImpl();
    }

    @Test
    @DisplayName("should register and retrieve module by ID")
    void testModuleRegistration() {
        // Given
        KernelModule module = new TestKernelModule("test-module", "1.0.0");

        // When
        registry.registerModule(module);

        // Then
        var retrieved = registry.getModule("test-module");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get()).isEqualTo(module);
    }

    @Test
    @DisplayName("should return empty optional for unknown module")
    void testUnknownModule() {
        // When/Then
        var result = registry.getModule("unknown-module");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should resolve dependencies in correct order")
    void testDependencyResolution() {
        // Given
        KernelModule moduleA = new TestKernelModule("module-a", "1.0.0");
        KernelModule moduleB = new TestKernelModule("module-b", "1.0.0", "module-a");

        registry.registerModule(moduleA);
        registry.registerModule(moduleB);

        // When
        var dependencies = registry.resolveDependencies(moduleB);

        // Then
        assertThat(dependencies).hasSize(1);
        assertThat(dependencies.get(0).getModuleId()).isEqualTo("module-a");
    }

    @Test
    @DisplayName("should detect missing dependencies")
    void testMissingDependency() {
        // Given
        KernelModule module = new TestKernelModule("module-with-deps", "1.0.0", "missing-dependency");

        // When/Then
        assertThatThrownBy(() -> registry.registerModule(module))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing-dependency");
    }

    @Test
    @DisplayName("should start module lifecycle successfully")
    void testModuleLifecycle() {
        // Given
        TestKernelModule module = new TestKernelModule("lifecycle-test", "1.0.0");
        registry.registerModule(module);

        // When - runPromise blocks the Eventloop correctly
        runPromise(() -> module.start());

        // Then
        assertThat(module.isStarted()).isTrue();

        // And stop
        runPromise(() -> module.stop());
        assertThat(module.isStopped()).isTrue();
    }

    @Test
    @DisplayName("should find modules by capability")
    void testModulesByCapability() {
        // Given
        KernelModule module = new TestKernelModule("capability-module", "1.0.0");
        registry.registerModule(module);
        registry.registerCapability(TestKernelModule.TEST_CAPABILITY);

        // When
        var modules = registry.getModulesByCapability(TestKernelModule.TEST_CAPABILITY);

        // Then
        assertThat(modules).isNotEmpty();
    }

    // ==================== Test Fixtures ====================

    /**
     * Test implementation of KernelModule for unit testing.
     */
    static class TestKernelModule implements KernelModule {
        static final com.ghatana.kernel.descriptor.KernelCapability TEST_CAPABILITY =
            new com.ghatana.kernel.descriptor.KernelCapability(
                "test.capability", "Test Capability", "For testing",
                com.ghatana.kernel.descriptor.KernelCapability.CapabilityType.DATA_MANAGEMENT,
                java.util.Map.of()
            );

        private final String moduleId;
        private final String version;
        private final java.util.Set<String> dependencies;
        private volatile boolean started = false;
        private volatile boolean stopped = false;

        TestKernelModule(String moduleId, String version, String... dependencies) {
            this.moduleId = moduleId;
            this.version = version;
            this.dependencies = java.util.Set.of(dependencies);
        }

        @Override
        public String getModuleId() { return moduleId; }

        @Override
        public String getVersion() { return version; }

        @Override
        public java.util.Set<com.ghatana.kernel.descriptor.KernelCapability> getCapabilities() {
            return java.util.Set.of(TEST_CAPABILITY);
        }

        @Override
        public java.util.Set<com.ghatana.kernel.descriptor.KernelDependency> getDependencies() {
            return dependencies.stream()
                .map(dep -> new com.ghatana.kernel.descriptor.KernelDependency(dep, "1.0.0",
                    com.ghatana.kernel.descriptor.KernelDependency.DependencyType.MODULE, false))
                .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public void initialize(com.ghatana.kernel.context.KernelContext context) {
            // No-op for test
        }

        @Override
        public Promise<Void> start() {
            started = true;
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            stopped = true;
            started = false;
            return Promise.complete();
        }

        @Override
        public com.ghatana.kernel.health.HealthStatus getHealthStatus() {
            return started
                ? com.ghatana.kernel.health.HealthStatus.healthy("Test module healthy")
                : com.ghatana.kernel.health.HealthStatus.unhealthy("Test module not started");
        }

        boolean isStarted() { return started; }
        boolean isStopped() { return stopped; }
    }
}
