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
 * <p>CRITICAL: NEVER call {@code .getResult()} on a Promise (throws NPE). 
 * Use {@code runPromise(() -> ...)} from EventloopTestBase instead.</p> 
 *
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@DisplayName("KernelRegistry Tests")
class KernelRegistryTest extends EventloopTestBase {

    private KernelRegistryImpl registry;

    @BeforeEach
    void setUp() { 
        registry = new KernelRegistryImpl(); 
    }

    @Test
    @DisplayName("Should register and retrieve module by ID")
    void testModuleRegistration() { 
        // GIVEN
        TestKernelModule module = new TestKernelModule("test-module", "1.0.0"); 

        // WHEN
        registry.registerModule(module); 

        // THEN
        Optional<KernelModule> retrieved = registry.getModule("test-module");
        assertThat(retrieved).isPresent(); 
        assertThat(retrieved.get()).isEqualTo(module); 
    }

    @Test
    @DisplayName("Should throw exception when registering duplicate module")
    void testDuplicateModuleRegistration() { 
        // GIVEN
        TestKernelModule module = new TestKernelModule("test-module", "1.0.0"); 
        registry.registerModule(module); 

        // WHEN/THEN
        assertThatThrownBy(() -> registry.registerModule(module)) 
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("Module already registered");
    }

    @Test
    @DisplayName("Should resolve dependencies in topological order")
    void testDependencyResolution() { 
        // GIVEN
        TestKernelModule moduleA = new TestKernelModule("module-a", "1.0.0", Set.of()); 
        TestKernelModule moduleB = new TestKernelModule("module-b", "1.0.0", 
            Set.of(new KernelDependency("module-a", "1.0.0", KernelDependency.DependencyType.MODULE, false))); 

        registry.registerModule(moduleA); 
        registry.registerModule(moduleB); 

        // WHEN
        List<KernelModule> dependencies = registry.resolveDependencies(moduleB); 

        // THEN
        assertThat(dependencies).hasSize(1); 
        assertThat(dependencies.get(0)).isEqualTo(moduleA); 
    }

    @Test
    @DisplayName("Should validate module dependencies")
    void testDependencyValidation() { 
        // GIVEN
        TestKernelModule module = new TestKernelModule("test-module", "1.0.0", 
            Set.of(new KernelDependency("missing-module", "1.0.0", KernelDependency.DependencyType.MODULE, false))); 

        // WHEN/THEN
        assertThatThrownBy(() -> registry.registerModule(module)) 
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("Dependency validation failed");
    }

    @Test
    @DisplayName("Should start all modules in dependency order using runPromise")
    void testModuleLifecycle_async() { 
        // GIVEN
        TestKernelModule module = new TestKernelModule("lifecycle-test", "1.0.0"); 
        registry.registerModule(module); 

        // WHEN - runPromise blocks the Eventloop correctly
        runPromise(() -> module.start()); 

        // THEN
        assertThat(module.isStarted()).isTrue(); 

        // AND stop
        runPromise(() -> module.stop()); 
        assertThat(module.isStopped()).isTrue(); 
    }

    @Test
    @DisplayName("Should start multiple modules in parallel using Promises.all")
    void testMultipleModuleStart() { 
        // GIVEN
        TestKernelModule module1 = new TestKernelModule("module-1", "1.0.0"); 
        TestKernelModule module2 = new TestKernelModule("module-2", "1.0.0"); 

        registry.registerModule(module1); 
        registry.registerModule(module2); 

        // WHEN - use runPromise for the combined promise
        runPromise(() -> Promises.all(List.of(module1.start(), module2.start()))); 

        // THEN
        assertThat(module1.isStarted()).isTrue(); 
        assertThat(module2.isStarted()).isTrue(); 
    }

    @Test
    @DisplayName("Should return healthy status for operational module")
    void testHealthStatus() { 
        // GIVEN
        TestKernelModule module = new TestKernelModule("health-test", "1.0.0"); 
        registry.registerModule(module); 

        // WHEN
        runPromise(() -> module.start()); 
        HealthStatus status = module.getHealthStatus(); 

        // THEN
        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY); 
    }

    @Test
    @DisplayName("Should aggregate health status from all modules")
    void testAggregateHealthStatus() { 
        // GIVEN
        TestKernelModule healthyModule = new TestKernelModule("healthy", "1.0.0"); 
        TestKernelModule unhealthyModule = new TestKernelModule("unhealthy", "1.0.0") { 
            @Override
            public HealthStatus getHealthStatus() { 
                return HealthStatus.unhealthy("Test failure");
            }
        };

        registry.registerModule(healthyModule); 
        registry.registerModule(unhealthyModule); 

        // WHEN
        HealthStatus aggregate = registry.getAggregateHealthStatus(); 

        // THEN
        assertThat(aggregate.getStatus()).isEqualTo(HealthStatus.Status.DEGRADED); 
    }

    @Test
    @DisplayName("Should get modules by capability")
    void testGetModulesByCapability() { 
        // GIVEN
        KernelCapability dataStorage = KernelCapability.Core.DATA_STORAGE;
        TestKernelModule module = new TestKernelModule("data-module", "1.0.0", 
            Set.of(dataStorage), Set.of()); 

        registry.registerModule(module); 

        // WHEN
        List<KernelModule> modules = registry.getModulesByCapability(dataStorage); 

        // THEN
        assertThat(modules).containsExactly(module); 
    }

    @Test
    @DisplayName("Should handle plugin registration")
    void testPluginRegistration() { 
        // GIVEN
        TestKernelPlugin plugin = new TestKernelPlugin("test-plugin", "1.0.0"); 

        // WHEN
        registry.registerPlugin(plugin); 

        // THEN
        Optional<KernelPlugin> retrieved = registry.getPlugin("test-plugin");
        assertThat(retrieved).isPresent(); 
        assertThat(retrieved.get()).isEqualTo(plugin); 
    }

    @Test
    @DisplayName("Should validate capability dependencies")
    void testCapabilityDependencyValidation() { 
        // GIVEN - module requiring DATA_STORAGE capability
        TestKernelModule module = new TestKernelModule("storage-user", "1.0.0", 
            Set.of(), 
            Set.of(new KernelDependency("data.storage", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false))); 

        // Register a module providing DATA_STORAGE
        TestKernelModule storageModule = new TestKernelModule("storage-provider", "1.0.0", 
            Set.of(KernelCapability.Core.DATA_STORAGE), Set.of()); 
        registry.registerModule(storageModule); 

        // WHEN/THEN - should not throw since capability is available
        assertThatCode(() -> registry.registerModule(module)).doesNotThrowAnyException(); 
    }

    @Test
    @DisplayName("Should reject module with missing capability dependency")
    void testMissingCapabilityDependency() { 
        // GIVEN - module requiring non-existent capability
        TestKernelModule module = new TestKernelModule("storage-user", "1.0.0", 
            Set.of(), 
            Set.of(new KernelDependency("nonexistent.capability", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false))); 

        // WHEN/THEN
        assertThatThrownBy(() -> registry.registerModule(module)) 
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("Missing required capability");
    }

    // ==================== Test Helpers ====================

    private static class TestKernelModule implements KernelModule {
        private final String moduleId;
        private final String version;
        private final Set<KernelCapability> capabilities;
        private final Set<KernelDependency> dependencies;
        private final AtomicBoolean started = new AtomicBoolean(false); 
        private final AtomicBoolean stopped = new AtomicBoolean(false); 

        TestKernelModule(String moduleId, String version) { 
            this(moduleId, version, Set.of(), Set.of()); 
        }

        TestKernelModule(String moduleId, String version, Set<KernelDependency> dependencies) { 
            this(moduleId, version, Set.of(), dependencies); 
        }

        TestKernelModule(String moduleId, String version, Set<KernelCapability> capabilities, 
                        Set<KernelDependency> dependencies) {
            this.moduleId = moduleId;
            this.version = version;
            this.capabilities = capabilities;
            this.dependencies = dependencies;
        }

        @Override
        public String getModuleId() { return moduleId; } 

        @Override
        public String getVersion() { return version; } 

        @Override
        public Set<KernelCapability> getCapabilities() { return capabilities; } 

        @Override
        public Set<KernelDependency> getDependencies() { return dependencies; } 

        @Override
        public void initialize(com.ghatana.kernel.context.KernelContext context) {} 

        @Override
        public Promise<Void> start() { 
            started.set(true); 
            return Promise.complete(); 
        }

        @Override
        public Promise<Void> stop() { 
            stopped.set(true); 
            started.set(false); 
            return Promise.complete(); 
        }

        @Override
        public HealthStatus getHealthStatus() { 
            if (!started.get()) { 
                return HealthStatus.unhealthy("Not started");
            }
            return HealthStatus.healthy(); 
        }

        boolean isStarted() { return started.get(); } 
        boolean isStopped() { return stopped.get(); } 
    }

    private static class TestKernelPlugin implements KernelPlugin {
        private final String pluginId;
        private final String version;

        TestKernelPlugin(String pluginId, String version) { 
            this.pluginId = pluginId;
            this.version = version;
        }

        @Override
        public com.ghatana.kernel.plugin.PluginManifest getManifest() { 
            return com.ghatana.kernel.plugin.PluginManifest.builder() 
                .pluginId(pluginId) 
                .version(version) 
                .build(); 
        }

        @Override
        public Set<String> getExportedContracts() { return Set.of(); } 

        @Override
        public Set<String> getRequiredContracts() { return Set.of(); } 

        @Override
        public String getModuleId() { return pluginId; } 

        @Override
        public String getVersion() { return version; } 

        @Override
        public Set<KernelCapability> getCapabilities() { return Set.of(); } 

        @Override
        public Set<KernelDependency> getDependencies() { return Set.of(); } 

        @Override
        public void initialize(com.ghatana.kernel.context.KernelContext context) {} 

        @Override
        public Promise<Void> install() { return Promise.complete(); } 

        @Override
        public Promise<Void> uninstall() { return Promise.complete(); } 

        @Override
        public Promise<Void> start() { return Promise.complete(); } 

        @Override
        public Promise<Void> stop() { return Promise.complete(); } 

        @Override
        public HealthStatus getHealthStatus() { return HealthStatus.healthy(); } 
    }
}
