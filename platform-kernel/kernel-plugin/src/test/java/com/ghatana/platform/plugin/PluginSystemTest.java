/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.platform.plugin;

import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive plugin system tests covering plugin discovery, loading,
 * lifecycle management, and communication.
 *
 * @doc.type class
 * @doc.purpose Validates plugin discovery, loading, lifecycle, and communication
 * @doc.layer platform
 * @doc.pattern PluginTest
 */
@DisplayName("Plugin System Tests")
class PluginSystemTest extends EventloopTestBase {

    // =========================================================================
    // Plugin Discovery
    // =========================================================================

    @Nested
    @DisplayName("Plugin Discovery")
    class PluginDiscoveryTests {

        @Test
        @DisplayName("should discover plugin by metadata")
        void shouldDiscoverPluginByMetadata() {
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");

            PluginMetadata metadata = plugin.metadata();

            assertThat(metadata).isNotNull();
            assertThat(metadata.getPluginId()).isEqualTo("test-plugin");
            assertThat(metadata.getVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("should validate plugin metadata structure")
        void shouldValidatePluginMetadataStructure() {
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");

            PluginMetadata metadata = plugin.metadata();

            assertThat(metadata.getPluginId()).isNotBlank();
            assertThat(metadata.getVersion()).isNotBlank();
            assertThat(metadata.getName()).isNotNull();
        }

        @Test
        @DisplayName("should discover plugin capabilities")
        void shouldDiscoverPluginCapabilities() {
            TestPluginCapability capability = new TestPluginCapability("test-capability");
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
            plugin.setCapabilities(Set.of(capability));

            Set<PluginCapability> capabilities = plugin.getCapabilities();

            assertThat(capabilities).containsExactly(capability);
        }

        @Test
        @DisplayName("should retrieve specific capability by type")
        void shouldRetrieveSpecificCapabilityByType() {
            TestPluginCapability capability = new TestPluginCapability("test-capability");
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
            plugin.setCapabilities(Set.of(capability));

            Optional<TestPluginCapability> retrieved = plugin.getCapability(TestPluginCapability.class);

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get()).isEqualTo(capability);
        }
    }

    // =========================================================================
    // Plugin Loading
    // =========================================================================

    @Nested
    @DisplayName("Plugin Loading")
    class PluginLoadingTests {

        @Test
        @DisplayName("should initialize plugin with context")
        void shouldInitializePluginWithContext() {
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
            PluginContext context = PluginContext.builder()
                    .tenantId("tenant-123")
                    .build();

            runPromise(() -> plugin.initialize(context));

            assertThat(plugin.getContext()).isNotNull();
            assertThat(plugin.getContext().getTenantId()).isEqualTo("tenant-123");
            assertThat(plugin.isInitialized()).isTrue();
        }

        @Test
        @DisplayName("should start plugin after initialization")
        void shouldStartPluginAfterInitialization() {
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));

            runPromise(plugin::start);

            assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING);
            assertThat(plugin.isRunning()).isTrue();
        }

        @Test
        @DisplayName("should stop plugin gracefully")
        void shouldStopPluginGracefully() {
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));
            runPromise(plugin::start);

            runPromise(plugin::stop);

            assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);
            assertThat(plugin.isRunning()).isFalse();
        }

        @Test
        @DisplayName("should shutdown plugin completely")
        void shouldShutdownPluginCompletely() {
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));
            runPromise(plugin::start);

            runPromise(plugin::shutdown);

            assertThat(plugin.getState()).isEqualTo(PluginState.SHUTDOWN);
            assertThat(plugin.isRunning()).isFalse();
        }

        @Test
        @DisplayName("should prevent start without initialization")
        void shouldPreventStartWithoutInitialization() {
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");

            assertThatThrownBy(() -> runPromise(plugin::start))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // Plugin Lifecycle
    // =========================================================================

    @Nested
    @DisplayName("Plugin Lifecycle")
    class PluginLifecycleTests {

        @Test
        @DisplayName("should transition through lifecycle states correctly")
        void shouldTransitionThroughLifecycleStatesCorrectly() {
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
            PluginContext context = PluginContext.builder().build();

            // Initial state
            assertThat(plugin.getState()).isEqualTo(PluginState.LOADED);

            // Initialize
            runPromise(() -> plugin.initialize(context));
            assertThat(plugin.getState()).isEqualTo(PluginState.INITIALIZED);

            // Start
            runPromise(plugin::start);
            assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING);

            // Stop
            runPromise(plugin::stop);
            assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);

            // Shutdown
            runPromise(plugin::shutdown);
            assertThat(plugin.getState()).isEqualTo(PluginState.SHUTDOWN);
        }

        @Test
        @DisplayName("should handle plugin restart")
        void shouldHandlePluginRestart() {
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));

            // First start
            runPromise(plugin::start);
            assertThat(plugin.getStartCount()).isEqualTo(1);

            // Stop
            runPromise(plugin::stop);

            // Restart
            runPromise(plugin::start);
            assertThat(plugin.getStartCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should handle plugin reload")
        void shouldHandlePluginReload() {
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));
            runPromise(plugin::start);

            // Reload (simulate)
            runPromise(plugin::shutdown);
            plugin.reset();
            runPromise(() -> plugin.initialize(context));
            runPromise(plugin::start);

            assertThat(plugin.isRunning()).isTrue();
            assertThat(plugin.getStartCount()).isEqualTo(1);
        }
    }

    // =========================================================================
    // Plugin Communication
    // =========================================================================

    @Nested
    @DisplayName("Plugin Communication")
    class PluginCommunicationTests {

        @Test
        @DisplayName("should communicate via plugin context")
        void shouldCommunicateViaPluginContext() {
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
            PluginContext context = PluginContext.builder()
                    .tenantId("tenant-123")
                    .metadata(Map.of("key", "value"))
                    .build();

            runPromise(() -> plugin.initialize(context));

            assertThat(plugin.getContext().getTenantId()).isEqualTo("tenant-123");
            assertThat(plugin.getContext().getMetadata()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("should share capabilities between plugins")
        void shouldShareCapabilitiesBetweenPlugins() {
            TestPluginCapability capability = new TestPluginCapability("shared-capability");
            TestPlugin plugin1 = new TestPlugin("plugin-1", "1.0.0");
            plugin1.setCapabilities(Set.of(capability));

            TestPlugin plugin2 = new TestPlugin("plugin-2", "1.0.0");
            plugin2.setCapabilities(Set.of(capability));

            assertThat(plugin1.getCapabilities()).contains(capability);
            assertThat(plugin2.getCapabilities()).contains(capability);
        }

        @Test
        @DisplayName("should handle capability registration dynamically")
        void shouldHandleCapabilityRegistrationDynamically() {
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");

            assertThat(plugin.getCapabilities()).isEmpty();

            TestPluginCapability capability = new TestPluginCapability("dynamic-capability");
            plugin.setCapabilities(Set.of(capability));

            assertThat(plugin.getCapabilities()).containsExactly(capability);
        }
    }

    // =========================================================================
    // Plugin Health
    // =========================================================================

    @Nested
    @DisplayName("Plugin Health")
    class PluginHealthTests {

        @Test
        @DisplayName("should report healthy status when running")
        void shouldReportHealthyStatusWhenRunning() {
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));
            runPromise(plugin::start);

            HealthStatus status = runPromise(plugin::healthCheck);

            assertThat(status).isEqualTo(HealthStatus.ok());
        }

        @Test
        @DisplayName("should report unhealthy status when stopped")
        void shouldReportUnhealthyStatusWhenStopped() {
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
            PluginContext context = PluginContext.builder().build();
            runPromise(() -> plugin.initialize(context));

            HealthStatus status = runPromise(plugin::healthCheck);

            assertThat(status).isNotEqualTo(HealthStatus.ok());
        }

        @Test
        @DisplayName("should report custom health status")
        void shouldReportCustomHealthStatus() {
            TestPlugin plugin = new TestPlugin("test-plugin", "1.0.0");
            plugin.setCustomHealthStatus(HealthStatus.degraded());

            HealthStatus status = runPromise(plugin::healthCheck);

            assertThat(status).isEqualTo(HealthStatus.degraded());
        }
    }

    // =========================================================================
    // Test Helper Classes
    // =========================================================================

    static class TestPlugin implements Plugin {
        private final String pluginId;
        private final String version;
        private final PluginMetadata metadata;
        private PluginContext context;
        private PluginState state = PluginState.LOADED;
        private Set<PluginCapability> capabilities = Set.of();
        private boolean initialized = false;
        private boolean running = false;
        private final AtomicInteger startCount = new AtomicInteger(0);
        private HealthStatus customHealthStatus = null;

        TestPlugin(String pluginId, String version) {
            this.pluginId = pluginId;
            this.version = version;
            this.metadata = new PluginMetadata(pluginId, version, "Test Plugin", "Test Description");
        }

        @Override
        public PluginMetadata metadata() {
            return metadata;
        }

        @Override
        public PluginState getState() {
            return state;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            this.context = context;
            this.initialized = true;
            this.state = PluginState.INITIALIZED;
            return Promise.complete();
        }

        @Override
        public Promise<Void> start() {
            if (!initialized) {
                return Promise.ofException(new IllegalStateException("Plugin not initialized"));
            }
            running = true;
            state = PluginState.RUNNING;
            startCount.incrementAndGet();
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            running = false;
            state = PluginState.STOPPED;
            return Promise.complete();
        }

        @Override
        public Promise<Void> shutdown() {
            running = false;
            state = PluginState.SHUTDOWN;
            return Promise.complete();
        }

        @Override
        public Promise<HealthStatus> healthCheck() {
            if (customHealthStatus != null) {
                return Promise.of(customHealthStatus);
            }
            return running ? Promise.of(HealthStatus.ok()) : Promise.of(HealthStatus.unhealthy());
        }

        @Override
        public Set<PluginCapability> getCapabilities() {
            return capabilities;
        }

        void setCapabilities(Set<PluginCapability> capabilities) {
            this.capabilities = capabilities;
        }

        void setCustomHealthStatus(HealthStatus status) {
            this.customHealthStatus = status;
        }

        void reset() {
            initialized = false;
            running = false;
            state = PluginState.LOADED;
            startCount.set(0);
        }

        PluginContext getContext() {
            return context;
        }

        boolean isInitialized() {
            return initialized;
        }

        boolean isRunning() {
            return running;
        }

        int getStartCount() {
            return startCount.get();
        }
    }

    static class TestPluginCapability implements PluginCapability {
        private final String capabilityId;

        TestPluginCapability(String capabilityId) {
            this.capabilityId = capabilityId;
        }

        @Override
        public String getCapabilityId() {
            return capabilityId;
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public String getName() {
            return "Test Capability";
        }
    }
}
