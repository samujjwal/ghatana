/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import com.ghatana.datacloud.ai.*;
import com.ghatana.datacloud.plugin.*;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AI and Plugin Integration Test
 *
 * @doc.type class
 * @doc.purpose Integration tests showing AI and plugin service usage
 * @doc.layer test
 * @doc.pattern Integration Test
 */
@DisplayName("AI and Plugin Integration Tests")
class AIPluginIntegrationTest extends EventloopTestBase {

    @Mock
    private LLMProvider llmProvider;

    @Mock
    private MetricsCollector metrics;

    private AIAssistServiceImpl aiService;
    private PluginRegistryImpl pluginRegistry;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        aiService = new AIAssistServiceImpl(llmProvider, metrics);
        pluginRegistry = new PluginRegistryImpl(metrics);

        when(llmProvider.getName()).thenReturn("OpenAI");
    }

    @Test
    @DisplayName("[INTEGRATION-005]: ai_assist_with_active_plugin")
    void aiAssistWithActivePlugin() {
        // Given - Register and activate a data processing plugin
        PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata(
            "data-processor",
            "Data Processor",
            "Processes data queries",
            "1.0.0",
            "tenant-alpha",
            PluginRegistry.PluginType.CUSTOM,
            PluginRegistry.PluginStatus.REGISTERED,
            List.of("processQuery"),
            List.of(),
            Map.of("capability", "data-analysis"),
            Instant.now(),
            null,
            "admin-1"
        );

        PluginRegistry.PluginMetadata registered = runPromise(() -> pluginRegistry.register(plugin));
        assertThat(registered.status()).isEqualTo(PluginRegistry.PluginStatus.REGISTERED);

        PluginRegistry.PluginMetadata activated = runPromise(() -> pluginRegistry.activate("data-processor"));
        assertThat(activated.isActive()).isTrue();

        // When - Execute plugin hook
        PluginRegistry.HookResult hookResult = runPromise(() ->
            pluginRegistry.executeHook("data-processor", "processQuery", Map.of("table", "sales")));

        // Then
        assertThat(hookResult.success()).isTrue();
        assertThat(hookResult.pluginId()).isEqualTo("data-processor");

        System.out.println("[INTEGRATION] Plugin hook executed: " + hookResult.hookName());
    }

    @Test
    @DisplayName("[INTEGRATION-006]: ai_service_status_with_plugins")
    void aiServiceStatusWithPlugins() {
        // Given - AI service processing queries
        when(llmProvider.complete(any())).thenReturn(Promise.of(new LLMProvider.CompletionResponse(
            "resp-1", "SQL result", 10, 5, 5, "stop", 25L, "gpt-4"
        )));

        AIAssistService.QueryContext context = new AIAssistService.QueryContext(
            "tenant-alpha", "user-1", null, null, null, Map.of(), null
        );

        // Process multiple queries
        for (int i = 0; i < 5; i++) {
            int queryIndex = i;
            runPromise(() -> aiService.processQuery("Query " + queryIndex, context));
        }

        // When - Get service status
        AIAssistService.ServiceStatus status = runPromise(() -> aiService.getStatus());

        // Then
        assertThat(status.available()).isTrue();
        assertThat(status.requestsProcessed()).isEqualTo(5);
        assertThat(status.provider()).isEqualTo("OpenAI");

        System.out.println("[INTEGRATION] AI Service processed " + status.requestsProcessed() + " requests");
    }

    @Test
    @DisplayName("[INTEGRATION-007]: plugin_registry_with_ai_integration")
    void pluginRegistryWithAIIntegration() {
        // Given - Register AI-powered plugin
        PluginRegistry.PluginMetadata aiPlugin = new PluginRegistry.PluginMetadata(
            "ai-enhancer",
            "AI Enhancer",
            "Enhances queries with AI",
            "1.0.0",
            "tenant-alpha",
            PluginRegistry.PluginType.ANALYTICS,
            PluginRegistry.PluginStatus.REGISTERED,
            List.of("enhanceQuery", "validateSQL"),
            List.of(),
            Map.of("aiEnabled", true),
            Instant.now(),
            null,
            "system"
        );

        // When - Register and configure
        PluginRegistry.PluginMetadata registered = runPromise(() -> pluginRegistry.register(aiPlugin));
        runPromise(() -> pluginRegistry.activate(aiPlugin.id()));

        Map<String, Object> config = Map.of(
            "model", "gpt-4",
            "temperature", 0.7,
            "maxTokens", 2000
        );
        Map<String, Object> savedConfig = runPromise(() -> pluginRegistry.updateConfiguration(aiPlugin.id(), config));

        // Then
        assertThat(registered.id()).isEqualTo("ai-enhancer");

        Map<String, Object> retrievedConfig = runPromise(() -> pluginRegistry.getConfiguration(aiPlugin.id()));
        assertThat(retrievedConfig.get("model")).isEqualTo("gpt-4");

        // Verify health
        PluginRegistry.PluginHealth health = runPromise(() -> pluginRegistry.getHealth(aiPlugin.id()));
        assertThat(health.healthy()).isTrue();

        System.out.println("[INTEGRATION] AI Plugin configured with model: " + retrievedConfig.get("model"));
    }

    @Test
    @DisplayName("[INTEGRATION-008]: multi_plugin_workflow")
    void multiPluginWorkflow() {
        // Given - Multiple plugins for a workflow
        String tenantId = "tenant-workflow";

        // Data source plugin
        PluginRegistry.PluginMetadata sourcePlugin = new PluginRegistry.PluginMetadata(
            "data-source",
            "Data Source",
            "Provides data access",
            "1.0.0",
            tenantId,
            PluginRegistry.PluginType.CUSTOM,
            PluginRegistry.PluginStatus.REGISTERED,
            List.of("fetchData"),
            List.of(),
            Map.of(),
            Instant.now(),
            null,
            "admin"
        );

        // Data transform plugin
        PluginRegistry.PluginMetadata transformPlugin = new PluginRegistry.PluginMetadata(
            "data-transform",
            "Data Transform",
            "Transforms data",
            "1.0.0",
            tenantId,
            PluginRegistry.PluginType.CUSTOM,
            PluginRegistry.PluginStatus.REGISTERED,
            List.of("transform"),
            List.of("data-source"), // Depends on data-source
            Map.of(),
            Instant.now(),
            null,
            "admin"
        );

        // Register both
        runPromise(() -> pluginRegistry.register(sourcePlugin));
        runPromise(() -> pluginRegistry.register(transformPlugin));

        // When - List plugins for tenant
        List<PluginRegistry.PluginMetadata> plugins = runPromise(() ->
            pluginRegistry.listPlugins(tenantId, null));

        // Then
        assertThat(plugins).hasSize(2);
        assertThat(plugins.stream().map(PluginRegistry.PluginMetadata::id))
            .containsExactlyInAnyOrder("data-source", "data-transform");

        // Activate all
        for (PluginRegistry.PluginMetadata p : plugins) {
            runPromise(() -> pluginRegistry.activate(p.id()));
        }

        // Verify all active
        List<PluginRegistry.PluginMetadata> activePlugins = runPromise(() ->
            pluginRegistry.listPlugins(tenantId, PluginRegistry.PluginStatus.ACTIVE));
        assertThat(activePlugins).hasSize(2);

        System.out.println("[INTEGRATION] " + activePlugins.size() + " plugins activated for workflow");
    }

    @Test
    @DisplayName("[INTEGRATION-009]: ai_conversation_with_plugin_context")
    void aiConversationWithPluginContext() {
        // Given - Create conversation
        AIAssistService.Conversation conv = runPromise(() ->
            aiService.createConversation("tenant-alpha", "user-1"));

        // Add plugin context message
        AIAssistService.Message pluginContextMsg = new AIAssistService.Message(
            "msg-1",
            AIAssistService.MessageRole.SYSTEM,
            "Available plugins: data-processor, ai-enhancer",
            Instant.now(),
            Map.of("plugins", List.of("data-processor", "ai-enhancer"))
        );

        AIAssistService.Conversation updated = runPromise(() ->
            aiService.addMessage(conv.id(), pluginContextMsg));

        // When - Add user query
        AIAssistService.Message userMsg = new AIAssistService.Message(
            "msg-2",
            AIAssistService.MessageRole.USER,
            "Process sales data using data-processor",
            Instant.now(),
            Map.of("intent", "process", "targetPlugin", "data-processor")
        );

        AIAssistService.Conversation finalConv = runPromise(() ->
            aiService.addMessage(conv.id(), userMsg));

        // Then
        assertThat(finalConv.messageCount()).isEqualTo(2);

        // Retrieve conversation
        AIAssistService.Conversation retrieved = runPromise(() -> aiService.getConversation(conv.id()));
        assertThat(retrieved.messages()).hasSize(2);

        System.out.println("[INTEGRATION] Conversation has " + retrieved.messageCount() + " messages with plugin context");
    }
}
