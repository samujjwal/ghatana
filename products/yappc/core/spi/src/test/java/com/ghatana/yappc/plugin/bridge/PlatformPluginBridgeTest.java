package com.ghatana.yappc.plugin.bridge;

import com.ghatana.platform.plugin.HealthStatus;
import com.ghatana.platform.plugin.Plugin;
import com.ghatana.platform.plugin.PluginRegistry;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.plugin.PluginCapabilities;
import com.ghatana.yappc.plugin.PluginContext;
import com.ghatana.yappc.plugin.PluginMetadata;
import com.ghatana.yappc.plugin.YAPPCPlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the YAPPC-to-platform plugin bridge infrastructure.
 *
 * <p>Validates:
 * <ul>
 *   <li>Metadata conversion from YAPPC to platform format</li>
 *   <li>Lifecycle delegation (initialize, start, stop, shutdown)</li>
 *   <li>Health check bridging</li>
 *   <li>Registration with the platform PluginRegistry</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Integration test for plugin bridge layer
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Platform Plugin Bridge Tests")
class PlatformPluginBridgeTest extends EventloopTestBase {

    @Nested
    @DisplayName("PlatformPluginBridge")
    class BridgeTests {

        @Test
        @DisplayName("should convert YAPPC metadata to platform format")
        void shouldConvertMetadata() {
            // GIVEN
            YAPPCPlugin plugin = new StubPlugin("test-validator", "Test Validator", "ai");

            // WHEN
            Plugin bridged = new PlatformPluginBridge(plugin);

            // THEN
            assertThat(bridged.metadata().id()).isEqualTo("yappc.test-validator");
            assertThat(bridged.metadata().name()).isEqualTo("Test Validator");
            assertThat(bridged.metadata().version()).isEqualTo("1.0.0");
            assertThat(bridged.metadata().type().name()).isEqualTo("AI");
        }

        @Test
        @DisplayName("should start in DISCOVERED state")
        void shouldStartInDiscoveredState() {
            // GIVEN / WHEN
            Plugin bridged = new PlatformPluginBridge(new StubPlugin("p1", "P1", "general"));

            // THEN
            assertThat(bridged.getState()).isEqualTo(PluginState.DISCOVERED);
        }

        @Test
        @DisplayName("should delegate start lifecycle to YAPPC plugin")
        void shouldDelegateStart() {
            // GIVEN
            StubPlugin stub = new StubPlugin("p1", "P1", "general");
            Plugin bridged = new PlatformPluginBridge(stub);

            // WHEN
            runPromise(() -> bridged.start());

            // THEN
            assertThat(bridged.getState()).isEqualTo(PluginState.RUNNING);
            assertThat(stub.started).isTrue();
        }

        @Test
        @DisplayName("should delegate stop lifecycle to YAPPC plugin")
        void shouldDelegateStop() {
            // GIVEN
            StubPlugin stub = new StubPlugin("p1", "P1", "general");
            Plugin bridged = new PlatformPluginBridge(stub);
            runPromise(() -> bridged.start());

            // WHEN
            runPromise(() -> bridged.stop());

            // THEN
            assertThat(bridged.getState()).isEqualTo(PluginState.STOPPED);
            assertThat(stub.stopped).isTrue();
        }

        @Test
        @DisplayName("should delegate health check to YAPPC plugin")
        void shouldDelegateHealthCheck() {
            // GIVEN
            StubPlugin stub = new StubPlugin("p1", "P1", "general");
            Plugin bridged = new PlatformPluginBridge(stub);

            // WHEN
            HealthStatus health = runPromise(bridged::healthCheck);

            // THEN
            assertThat(health.healthy()).isTrue();
        }

        @Test
        @DisplayName("should reject null delegate")
        void shouldRejectNullDelegate() {
            assertThatThrownBy(() -> new PlatformPluginBridge(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("UnifiedPluginBootstrap")
    class BootstrapTests {

        @Test
        @DisplayName("should register bridged plugins with platform registry")
        void shouldRegisterWithPlatformRegistry() {
            // GIVEN
            PluginRegistry registry = new PluginRegistry();
            PlatformPluginBridge bridge = new PlatformPluginBridge(
                    new StubPlugin("boot-test", "Boot Test", "general"));
            registry.register(bridge);

            // THEN
            assertThat(registry.isRegistered("yappc.boot-test")).isTrue();
            assertThat(registry.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("should not duplicate registration")
        void shouldNotDuplicateRegistration() {
            // GIVEN
            PluginRegistry registry = new PluginRegistry();
            PlatformPluginBridge bridge = new PlatformPluginBridge(
                    new StubPlugin("dup-test", "Dup Test", "general"));
            registry.register(bridge);

            // WHEN / THEN
            assertThatThrownBy(() -> registry.register(bridge))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered");
        }
    }

    // =========================================================================
    // Test doubles
    // =========================================================================

    /**
     * Minimal YAPPC plugin stub for testing.
     */
    private static class StubPlugin implements YAPPCPlugin {

        private final String id;
        private final String name;
        private final String category;
        boolean initialized;
        boolean started;
        boolean stopped;
        boolean shutdown;

        StubPlugin(String id, String name, String category) {
            this.id = id;
            this.name = name;
            this.category = category;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            initialized = true;
            return Promise.complete();
        }

        @Override
        public Promise<Void> start() {
            started = true;
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            stopped = true;
            return Promise.complete();
        }

        @Override
        public Promise<Void> shutdown() {
            shutdown = true;
            return Promise.complete();
        }

        @Override
        public PluginMetadata getMetadata() {
            return PluginMetadata.builder()
                    .id(id)
                    .name(name)
                    .category(category)
                    .build();
        }

        @Override
        public PluginCapabilities getCapabilities() {
            return PluginCapabilities.builder().build();
        }

        @Override
        public Promise<com.ghatana.yappc.plugin.HealthStatus> checkHealth() {
            return Promise.of(com.ghatana.yappc.plugin.HealthStatus.healthy());
        }
    }
}
