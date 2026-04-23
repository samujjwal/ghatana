package com.ghatana.yappc.plugin.bridge;

import com.ghatana.platform.health.HealthStatus;
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


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the YAPPC-to-platform plugin bridge infrastructure.
 *
 * <p>Validates:
 * <ul>
 *   <li>Metadata conversion from YAPPC to platform format</li>
 *   <li>Lifecycle delegation (initialize, start, stop, shutdown)</li> // GH-90000
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
        void shouldConvertMetadata() { // GH-90000
            // GIVEN
            YAPPCPlugin plugin = new StubPlugin("test-validator", "Test Validator", "ai"); // GH-90000

            // WHEN
            Plugin bridged = new PlatformPluginBridge(plugin); // GH-90000

            // THEN
            assertThat(bridged.metadata().id()).isEqualTo("yappc.test-validator");
            assertThat(bridged.metadata().name()).isEqualTo("Test Validator");
            assertThat(bridged.metadata().version()).isEqualTo("1.0.0");
            assertThat(bridged.metadata().type().name()).isEqualTo("AI");
        }

        @Test
        @DisplayName("should start in DISCOVERED state")
        void shouldStartInDiscoveredState() { // GH-90000
            // GIVEN / WHEN
            Plugin bridged = new PlatformPluginBridge(new StubPlugin("p1", "P1", "general")); // GH-90000

            // THEN
            assertThat(bridged.getState()).isEqualTo(PluginState.DISCOVERED); // GH-90000
        }

        @Test
        @DisplayName("should delegate start lifecycle to YAPPC plugin")
        void shouldDelegateStart() { // GH-90000
            // GIVEN
            StubPlugin stub = new StubPlugin("p1", "P1", "general"); // GH-90000
            Plugin bridged = new PlatformPluginBridge(stub); // GH-90000

            // WHEN
            runPromise(() -> bridged.start()); // GH-90000

            // THEN
            assertThat(bridged.getState()).isEqualTo(PluginState.RUNNING); // GH-90000
            assertThat(stub.started).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should delegate stop lifecycle to YAPPC plugin")
        void shouldDelegateStop() { // GH-90000
            // GIVEN
            StubPlugin stub = new StubPlugin("p1", "P1", "general"); // GH-90000
            Plugin bridged = new PlatformPluginBridge(stub); // GH-90000
            runPromise(() -> bridged.start()); // GH-90000

            // WHEN
            runPromise(() -> bridged.stop()); // GH-90000

            // THEN
            assertThat(bridged.getState()).isEqualTo(PluginState.STOPPED); // GH-90000
            assertThat(stub.stopped).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should delegate health check to YAPPC plugin")
        void shouldDelegateHealthCheck() { // GH-90000
            // GIVEN
            StubPlugin stub = new StubPlugin("p1", "P1", "general"); // GH-90000
            Plugin bridged = new PlatformPluginBridge(stub); // GH-90000

            // WHEN
            HealthStatus health = runPromise(bridged::healthCheck); // GH-90000

            // THEN
            assertThat(health.isHealthy()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should reject null delegate")
        void shouldRejectNullDelegate() { // GH-90000
            assertThatThrownBy(() -> new PlatformPluginBridge(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("UnifiedPluginBootstrap")
    class BootstrapTests {

        @Test
        @DisplayName("should register bridged plugins with platform registry")
        void shouldRegisterWithPlatformRegistry() { // GH-90000
            // GIVEN
            PluginRegistry registry = new PluginRegistry(); // GH-90000
            PlatformPluginBridge bridge = new PlatformPluginBridge( // GH-90000
                    new StubPlugin("boot-test", "Boot Test", "general")); // GH-90000
            registry.register(bridge); // GH-90000

            // THEN
            assertThat(registry.isRegistered("yappc.boot-test")).isTrue();
            assertThat(registry.size()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("should not duplicate registration")
        void shouldNotDuplicateRegistration() { // GH-90000
            // GIVEN
            PluginRegistry registry = new PluginRegistry(); // GH-90000
            PlatformPluginBridge bridge = new PlatformPluginBridge( // GH-90000
                    new StubPlugin("dup-test", "Dup Test", "general")); // GH-90000
            registry.register(bridge); // GH-90000

            // WHEN / THEN
            assertThatThrownBy(() -> registry.register(bridge)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
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

        StubPlugin(String id, String name, String category) { // GH-90000
            this.id = id;
            this.name = name;
            this.category = category;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) { // GH-90000
            initialized = true;
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Void> start() { // GH-90000
            started = true;
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Void> stop() { // GH-90000
            stopped = true;
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Void> shutdown() { // GH-90000
            shutdown = true;
            return Promise.complete(); // GH-90000
        }

        @Override
        public PluginMetadata getMetadata() { // GH-90000
            return PluginMetadata.builder() // GH-90000
                    .id(id) // GH-90000
                    .name(name) // GH-90000
                    .category(category) // GH-90000
                    .build(); // GH-90000
        }

        @Override
        public PluginCapabilities getCapabilities() { // GH-90000
            return PluginCapabilities.builder().build(); // GH-90000
        }

        @Override
        public Promise<com.ghatana.yappc.plugin.HealthStatus> checkHealth() { // GH-90000
            return Promise.of(com.ghatana.yappc.plugin.HealthStatus.healthy()); // GH-90000
        }
    }
}
