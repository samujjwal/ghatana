package com.ghatana.platform.plugin;

import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PluginRegistry Hardening [GH-90000]")
class PluginRegistryHardeningTest extends EventloopTestBase {

    @AfterEach
    void clearCompatibilityProperty() {
        System.clearProperty("ghatana.platform.version");
    }

    @Test
    @DisplayName("KP-1: lifecycle FSM transitions stay legal through init/start/stop/shutdown")
    void shouldFollowLifecycleFsmTransitions() {
        PluginRegistry registry = new PluginRegistry();
        TrackingPlugin plugin = new TrackingPlugin("kp1-plugin");

        registry.register(plugin);

        runPromise(() -> registry.initializeAll(new NoOpPluginContext()));
        assertThat(plugin.getState()).isEqualTo(PluginState.INITIALIZED);

        runPromise(registry::startAll);
        assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING);

        runPromise(registry::stopAll);
        assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);

        runPromise(registry::shutdownAll);
        assertThat(plugin.getState()).isEqualTo(PluginState.SHUTDOWN);

        assertThat(plugin.events).containsExactly("initialize", "start", "stop", "shutdown");
    }

    @Test
    @DisplayName("KP-2: provider discovery continues after a plugin creation failure")
    void shouldIsolateProviderLoadFailuresDuringDiscovery() {
        PluginRegistry registry = new PluginRegistry();

        PluginProvider failingProvider = new TestPluginProvider("failing-provider", 10, true, true);
        PluginProvider healthyProvider = new TestPluginProvider("healthy-provider", 20, false, true);

        int discovered = registry.discoverPlugins(List.of(failingProvider, healthyProvider));

        assertThat(discovered).isEqualTo(1);
        assertThat(registry.isRegistered("healthy-provider")).isTrue();
        assertThat(registry.isRegistered("failing-provider")).isFalse();
    }

    @Test
    @DisplayName("KP-3: incompatible plugin versions are rejected at registration")
    void shouldRejectVersionMismatchDuringRegistration() {
        System.setProperty("ghatana.platform.version", "1.0.0");
        PluginRegistry registry = new PluginRegistry();

        TrackingPlugin incompatiblePlugin = new TrackingPlugin(
            "kp3-incompatible",
            PluginCompatibility.dataCloudVersion("2.0.0")
        );

        assertThatThrownBy(() -> registry.register(incompatiblePlugin))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("incompatible");
    }

    @Test
    @DisplayName("KP-5: SPI discovery is idempotent for duplicate providers and pre-registered plugins")
    void shouldKeepSpiRegistrationIdempotent() {
        PluginRegistry registry = new PluginRegistry();

        TrackingPlugin preRegistered = new TrackingPlugin("duplicate-provider");
        registry.register(preRegistered);

        PluginProvider duplicateProviderA = new TestPluginProvider("duplicate-provider", 10, false, true);
        PluginProvider duplicateProviderB = new TestPluginProvider("duplicate-provider", 20, false, true);
        PluginProvider disabledProvider = new TestPluginProvider("disabled-provider", 5, false, false);

        int discovered = registry.discoverPlugins(List.of(duplicateProviderA, duplicateProviderB, disabledProvider));

        assertThat(discovered).isEqualTo(0);
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.isRegistered("duplicate-provider")).isTrue();
        assertThat(registry.isRegistered("disabled-provider")).isFalse();
    }

    private static final class TrackingPlugin implements Plugin {
        private final PluginMetadata metadata;
        private final List<String> events = new ArrayList<>();
        private PluginState state = PluginState.UNLOADED;

        private TrackingPlugin(String id) {
            this(id, null);
        }

        private TrackingPlugin(String id, PluginCompatibility compatibility) {
            this.metadata = PluginMetadata.builder()
                .id(id)
                .name(id)
                .version("1.0.0")
                .type(PluginType.CUSTOM)
                .compatibility(compatibility)
                .build();
        }

        @Override
        public @NotNull PluginMetadata metadata() {
            return metadata;
        }

        @Override
        public @NotNull PluginState getState() {
            return state;
        }

        @Override
        public @NotNull Promise<Void> initialize(@NotNull PluginContext context) {
            events.add("initialize");
            state = PluginState.INITIALIZED;
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<Void> start() {
            events.add("start");
            state = PluginState.RUNNING;
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<Void> stop() {
            events.add("stop");
            state = PluginState.STOPPED;
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<Void> shutdown() {
            events.add("shutdown");
            state = PluginState.SHUTDOWN;
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<HealthStatus> healthCheck() {
            return Promise.of(HealthStatus.ok());
        }
    }

    private static final class TestPluginProvider implements PluginProvider {
        private final String id;
        private final int priority;
        private final boolean shouldThrow;
        private final boolean enabled;

        private TestPluginProvider(String id, int priority, boolean shouldThrow, boolean enabled) {
            this.id = id;
            this.priority = priority;
            this.shouldThrow = shouldThrow;
            this.enabled = enabled;
        }

        @Override
        public @NotNull Plugin createPlugin() {
            if (shouldThrow) {
                throw new IllegalStateException("simulated provider failure");
            }
            return new TrackingPlugin(id);
        }

        @Override
        public @NotNull PluginMetadata getMetadata() {
            return PluginMetadata.builder()
                .id(id)
                .name(id)
                .type(PluginType.CUSTOM)
                .build();
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }

    private static final class NoOpPluginContext implements PluginContext {
        @Override
        public <T> T getConfig(@NotNull Class<T> configType) {
            return null;
        }

        @Override
        public @NotNull <T extends Plugin> java.util.Optional<T> findPlugin(@NotNull String pluginId) {
            return java.util.Optional.empty();
        }

        @Override
        public @NotNull List<Plugin> findPluginsByCapability(@NotNull Class<? extends PluginCapability> capability) {
            return List.of();
        }

        @Override
        public @NotNull PluginInteractionBus getInteractionBus() {
            return new PluginInteractionBus() {
                @Override
                public <Req, Res> @NotNull Promise<Res> request(@NotNull String targetPluginId, @NotNull Req request, @NotNull Class<Res> responseType, @NotNull java.time.Duration timeout) {
                    return Promise.ofException(new UnsupportedOperationException("No bus in test context"));
                }

                @Override
                public void publish(@NotNull String topic, @NotNull Object event) {
                }

                @Override
                public void subscribe(@NotNull String topic, @NotNull java.util.function.Consumer<Object> listener) {
                }
            };
        }
    }
}

