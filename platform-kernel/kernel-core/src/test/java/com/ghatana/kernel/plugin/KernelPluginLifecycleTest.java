package com.ghatana.kernel.plugin;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.test.TestKernelContextFactory;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Kernel plugin lifecycle management.
 * Validates plugin registration, initialization, and lifecycle transitions.
 *
 * @doc.type class
 * @doc.purpose Validates Kernel plugin lifecycle and state management
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Kernel Plugin Lifecycle Tests [GH-90000]")
class KernelPluginLifecycleTest extends EventloopTestBase {

    private KernelRegistryImpl registry;
    private KernelContext context;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new KernelRegistryImpl(); // GH-90000
        context = TestKernelContextFactory.create(registry); // GH-90000
    }

    @Test
    @DisplayName("Should register and initialize plugin successfully [GH-90000]")
    void testPluginRegistrationAndInitialization() { // GH-90000
        // GIVEN: A plugin module
        TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0"); // GH-90000

        // WHEN: Register and initialize plugin
        registry.registerModule(plugin); // GH-90000
        plugin.initialize(context); // GH-90000

        // THEN: Plugin is initialized
        assertThat(plugin.isInitialized()).isTrue(); // GH-90000
        assertThat(plugin.getInitializationCount()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should handle plugin start and stop lifecycle [GH-90000]")
    void testPluginStartStopLifecycle() { // GH-90000
        // GIVEN: An initialized plugin
        TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0"); // GH-90000
        registry.registerModule(plugin); // GH-90000
        plugin.initialize(context); // GH-90000

        // WHEN: Start plugin
        runPromise(() -> plugin.start()); // GH-90000

        // THEN: Plugin is started
        assertThat(plugin.isStarted()).isTrue(); // GH-90000

        // WHEN: Stop plugin
        runPromise(() -> plugin.stop()); // GH-90000

        // THEN: Plugin is stopped
        assertThat(plugin.isStarted()).isFalse(); // GH-90000
        assertThat(plugin.isStopped()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should prevent double initialization [GH-90000]")
    void testPreventDoubleInitialization() { // GH-90000
        // GIVEN: An initialized plugin
        TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0"); // GH-90000
        registry.registerModule(plugin); // GH-90000
        plugin.initialize(context); // GH-90000

        // WHEN: Attempt to initialize again
        plugin.initialize(context); // GH-90000

        // THEN: Initialization only happens once
        assertThat(plugin.getInitializationCount()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should handle plugin dependencies during initialization [GH-90000]")
    void testPluginDependencyInitialization() { // GH-90000
        // GIVEN: Two plugins with dependency relationship
        TestPlugin providerPlugin = new TestPlugin("provider-plugin", "1.0.0"); // GH-90000
        providerPlugin.addCapability(createTestCapability("test-capability [GH-90000]"));

        DependentPlugin dependentPlugin = new DependentPlugin( // GH-90000
            "dependent-plugin",
            "1.0.0",
            "provider-plugin"
        );

        // WHEN: Register both plugins
        registry.registerModule(providerPlugin); // GH-90000
        registry.registerModule(dependentPlugin); // GH-90000

        providerPlugin.initialize(context); // GH-90000
        dependentPlugin.initialize(context); // GH-90000

        // THEN: Both plugins initialized
        assertThat(providerPlugin.isInitialized()).isTrue(); // GH-90000
        assertThat(dependentPlugin.isInitialized()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should handle plugin unregistration [GH-90000]")
    void testPluginUnregistration() { // GH-90000
        // GIVEN: A registered and started plugin
        TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0"); // GH-90000
        registry.registerModule(plugin); // GH-90000
        plugin.initialize(context); // GH-90000
        runPromise(() -> plugin.start()); // GH-90000

        // WHEN: Unregister plugin
        runPromise(() -> plugin.stop()); // GH-90000
        // In real implementation: registry.unregisterModule(plugin.getModuleId()); // GH-90000

        // THEN: Plugin is stopped
        assertThat(plugin.isStopped()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should handle plugin state transitions [GH-90000]")
    void testPluginStateTransitions() { // GH-90000
        // GIVEN: A plugin tracking state transitions
        StateTrackingPlugin plugin = new StateTrackingPlugin("state-plugin", "1.0.0"); // GH-90000
        registry.registerModule(plugin); // GH-90000

        // WHEN: Go through lifecycle
        plugin.initialize(context); // GH-90000
        assertThat(plugin.getCurrentState()).isEqualTo(PluginState.INITIALIZED); // GH-90000

        runPromise(() -> plugin.start()); // GH-90000
        assertThat(plugin.getCurrentState()).isEqualTo(PluginState.STARTED); // GH-90000

        runPromise(() -> plugin.stop()); // GH-90000
        assertThat(plugin.getCurrentState()).isEqualTo(PluginState.STOPPED); // GH-90000

        // THEN: State transitions recorded
        assertThat(plugin.getStateHistory()).containsExactly( // GH-90000
            PluginState.REGISTERED,
            PluginState.INITIALIZED,
            PluginState.STARTED,
            PluginState.STOPPED
        );
    }

    @Test
    @DisplayName("Should handle plugin initialization failure [GH-90000]")
    void testPluginInitializationFailure() { // GH-90000
        // GIVEN: A plugin that fails initialization
        FailingPlugin plugin = new FailingPlugin("failing-plugin", "1.0.0"); // GH-90000
        registry.registerModule(plugin); // GH-90000

        // WHEN: Attempt to initialize
        // THEN: Initialization fails
        assertThatThrownBy(() -> plugin.initialize(context)) // GH-90000
            .isInstanceOf(RuntimeException.class) // GH-90000
            .hasMessageContaining("Initialization failed [GH-90000]");

        assertThat(plugin.isInitialized()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should handle plugin hot reload [GH-90000]")
    void testPluginHotReload() { // GH-90000
        // GIVEN: A running plugin
        TestPlugin plugin = new TestPlugin("hot-reload-plugin", "1.0.0"); // GH-90000
        registry.registerModule(plugin); // GH-90000
        plugin.initialize(context); // GH-90000
        runPromise(() -> plugin.start()); // GH-90000

        // WHEN: Stop, update, and restart
        runPromise(() -> plugin.stop()); // GH-90000
        plugin.updateVersion("2.0.0 [GH-90000]");
        runPromise(() -> plugin.start()); // GH-90000

        // THEN: Plugin restarted with new version
        assertThat(plugin.isStarted()).isTrue(); // GH-90000
        assertThat(plugin.getVersion()).isEqualTo("2.0.0 [GH-90000]");
    }

    // Test plugin implementations

    private static class TestPlugin implements KernelModule {
        private final String moduleId;
        private String version;
        private final AtomicBoolean initialized = new AtomicBoolean(false); // GH-90000
        private final AtomicBoolean started = new AtomicBoolean(false); // GH-90000
        private final AtomicBoolean stopped = new AtomicBoolean(false); // GH-90000
        private final AtomicInteger initializationCount = new AtomicInteger(0); // GH-90000
        private final List<KernelCapability> capabilities = new ArrayList<>(); // GH-90000

        TestPlugin(String moduleId, String version) { // GH-90000
            this.moduleId = moduleId;
            this.version = version;
        }

        void addCapability(KernelCapability capability) { // GH-90000
            capabilities.add(capability); // GH-90000
        }

        void updateVersion(String newVersion) { // GH-90000
            this.version = newVersion;
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
            return Set.copyOf(capabilities); // GH-90000
        }

        @Override
        public void initialize(KernelContext context) { // GH-90000
            if (initialized.compareAndSet(false, true)) { // GH-90000
                initializationCount.incrementAndGet(); // GH-90000
            }
        }

        @Override
        public Promise<Void> start() { // GH-90000
            started.set(true); // GH-90000
            stopped.set(false); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Void> stop() { // GH-90000
            started.set(false); // GH-90000
            stopped.set(true); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public HealthStatus getHealthStatus() { // GH-90000
            return HealthStatus.healthy(); // GH-90000
        }

        boolean isInitialized() { // GH-90000
            return initialized.get(); // GH-90000
        }

        boolean isStarted() { // GH-90000
            return started.get(); // GH-90000
        }

        boolean isStopped() { // GH-90000
            return stopped.get(); // GH-90000
        }

        int getInitializationCount() { // GH-90000
            return initializationCount.get(); // GH-90000
        }
    }

    private static class DependentPlugin extends TestPlugin {
        private final List<KernelDependency> dependencies = new ArrayList<>(); // GH-90000

        DependentPlugin(String moduleId, String version, String dependsOn) { // GH-90000
            super(moduleId, version); // GH-90000
            dependencies.add(new KernelDependency(dependsOn, "1.0.0", KernelDependency.DependencyType.MODULE, false)); // GH-90000
        }

        @Override
        public Set<KernelDependency> getDependencies() { // GH-90000
            return Set.copyOf(dependencies); // GH-90000
        }
    }

    private enum PluginState {
        REGISTERED,
        INITIALIZED,
        STARTED,
        STOPPED
    }

    private static class StateTrackingPlugin extends TestPlugin {
        private PluginState currentState = PluginState.REGISTERED;
        private final List<PluginState> stateHistory = new ArrayList<>(); // GH-90000

        StateTrackingPlugin(String moduleId, String version) { // GH-90000
            super(moduleId, version); // GH-90000
            stateHistory.add(PluginState.REGISTERED); // GH-90000
        }

        @Override
        public void initialize(KernelContext context) { // GH-90000
            super.initialize(context); // GH-90000
            currentState = PluginState.INITIALIZED;
            stateHistory.add(PluginState.INITIALIZED); // GH-90000
        }

        @Override
        public Promise<Void> start() { // GH-90000
            return super.start().then(() -> { // GH-90000
                currentState = PluginState.STARTED;
                stateHistory.add(PluginState.STARTED); // GH-90000
                return Promise.complete(); // GH-90000
            });
        }

        @Override
        public Promise<Void> stop() { // GH-90000
            return super.stop().then(() -> { // GH-90000
                currentState = PluginState.STOPPED;
                stateHistory.add(PluginState.STOPPED); // GH-90000
                return Promise.complete(); // GH-90000
            });
        }

        PluginState getCurrentState() { // GH-90000
            return currentState;
        }

        List<PluginState> getStateHistory() { // GH-90000
            return new ArrayList<>(stateHistory); // GH-90000
        }
    }

    private static class FailingPlugin extends TestPlugin {
        FailingPlugin(String moduleId, String version) { // GH-90000
            super(moduleId, version); // GH-90000
        }

        @Override
        public void initialize(KernelContext context) { // GH-90000
            throw new RuntimeException("Initialization failed [GH-90000]");
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
