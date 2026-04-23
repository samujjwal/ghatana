/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        registry = new PluginRegistryImpl(metrics); // GH-90000
    }

    @Nested
    @DisplayName("Register Plugin")
    class RegisterTests {

        @Test
        @DisplayName("[TEST-011]: register_successfully_registers_plugin")
        void registerSuccessfully() { // GH-90000
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata( // GH-90000
                "plugin-1", "Test Plugin", "Description", "1.0.0",
                "tenant-alpha", PluginRegistry.PluginType.CUSTOM,
                PluginRegistry.PluginStatus.REGISTERED,
                List.of("hook1"), List.of(),
                Map.of(), Instant.now(), null, "user-1" // GH-90000
            );

            // When
            PluginRegistry.PluginMetadata result = runPromise(() -> registry.register(plugin)); // GH-90000

            // Then
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.id()).isEqualTo("plugin-1");
            assertThat(result.status()).isEqualTo(PluginRegistry.PluginStatus.REGISTERED); // GH-90000
            assertThat(result.isActive()).isFalse(); // GH-90000
            verify(metrics).incrementCounter("plugin.register.success", "tenant", "tenant-alpha", "type", "CUSTOM"); // GH-90000
        }

        @Test
        @DisplayName("[TEST-011]: register_throws_for_duplicate_id")
        void registerDuplicate() { // GH-90000
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata( // GH-90000
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.REGISTERED,
                List.of(), List.of(), Map.of(), Instant.now(), null, "user-1" // GH-90000
            );

            runPromise(() -> registry.register(plugin)); // GH-90000

            // When/Then
            clearFatalError(); // GH-90000
            try {
                runPromise(() -> registry.register(plugin)); // GH-90000
            } catch (Exception e) { // GH-90000
                // Expected
            }
            verify(metrics).incrementCounter("plugin.register.conflict", "plugin", "plugin-1"); // GH-90000
        }
    }

    @Nested
    @DisplayName("Activate/Deactivate")
    class ActivationTests {

        @Test
        @DisplayName("[TEST-012]: activate_successfully_activates_plugin")
        void activateSuccessfully() { // GH-90000
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata( // GH-90000
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.REGISTERED,
                List.of(), List.of(), Map.of(), Instant.now(), null, "user-1" // GH-90000
            );
            runPromise(() -> registry.register(plugin)); // GH-90000

            // When
            PluginRegistry.PluginMetadata result = runPromise(() -> registry.activate("plugin-1"));

            // Then
            assertThat(result.status()).isEqualTo(PluginRegistry.PluginStatus.ACTIVE); // GH-90000
            assertThat(result.isActive()).isTrue(); // GH-90000
            assertThat(result.activatedAt()).isNotNull(); // GH-90000
            verify(metrics).incrementCounter("plugin.activate.success", "plugin", "plugin-1"); // GH-90000
        }

        @Test
        @DisplayName("[TEST-012]: activate_throws_for_missing_plugin")
        void activateMissing() { // GH-90000
            // When
            clearFatalError(); // GH-90000
            try {
                runPromise(() -> registry.activate("missing-plugin"));
            } catch (Exception e) { // GH-90000
                // Expected
            }
            verify(metrics).incrementCounter("plugin.activate.error", "plugin", "missing-plugin", "reason", "not_found"); // GH-90000
        }

        @Test
        @DisplayName("[TEST-013]: deactivate_successfully_deactivates_plugin")
        void deactivateSuccessfully() { // GH-90000
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata( // GH-90000
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.ACTIVE,
                List.of(), List.of(), Map.of(), Instant.now(), Instant.now(), "user-1" // GH-90000
            );
            runPromise(() -> registry.register(plugin)); // GH-90000

            // When
            PluginRegistry.PluginMetadata result = runPromise(() -> registry.deactivate("plugin-1"));

            // Then
            assertThat(result.status()).isEqualTo(PluginRegistry.PluginStatus.INACTIVE); // GH-90000
            assertThat(result.isActive()).isFalse(); // GH-90000
            verify(metrics).incrementCounter("plugin.deactivate.success", "plugin", "plugin-1"); // GH-90000
        }
    }

    @Nested
    @DisplayName("Hook Execution")
    class HookExecutionTests {

        @Test
        @DisplayName("[TEST-014]: executeHook_successfully_executes_active_plugin_hook")
        void executeHookSuccess() { // GH-90000
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata( // GH-90000
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.ACTIVE,
                List.of("processData"), List.of(), Map.of(),
                Instant.now(), Instant.now(), "user-1" // GH-90000
            );
            runPromise(() -> registry.register(plugin)); // GH-90000
            runPromise(() -> registry.activate("plugin-1"));

            // When
            PluginRegistry.HookResult result = runPromise(() -> // GH-90000
                registry.executeHook("plugin-1", "processData", Map.of("key", "value"))); // GH-90000

            // Then
            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(result.pluginId()).isEqualTo("plugin-1");
            assertThat(result.hookName()).isEqualTo("processData");
            verify(metrics).incrementCounter("plugin.hook.success", "plugin", "plugin-1", "hook", "processData"); // GH-90000
        }

        @Test
        @DisplayName("[TEST-014]: executeHook_fails_for_inactive_plugin")
        void executeHookInactive() { // GH-90000
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata( // GH-90000
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.INACTIVE,
                List.of("processData"), List.of(), Map.of(),
                Instant.now(), null, "user-1" // GH-90000
            );
            runPromise(() -> registry.register(plugin)); // GH-90000

            // When
            PluginRegistry.HookResult result = runPromise(() -> // GH-90000
                registry.executeHook("plugin-1", "processData", Map.of())); // GH-90000

            // Then
            assertThat(result.success()).isFalse(); // GH-90000
            assertThat(result.errorMessage()).contains("not active");
        }

        @Test
        @DisplayName("[TEST-014]: executeHook_fails_for_missing_hook")
        void executeHookMissing() { // GH-90000
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata( // GH-90000
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.ACTIVE,
                List.of("processData"), List.of(), Map.of(),
                Instant.now(), Instant.now(), "user-1" // GH-90000
            );
            runPromise(() -> registry.register(plugin)); // GH-90000

            // When
            PluginRegistry.HookResult result = runPromise(() -> // GH-90000
                registry.executeHook("plugin-1", "missingHook", Map.of())); // GH-90000

            // Then
            assertThat(result.success()).isFalse(); // GH-90000
            assertThat(result.errorMessage()).contains("Hook not available");
        }
    }

    @Nested
    @DisplayName("Configuration Management")
    class ConfigurationTests {

        @Test
        @DisplayName("[TEST-015]: updateConfiguration_successfully_updates_config")
        void updateConfiguration() { // GH-90000
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata( // GH-90000
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.REGISTERED,
                List.of(), List.of(), Map.of(), Instant.now(), null, "user-1" // GH-90000
            );
            runPromise(() -> registry.register(plugin)); // GH-90000

            Map<String, Object> config = Map.of("endpoint", "https://api.example.com", "timeout", 5000); // GH-90000

            // When
            Map<String, Object> result = runPromise(() -> registry.updateConfiguration("plugin-1", config)); // GH-90000

            // Then
            assertThat(result).isEqualTo(config); // GH-90000

            // Verify get returns same config
            Map<String, Object> retrieved = runPromise(() -> registry.getConfiguration("plugin-1"));
            assertThat(retrieved).isEqualTo(config); // GH-90000
            verify(metrics).incrementCounter("plugin.config.update", "plugin", "plugin-1"); // GH-90000
        }
    }

    @Nested
    @DisplayName("Health Check")
    class HealthTests {

        @Test
        @DisplayName("[TEST-016]: getHealth_returns_healthy_for_active_plugin")
        void getHealthHealthy() { // GH-90000
            // Given
            PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata( // GH-90000
                "plugin-1", "Test", "Desc", "1.0", "tenant-alpha",
                PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.REGISTERED,
                List.of(), List.of(), Map.of(), Instant.now(), Instant.now(), "user-1" // GH-90000
            );
            runPromise(() -> registry.register(plugin)); // GH-90000
            runPromise(() -> registry.activate("plugin-1"));

            // When
            PluginRegistry.PluginHealth health = runPromise(() -> registry.getHealth("plugin-1"));

            // Then
            assertThat(health.healthy()).isTrue(); // GH-90000
            assertThat(health.status()).isEqualTo("HEALTHY");
            assertThat(health.issues()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("[TEST-016]: getHealth_returns_unhealthy_for_missing_plugin")
        void getHealthMissing() { // GH-90000
            // When
            PluginRegistry.PluginHealth health = runPromise(() -> registry.getHealth("missing"));

            // Then
            assertThat(health.healthy()).isFalse(); // GH-90000
            assertThat(health.status()).isEqualTo("NOT_FOUND");
        }
    }
}
