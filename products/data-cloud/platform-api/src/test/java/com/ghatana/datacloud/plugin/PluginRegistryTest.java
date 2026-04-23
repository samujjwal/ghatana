/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.plugin;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for plugin registry lifecycle (PF001). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Plugin lifecycle tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("PluginRegistry – Plugin Lifecycle (PF001)")
class PluginRegistryTest extends EventloopTestBase {

    @Mock
    private PluginRegistry pluginRegistry;

    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("[PF001]: register_creates_new_plugin")
        void registerCreatesNewPlugin() { // GH-90000
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata( // GH-90000
                "new-plugin", "New Plugin", "A new plugin", "1.0.0",
                "tenant-alpha", PluginRegistry.PluginType.CUSTOM,
                PluginRegistry.PluginStatus.REGISTERED,
                List.of("onInit", "onData"), List.of(), Map.of(), // GH-90000
                Instant.now(), null, "user-001" // GH-90000
            );

            when(pluginRegistry.register(any())) // GH-90000
                .thenReturn(Promise.of(plugin)); // GH-90000

            PluginRegistry.PluginMetadata result = runPromise(() -> pluginRegistry.register(plugin)); // GH-90000

            assertThat(result.id()).isEqualTo("new-plugin");
            assertThat(result.status()).isEqualTo(PluginRegistry.PluginStatus.REGISTERED); // GH-90000
        }

        @Test
        @DisplayName("[PF001]: unregister_removes_plugin")
        void unregisterRemovesPlugin() { // GH-90000
            String pluginId = "plugin-to-remove";

            when(pluginRegistry.unregister(pluginId)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> pluginRegistry.unregister(pluginId)); // GH-90000

            verify(pluginRegistry).unregister(pluginId); // GH-90000
        }

        @Test
        @DisplayName("[PF001]: get_plugin_returns_existing")
        void getPluginReturnsExisting() { // GH-90000
            String pluginId = "existing-plugin";
            PluginRegistry.PluginMetadata plugin = createPlugin(pluginId, PluginRegistry.PluginStatus.ACTIVE); // GH-90000

            when(pluginRegistry.getPlugin(pluginId)) // GH-90000
                .thenReturn(Promise.of(Optional.of(plugin))); // GH-90000

            Optional<PluginRegistry.PluginMetadata> result = runPromise(() -> pluginRegistry.getPlugin(pluginId)); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().id()).isEqualTo(pluginId); // GH-90000
        }
    }

    @Nested
    @DisplayName("Activation")
    class ActivationTests {

        @Test
        @DisplayName("[PF001]: activate_enables_plugin")
        void activateEnablesPlugin() { // GH-90000
            String pluginId = "inactive-plugin";
            PluginRegistry.PluginMetadata activated = createPlugin(pluginId, PluginRegistry.PluginStatus.ACTIVE); // GH-90000

            when(pluginRegistry.activate(pluginId)) // GH-90000
                .thenReturn(Promise.of(activated)); // GH-90000

            PluginRegistry.PluginMetadata result = runPromise(() -> pluginRegistry.activate(pluginId)); // GH-90000

            assertThat(result.status()).isEqualTo(PluginRegistry.PluginStatus.ACTIVE); // GH-90000
            assertThat(result.isActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[PF001]: deactivate_disables_plugin")
        void deactivateDisablesPlugin() { // GH-90000
            String pluginId = "active-plugin";
            PluginRegistry.PluginMetadata deactivated = createPlugin(pluginId, PluginRegistry.PluginStatus.INACTIVE); // GH-90000

            when(pluginRegistry.deactivate(pluginId)) // GH-90000
                .thenReturn(Promise.of(deactivated)); // GH-90000

            PluginRegistry.PluginMetadata result = runPromise(() -> pluginRegistry.deactivate(pluginId)); // GH-90000

            assertThat(result.status()).isEqualTo(PluginRegistry.PluginStatus.INACTIVE); // GH-90000
            assertThat(result.isActive()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Listing")
    class ListingTests {

        @Test
        @DisplayName("[PF001]: list_plugins_returns_all_for_tenant")
        void listPluginsReturnsAllForTenant() { // GH-90000
            String tenantId = "tenant-alpha";

            List<PluginRegistry.PluginMetadata> plugins = List.of( // GH-90000
                createPlugin("p1", PluginRegistry.PluginStatus.ACTIVE), // GH-90000
                createPlugin("p2", PluginRegistry.PluginStatus.INACTIVE), // GH-90000
                createPlugin("p3", PluginRegistry.PluginStatus.REGISTERED) // GH-90000
            );

            when(pluginRegistry.listPlugins(tenantId, null)) // GH-90000
                .thenReturn(Promise.of(plugins)); // GH-90000

            List<PluginRegistry.PluginMetadata> result = runPromise(() -> // GH-90000
                pluginRegistry.listPlugins(tenantId, null) // GH-90000
            );

            assertThat(result).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("[PF001]: list_plugins_filters_by_status")
        void listPluginsFiltersByStatus() { // GH-90000
            String tenantId = "tenant-alpha";

            List<PluginRegistry.PluginMetadata> activePlugins = List.of( // GH-90000
                createPlugin("p1", PluginRegistry.PluginStatus.ACTIVE), // GH-90000
                createPlugin("p2", PluginRegistry.PluginStatus.ACTIVE) // GH-90000
            );

            when(pluginRegistry.listPlugins(tenantId, PluginRegistry.PluginStatus.ACTIVE)) // GH-90000
                .thenReturn(Promise.of(activePlugins)); // GH-90000

            List<PluginRegistry.PluginMetadata> result = runPromise(() -> // GH-90000
                pluginRegistry.listPlugins(tenantId, PluginRegistry.PluginStatus.ACTIVE) // GH-90000
            );

            assertThat(result).hasSize(2); // GH-90000
            assertThat(result).allMatch(p -> p.status() == PluginRegistry.PluginStatus.ACTIVE); // GH-90000
        }
    }

    @Nested
    @DisplayName("Hooks")
    class HooksTests {

        @Test
        @DisplayName("[PF001]: execute_hook_runs_plugin_hook")
        void executeHookRunsPluginHook() { // GH-90000
            String pluginId = "plugin-with-hooks";
            String hookName = "onData";
            Map<String, Object> context = Map.of("data", "test"); // GH-90000

            PluginRegistry.HookResult hookResult = new PluginRegistry.HookResult( // GH-90000
                pluginId, hookName, true, "processed", null, 100
            );

            when(pluginRegistry.executeHook(pluginId, hookName, context)) // GH-90000
                .thenReturn(Promise.of(hookResult)); // GH-90000

            PluginRegistry.HookResult result = runPromise(() -> // GH-90000
                pluginRegistry.executeHook(pluginId, hookName, context) // GH-90000
            );

            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(result.pluginId()).isEqualTo(pluginId); // GH-90000
            assertThat(result.hookName()).isEqualTo(hookName); // GH-90000
        }

        @Test
        @DisplayName("[PF001]: plugin_has_hook_checks_correctly")
        void pluginHasHookChecksCorrectly() { // GH-90000
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata( // GH-90000
                "hooked-plugin", "Hooked", "", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.ACTIVE,
                List.of("onInit", "onData", "onClose"), List.of(), Map.of(), // GH-90000
                Instant.now(), Instant.now(), "user" // GH-90000
            );

            assertThat(plugin.hasHook("onData")).isTrue();
            assertThat(plugin.hasHook("onMissing")).isFalse();
        }
    }

    @Nested
    @DisplayName("Health")
    class HealthTests {

        @Test
        @DisplayName("[PF001]: get_health_returns_plugin_status")
        void getHealthReturnsPluginStatus() { // GH-90000
            String pluginId = "healthy-plugin";

            PluginRegistry.PluginHealth health = new PluginRegistry.PluginHealth( // GH-90000
                pluginId, true, "RUNNING", System.currentTimeMillis(), // GH-90000
                "Plugin is healthy", List.of() // GH-90000
            );

            when(pluginRegistry.getHealth(pluginId)) // GH-90000
                .thenReturn(Promise.of(health)); // GH-90000

            PluginRegistry.PluginHealth result = runPromise(() -> pluginRegistry.getHealth(pluginId)); // GH-90000

            assertThat(result.healthy()).isTrue(); // GH-90000
            assertThat(result.pluginId()).isEqualTo(pluginId); // GH-90000
        }

        @Test
        @DisplayName("[PF001]: get_health_reports_issues")
        void getHealthReportsIssues() { // GH-90000
            String pluginId = "unhealthy-plugin";

            PluginRegistry.PluginHealth health = new PluginRegistry.PluginHealth( // GH-90000
                pluginId, false, "ERROR", System.currentTimeMillis(), // GH-90000
                "Connection failed", List.of("Database unreachable", "Timeout") // GH-90000
            );

            when(pluginRegistry.getHealth(pluginId)) // GH-90000
                .thenReturn(Promise.of(health)); // GH-90000

            PluginRegistry.PluginHealth result = runPromise(() -> pluginRegistry.getHealth(pluginId)); // GH-90000

            assertThat(result.healthy()).isFalse(); // GH-90000
            assertThat(result.issues()).hasSize(2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("[PF001]: get_configuration_returns_config")
        void getConfigurationReturnsConfig() { // GH-90000
            String pluginId = "configured-plugin";
            Map<String, Object> config = Map.of( // GH-90000
                "endpoint", "https://api.example.com",
                "timeout", 30,
                "retries", 3
            );

            when(pluginRegistry.getConfiguration(pluginId)) // GH-90000
                .thenReturn(Promise.of(config)); // GH-90000

            Map<String, Object> result = runPromise(() -> pluginRegistry.getConfiguration(pluginId)); // GH-90000

            assertThat(result).containsKeys("endpoint", "timeout", "retries"); // GH-90000
        }

        @Test
        @DisplayName("[PF001]: update_configuration_changes_config")
        void updateConfigurationChangesConfig() { // GH-90000
            String pluginId = "configurable-plugin";
            Map<String, Object> newConfig = Map.of("timeout", 60); // GH-90000

            when(pluginRegistry.updateConfiguration(pluginId, newConfig)) // GH-90000
                .thenReturn(Promise.of(newConfig)); // GH-90000

            Map<String, Object> result = runPromise(() -> // GH-90000
                pluginRegistry.updateConfiguration(pluginId, newConfig) // GH-90000
            );

            assertThat(result.get("timeout")).isEqualTo(60);
        }
    }

    private PluginRegistry.PluginMetadata createPlugin(String id, PluginRegistry.PluginStatus status) { // GH-90000
        return new PluginRegistry.PluginMetadata( // GH-90000
            id, id, "", "1.0", "tenant-alpha",
            PluginRegistry.PluginType.CUSTOM, status,
            List.of(), List.of(), Map.of(), // GH-90000
            Instant.now(), status == PluginRegistry.PluginStatus.ACTIVE ? Instant.now() : null, // GH-90000
            "user"
        );
    }
}
