/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugin;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * PluginRegistryImpl Tests - 100% Coverage
 *
 * @doc.type class
 * @doc.purpose Comprehensive tests for PluginRegistryImpl
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("PluginRegistryImpl Tests")
class PluginRegistryImplTest extends EventloopTestBase {

    @Mock
    private MetricsCollector metrics;

    private PluginRegistryImpl registry;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registry = new PluginRegistryImpl(metrics);
    }

    @Nested
    @DisplayName("Register Plugin")
    class RegisterTests {

        @Test
        @DisplayName("[TEST-011]: register_successfully_registers_plugin")
        void registerSuccessfully() {
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata(
                "plugin-1", "Test Plugin", "Description", "1.0.0",
                "tenant-alpha", PluginRegistry.PluginType.CUSTOM,
                PluginRegistry.PluginStatus.REGISTERED,
                List.of("hook1"), List.of(),
                Map.of(), Instant.now(), null, "user-1"
            );

            // When
            PluginRegistry.PluginMetadata result = runPromise(() -> registry.register(plugin));

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo("plugin-1");
            assertThat(result.status()).isEqualTo(PluginRegistry.PluginStatus.REGISTERED);
            assertThat(result.isActive()).isFalse();
            verify(metrics).incrementCounter("plugin.register.success", "tenant", "tenant-alpha", "type", "CUSTOM");
        }

        @Test
        @DisplayName("[TEST-011]: register_throws_for_duplicate_id")
        void registerDuplicate() {
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata(
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.REGISTERED,
                List.of(), List.of(), Map.of(), Instant.now(), null, "user-1"
            );

            runPromise(() -> registry.register(plugin));

            // When/Then
            clearFatalError();
            try {
                runPromise(() -> registry.register(plugin));
            } catch (Exception e) {
                // Expected
            }
            verify(metrics).incrementCounter("plugin.register.conflict", "plugin", "plugin-1");
        }
    }

    @Nested
    @DisplayName("Activate/Deactivate")
    class ActivationTests {

        @Test
        @DisplayName("[TEST-012]: activate_successfully_activates_plugin")
        void activateSuccessfully() {
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata(
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.REGISTERED,
                List.of(), List.of(), Map.of(), Instant.now(), null, "user-1"
            );
            runPromise(() -> registry.register(plugin));

            // When
            PluginRegistry.PluginMetadata result = runPromise(() -> registry.activate("plugin-1"));

            // Then
            assertThat(result.status()).isEqualTo(PluginRegistry.PluginStatus.ACTIVE);
            assertThat(result.isActive()).isTrue();
            assertThat(result.activatedAt()).isNotNull();
            verify(metrics).incrementCounter("plugin.activate.success", "plugin", "plugin-1");
        }

        @Test
        @DisplayName("[TEST-012]: activate_throws_for_missing_plugin")
        void activateMissing() {
            // When
            clearFatalError();
            try {
                runPromise(() -> registry.activate("missing-plugin"));
            } catch (Exception e) {
                // Expected
            }
            verify(metrics).incrementCounter("plugin.activate.error", "plugin", "missing-plugin", "reason", "not_found");
        }

        @Test
        @DisplayName("[TEST-013]: deactivate_successfully_deactivates_plugin")
        void deactivateSuccessfully() {
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata(
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.ACTIVE,
                List.of(), List.of(), Map.of(), Instant.now(), Instant.now(), "user-1"
            );
            runPromise(() -> registry.register(plugin));

            // When
            PluginRegistry.PluginMetadata result = runPromise(() -> registry.deactivate("plugin-1"));

            // Then
            assertThat(result.status()).isEqualTo(PluginRegistry.PluginStatus.INACTIVE);
            assertThat(result.isActive()).isFalse();
            verify(metrics).incrementCounter("plugin.deactivate.success", "plugin", "plugin-1");
        }
    }

    @Nested
    @DisplayName("Hook Execution")
    class HookExecutionTests {

        @Test
        @DisplayName("[TEST-014]: executeHook_successfully_executes_active_plugin_hook")
        void executeHookSuccess() {
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata(
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.ACTIVE,
                List.of("processData"), List.of(), Map.of(),
                Instant.now(), Instant.now(), "user-1"
            );
            runPromise(() -> registry.register(plugin));

            // When
            PluginRegistry.HookResult result = runPromise(() ->
                registry.executeHook("plugin-1", "processData", Map.of("key", "value")));

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.pluginId()).isEqualTo("plugin-1");
            assertThat(result.hookName()).isEqualTo("processData");
            verify(metrics).incrementCounter("plugin.hook.success", "plugin", "plugin-1", "hook", "processData");
        }

        @Test
        @DisplayName("[TEST-014]: executeHook_fails_for_inactive_plugin")
        void executeHookInactive() {
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata(
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.INACTIVE,
                List.of("processData"), List.of(), Map.of(),
                Instant.now(), null, "user-1"
            );
            runPromise(() -> registry.register(plugin));

            // When
            PluginRegistry.HookResult result = runPromise(() ->
                registry.executeHook("plugin-1", "processData", Map.of()));

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("not active");
        }

        @Test
        @DisplayName("[TEST-014]: executeHook_fails_for_missing_hook")
        void executeHookMissing() {
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata(
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.ACTIVE,
                List.of("processData"), List.of(), Map.of(),
                Instant.now(), Instant.now(), "user-1"
            );
            runPromise(() -> registry.register(plugin));

            // When
            PluginRegistry.HookResult result = runPromise(() ->
                registry.executeHook("plugin-1", "missingHook", Map.of()));

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("Hook not available");
        }
    }

    @Nested
    @DisplayName("Configuration Management")
    class ConfigurationTests {

        @Test
        @DisplayName("[TEST-015]: updateConfiguration_successfully_updates_config")
        void updateConfiguration() {
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata(
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.REGISTERED,
                List.of(), List.of(), Map.of(), Instant.now(), null, "user-1"
            );
            runPromise(() -> registry.register(plugin));

            Map<String, Object> config = Map.of("endpoint", "https://api.example.com", "timeout", 5000);

            // When
            Map<String, Object> result = runPromise(() -> registry.updateConfiguration("plugin-1", config));

            // Then
            assertThat(result).isEqualTo(config);

            // Verify get returns same config
            Map<String, Object> retrieved = runPromise(() -> registry.getConfiguration("plugin-1"));
            assertThat(retrieved).isEqualTo(config);
            verify(metrics).incrementCounter("plugin.config.update", "plugin", "plugin-1");
        }
    }

    @Nested
    @DisplayName("Health Check")
    class HealthTests {

        @Test
        @DisplayName("[TEST-016]: getHealth_returns_healthy_for_active_plugin")
        void getHealthHealthy() {
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata(
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.ACTIVE,
                List.of(), List.of(), Map.of(), Instant.now(), Instant.now(), "user-1"
            );
            runPromise(() -> registry.register(plugin));

            // When
            PluginRegistry.PluginHealth health = runPromise(() -> registry.getHealth("plugin-1"));

            // Then
            assertThat(health.healthy()).isTrue();
            assertThat(health.status()).isEqualTo("HEALTHY");
            assertThat(health.issues()).isEmpty();
        }

        @Test
        @DisplayName("[TEST-016]: getHealth_returns_unhealthy_for_missing_plugin")
        void getHealthMissing() {
            // When
            PluginRegistry.PluginHealth health = runPromise(() -> registry.getHealth("missing"));

            // Then
            assertThat(health.healthy()).isFalse();
            assertThat(health.status()).isEqualTo("NOT_FOUND");
        }
    }
}
