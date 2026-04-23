/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        aiService = new AIAssistServiceImpl(llmProvider, metrics); // GH-90000
        pluginRegistry = new PluginRegistryImpl(metrics); // GH-90000

        when(llmProvider.getName()).thenReturn("OpenAI");
    }

    @Test
    @DisplayName("[INTEGRATION-005]: ai_assist_with_active_plugin")
    void aiAssistWithActivePlugin() { // GH-90000
        // Given - Register and activate a data processing plugin
        PluginRegistry.PluginMetadata plugin = new PluginRegistry.PluginMetadata( // GH-90000
            "data-processor",
            "Data Processor",
            "Processes data queries",
            "1.0.0",
            "tenant-alpha",
            PluginRegistry.PluginType.CUSTOM,
            PluginRegistry.PluginStatus.REGISTERED,
            List.of("processQuery"),
            List.of(), // GH-90000
            Map.of("capability", "data-analysis"), // GH-90000
            Instant.now(), // GH-90000
            null,
            "admin-1"
        );

        PluginRegistry.PluginMetadata registered = runPromise(() -> pluginRegistry.register(plugin)); // GH-90000
        assertThat(registered.status()).isEqualTo(PluginRegistry.PluginStatus.REGISTERED); // GH-90000

        PluginRegistry.PluginMetadata activated = runPromise(() -> pluginRegistry.activate("data-processor"));
        assertThat(activated.isActive()).isTrue(); // GH-90000

        // When - Execute plugin hook
        PluginRegistry.HookResult hookResult = runPromise(() -> // GH-90000
            pluginRegistry.executeHook("data-processor", "processQuery", Map.of("table", "sales"))); // GH-90000

        // Then
        assertThat(hookResult.success()).isTrue(); // GH-90000
        assertThat(hookResult.pluginId()).isEqualTo("data-processor");

        System.out.println("[INTEGRATION] Plugin hook executed: " + hookResult.hookName()); // GH-90000
    }

    @Test
    @DisplayName("[INTEGRATION-006]: ai_service_status_with_plugins")
    void aiServiceStatusWithPlugins() { // GH-90000
        // Given - AI service processing queries
        when(llmProvider.complete(any())).thenReturn(Promise.of(new LLMProvider.CompletionResponse( // GH-90000
            "resp-1", "SQL result", 10, 5, 5, "stop", 25L, "gpt-4"
        )));

        AIAssistService.QueryContext context = new AIAssistService.QueryContext( // GH-90000
            "tenant-alpha", "user-1", null, null, null, Map.of(), null // GH-90000
        );

        // Process multiple queries
        for (int i = 0; i < 5; i++) { // GH-90000
            int queryIndex = i;
            runPromise(() -> aiService.processQuery("Query " + queryIndex, context)); // GH-90000
        }

        // When - Get service status
        AIAssistService.ServiceStatus status = runPromise(() -> aiService.getStatus()); // GH-90000

        // Then
        assertThat(status.available()).isTrue(); // GH-90000
        assertThat(status.requestsProcessed()).isEqualTo(5); // GH-90000
        assertThat(status.provider()).isEqualTo("OpenAI");

        System.out.println("[INTEGRATION] AI Service processed " + status.requestsProcessed() + " requests"); // GH-90000
    }

    @Test
    @DisplayName("[INTEGRATION-007]: plugin_registry_with_ai_integration")
    void pluginRegistryWithAIIntegration() { // GH-90000
        // Given - Register AI-powered plugin
        PluginRegistry.PluginMetadata aiPlugin = new PluginRegistry.PluginMetadata( // GH-90000
            "ai-enhancer",
            "AI Enhancer",
            "Enhances queries with AI",
            "1.0.0",
            "tenant-alpha",
            PluginRegistry.PluginType.ANALYTICS,
            PluginRegistry.PluginStatus.REGISTERED,
            List.of("enhanceQuery", "validateSQL"), // GH-90000
            List.of(), // GH-90000
            Map.of("aiEnabled", true), // GH-90000
            Instant.now(), // GH-90000
            null,
            "system"
        );

        // When - Register and configure
        PluginRegistry.PluginMetadata registered = runPromise(() -> pluginRegistry.register(aiPlugin)); // GH-90000
        runPromise(() -> pluginRegistry.activate(aiPlugin.id())); // GH-90000

        Map<String, Object> config = Map.of( // GH-90000
            "model", "gpt-4",
            "temperature", 0.7,
            "maxTokens", 2000
        );
        Map<String, Object> savedConfig = runPromise(() -> pluginRegistry.updateConfiguration(aiPlugin.id(), config)); // GH-90000

        // Then
        assertThat(registered.id()).isEqualTo("ai-enhancer");

        Map<String, Object> retrievedConfig = runPromise(() -> pluginRegistry.getConfiguration(aiPlugin.id())); // GH-90000
        assertThat(retrievedConfig.get("model")).isEqualTo("gpt-4");

        // Verify health
        PluginRegistry.PluginHealth health = runPromise(() -> pluginRegistry.getHealth(aiPlugin.id())); // GH-90000
        assertThat(health.healthy()).isTrue(); // GH-90000

        System.out.println("[INTEGRATION] AI Plugin configured with model: " + retrievedConfig.get("model"));
    }

    @Test
    @DisplayName("[INTEGRATION-008]: multi_plugin_workflow")
    void multiPluginWorkflow() { // GH-90000
        // Given - Multiple plugins for a workflow
        String tenantId = "tenant-workflow";

        // Data source plugin
        PluginRegistry.PluginMetadata sourcePlugin = new PluginRegistry.PluginMetadata( // GH-90000
            "data-source",
            "Data Source",
            "Provides data access",
            "1.0.0",
            tenantId,
            PluginRegistry.PluginType.CUSTOM,
            PluginRegistry.PluginStatus.REGISTERED,
            List.of("fetchData"),
            List.of(), // GH-90000
            Map.of(), // GH-90000
            Instant.now(), // GH-90000
            null,
            "admin"
        );

        // Data transform plugin
        PluginRegistry.PluginMetadata transformPlugin = new PluginRegistry.PluginMetadata( // GH-90000
            "data-transform",
            "Data Transform",
            "Transforms data",
            "1.0.0",
            tenantId,
            PluginRegistry.PluginType.CUSTOM,
            PluginRegistry.PluginStatus.REGISTERED,
            List.of("transform"),
            List.of("data-source"), // Depends on data-source
            Map.of(), // GH-90000
            Instant.now(), // GH-90000
            null,
            "admin"
        );

        // Register both
        runPromise(() -> pluginRegistry.register(sourcePlugin)); // GH-90000
        runPromise(() -> pluginRegistry.register(transformPlugin)); // GH-90000

        // When - List plugins for tenant
        List<PluginRegistry.PluginMetadata> plugins = runPromise(() -> // GH-90000
            pluginRegistry.listPlugins(tenantId, null)); // GH-90000

        // Then
        assertThat(plugins).hasSize(2); // GH-90000
        assertThat(plugins.stream().map(PluginRegistry.PluginMetadata::id)) // GH-90000
            .containsExactlyInAnyOrder("data-source", "data-transform"); // GH-90000

        // Activate all
        for (PluginRegistry.PluginMetadata p : plugins) { // GH-90000
            runPromise(() -> pluginRegistry.activate(p.id())); // GH-90000
        }

        // Verify all active
        List<PluginRegistry.PluginMetadata> activePlugins = runPromise(() -> // GH-90000
            pluginRegistry.listPlugins(tenantId, PluginRegistry.PluginStatus.ACTIVE)); // GH-90000
        assertThat(activePlugins).hasSize(2); // GH-90000

        System.out.println("[INTEGRATION] " + activePlugins.size() + " plugins activated for workflow"); // GH-90000
    }

    @Test
    @DisplayName("[INTEGRATION-009]: ai_conversation_with_plugin_context")
    void aiConversationWithPluginContext() { // GH-90000
        // Given - Create conversation
        AIAssistService.Conversation conv = runPromise(() -> // GH-90000
            aiService.createConversation("tenant-alpha", "user-1")); // GH-90000

        // Add plugin context message
        AIAssistService.Message pluginContextMsg = new AIAssistService.Message( // GH-90000
            "msg-1",
            AIAssistService.MessageRole.SYSTEM,
            "Available plugins: data-processor, ai-enhancer",
            Instant.now(), // GH-90000
            Map.of("plugins", List.of("data-processor", "ai-enhancer")) // GH-90000
        );

        AIAssistService.Conversation updated = runPromise(() -> // GH-90000
            aiService.addMessage(conv.id(), pluginContextMsg)); // GH-90000

        // When - Add user query
        AIAssistService.Message userMsg = new AIAssistService.Message( // GH-90000
            "msg-2",
            AIAssistService.MessageRole.USER,
            "Process sales data using data-processor",
            Instant.now(), // GH-90000
            Map.of("intent", "process", "targetPlugin", "data-processor") // GH-90000
        );

        AIAssistService.Conversation finalConv = runPromise(() -> // GH-90000
            aiService.addMessage(conv.id(), userMsg)); // GH-90000

        // Then
        assertThat(finalConv.messageCount()).isEqualTo(2); // GH-90000

        // Retrieve conversation
        AIAssistService.Conversation retrieved = runPromise(() -> aiService.getConversation(conv.id())); // GH-90000
        assertThat(retrieved.messages()).hasSize(2); // GH-90000

        System.out.println("[INTEGRATION] Conversation has " + retrieved.messageCount() + " messages with plugin context"); // GH-90000
    }
}
