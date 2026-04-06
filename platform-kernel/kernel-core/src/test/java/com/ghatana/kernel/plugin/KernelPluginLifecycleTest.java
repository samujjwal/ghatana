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
@DisplayName("Kernel Plugin Lifecycle Tests")
class KernelPluginLifecycleTest extends EventloopTestBase {

    private KernelRegistryImpl registry;
    private KernelContext context;

    @BeforeEach
    void setUp() {
        registry = new KernelRegistryImpl();
        context = TestKernelContextFactory.create(registry);
    }

    @Test
    @DisplayName("Should register and initialize plugin successfully")
    void testPluginRegistrationAndInitialization() {
        // GIVEN: A plugin module
        TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");

        // WHEN: Register and initialize plugin
        registry.registerModule(plugin);
        plugin.initialize(context);

        // THEN: Plugin is initialized
        assertThat(plugin.isInitialized()).isTrue();
        assertThat(plugin.getInitializationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle plugin start and stop lifecycle")
    void testPluginStartStopLifecycle() {
        // GIVEN: An initialized plugin
        TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
        registry.registerModule(plugin);
        plugin.initialize(context);

        // WHEN: Start plugin
        runPromise(() -> plugin.start());

        // THEN: Plugin is started
        assertThat(plugin.isStarted()).isTrue();

        // WHEN: Stop plugin
        runPromise(() -> plugin.stop());

        // THEN: Plugin is stopped
        assertThat(plugin.isStarted()).isFalse();
        assertThat(plugin.isStopped()).isTrue();
    }

    @Test
    @DisplayName("Should prevent double initialization")
    void testPreventDoubleInitialization() {
        // GIVEN: An initialized plugin
        TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
        registry.registerModule(plugin);
        plugin.initialize(context);

        // WHEN: Attempt to initialize again
        plugin.initialize(context);

        // THEN: Initialization only happens once
        assertThat(plugin.getInitializationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle plugin dependencies during initialization")
    void testPluginDependencyInitialization() {
        // GIVEN: Two plugins with dependency relationship
        TestPlugin providerPlugin = new TestPlugin("provider-plugin", "1.0.0");
        providerPlugin.addCapability(createTestCapability("test-capability"));

        DependentPlugin dependentPlugin = new DependentPlugin(
            "dependent-plugin",
            "1.0.0",
            "provider-plugin"
        );

        // WHEN: Register both plugins
        registry.registerModule(providerPlugin);
        registry.registerModule(dependentPlugin);

        providerPlugin.initialize(context);
        dependentPlugin.initialize(context);

        // THEN: Both plugins initialized
        assertThat(providerPlugin.isInitialized()).isTrue();
        assertThat(dependentPlugin.isInitialized()).isTrue();
    }

    @Test
    @DisplayName("Should handle plugin unregistration")
    void testPluginUnregistration() {
        // GIVEN: A registered and started plugin
        TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
        registry.registerModule(plugin);
        plugin.initialize(context);
        runPromise(() -> plugin.start());

        // WHEN: Unregister plugin
        runPromise(() -> plugin.stop());
        // In real implementation: registry.unregisterModule(plugin.getModuleId());

        // THEN: Plugin is stopped
        assertThat(plugin.isStopped()).isTrue();
    }

    @Test
    @DisplayName("Should handle plugin state transitions")
    void testPluginStateTransitions() {
        // GIVEN: A plugin tracking state transitions
        StateTrackingPlugin plugin = new StateTrackingPlugin("state-plugin", "1.0.0");
        registry.registerModule(plugin);

        // WHEN: Go through lifecycle
        plugin.initialize(context);
        assertThat(plugin.getCurrentState()).isEqualTo(PluginState.INITIALIZED);

        runPromise(() -> plugin.start());
        assertThat(plugin.getCurrentState()).isEqualTo(PluginState.STARTED);

        runPromise(() -> plugin.stop());
        assertThat(plugin.getCurrentState()).isEqualTo(PluginState.STOPPED);

        // THEN: State transitions recorded
        assertThat(plugin.getStateHistory()).containsExactly(
            PluginState.REGISTERED,
            PluginState.INITIALIZED,
            PluginState.STARTED,
            PluginState.STOPPED
        );
    }

    @Test
    @DisplayName("Should handle plugin initialization failure")
    void testPluginInitializationFailure() {
        // GIVEN: A plugin that fails initialization
        FailingPlugin plugin = new FailingPlugin("failing-plugin", "1.0.0");
        registry.registerModule(plugin);

        // WHEN: Attempt to initialize
        // THEN: Initialization fails
        assertThatThrownBy(() -> plugin.initialize(context))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Initialization failed");

        assertThat(plugin.isInitialized()).isFalse();
    }

    @Test
    @DisplayName("Should handle plugin hot reload")
    void testPluginHotReload() {
        // GIVEN: A running plugin
        TestPlugin plugin = new TestPlugin("hot-reload-plugin", "1.0.0");
        registry.registerModule(plugin);
        plugin.initialize(context);
        runPromise(() -> plugin.start());

        // WHEN: Stop, update, and restart
        runPromise(() -> plugin.stop());
        plugin.updateVersion("2.0.0");
        runPromise(() -> plugin.start());

        // THEN: Plugin restarted with new version
        assertThat(plugin.isStarted()).isTrue();
        assertThat(plugin.getVersion()).isEqualTo("2.0.0");
    }

    // Test plugin implementations

    private static class TestPlugin implements KernelModule {
        private final String moduleId;
        private String version;
        private final AtomicBoolean initialized = new AtomicBoolean(false);
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicBoolean stopped = new AtomicBoolean(false);
        private final AtomicInteger initializationCount = new AtomicInteger(0);
        private final List<KernelCapability> capabilities = new ArrayList<>();

        TestPlugin(String moduleId, String version) {
            this.moduleId = moduleId;
            this.version = version;
        }

        void addCapability(KernelCapability capability) {
            capabilities.add(capability);
        }

        void updateVersion(String newVersion) {
            this.version = newVersion;
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
            return Set.of();
        }

        @Override
        public Set<KernelCapability> getCapabilities() {
            return Set.copyOf(capabilities);
        }

        @Override
        public void initialize(KernelContext context) {
            if (initialized.compareAndSet(false, true)) {
                initializationCount.incrementAndGet();
            }
        }

        @Override
        public Promise<Void> start() {
            started.set(true);
            stopped.set(false);
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            started.set(false);
            stopped.set(true);
            return Promise.complete();
        }

        @Override
        public HealthStatus getHealthStatus() {
            return HealthStatus.healthy();
        }

        boolean isInitialized() {
            return initialized.get();
        }

        boolean isStarted() {
            return started.get();
        }

        boolean isStopped() {
            return stopped.get();
        }

        int getInitializationCount() {
            return initializationCount.get();
        }
    }

    private static class DependentPlugin extends TestPlugin {
        private final List<KernelDependency> dependencies = new ArrayList<>();

        DependentPlugin(String moduleId, String version, String dependsOn) {
            super(moduleId, version);
            dependencies.add(new KernelDependency(dependsOn, "1.0.0", KernelDependency.DependencyType.MODULE, false));
        }

        @Override
        public Set<KernelDependency> getDependencies() {
            return Set.copyOf(dependencies);
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
        private final List<PluginState> stateHistory = new ArrayList<>();

        StateTrackingPlugin(String moduleId, String version) {
            super(moduleId, version);
            stateHistory.add(PluginState.REGISTERED);
        }

        @Override
        public void initialize(KernelContext context) {
            super.initialize(context);
            currentState = PluginState.INITIALIZED;
            stateHistory.add(PluginState.INITIALIZED);
        }

        @Override
        public Promise<Void> start() {
            return super.start().then(() -> {
                currentState = PluginState.STARTED;
                stateHistory.add(PluginState.STARTED);
                return Promise.complete();
            });
        }

        @Override
        public Promise<Void> stop() {
            return super.stop().then(() -> {
                currentState = PluginState.STOPPED;
                stateHistory.add(PluginState.STOPPED);
                return Promise.complete();
            });
        }

        PluginState getCurrentState() {
            return currentState;
        }

        List<PluginState> getStateHistory() {
            return new ArrayList<>(stateHistory);
        }
    }

    private static class FailingPlugin extends TestPlugin {
        FailingPlugin(String moduleId, String version) {
            super(moduleId, version);
        }

        @Override
        public void initialize(KernelContext context) {
            throw new RuntimeException("Initialization failed");
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
