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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for enable/disable plugin runtime controls in {@link PluginRegistry}.
 *
 * @doc.type class
 * @doc.purpose Validates runtime plugin enable/disable and startAllPlugins skip-disabled behaviour
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("PluginRegistry Enable/Disable Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class PluginRegistryEnableDisableTest extends EventloopTestBase {

    @Mock
    private CapabilityRegistry capabilityRegistry;

    private PluginRegistry pluginRegistry;

    @BeforeEach
    void setUp() { // GH-90000
        lenient().doNothing().when(capabilityRegistry).registerCapability(any(KernelCapability.class)); // GH-90000
        pluginRegistry = new PluginRegistry(capabilityRegistry); // GH-90000
    }

    @Test
    @DisplayName("Newly registered plugin is enabled by default [GH-90000]")
    void newPluginIsEnabledByDefault() { // GH-90000
        StubPlugin plugin = new StubPlugin("plugin-a [GH-90000]");
        pluginRegistry.registerPlugin(plugin); // GH-90000

        assertThat(pluginRegistry.isPluginEnabled("plugin-a [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("disablePlugin marks plugin as disabled [GH-90000]")
    void disablePluginMarksAsDisabled() { // GH-90000
        StubPlugin plugin = new StubPlugin("plugin-a [GH-90000]");
        pluginRegistry.registerPlugin(plugin); // GH-90000

        runPromise(() -> pluginRegistry.disablePlugin("plugin-a [GH-90000]"));

        assertThat(pluginRegistry.isPluginEnabled("plugin-a [GH-90000]")).isFalse();
        assertThat(pluginRegistry.getDisabledPluginIds()).contains("plugin-a [GH-90000]");
    }

    @Test
    @DisplayName("enablePlugin re-enables a disabled plugin [GH-90000]")
    void enablePluginReEnablesDisabled() { // GH-90000
        StubPlugin plugin = new StubPlugin("plugin-a [GH-90000]");
        pluginRegistry.registerPlugin(plugin); // GH-90000
        runPromise(() -> pluginRegistry.disablePlugin("plugin-a [GH-90000]"));

        pluginRegistry.enablePlugin("plugin-a [GH-90000]");

        assertThat(pluginRegistry.isPluginEnabled("plugin-a [GH-90000]")).isTrue();
        assertThat(pluginRegistry.getDisabledPluginIds()).doesNotContain("plugin-a [GH-90000]");
    }

    @Test
    @DisplayName("startAllPlugins skips disabled plugins [GH-90000]")
    void startAllSkipsDisabledPlugin() { // GH-90000
        StubPlugin enabled = new StubPlugin("plugin-enabled [GH-90000]");
        StubPlugin disabled = new StubPlugin("plugin-disabled [GH-90000]");

        pluginRegistry.registerPlugin(enabled); // GH-90000
        pluginRegistry.registerPlugin(disabled); // GH-90000
        runPromise(() -> pluginRegistry.disablePlugin("plugin-disabled [GH-90000]"));

        runPromise(pluginRegistry::startAllPlugins); // GH-90000

        assertThat(enabled.started.get()).isTrue(); // GH-90000
        assertThat(disabled.started.get()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("disablePlugin calls stop() on a running plugin [GH-90000]")
    void disablePluginStopsRunningPlugin() { // GH-90000
        StubPlugin plugin = new StubPlugin("plugin-a [GH-90000]");
        pluginRegistry.registerPlugin(plugin); // GH-90000
        runPromise(plugin::start); // GH-90000

        runPromise(() -> pluginRegistry.disablePlugin("plugin-a [GH-90000]"));

        assertThat(plugin.stopped.get()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("disablePlugin throws for unregistered plugin id [GH-90000]")
    void disableUnregisteredPluginThrows() { // GH-90000
        assertThatThrownBy(() -> pluginRegistry.disablePlugin("ghost [GH-90000]"))
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("not registered [GH-90000]");
    }

    @Test
    @DisplayName("enablePlugin throws for unregistered plugin id [GH-90000]")
    void enableUnregisteredPluginThrows() { // GH-90000
        assertThatThrownBy(() -> pluginRegistry.enablePlugin("ghost [GH-90000]"))
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("not registered [GH-90000]");
    }

    @Test
    @DisplayName("getDisabledPluginIds returns unmodifiable view [GH-90000]")
    void disabledIdsViewIsUnmodifiable() { // GH-90000
        StubPlugin plugin = new StubPlugin("plugin-a [GH-90000]");
        pluginRegistry.registerPlugin(plugin); // GH-90000
        runPromise(() -> pluginRegistry.disablePlugin("plugin-a [GH-90000]"));

        Set<String> disabled = pluginRegistry.getDisabledPluginIds(); // GH-90000
        assertThatThrownBy(() -> disabled.add("other [GH-90000]"))
            .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }

    // ==================== Test fixture ====================

    static class StubPlugin implements KernelPlugin {
        private final String id;
        final AtomicBoolean started = new AtomicBoolean(false); // GH-90000
        final AtomicBoolean stopped = new AtomicBoolean(false); // GH-90000

        StubPlugin(String id) { // GH-90000
            this.id = id;
        }

        @Override public String getModuleId() { return id; } // GH-90000
        @Override public String getVersion() { return "1.0.0"; } // GH-90000
        @Override public Set<KernelCapability> getCapabilities() { return Set.of(); } // GH-90000
        @Override public Set<KernelDependency> getDependencies() { return Set.of(); } // GH-90000
        @Override public void initialize(KernelContext ctx) { } // GH-90000

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
        public HealthStatus getHealthStatus() { return HealthStatus.healthy(); } // GH-90000

        @Override public PluginManifest getManifest() { return null; } // GH-90000
        @Override public Set<String> getExportedContracts() { return Set.of(); } // GH-90000
        @Override public Set<String> getRequiredContracts() { return Set.of(); } // GH-90000
        @Override public Promise<Void> install() { return Promise.complete(); } // GH-90000
        @Override public Promise<Void> uninstall() { return Promise.complete(); } // GH-90000
    }
}
