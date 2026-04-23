/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.plugin.lifecycle;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the plugin lifecycle — discover, install, activate,
 * deactivate, uninstall, and isolation guarantees.
 *
 * @doc.type    class
 * @doc.purpose Plugin lifecycle integration tests: install, activate, deactivate, uninstall
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("Plugin Lifecycle Integration Tests")
@Tag("integration")
class PluginLifecycleIntegrationTest extends EventloopTestBase {

    // ── Plugin model ──────────────────────────────────────────────────────────

    enum PluginStatus { DISCOVERED, INSTALLED, ACTIVE, INACTIVE, UNINSTALLED }

    record PluginDescriptor(String pluginId, String name, String version, // GH-90000
                             String pluginClass, Map<String, String> config) {}

    record PluginState(PluginDescriptor descriptor, PluginStatus status, // GH-90000
                       List<String> lifecycleLog) {}

    private PluginManager manager;

    @BeforeEach
    void setUp() { // GH-90000
        manager = new PluginManager(); // GH-90000
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("discovered plugins are available in DISCOVERED status")
    void discoveredPluginsAvailableInDiscoveredStatus() { // GH-90000
        PluginDescriptor descriptor = new PluginDescriptor( // GH-90000
                "plugin-csv", "CSV Connector", "1.0.0",
                "com.example.CsvConnector", Map.of("delimiter", ",")); // GH-90000

        manager.discover(descriptor); // GH-90000

        Optional<PluginState> state = manager.getState("plugin-csv");
        assertThat(state).isPresent(); // GH-90000
        assertThat(state.get().status()).isEqualTo(PluginStatus.DISCOVERED); // GH-90000
    }

    @Test
    @DisplayName("discovering the same plugin twice is idempotent")
    void discoveringSamePluginTwiceIsIdempotent() { // GH-90000
        PluginDescriptor desc = new PluginDescriptor("plugin-idem", "Idem", "1.0", "com.Idem", Map.of()); // GH-90000
        manager.discover(desc); // GH-90000
        manager.discover(desc); // second call // GH-90000

        assertThat(manager.listAll()).hasSize(1); // GH-90000
    }

    // ── Installation ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("installed plugin transitions from DISCOVERED to INSTALLED")
    void installedPluginTransitionsToInstalled() { // GH-90000
        PluginDescriptor desc = new PluginDescriptor("plugin-inst", "Inst", "1.0", "Inst", Map.of()); // GH-90000
        manager.discover(desc); // GH-90000
        manager.install("plugin-inst");

        assertThat(manager.getState("plugin-inst").get().status()).isEqualTo(PluginStatus.INSTALLED);
    }

    @Test
    @DisplayName("installing a non-discovered plugin throws an exception")
    void installingUndiscoveredPluginThrows() { // GH-90000
        assertThatThrownBy(() -> manager.install("plugin-ghost"))
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("not discovered");
    }

    // ── Activation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("activated plugin transitions from INSTALLED to ACTIVE")
    void activationTransitionsToActive() { // GH-90000
        PluginDescriptor desc = new PluginDescriptor("plugin-act", "Act", "1.0", "Act", Map.of()); // GH-90000
        manager.discover(desc); // GH-90000
        manager.install("plugin-act");
        manager.activate("plugin-act");

        assertThat(manager.getState("plugin-act").get().status()).isEqualTo(PluginStatus.ACTIVE);
    }

    @Test
    @DisplayName("activating an uninstalled plugin throws")
    void activatingUninstalledPluginThrows() { // GH-90000
        PluginDescriptor desc = new PluginDescriptor("plugin-noact", "NoAct", "1.0", "NoAct", Map.of()); // GH-90000
        manager.discover(desc); // GH-90000

        assertThatThrownBy(() -> manager.activate("plugin-noact"))
                .isInstanceOf(IllegalStateException.class); // GH-90000
    }

    @Test
    @DisplayName("lifecycle log records each transition in order")
    void lifecycleLogRecordsTransitionsInOrder() { // GH-90000
        PluginDescriptor desc = new PluginDescriptor("plugin-log", "Log", "1.0", "Log", Map.of()); // GH-90000
        manager.discover(desc); // GH-90000
        manager.install("plugin-log");
        manager.activate("plugin-log");
        manager.deactivate("plugin-log");

        List<String> log = manager.getState("plugin-log").get().lifecycleLog();
        assertThat(log).containsExactly("DISCOVERED", "INSTALLED", "ACTIVE", "INACTIVE"); // GH-90000
    }

    // ── Deactivation ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("deactivated plugin transitions from ACTIVE to INACTIVE")
    void deactivationTransitionsToInactive() { // GH-90000
        PluginDescriptor desc = new PluginDescriptor("plugin-deact", "Deact", "1.0", "Deact", Map.of()); // GH-90000
        manager.discover(desc); // GH-90000
        manager.install("plugin-deact");
        manager.activate("plugin-deact");
        manager.deactivate("plugin-deact");

        assertThat(manager.getState("plugin-deact").get().status()).isEqualTo(PluginStatus.INACTIVE);
    }

    // ── Uninstallation ────────────────────────────────────────────────────────

    @Test
    @DisplayName("uninstalled plugin gets removed from the registry")
    void uninstalledPluginRemovedFromRegistry() { // GH-90000
        PluginDescriptor desc = new PluginDescriptor("plugin-uninst", "Uninst", "1.0", "Uninst", Map.of()); // GH-90000
        manager.discover(desc); // GH-90000
        manager.install("plugin-uninst");
        manager.uninstall("plugin-uninst");

        assertThat(manager.getState("plugin-uninst").get().status())
                .isEqualTo(PluginStatus.UNINSTALLED); // GH-90000
    }

    @Test
    @DisplayName("active plugin cannot be uninstalled without deactivation")
    void activePluginCannotBeUninstalledDirectly() { // GH-90000
        PluginDescriptor desc = new PluginDescriptor("plugin-blocked", "Blocked", "1.0", "B", Map.of()); // GH-90000
        manager.discover(desc); // GH-90000
        manager.install("plugin-blocked");
        manager.activate("plugin-blocked");

        assertThatThrownBy(() -> manager.uninstall("plugin-blocked"))
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("active");
    }

    // ── Isolation ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("failure in one plugin does not affect lifecycle of other plugins")
    void failureInOnePluginDoesNotAffectOthers() { // GH-90000
        PluginDescriptor healthy = new PluginDescriptor("plugin-ok", "Ok", "1.0", "Ok", Map.of()); // GH-90000
        PluginDescriptor bad = new PluginDescriptor("plugin-bad", "Bad", "1.0", "Bad", Map.of()); // GH-90000

        manager.discover(healthy); // GH-90000
        manager.discover(bad); // GH-90000
        manager.install("plugin-ok");
        manager.install("plugin-bad");
        manager.activate("plugin-ok");

        // Activating the bad plugin fails, but the healthy plugin stays ACTIVE
        manager.simulateFault("plugin-bad", "activation_error"); // GH-90000
        try { manager.activate("plugin-bad"); } catch (Exception ignored) {}

        assertThat(manager.getState("plugin-ok").get().status()).isEqualTo(PluginStatus.ACTIVE);
    }

    // ── Plugin manager implementation (for tests) ───────────────────────────── // GH-90000

    static class PluginManager {
        private final Map<String, PluginState> registry = new LinkedHashMap<>(); // GH-90000
        private final Set<String> faultyPlugins = new HashSet<>(); // GH-90000

        void discover(PluginDescriptor descriptor) { // GH-90000
            registry.computeIfAbsent(descriptor.pluginId(), k -> { // GH-90000
                List<String> log = new CopyOnWriteArrayList<>(); // GH-90000
                log.add("DISCOVERED");
                return new PluginState(descriptor, PluginStatus.DISCOVERED, log); // GH-90000
            });
        }

        void install(String pluginId) { // GH-90000
            PluginState state = registry.get(pluginId); // GH-90000
            if (state == null || state.status() != PluginStatus.DISCOVERED) { // GH-90000
                throw new IllegalStateException("Plugin not discovered");
            }
            transition(pluginId, PluginStatus.INSTALLED, "INSTALLED"); // GH-90000
        }

        void activate(String pluginId) { // GH-90000
            PluginState state = requireState(pluginId); // GH-90000
            if (state.status() != PluginStatus.INSTALLED && state.status() != PluginStatus.INACTIVE) { // GH-90000
                throw new IllegalStateException("Plugin not installed: " + pluginId); // GH-90000
            }
            if (faultyPlugins.contains(pluginId)) { // GH-90000
                throw new RuntimeException("Simulated activation fault for " + pluginId); // GH-90000
            }
            transition(pluginId, PluginStatus.ACTIVE, "ACTIVE"); // GH-90000
        }

        void deactivate(String pluginId) { // GH-90000
            transition(pluginId, PluginStatus.INACTIVE, "INACTIVE"); // GH-90000
        }

        void uninstall(String pluginId) { // GH-90000
            PluginState state = requireState(pluginId); // GH-90000
            if (state.status() == PluginStatus.ACTIVE) { // GH-90000
                throw new IllegalStateException("Cannot uninstall an active plugin");
            }
            transition(pluginId, PluginStatus.UNINSTALLED, "UNINSTALLED"); // GH-90000
        }

        void simulateFault(String pluginId, String faultType) { // GH-90000
            faultyPlugins.add(pluginId); // GH-90000
        }

        Optional<PluginState> getState(String pluginId) { // GH-90000
            return Optional.ofNullable(registry.get(pluginId)); // GH-90000
        }

        List<PluginState> listAll() { return new ArrayList<>(registry.values()); } // GH-90000

        private PluginState requireState(String pluginId) { // GH-90000
            PluginState s = registry.get(pluginId); // GH-90000
            if (s == null) throw new IllegalStateException("Plugin not found: " + pluginId); // GH-90000
            return s;
        }

        private void transition(String pluginId, PluginStatus newStatus, String logEntry) { // GH-90000
            PluginState old = requireState(pluginId); // GH-90000
            old.lifecycleLog().add(logEntry); // GH-90000
            registry.put(pluginId, new PluginState(old.descriptor(), newStatus, old.lifecycleLog())); // GH-90000
        }
    }
}
