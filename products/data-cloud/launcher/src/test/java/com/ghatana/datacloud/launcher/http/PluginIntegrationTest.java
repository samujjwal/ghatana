/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for plugin management end-to-end (PF001).
 *
 * @doc.type class
 * @doc.purpose Plugin end-to-end integration tests
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 15, unit = TimeUnit.SECONDS)
@DisplayName("PluginIntegration – End-to-End (PF001)")
class PluginIntegrationTest extends DataCloudHttpServerTestBase {

    @Mock
    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception {
        port = findFreePort();
        startServer();
    }

    @Override
    protected void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
        waitForServerReady(5000);
    }

    @Nested
    @DisplayName("Plugin Registration Flow")
    class PluginRegistrationFlowTests {

        @Test
        @DisplayName("[PF001]: register_plugin_via_api")
        void registerPluginViaApi() throws Exception {
            Map<String, Object> newPlugin = Map.of(
                "id", "new-plugin",
                "name", "New Plugin",
                "version", "1.0.0",
                "type", "CUSTOM",
                "hooks", java.util.List.of("onInit", "onData")
            );

            Map<String, Object> registeredPlugin = Map.of(
                "id", "new-plugin",
                "name", "New Plugin",
                "status", "REGISTERED",
                "tenantId", "tenant-alpha",
                "registeredAt", Instant.now().toString()
            );

            lenient().when(mockClient.registerPlugin(any(), any()))
                .thenReturn(Promise.of(registeredPlugin));

            var response = postJson("/api/v1/plugins", newPlugin, withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("id")).isEqualTo("new-plugin");
            assertThat(body.get("status")).isEqualTo("REGISTERED");
        }

        @Test
        @DisplayName("[PF001]: get_plugin_via_api")
        void getPluginViaApi() throws Exception {
            String pluginId = "existing-plugin";

            Map<String, Object> plugin = Map.of(
                "id", pluginId,
                "name", "Existing Plugin",
                "status", "ACTIVE",
                "type", "DATA_SOURCE",
                "version", "2.0.0",
                "hooks", java.util.List.of("onInit", "onSync")
            );

            lenient().when(mockClient.getPlugin(any(), eq(pluginId)))
                .thenReturn(Promise.of(plugin));

            var response = get("/api/v1/plugins/" + pluginId, withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("id")).isEqualTo(pluginId);
            assertThat(body.get("status")).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("[PF001]: list_plugins_via_api")
        void listPluginsViaApi() throws Exception {
            var plugins = java.util.List.of(
                Map.of("id", "p1", "name", "Plugin 1", "status", "ACTIVE"),
                Map.of("id", "p2", "name", "Plugin 2", "status", "INACTIVE"),
                Map.of("id", "p3", "name", "Plugin 3", "status", "REGISTERED")
            );

            lenient().when(mockClient.listPlugins(any()))
                .thenReturn(Promise.of(plugins));

            var response = get("/api/v1/plugins", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> pluginList = (java.util.List<Map<String, Object>>) body.get("plugins");
            assertThat(pluginList).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Plugin Activation Flow")
    class PluginActivationFlowTests {

        @Test
        @DisplayName("[PF001]: activate_plugin_via_api")
        void activatePluginViaApi() throws Exception {
            String pluginId = "inactive-plugin";

            Map<String, Object> activatedPlugin = Map.of(
                "id", pluginId,
                "status", "ACTIVE",
                "activatedAt", Instant.now().toString()
            );

            lenient().when(mockClient.activatePlugin(any(), eq(pluginId)))
                .thenReturn(Promise.of(activatedPlugin));

            var response = post("/api/v1/plugins/" + pluginId + "/activate", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("status")).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("[PF001]: deactivate_plugin_via_api")
        void deactivatePluginViaApi() throws Exception {
            String pluginId = "active-plugin";

            Map<String, Object> deactivatedPlugin = Map.of(
                "id", pluginId,
                "status", "INACTIVE"
            );

            lenient().when(mockClient.deactivatePlugin(any(), eq(pluginId)))
                .thenReturn(Promise.of(deactivatedPlugin));

            var response = post("/api/v1/plugins/" + pluginId + "/deactivate", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("status")).isEqualTo("INACTIVE");
        }
    }

    @Nested
    @DisplayName("Plugin Hook Execution")
    class PluginHookExecutionTests {

        @Test
        @DisplayName("[PF001]: execute_hook_via_api")
        void executeHookViaApi() throws Exception {
            String pluginId = "hooked-plugin";
            String hookName = "onData";

            Map<String, Object> hookContext = Map.of(
                "data", "test data",
                "source", "api"
            );

            Map<String, Object> hookResult = Map.of(
                "pluginId", pluginId,
                "hookName", hookName,
                "success", true,
                "result", "processed",
                "executionTimeMs", 150
            );

            lenient().when(mockClient.executePluginHook(any(), eq(pluginId), eq(hookName), any()))
                .thenReturn(Promise.of(hookResult));

            var response = postJson("/api/v1/plugins/" + pluginId + "/hooks/" + hookName,
                hookContext, withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("success")).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Plugin Health")
    class PluginHealthTests {

        @Test
        @DisplayName("[PF001]: get_plugin_health_via_api")
        void getPluginHealthViaApi() throws Exception {
            String pluginId = "monitored-plugin";

            Map<String, Object> health = Map.of(
                "pluginId", pluginId,
                "healthy", true,
                "status", "RUNNING",
                "issues", java.util.List.of()
            );

            lenient().when(mockClient.getPluginHealth(any(), eq(pluginId)))
                .thenReturn(Promise.of(health));

            var response = get("/api/v1/plugins/" + pluginId + "/health", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("healthy")).isEqualTo(true);
            assertThat(body.get("status")).isEqualTo("RUNNING");
        }
    }

    @Nested
    @DisplayName("Plugin Unregistration")
    class PluginUnregistrationTests {

        @Test
        @DisplayName("[PF001]: unregister_plugin_via_api")
        void unregisterPluginViaApi() throws Exception {
            String pluginId = "plugin-to-remove";

            lenient().when(mockClient.unregisterPlugin(any(), eq(pluginId)))
                .thenReturn(Promise.of(Map.of("unregistered", true)));

            var response = delete("/api/v1/plugins/" + pluginId, withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("unregistered")).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("[PF001]: nonexistent_plugin_returns_404")
        void nonexistentPluginReturns404() throws Exception {
            String pluginId = "nonexistent-plugin";

            lenient().when(mockClient.getPlugin(any(), eq(pluginId)))
                .thenReturn(Promise.of(null));

            var response = get("/api/v1/plugins/" + pluginId, withTenant("tenant-alpha"));

            assertStatusCode(response, 404);
        }
    }
}
