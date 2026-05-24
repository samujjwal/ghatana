package com.ghatana.kernel.loader;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.plugin.KernelPlugin;
import com.ghatana.kernel.plugin.PluginManifest;
import com.ghatana.kernel.registry.CapabilityRegistry;
import com.ghatana.kernel.registry.PluginRegistry;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ephemeral plugin gating in Kernel.
 * Validates that ephemeral plugins are blocked in production environments.
 *
 * @doc.type class
 * @doc.purpose Validates ephemeral plugin gating in production
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Plugin Loader Ephemeral Plugin Gating Tests")
class PluginLoaderEphemeralPluginTest {

    private PluginRegistry pluginRegistry;

    @BeforeEach
    void setUp() {
        pluginRegistry = new PluginRegistry(new InMemoryCapabilityRegistry());
    }

    @Nested
    @DisplayName("Production Environment Gating")
    class ProductionEnvironmentGating {

        @Test
        @DisplayName("blocks ephemeral plugin in production environment")
        void blocksEphemeralPluginInProduction() {
            TestablePluginLoader pluginLoader = testableLoader(true);

            boolean loaded = pluginLoader.loadPluginDirectly(testPlugin("ephemeral-plugin", true));

            assertThat(loaded).isFalse();
            assertThat(isRegistered("ephemeral-plugin")).isFalse();
        }

        @Test
        @DisplayName("allows non-ephemeral plugin in production environment")
        void allowsNonEphemeralPluginInProduction() {
            TestablePluginLoader pluginLoader = testableLoader(true);

            boolean loaded = pluginLoader.loadPluginDirectly(testPlugin("persistent-plugin", false));

            assertThat(loaded).isTrue();
            assertThat(isRegistered("persistent-plugin")).isTrue();
        }
    }

    @Nested
    @DisplayName("Non-Production Environment")
    class NonProductionEnvironment {

        @Test
        @DisplayName("allows ephemeral plugin in development environment")
        void allowsEphemeralPluginInDevelopment() {
            TestablePluginLoader pluginLoader = testableLoader(false);

            boolean loaded = pluginLoader.loadPluginDirectly(testPlugin("ephemeral-plugin", true));

            assertThat(loaded).isTrue();
            assertThat(isRegistered("ephemeral-plugin")).isTrue();
        }

        @Test
        @DisplayName("allows non-ephemeral plugin in development environment")
        void allowsNonEphemeralPluginInDevelopment() {
            TestablePluginLoader pluginLoader = testableLoader(false);

            boolean loaded = pluginLoader.loadPluginDirectly(testPlugin("persistent-plugin", false));

            assertThat(loaded).isTrue();
            assertThat(isRegistered("persistent-plugin")).isTrue();
        }
    }

    @Nested
    @DisplayName("Plugin Manifest Ephemeral Field")
    class PluginManifestEphemeralField {

        @Test
        @DisplayName("ephemeral field defaults to false")
        void ephemeralDefaultsToFalse() {
            PluginManifest manifest = PluginManifest.builder()
                .pluginId("test-plugin")
                .version("1.0.0")
                .build();

            assertThat(manifest.isEphemeral()).isFalse();
        }

        @Test
        @DisplayName("ephemeral field can be set to true")
        void ephemeralCanBeSetToTrue() {
            PluginManifest manifest = PluginManifest.builder()
                .pluginId("test-plugin")
                .version("1.0.0")
                .ephemeral(true)
                .build();

            assertThat(manifest.isEphemeral()).isTrue();
        }

        @Test
        @DisplayName("ephemeral field can be set to false explicitly")
        void ephemeralCanBeSetToFalse() {
            PluginManifest manifest = PluginManifest.builder()
                .pluginId("test-plugin")
                .version("1.0.0")
                .ephemeral(false)
                .build();

            assertThat(manifest.isEphemeral()).isFalse();
        }
    }

    private TestablePluginLoader testableLoader(boolean productionEnvironment) {
        return new TestablePluginLoader(pluginRegistry, "/tmp/plugins", productionEnvironment);
    }

    private boolean isRegistered(String pluginId) {
        return pluginRegistry.getPlugin(pluginId).isPresent();
    }

    private static TestPlugin testPlugin(String pluginId, boolean ephemeral) {
        return new TestPlugin(
            PluginManifest.builder()
                .pluginId(pluginId)
                .version("1.0.0")
                .ephemeral(ephemeral)
                .build()
        );
    }

    private static final class TestablePluginLoader extends PluginLoader {
        private final boolean productionEnvironment;

        private TestablePluginLoader(
            PluginRegistry pluginRegistry,
            String pluginDirectory,
            boolean productionEnvironment
        ) {
            super(pluginRegistry, pluginDirectory);
            this.productionEnvironment = productionEnvironment;
        }

        private boolean loadPluginDirectly(KernelPlugin plugin) {
            return loadDiscoveredPlugin(plugin);
        }

        @Override
        protected boolean isProductionEnvironment() {
            return productionEnvironment;
        }
    }

    private static final class TestPlugin implements KernelPlugin {
        private final PluginManifest manifest;

        private TestPlugin(PluginManifest manifest) {
            this.manifest = manifest;
        }

        @Override
        public PluginManifest getManifest() {
            return manifest;
        }

        @Override
        public Set<String> getExportedContracts() {
            return Set.of();
        }

        @Override
        public Set<String> getRequiredContracts() {
            return Set.of();
        }

        @Override
        public Promise<Void> install() {
            return Promise.complete();
        }

        @Override
        public Promise<Void> uninstall() {
            return Promise.complete();
        }
    }

    private static final class InMemoryCapabilityRegistry implements CapabilityRegistry {
        private final Map<String, KernelCapability> capabilities = new HashMap<>();

        @Override
        public void registerCapability(KernelCapability capability, Object implementation) {
            capabilities.put(capability.getCapabilityId(), capability);
        }

        @Override
        public <T> Optional<T> getCapability(KernelCapability capability, Class<T> type) {
            return Optional.empty();
        }

        @Override
        public boolean hasCapability(KernelCapability capability) {
            return capabilities.containsKey(capability.getCapabilityId());
        }

        @Override
        public List<KernelCapability> getCapabilities() {
            return List.copyOf(capabilities.values());
        }
    }
}
