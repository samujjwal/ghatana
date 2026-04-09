package com.ghatana.kernel.dependency;

import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for circular dependency detection in Kernel module registration.
 *
 * @doc.type class
 * @doc.purpose Validates circular dependency detection in module dependency graph
 * @doc.layer platform
 * @doc.pattern Test
 */
@Disabled("Current KernelRegistryImpl validates required module dependencies at registration time and does not expose the unresolved-graph contract these archived cycle tests assume.")
@DisplayName("Circular Dependency Detection Tests")
class CircularDependencyDetectionTest extends EventloopTestBase {

    private KernelRegistryImpl registry;

    @BeforeEach
    void setUp() {
        registry = new KernelRegistryImpl();
    }

    @Test
    @DisplayName("Should detect simple circular dependency (A → B → A)")
    void testSimpleCircularDependency() {
        // GIVEN: Module A depends on B, Module B depends on A
        TestModule moduleA = new TestModule("module-a", "1.0.0");
        TestModule moduleB = new TestModule("module-b", "1.0.0");

        moduleA.addDependency(new KernelDependency(
            "module-b",
            "1.0.0",
            KernelDependency.DependencyType.MODULE,
            false
        ));

        moduleB.addDependency(new KernelDependency(
            "module-a",
            "1.0.0",
            KernelDependency.DependencyType.MODULE,
            false
        ));

        // WHEN: Register module A
        registry.registerModule(moduleA);

        // THEN: Registering module B should detect circular dependency
        assertThatThrownBy(() -> registry.registerModule(moduleB))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Circular dependency detected")
            .hasMessageContaining("module-a")
            .hasMessageContaining("module-b");
    }

    @Test
    @DisplayName("Should detect complex circular dependency (A → B → C → D → B)")
    void testComplexCircularDependency() {
        // GIVEN: A → B → C → D → B (circular at B)
        TestModule moduleA = new TestModule("module-a", "1.0.0");
        TestModule moduleB = new TestModule("module-b", "1.0.0");
        TestModule moduleC = new TestModule("module-c", "1.0.0");
        TestModule moduleD = new TestModule("module-d", "1.0.0");

        moduleA.addDependency(new KernelDependency("module-b", "1.0.0", KernelDependency.DependencyType.MODULE, false));
        moduleB.addDependency(new KernelDependency("module-c", "1.0.0", KernelDependency.DependencyType.MODULE, false));
        moduleC.addDependency(new KernelDependency("module-d", "1.0.0", KernelDependency.DependencyType.MODULE, false));
        moduleD.addDependency(new KernelDependency("module-b", "1.0.0", KernelDependency.DependencyType.MODULE, false));

        // WHEN: Register modules in order
        registry.registerModule(moduleB);
        registry.registerModule(moduleC);
        registry.registerModule(moduleD);

        // THEN: Registering module A should detect circular dependency
        assertThatThrownBy(() -> registry.registerModule(moduleA))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Circular dependency");
    }

    @Test
    @DisplayName("Should detect self-dependency (A → A)")
    void testSelfDependency() {
        // GIVEN: Module depends on itself
        TestModule module = new TestModule("module-a", "1.0.0");
        module.addDependency(new KernelDependency(
            "module-a",
            "1.0.0",
            KernelDependency.DependencyType.MODULE,
            false
        ));

        // WHEN/THEN: Registration should fail
        assertThatThrownBy(() -> registry.registerModule(module))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Self-dependency")
            .hasMessageContaining("module-a");
    }

    @Test
    @DisplayName("Should allow diamond dependency pattern (not circular)")
    void testDiamondDependencyPattern() {
        // GIVEN: Diamond pattern A → B, A → C, B → D, C → D (valid, not circular)
        TestModule moduleD = new TestModule("module-d", "1.0.0");
        TestModule moduleB = new TestModule("module-b", "1.0.0");
        TestModule moduleC = new TestModule("module-c", "1.0.0");
        TestModule moduleA = new TestModule("module-a", "1.0.0");

        moduleB.addDependency(new KernelDependency("module-d", "1.0.0", KernelDependency.DependencyType.MODULE, false));
        moduleC.addDependency(new KernelDependency("module-d", "1.0.0", KernelDependency.DependencyType.MODULE, false));
        moduleA.addDependency(new KernelDependency("module-b", "1.0.0", KernelDependency.DependencyType.MODULE, false));
        moduleA.addDependency(new KernelDependency("module-c", "1.0.0", KernelDependency.DependencyType.MODULE, false));

        // WHEN: Register all modules
        assertThatCode(() -> {
            registry.registerModule(moduleD);
            registry.registerModule(moduleB);
            registry.registerModule(moduleC);
            registry.registerModule(moduleA);
        }).doesNotThrowAnyException();

        // THEN: All modules registered successfully
        assertThat(registry.getAllModules()).hasSize(4);

        // AND: Dependency resolution works correctly
        List<KernelModule> resolved = registry.resolveDependencies(moduleA);
        assertThat(resolved).containsExactlyInAnyOrder(moduleB, moduleC, moduleD);
    }

    @Test
    @DisplayName("Should detect circular dependency with optional dependencies")
    void testCircularDependencyWithOptionalDeps() {
        // GIVEN: A → B (required), B → C (required), C → A (optional)
        TestModule moduleA = new TestModule("module-a", "1.0.0");
        TestModule moduleB = new TestModule("module-b", "1.0.0");
        TestModule moduleC = new TestModule("module-c", "1.0.0");

        moduleA.addDependency(new KernelDependency("module-b", "1.0.0", KernelDependency.DependencyType.MODULE, false));
        moduleB.addDependency(new KernelDependency("module-c", "1.0.0", KernelDependency.DependencyType.MODULE, false));
        moduleC.addDependency(new KernelDependency("module-a", "1.0.0", KernelDependency.DependencyType.MODULE, true)); // optional

        // WHEN: Register modules
        assertThatCode(() -> {
            registry.registerModule(moduleA);
            registry.registerModule(moduleB);
            registry.registerModule(moduleC);
        }).doesNotThrowAnyException();

        // THEN: All modules registered (optional dependency breaks the cycle)
        assertThat(registry.getAllModules()).hasSize(3);
    }

    @Test
    @DisplayName("Should provide clear error message with dependency path")
    void testCircularDependencyErrorMessage() {
        // GIVEN: A → B → C → A
        TestModule moduleA = new TestModule("module-a", "1.0.0");
        TestModule moduleB = new TestModule("module-b", "1.0.0");
        TestModule moduleC = new TestModule("module-c", "1.0.0");

        moduleA.addDependency(new KernelDependency("module-b", "1.0.0", KernelDependency.DependencyType.MODULE, false));
        moduleB.addDependency(new KernelDependency("module-c", "1.0.0", KernelDependency.DependencyType.MODULE, false));
        moduleC.addDependency(new KernelDependency("module-a", "1.0.0", KernelDependency.DependencyType.MODULE, false));

        // WHEN: Register modules
        registry.registerModule(moduleA);
        registry.registerModule(moduleB);

        // THEN: Error message should show the cycle path
        assertThatThrownBy(() -> registry.registerModule(moduleC))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("module-a")
            .hasMessageContaining("module-b")
            .hasMessageContaining("module-c")
            .hasMessageContaining("→"); // Shows dependency path
    }

    /**
     * Test module implementation for dependency testing.
     */
    private static class TestModule implements KernelModule {
        private final String moduleId;
        private final String version;
        private final List<KernelDependency> dependencies = new java.util.ArrayList<>();

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
        public java.util.Set<KernelDependency> getDependencies() {
            return new java.util.HashSet<>(dependencies);
        }

        @Override
        public java.util.Set<com.ghatana.kernel.descriptor.KernelCapability> getCapabilities() {
            return java.util.Set.of();
        }

        @Override
        public void initialize(com.ghatana.kernel.context.KernelContext context) {
            // No-op for test
        }

        @Override
        public io.activej.promise.Promise<Void> start() {
            return io.activej.promise.Promise.complete();
        }

        @Override
        public io.activej.promise.Promise<Void> stop() {
            return io.activej.promise.Promise.complete();
        }

        @Override
        public com.ghatana.platform.health.HealthStatus getHealthStatus() {
            return com.ghatana.platform.health.HealthStatus.healthy();
        }
    }
}
