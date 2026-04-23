package com.ghatana.kernel;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.plugin.KernelPlugin;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 * Kernel registry unit tests — ALL async tests MUST extend EventloopTestBase.
 *
 * <p>CRITICAL: NEVER call {@code .getResult()} on a Promise (throws NPE). // GH-90000
 * Use {@code runPromise(() -> ...)} from EventloopTestBase instead.</p> // GH-90000
 *
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@DisplayName("KernelRegistry Tests")
class KernelRegistryTest extends EventloopTestBase {

    private KernelRegistryImpl registry;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new KernelRegistryImpl(); // GH-90000
    }

    @Test
    @DisplayName("Should register and retrieve module by ID")
    void testModuleRegistration() { // GH-90000
        // GIVEN
        TestKernelModule module = new TestKernelModule("test-module", "1.0.0"); // GH-90000

        // WHEN
        registry.registerModule(module); // GH-90000

        // THEN
        Optional<KernelModule> retrieved = registry.getModule("test-module");
        assertThat(retrieved).isPresent(); // GH-90000
        assertThat(retrieved.get()).isEqualTo(module); // GH-90000
    }

    @Test
    @DisplayName("Should throw exception when registering duplicate module")
    void testDuplicateModuleRegistration() { // GH-90000
        // GIVEN
        TestKernelModule module = new TestKernelModule("test-module", "1.0.0"); // GH-90000
        registry.registerModule(module); // GH-90000

        // WHEN/THEN
        assertThatThrownBy(() -> registry.registerModule(module)) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("Module already registered");
    }

    @Test
    @DisplayName("Should resolve dependencies in topological order")
    void testDependencyResolution() { // GH-90000
        // GIVEN
        TestKernelModule moduleA = new TestKernelModule("module-a", "1.0.0", Set.of()); // GH-90000
        TestKernelModule moduleB = new TestKernelModule("module-b", "1.0.0", // GH-90000
            Set.of(new KernelDependency("module-a", "1.0.0", KernelDependency.DependencyType.MODULE, false))); // GH-90000

        registry.registerModule(moduleA); // GH-90000
        registry.registerModule(moduleB); // GH-90000

        // WHEN
        List<KernelModule> dependencies = registry.resolveDependencies(moduleB); // GH-90000

        // THEN
        assertThat(dependencies).hasSize(1); // GH-90000
        assertThat(dependencies.get(0)).isEqualTo(moduleA); // GH-90000
    }

    @Test
    @DisplayName("Should validate module dependencies")
    void testDependencyValidation() { // GH-90000
        // GIVEN
        TestKernelModule module = new TestKernelModule("test-module", "1.0.0", // GH-90000
            Set.of(new KernelDependency("missing-module", "1.0.0", KernelDependency.DependencyType.MODULE, false))); // GH-90000

        // WHEN/THEN
        assertThatThrownBy(() -> registry.registerModule(module)) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("Dependency validation failed");
    }

    @Test
    @DisplayName("Should start all modules in dependency order using runPromise")
    void testModuleLifecycle_async() { // GH-90000
        // GIVEN
        TestKernelModule module = new TestKernelModule("lifecycle-test", "1.0.0"); // GH-90000
        registry.registerModule(module); // GH-90000

        // WHEN - runPromise blocks the Eventloop correctly
        runPromise(() -> module.start()); // GH-90000

        // THEN
        assertThat(module.isStarted()).isTrue(); // GH-90000

        // AND stop
        runPromise(() -> module.stop()); // GH-90000
        assertThat(module.isStopped()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should start multiple modules in parallel using Promises.all")
    void testMultipleModuleStart() { // GH-90000
        // GIVEN
        TestKernelModule module1 = new TestKernelModule("module-1", "1.0.0"); // GH-90000
        TestKernelModule module2 = new TestKernelModule("module-2", "1.0.0"); // GH-90000

        registry.registerModule(module1); // GH-90000
        registry.registerModule(module2); // GH-90000

        // WHEN - use runPromise for the combined promise
        runPromise(() -> Promises.all(List.of(module1.start(), module2.start()))); // GH-90000

        // THEN
        assertThat(module1.isStarted()).isTrue(); // GH-90000
        assertThat(module2.isStarted()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should return healthy status for operational module")
    void testHealthStatus() { // GH-90000
        // GIVEN
        TestKernelModule module = new TestKernelModule("health-test", "1.0.0"); // GH-90000
        registry.registerModule(module); // GH-90000

        // WHEN
        runPromise(() -> module.start()); // GH-90000
        HealthStatus status = module.getHealthStatus(); // GH-90000

        // THEN
        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY); // GH-90000
    }

    @Test
    @DisplayName("Should aggregate health status from all modules")
    void testAggregateHealthStatus() { // GH-90000
        // GIVEN
        TestKernelModule healthyModule = new TestKernelModule("healthy", "1.0.0"); // GH-90000
        TestKernelModule unhealthyModule = new TestKernelModule("unhealthy", "1.0.0") { // GH-90000
            @Override
            public HealthStatus getHealthStatus() { // GH-90000
                return HealthStatus.unhealthy("Test failure");
            }
        };

        registry.registerModule(healthyModule); // GH-90000
        registry.registerModule(unhealthyModule); // GH-90000

        // WHEN
        HealthStatus aggregate = registry.getAggregateHealthStatus(); // GH-90000

        // THEN
        assertThat(aggregate.getStatus()).isEqualTo(HealthStatus.Status.DEGRADED); // GH-90000
    }

    @Test
    @DisplayName("Should get modules by capability")
    void testGetModulesByCapability() { // GH-90000
        // GIVEN
        KernelCapability dataStorage = KernelCapability.Core.DATA_STORAGE;
        TestKernelModule module = new TestKernelModule("data-module", "1.0.0", // GH-90000
            Set.of(dataStorage), Set.of()); // GH-90000

        registry.registerModule(module); // GH-90000

        // WHEN
        List<KernelModule> modules = registry.getModulesByCapability(dataStorage); // GH-90000

        // THEN
        assertThat(modules).containsExactly(module); // GH-90000
    }

    @Test
    @DisplayName("Should handle plugin registration")
    void testPluginRegistration() { // GH-90000
        // GIVEN
        TestKernelPlugin plugin = new TestKernelPlugin("test-plugin", "1.0.0"); // GH-90000

        // WHEN
        registry.registerPlugin(plugin); // GH-90000

        // THEN
        Optional<KernelPlugin> retrieved = registry.getPlugin("test-plugin");
        assertThat(retrieved).isPresent(); // GH-90000
        assertThat(retrieved.get()).isEqualTo(plugin); // GH-90000
    }

    @Test
    @DisplayName("Should validate capability dependencies")
    void testCapabilityDependencyValidation() { // GH-90000
        // GIVEN - module requiring DATA_STORAGE capability
        TestKernelModule module = new TestKernelModule("storage-user", "1.0.0", // GH-90000
            Set.of(), // GH-90000
            Set.of(new KernelDependency("data.storage", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false))); // GH-90000

        // Register a module providing DATA_STORAGE
        TestKernelModule storageModule = new TestKernelModule("storage-provider", "1.0.0", // GH-90000
            Set.of(KernelCapability.Core.DATA_STORAGE), Set.of()); // GH-90000
        registry.registerModule(storageModule); // GH-90000

        // WHEN/THEN - should not throw since capability is available
        assertThatCode(() -> registry.registerModule(module)).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("Should reject module with missing capability dependency")
    void testMissingCapabilityDependency() { // GH-90000
        // GIVEN - module requiring non-existent capability
        TestKernelModule module = new TestKernelModule("storage-user", "1.0.0", // GH-90000
            Set.of(), // GH-90000
            Set.of(new KernelDependency("nonexistent.capability", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false))); // GH-90000

        // WHEN/THEN
        assertThatThrownBy(() -> registry.registerModule(module)) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("Missing required capability");
    }

    // ==================== Test Helpers ====================

    private static class TestKernelModule implements KernelModule {
        private final String moduleId;
        private final String version;
        private final Set<KernelCapability> capabilities;
        private final Set<KernelDependency> dependencies;
        private final AtomicBoolean started = new AtomicBoolean(false); // GH-90000
        private final AtomicBoolean stopped = new AtomicBoolean(false); // GH-90000

        TestKernelModule(String moduleId, String version) { // GH-90000
            this(moduleId, version, Set.of(), Set.of()); // GH-90000
        }

        TestKernelModule(String moduleId, String version, Set<KernelDependency> dependencies) { // GH-90000
            this(moduleId, version, Set.of(), dependencies); // GH-90000
        }

        TestKernelModule(String moduleId, String version, Set<KernelCapability> capabilities, // GH-90000
                        Set<KernelDependency> dependencies) {
            this.moduleId = moduleId;
            this.version = version;
            this.capabilities = capabilities;
            this.dependencies = dependencies;
        }

        @Override
        public String getModuleId() { return moduleId; } // GH-90000

        @Override
        public String getVersion() { return version; } // GH-90000

        @Override
        public Set<KernelCapability> getCapabilities() { return capabilities; } // GH-90000

        @Override
        public Set<KernelDependency> getDependencies() { return dependencies; } // GH-90000

        @Override
        public void initialize(com.ghatana.kernel.context.KernelContext context) {} // GH-90000

        @Override
        public Promise<Void> start() { // GH-90000
            started.set(true); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Void> stop() { // GH-90000
            stopped.set(true); // GH-90000
            started.set(false); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public HealthStatus getHealthStatus() { // GH-90000
            if (!started.get()) { // GH-90000
                return HealthStatus.unhealthy("Not started");
            }
            return HealthStatus.healthy(); // GH-90000
        }

        boolean isStarted() { return started.get(); } // GH-90000
        boolean isStopped() { return stopped.get(); } // GH-90000
    }

    private static class TestKernelPlugin implements KernelPlugin {
        private final String pluginId;
        private final String version;

        TestKernelPlugin(String pluginId, String version) { // GH-90000
            this.pluginId = pluginId;
            this.version = version;
        }

        @Override
        public com.ghatana.kernel.plugin.PluginManifest getManifest() { // GH-90000
            return com.ghatana.kernel.plugin.PluginManifest.builder() // GH-90000
                .pluginId(pluginId) // GH-90000
                .version(version) // GH-90000
                .build(); // GH-90000
        }

        @Override
        public Set<String> getExportedContracts() { return Set.of(); } // GH-90000

        @Override
        public Set<String> getRequiredContracts() { return Set.of(); } // GH-90000

        @Override
        public String getModuleId() { return pluginId; } // GH-90000

        @Override
        public String getVersion() { return version; } // GH-90000

        @Override
        public Set<KernelCapability> getCapabilities() { return Set.of(); } // GH-90000

        @Override
        public Set<KernelDependency> getDependencies() { return Set.of(); } // GH-90000

        @Override
        public void initialize(com.ghatana.kernel.context.KernelContext context) {} // GH-90000

        @Override
        public Promise<Void> install() { return Promise.complete(); } // GH-90000

        @Override
        public Promise<Void> uninstall() { return Promise.complete(); } // GH-90000

        @Override
        public Promise<Void> start() { return Promise.complete(); } // GH-90000

        @Override
        public Promise<Void> stop() { return Promise.complete(); } // GH-90000

        @Override
        public HealthStatus getHealthStatus() { return HealthStatus.healthy(); } // GH-90000
    }
}
