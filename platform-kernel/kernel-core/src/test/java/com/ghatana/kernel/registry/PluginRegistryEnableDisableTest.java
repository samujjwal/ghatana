package com.ghatana.kernel.registry;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.kernel.plugin.KernelPlugin;
import com.ghatana.kernel.plugin.PluginManifest;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for enable/disable plugin runtime controls in {@link PluginRegistry}.
 *
 * @doc.type class
 * @doc.purpose Validates runtime plugin enable/disable and startAllPlugins skip-disabled behaviour
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("PluginRegistry Enable/Disable Tests")
class PluginRegistryEnableDisableTest extends EventloopTestBase {

    private InMemoryCapabilityRegistry capabilityRegistry;
    private PluginRegistry pluginRegistry;

    @BeforeEach
    void setUp() {
        capabilityRegistry = new InMemoryCapabilityRegistry();
        pluginRegistry = new PluginRegistry(capabilityRegistry);
    }

    @Test
    @DisplayName("Newly registered plugin is enabled by default")
    void newPluginIsEnabledByDefault() { 
        StubPlugin plugin = new StubPlugin("plugin-a");
        pluginRegistry.registerPlugin(plugin); 

        assertThat(pluginRegistry.isPluginEnabled("plugin-a")).isTrue();
    }

    @Test
    @DisplayName("disablePlugin marks plugin as disabled")
    void disablePluginMarksAsDisabled() { 
        StubPlugin plugin = new StubPlugin("plugin-a");
        pluginRegistry.registerPlugin(plugin); 

        runPromise(() -> pluginRegistry.disablePlugin("plugin-a"));

        assertThat(pluginRegistry.isPluginEnabled("plugin-a")).isFalse();
        assertThat(pluginRegistry.getDisabledPluginIds()).contains("plugin-a");
    }

    @Test
    @DisplayName("enablePlugin re-enables a disabled plugin")
    void enablePluginReEnablesDisabled() { 
        StubPlugin plugin = new StubPlugin("plugin-a");
        pluginRegistry.registerPlugin(plugin); 
        runPromise(() -> pluginRegistry.disablePlugin("plugin-a"));

        pluginRegistry.enablePlugin("plugin-a");

        assertThat(pluginRegistry.isPluginEnabled("plugin-a")).isTrue();
        assertThat(pluginRegistry.getDisabledPluginIds()).doesNotContain("plugin-a");
    }

    @Test
    @DisplayName("startAllPlugins skips disabled plugins")
    void startAllSkipsDisabledPlugin() { 
        StubPlugin enabled = new StubPlugin("plugin-enabled");
        StubPlugin disabled = new StubPlugin("plugin-disabled");

        pluginRegistry.registerPlugin(enabled); 
        pluginRegistry.registerPlugin(disabled); 
        runPromise(() -> pluginRegistry.disablePlugin("plugin-disabled"));

        runPromise(pluginRegistry::startAllPlugins); 

        assertThat(enabled.started.get()).isTrue(); 
        assertThat(disabled.started.get()).isFalse(); 
    }

    @Test
    @DisplayName("disablePlugin calls stop() on a running plugin")
    void disablePluginStopsRunningPlugin() { 
        StubPlugin plugin = new StubPlugin("plugin-a");
        pluginRegistry.registerPlugin(plugin); 
        runPromise(plugin::start); 

        runPromise(() -> pluginRegistry.disablePlugin("plugin-a"));

        assertThat(plugin.stopped.get()).isTrue(); 
    }

    @Test
    @DisplayName("disablePlugin throws for unregistered plugin id")
    void disableUnregisteredPluginThrows() { 
        assertThatThrownBy(() -> pluginRegistry.disablePlugin("ghost"))
            .isInstanceOf(IllegalArgumentException.class) 
            .hasMessageContaining("not registered");
    }

    @Test
    @DisplayName("enablePlugin throws for unregistered plugin id")
    void enableUnregisteredPluginThrows() { 
        assertThatThrownBy(() -> pluginRegistry.enablePlugin("ghost"))
            .isInstanceOf(IllegalArgumentException.class) 
            .hasMessageContaining("not registered");
    }

    @Test
    @DisplayName("getDisabledPluginIds returns unmodifiable view")
    void disabledIdsViewIsUnmodifiable() { 
        StubPlugin plugin = new StubPlugin("plugin-a");
        pluginRegistry.registerPlugin(plugin); 
        runPromise(() -> pluginRegistry.disablePlugin("plugin-a"));

        Set<String> disabled = pluginRegistry.getDisabledPluginIds(); 
        assertThatThrownBy(() -> disabled.add("other"))
            .isInstanceOf(UnsupportedOperationException.class); 
    }

    // ==================== Test doubles ====================

    private static final class InMemoryCapabilityRegistry implements CapabilityRegistry {
        private final java.util.Map<String, KernelCapability> capabilities = new java.util.HashMap<>();

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

        public void unregisterCapability(String capabilityId) {
            capabilities.remove(capabilityId);
        }

        @Override
        public List<KernelCapability> getCapabilities() {
            return List.copyOf(capabilities.values());
        }
    }

    static class StubPlugin implements KernelPlugin {
        private final String id;
        final AtomicBoolean started = new AtomicBoolean(false); 
        final AtomicBoolean stopped = new AtomicBoolean(false); 

        StubPlugin(String id) { 
            this.id = id;
        }

        @Override public String getModuleId() { return id; } 
        @Override public String getVersion() { return "1.0.0"; } 
        @Override public Set<KernelCapability> getCapabilities() { return Set.of(); } 
        @Override public Set<KernelDependency> getDependencies() { return Set.of(); } 
        @Override public void initialize(KernelContext ctx) { } 

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
        public HealthStatus getHealthStatus() { return HealthStatus.healthy(); } 

        @Override public PluginManifest getManifest() { return null; } 
        @Override public Set<String> getExportedContracts() { return Set.of(); } 
        @Override public Set<String> getRequiredContracts() { return Set.of(); } 
        @Override public Promise<Void> install() { return Promise.complete(); } 
        @Override public Promise<Void> uninstall() { return Promise.complete(); } 
    }
}
