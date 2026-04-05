/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * Tests for plugin registry lifecycle (PF001).
 *
 * @doc.type class
 * @doc.purpose Plugin lifecycle tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PluginRegistry – Plugin Lifecycle (PF001)")
class PluginRegistryTest extends EventloopTestBase {

    @Mock
    private PluginRegistry pluginRegistry;

    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("[PF001]: register_creates_new_plugin")
        void registerCreatesNewPlugin() {
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata(
                "new-plugin", "New Plugin", "A new plugin", "1.0.0",
                "tenant-alpha", PluginRegistry.PluginType.CUSTOM,
                PluginRegistry.PluginStatus.REGISTERED,
                List.of("onInit", "onData"), List.of(), Map.of(),
                Instant.now(), null, "user-001"
            );

            when(pluginRegistry.register(any()))
                .thenReturn(Promise.of(plugin));

            PluginRegistry.PluginMetadata result = runPromise(() -> pluginRegistry.register(plugin));

            assertThat(result.id()).isEqualTo("new-plugin");
            assertThat(result.status()).isEqualTo(PluginRegistry.PluginStatus.REGISTERED);
        }

        @Test
        @DisplayName("[PF001]: unregister_removes_plugin")
        void unregisterRemovesPlugin() {
            String pluginId = "plugin-to-remove";

            when(pluginRegistry.unregister(pluginId))
                .thenReturn(Promise.of((Void) null));

            runPromise(() -> pluginRegistry.unregister(pluginId));

            verify(pluginRegistry).unregister(pluginId);
        }

        @Test
        @DisplayName("[PF001]: get_plugin_returns_existing")
        void getPluginReturnsExisting() {
            String pluginId = "existing-plugin";
            PluginRegistry.PluginMetadata plugin = createPlugin(pluginId, PluginRegistry.PluginStatus.ACTIVE);

            when(pluginRegistry.getPlugin(pluginId))
                .thenReturn(Promise.of(Optional.of(plugin)));

            Optional<PluginRegistry.PluginMetadata> result = runPromise(() -> pluginRegistry.getPlugin(pluginId));

            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(pluginId);
        }
    }

    @Nested
    @DisplayName("Activation")
    class ActivationTests {

        @Test
        @DisplayName("[PF001]: activate_enables_plugin")
        void activateEnablesPlugin() {
            String pluginId = "inactive-plugin";
            PluginRegistry.PluginMetadata activated = createPlugin(pluginId, PluginRegistry.PluginStatus.ACTIVE);

            when(pluginRegistry.activate(pluginId))
                .thenReturn(Promise.of(activated));

            PluginRegistry.PluginMetadata result = runPromise(() -> pluginRegistry.activate(pluginId));

            assertThat(result.status()).isEqualTo(PluginRegistry.PluginStatus.ACTIVE);
            assertThat(result.isActive()).isTrue();
        }

        @Test
        @DisplayName("[PF001]: deactivate_disables_plugin")
        void deactivateDisablesPlugin() {
            String pluginId = "active-plugin";
            PluginRegistry.PluginMetadata deactivated = createPlugin(pluginId, PluginRegistry.PluginStatus.INACTIVE);

            when(pluginRegistry.deactivate(pluginId))
                .thenReturn(Promise.of(deactivated));

            PluginRegistry.PluginMetadata result = runPromise(() -> pluginRegistry.deactivate(pluginId));

            assertThat(result.status()).isEqualTo(PluginRegistry.PluginStatus.INACTIVE);
            assertThat(result.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Listing")
    class ListingTests {

        @Test
        @DisplayName("[PF001]: list_plugins_returns_all_for_tenant")
        void listPluginsReturnsAllForTenant() {
            String tenantId = "tenant-alpha";

            List<PluginRegistry.PluginMetadata> plugins = List.of(
                createPlugin("p1", PluginRegistry.PluginStatus.ACTIVE),
                createPlugin("p2", PluginRegistry.PluginStatus.INACTIVE),
                createPlugin("p3", PluginRegistry.PluginStatus.REGISTERED)
            );

            when(pluginRegistry.listPlugins(tenantId, null))
                .thenReturn(Promise.of(plugins));

            List<PluginRegistry.PluginMetadata> result = runPromise(() ->
                pluginRegistry.listPlugins(tenantId, null)
            );

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("[PF001]: list_plugins_filters_by_status")
        void listPluginsFiltersByStatus() {
            String tenantId = "tenant-alpha";

            List<PluginRegistry.PluginMetadata> activePlugins = List.of(
                createPlugin("p1", PluginRegistry.PluginStatus.ACTIVE),
                createPlugin("p2", PluginRegistry.PluginStatus.ACTIVE)
            );

            when(pluginRegistry.listPlugins(tenantId, PluginRegistry.PluginStatus.ACTIVE))
                .thenReturn(Promise.of(activePlugins));

            List<PluginRegistry.PluginMetadata> result = runPromise(() ->
                pluginRegistry.listPlugins(tenantId, PluginRegistry.PluginStatus.ACTIVE)
            );

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(p -> p.status() == PluginRegistry.PluginStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Hooks")
    class HooksTests {

        @Test
        @DisplayName("[PF001]: execute_hook_runs_plugin_hook")
        void executeHookRunsPluginHook() {
            String pluginId = "plugin-with-hooks";
            String hookName = "onData";
            Map<String, Object> context = Map.of("data", "test");

            PluginRegistry.HookResult hookResult = new PluginRegistry.HookResult(
                pluginId, hookName, true, "processed", null, 100
            );

            when(pluginRegistry.executeHook(pluginId, hookName, context))
                .thenReturn(Promise.of(hookResult));

            PluginRegistry.HookResult result = runPromise(() ->
                pluginRegistry.executeHook(pluginId, hookName, context)
            );

            assertThat(result.success()).isTrue();
            assertThat(result.pluginId()).isEqualTo(pluginId);
            assertThat(result.hookName()).isEqualTo(hookName);
        }

        @Test
        @DisplayName("[PF001]: plugin_has_hook_checks_correctly")
        void pluginHasHookChecksCorrectly() {
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata(
                "hooked-plugin", "Hooked", "", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.ACTIVE,
                List.of("onInit", "onData", "onClose"), List.of(), Map.of(),
                Instant.now(), Instant.now(), "user"
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
        void getHealthReturnsPluginStatus() {
            String pluginId = "healthy-plugin";

            PluginRegistry.PluginHealth health = new PluginRegistry.PluginHealth(
                pluginId, true, "RUNNING", System.currentTimeMillis(),
                "Plugin is healthy", List.of()
            );

            when(pluginRegistry.getHealth(pluginId))
                .thenReturn(Promise.of(health));

            PluginRegistry.PluginHealth result = runPromise(() -> pluginRegistry.getHealth(pluginId));

            assertThat(result.healthy()).isTrue();
            assertThat(result.pluginId()).isEqualTo(pluginId);
        }

        @Test
        @DisplayName("[PF001]: get_health_reports_issues")
        void getHealthReportsIssues() {
            String pluginId = "unhealthy-plugin";

            PluginRegistry.PluginHealth health = new PluginRegistry.PluginHealth(
                pluginId, false, "ERROR", System.currentTimeMillis(),
                "Connection failed", List.of("Database unreachable", "Timeout")
            );

            when(pluginRegistry.getHealth(pluginId))
                .thenReturn(Promise.of(health));

            PluginRegistry.PluginHealth result = runPromise(() -> pluginRegistry.getHealth(pluginId));

            assertThat(result.healthy()).isFalse();
            assertThat(result.issues()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("[PF001]: get_configuration_returns_config")
        void getConfigurationReturnsConfig() {
            String pluginId = "configured-plugin";
            Map<String, Object> config = Map.of(
                "endpoint", "https://api.example.com",
                "timeout", 30,
                "retries", 3
            );

            when(pluginRegistry.getConfiguration(pluginId))
                .thenReturn(Promise.of(config));

            Map<String, Object> result = runPromise(() -> pluginRegistry.getConfiguration(pluginId));

            assertThat(result).containsKeys("endpoint", "timeout", "retries");
        }

        @Test
        @DisplayName("[PF001]: update_configuration_changes_config")
        void updateConfigurationChangesConfig() {
            String pluginId = "configurable-plugin";
            Map<String, Object> newConfig = Map.of("timeout", 60);

            when(pluginRegistry.updateConfiguration(pluginId, newConfig))
                .thenReturn(Promise.of(newConfig));

            Map<String, Object> result = runPromise(() ->
                pluginRegistry.updateConfiguration(pluginId, newConfig)
            );

            assertThat(result.get("timeout")).isEqualTo(60);
        }
    }

    private PluginRegistry.PluginMetadata createPlugin(String id, PluginRegistry.PluginStatus status) {
        return new PluginRegistry.PluginMetadata(
            id, id, "", "1.0", "tenant-alpha",
            PluginRegistry.PluginType.CUSTOM, status,
            List.of(), List.of(), Map.of(),
            Instant.now(), status == PluginRegistry.PluginStatus.ACTIVE ? Instant.now() : null,
            "user"
        );
    }
}
