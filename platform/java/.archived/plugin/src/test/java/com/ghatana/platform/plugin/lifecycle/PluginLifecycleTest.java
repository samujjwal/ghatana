package com.ghatana.platform.plugin.lifecycle;

import com.ghatana.platform.plugin.PluginRegistry;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.test.InMemoryStoragePlugin;
import com.ghatana.platform.plugin.test.PluginTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for plugin lifecycle — validates UNLOADED → INITIALIZED → RUNNING → STOPPED
 * state transitions, duplicate registration, and registry discovery.
 *
 * @doc.type class
 * @doc.purpose Tests for platform plugin lifecycle state transitions and registry management
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Plugin Lifecycle Tests")
@Tag("integration")
class PluginLifecycleTest extends PluginTestBase {

    // ── State transitions ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("plugin state transitions")
    class StateTransitions {

        @Test
        @DisplayName("plugin starts in UNLOADED state before registration")
        void plugin_startsInUnloadedState_beforeRegistration() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();

            assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED);
        }

        @Test
        @DisplayName("plugin transitions to INITIALIZED after initializeAll")
        void plugin_transitionsToInitialized_afterInitializeAll() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            runPromise(() -> registry.initializeAll(context));

            assertThat(plugin.getState()).isEqualTo(PluginState.INITIALIZED);
        }

        @Test
        @DisplayName("plugin transitions to RUNNING after startAll")
        void plugin_transitionsToRunning_afterStartAll() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            runPromise(() -> registry.initializeAll(context).then(() -> registry.startAll()));

            assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING);
        }

        @Test
        @DisplayName("plugin transitions to STOPPED after stopAll")
        void plugin_transitionsToStopped_afterStopAll() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            runPromise(() -> registry.initializeAll(context)
                    .then(() -> registry.startAll())
                    .then(() -> registry.stopAll()));

            assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);
        }
    }

    // ── Duplicate registration ────────────────────────────────────────────────

    @Nested
    @DisplayName("duplicate registration")
    class DuplicateRegistration {

        @Test
        @DisplayName("registering a plugin with a duplicate ID throws IllegalArgumentException")
        void registeringPluginWithDuplicateId_throwsIllegalArgumentException() {
            InMemoryStoragePlugin plugin1 = new InMemoryStoragePlugin();
            InMemoryStoragePlugin plugin2 = new InMemoryStoragePlugin(); // same ID

            registry.register(plugin1);

            assertThatThrownBy(() -> registry.register(plugin2))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── Registry lookup ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("registry lookup")
    class RegistryLookup {

        @Test
        @DisplayName("registered plugin is retrievable by ID")
        void registeredPlugin_isRetrievableById() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            java.util.Optional<?> found = registry.getById(plugin.metadata().id());

            assertThat(found).isPresent();
        }

        @Test
        @DisplayName("unknown plugin ID returns empty Optional")
        void unknownPluginId_returnsEmptyOptional() {
            java.util.Optional<?> found = registry.getById("com.nonexistent.plugin");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("registry returns all registered plugins")
        void registry_returnsAllRegisteredPlugins() {
            InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
            registry.register(plugin);

            assertThat(registry.getAll()).hasSize(1);
        }
    }
}
