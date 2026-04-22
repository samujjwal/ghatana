package com.ghatana.yappc.plugin;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PluginRegistry Boundary Tests")
class PluginRegistryBoundaryTest extends EventloopTestBase {

    @Test
    @DisplayName("create rejects null context")
    void create_rejectsNullContext() {
        assertThatThrownBy(() -> PluginRegistry.create(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context");
    }

    @Test
    @DisplayName("registerPlugin rejects duplicate plugin IDs")
    void registerPlugin_rejectsDuplicates() {
        PluginRegistry registry = PluginRegistry.create(defaultContext());

        runPromise(() -> registry.registerPlugin(new TestGeneratorPlugin("dup-plugin")));

        assertThatThrownBy(() -> runPromise(() -> registry.registerPlugin(new TestGeneratorPlugin("dup-plugin"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("registerPlugin rejects null plugin")
    void registerPlugin_rejectsNullPlugin() {
        PluginRegistry registry = PluginRegistry.create(defaultContext());

        assertThatThrownBy(() -> runPromise(() -> registry.registerPlugin(null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("plugin");
    }

    @Test
    @DisplayName("shutdown calls plugin shutdown before clearing registry")
    void shutdown_invokesPluginShutdown() {
        PluginRegistry registry = PluginRegistry.create(defaultContext());
        AtomicBoolean shutdownCalled = new AtomicBoolean(false);

        runPromise(() -> registry.registerPlugin(new TestGeneratorPlugin("shutdown-plugin", shutdownCalled)));
        assertThat(registry.getPluginCount()).isEqualTo(1);

        runPromise(registry::shutdown);

        assertThat(shutdownCalled).isTrue();
        assertThat(registry.getPluginCount()).isZero();
    }

    private static PluginContext defaultContext() {
        return DefaultPluginContext.builder()
                .yappcVersion("1.0.0")
                .pluginDirectory("./test-plugins")
                .configuration(Map.of())
                .build();
    }

    private static final class TestGeneratorPlugin implements GeneratorPlugin {

        private final String id;
        private final AtomicBoolean shutdownCalled;

        private TestGeneratorPlugin(String id) {
            this(id, new AtomicBoolean(false));
        }

        private TestGeneratorPlugin(String id, AtomicBoolean shutdownCalled) {
            this.id = id;
            this.shutdownCalled = shutdownCalled;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> start() {
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            return Promise.complete();
        }

        @Override
        public Promise<Void> shutdown() {
            shutdownCalled.set(true);
            return Promise.complete();
        }

        @Override
        public PluginMetadata getMetadata() {
            return PluginMetadata.builder()
                    .id(id)
                    .name("Boundary Test Generator")
                    .version("1.0.0")
                    .build();
        }

        @Override
        public PluginCapabilities getCapabilities() {
            return PluginCapabilities.builder().build();
        }

        @Override
        public Promise<HealthStatus> checkHealth() {
            return Promise.of(HealthStatus.healthy());
        }

        @Override
        public Promise<GenerationResult> generate(GenerationContext context) {
            return Promise.of(GenerationResult.builder()
                    .generatorId(id)
                    .success(true)
                    .build());
        }

        @Override
        public Set<String> getSupportedLanguages() {
            return Set.of("java");
        }
    }
}


