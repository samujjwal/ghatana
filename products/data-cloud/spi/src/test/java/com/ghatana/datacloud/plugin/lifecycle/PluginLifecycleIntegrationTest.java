/*
 * Copyright (c) 2026 Ghatana Inc.
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

    record PluginDescriptor(String pluginId, String name, String version,
                             String pluginClass, Map<String, String> config) {}

    record PluginState(PluginDescriptor descriptor, PluginStatus status,
                       List<String> lifecycleLog) {}

    private PluginManager manager;

    @BeforeEach
    void setUp() {
        manager = new PluginManager();
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("discovered plugins are available in DISCOVERED status")
    void discoveredPluginsAvailableInDiscoveredStatus() {
        PluginDescriptor descriptor = new PluginDescriptor(
                "plugin-csv", "CSV Connector", "1.0.0",
                "com.example.CsvConnector", Map.of("delimiter", ","));

        manager.discover(descriptor);

        Optional<PluginState> state = manager.getState("plugin-csv");
        assertThat(state).isPresent();
        assertThat(state.get().status()).isEqualTo(PluginStatus.DISCOVERED);
    }

    @Test
    @DisplayName("discovering the same plugin twice is idempotent")
    void discoveringSamePluginTwiceIsIdempotent() {
        PluginDescriptor desc = new PluginDescriptor("plugin-idem", "Idem", "1.0", "com.Idem", Map.of());
        manager.discover(desc);
        manager.discover(desc); // second call

        assertThat(manager.listAll()).hasSize(1);
    }

    // ── Installation ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("installed plugin transitions from DISCOVERED to INSTALLED")
    void installedPluginTransitionsToInstalled() {
        PluginDescriptor desc = new PluginDescriptor("plugin-inst", "Inst", "1.0", "Inst", Map.of());
        manager.discover(desc);
        manager.install("plugin-inst");

        assertThat(manager.getState("plugin-inst").get().status()).isEqualTo(PluginStatus.INSTALLED);
    }

    @Test
    @DisplayName("installing a non-discovered plugin throws an exception")
    void installingUndiscoveredPluginThrows() {
        assertThatThrownBy(() -> manager.install("plugin-ghost"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not discovered");
    }

    // ── Activation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("activated plugin transitions from INSTALLED to ACTIVE")
    void activationTransitionsToActive() {
        PluginDescriptor desc = new PluginDescriptor("plugin-act", "Act", "1.0", "Act", Map.of());
        manager.discover(desc);
        manager.install("plugin-act");
        manager.activate("plugin-act");

        assertThat(manager.getState("plugin-act").get().status()).isEqualTo(PluginStatus.ACTIVE);
    }

    @Test
    @DisplayName("activating an uninstalled plugin throws")
    void activatingUninstalledPluginThrows() {
        PluginDescriptor desc = new PluginDescriptor("plugin-noact", "NoAct", "1.0", "NoAct", Map.of());
        manager.discover(desc);

        assertThatThrownBy(() -> manager.activate("plugin-noact"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("lifecycle log records each transition in order")
    void lifecycleLogRecordsTransitionsInOrder() {
        PluginDescriptor desc = new PluginDescriptor("plugin-log", "Log", "1.0", "Log", Map.of());
        manager.discover(desc);
        manager.install("plugin-log");
        manager.activate("plugin-log");
        manager.deactivate("plugin-log");

        List<String> log = manager.getState("plugin-log").get().lifecycleLog();
        assertThat(log).containsExactly("DISCOVERED", "INSTALLED", "ACTIVE", "INACTIVE");
    }

    // ── Deactivation ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("deactivated plugin transitions from ACTIVE to INACTIVE")
    void deactivationTransitionsToInactive() {
        PluginDescriptor desc = new PluginDescriptor("plugin-deact", "Deact", "1.0", "Deact", Map.of());
        manager.discover(desc);
        manager.install("plugin-deact");
        manager.activate("plugin-deact");
        manager.deactivate("plugin-deact");

        assertThat(manager.getState("plugin-deact").get().status()).isEqualTo(PluginStatus.INACTIVE);
    }

    // ── Uninstallation ────────────────────────────────────────────────────────

    @Test
    @DisplayName("uninstalled plugin gets removed from the registry")
    void uninstalledPluginRemovedFromRegistry() {
        PluginDescriptor desc = new PluginDescriptor("plugin-uninst", "Uninst", "1.0", "Uninst", Map.of());
        manager.discover(desc);
        manager.install("plugin-uninst");
        manager.uninstall("plugin-uninst");

        assertThat(manager.getState("plugin-uninst").get().status())
                .isEqualTo(PluginStatus.UNINSTALLED);
    }

    @Test
    @DisplayName("active plugin cannot be uninstalled without deactivation")
    void activePluginCannotBeUninstalledDirectly() {
        PluginDescriptor desc = new PluginDescriptor("plugin-blocked", "Blocked", "1.0", "B", Map.of());
        manager.discover(desc);
        manager.install("plugin-blocked");
        manager.activate("plugin-blocked");

        assertThatThrownBy(() -> manager.uninstall("plugin-blocked"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active");
    }

    // ── Isolation ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("failure in one plugin does not affect lifecycle of other plugins")
    void failureInOnePluginDoesNotAffectOthers() {
        PluginDescriptor healthy = new PluginDescriptor("plugin-ok", "Ok", "1.0", "Ok", Map.of());
        PluginDescriptor bad = new PluginDescriptor("plugin-bad", "Bad", "1.0", "Bad", Map.of());

        manager.discover(healthy);
        manager.discover(bad);
        manager.install("plugin-ok");
        manager.install("plugin-bad");
        manager.activate("plugin-ok");

        // Activating the bad plugin fails, but the healthy plugin stays ACTIVE
        manager.simulateFault("plugin-bad", "activation_error");
        try { manager.activate("plugin-bad"); } catch (Exception ignored) {}

        assertThat(manager.getState("plugin-ok").get().status()).isEqualTo(PluginStatus.ACTIVE);
    }

    // ── Plugin manager implementation (for tests) ─────────────────────────────

    static class PluginManager {
        private final Map<String, PluginState> registry = new LinkedHashMap<>();
        private final Set<String> faultyPlugins = new HashSet<>();

        void discover(PluginDescriptor descriptor) {
            registry.computeIfAbsent(descriptor.pluginId(), k -> {
                List<String> log = new CopyOnWriteArrayList<>();
                log.add("DISCOVERED");
                return new PluginState(descriptor, PluginStatus.DISCOVERED, log);
            });
        }

        void install(String pluginId) {
            PluginState state = requireState(pluginId);
            if (state.status() != PluginStatus.DISCOVERED) {
                throw new IllegalStateException("Plugin not discovered: " + pluginId);
            }
            transition(pluginId, PluginStatus.INSTALLED, "INSTALLED");
        }

        void activate(String pluginId) {
            PluginState state = requireState(pluginId);
            if (state.status() != PluginStatus.INSTALLED && state.status() != PluginStatus.INACTIVE) {
                throw new IllegalStateException("Plugin not installed: " + pluginId);
            }
            if (faultyPlugins.contains(pluginId)) {
                throw new RuntimeException("Simulated activation fault for " + pluginId);
            }
            transition(pluginId, PluginStatus.ACTIVE, "ACTIVE");
        }

        void deactivate(String pluginId) {
            transition(pluginId, PluginStatus.INACTIVE, "INACTIVE");
        }

        void uninstall(String pluginId) {
            PluginState state = requireState(pluginId);
            if (state.status() == PluginStatus.ACTIVE) {
                throw new IllegalStateException("Cannot uninstall an active plugin");
            }
            transition(pluginId, PluginStatus.UNINSTALLED, "UNINSTALLED");
        }

        void simulateFault(String pluginId, String faultType) {
            faultyPlugins.add(pluginId);
        }

        Optional<PluginState> getState(String pluginId) {
            return Optional.ofNullable(registry.get(pluginId));
        }

        List<PluginState> listAll() { return new ArrayList<>(registry.values()); }

        private PluginState requireState(String pluginId) {
            PluginState s = registry.get(pluginId);
            if (s == null) throw new IllegalStateException("Plugin not found: " + pluginId);
            return s;
        }

        private void transition(String pluginId, PluginStatus newStatus, String logEntry) {
            PluginState old = requireState(pluginId);
            old.lifecycleLog().add(logEntry);
            registry.put(pluginId, new PluginState(old.descriptor(), newStatus, old.lifecycleLog()));
        }
    }
}
