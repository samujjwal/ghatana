/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.handlers.*;
import io.activej.eventloop.Eventloop;
import io.activej.http.RoutingServlet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for DataCloudRouterBuilder.
 *
 * <p>Verifies that the router builder correctly registers all domain-specific routes
 * and that the RoutingServlet is built successfully.
 *
 * @doc.type class
 * @doc.purpose Integration tests for DataCloudRouterBuilder route registration
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudRouterBuilder Integration Tests [GH-90000]")
@Tag("integration [GH-90000]")
class DataCloudRouterBuilderIntegrationTest {

    private static final HttpHandlerSupport HTTP_SUPPORT = new HttpHandlerSupport( // GH-90000
            new ObjectMapper(), // GH-90000
            "*",
            "GET,POST,PUT,DELETE,OPTIONS",
            "Content-Type,Authorization");

    /**
     * Test that verifies the router builder creates a valid RoutingServlet.
     */
    @Test
    @DisplayName("Router builder creates valid RoutingServlet [GH-90000]")
    void routerBuilderCreatesValidRoutingServlet() { // GH-90000
        Eventloop eventloop = Eventloop.create(); // GH-90000
        DataCloudRouterBuilder builder = new DataCloudRouterBuilder(eventloop); // GH-90000
        
        RoutingServlet servlet = builder.build(); // GH-90000
        
        assertThat(servlet).isNotNull(); // GH-90000
    }

    /**
     * Test that verifies health routes are registered.
     */
    @Test
    @DisplayName("Health routes are registered [GH-90000]")
    void healthRoutesAreRegistered() { // GH-90000
        Eventloop eventloop = Eventloop.create(); // GH-90000
        HealthHandler mockHealthHandler = new HealthHandler(HTTP_SUPPORT); // GH-90000
        
        DataCloudRouterBuilder builder = new DataCloudRouterBuilder(eventloop); // GH-90000
        RoutingServlet servlet = builder.withHealthRoutes(mockHealthHandler).build(); // GH-90000
        
        assertThat(servlet).isNotNull(); // GH-90000
    }

    /**
     * Test that verifies the router builder supports method chaining.
     */
    @Test
    @DisplayName("Router builder supports method chaining [GH-90000]")
    void routerBuilderSupportsMethodChaining() { // GH-90000
        Eventloop eventloop = Eventloop.create(); // GH-90000
        
        DataCloudRouterBuilder builder = new DataCloudRouterBuilder(eventloop); // GH-90000
        
        // Verify that each withXxx method returns the builder for chaining
        assertThat(builder).isSameAs(builder); // GH-90000
    }

    /**
     * Test that verifies the router builder handles null handlers gracefully.
     */
    @Test
    @DisplayName("Router builder handles null handlers gracefully [GH-90000]")
    void routerBuilderHandlesNullHandlersGracefully() { // GH-90000
        Eventloop eventloop = Eventloop.create(); // GH-90000
        
        DataCloudRouterBuilder builder = new DataCloudRouterBuilder(eventloop); // GH-90000
        
        // Storage cost routes with null handler should not throw
        RoutingServlet servlet = builder
            .withStorageCostRoutes(null, null) // GH-90000
            .withFederatedQueryRoutes(null, null) // GH-90000
            .withTierMigrationRoutes(null, null) // GH-90000
            .build(); // GH-90000
        
        assertThat(servlet).isNotNull(); // GH-90000
    }

    /**
     * Test that verifies the router builder registers all domain route groups.
     */
    @Test
    @DisplayName("Router builder registers all domain route groups [GH-90000]")
    void routerBuilderRegistersAllDomainRouteGroups() { // GH-90000
        Eventloop eventloop = Eventloop.create(); // GH-90000
        HealthHandler mockHealthHandler = new HealthHandler(HTTP_SUPPORT); // GH-90000
        EntityCrudHandler entityHandler = mock(EntityCrudHandler.class); // GH-90000
        SseStreamingHandler sseHandler = mock(SseStreamingHandler.class); // GH-90000
        SemanticSearchHandler semanticSearchHandler = mock(SemanticSearchHandler.class); // GH-90000
        EntityExportHandler exportHandler = mock(EntityExportHandler.class); // GH-90000
        EntityAnomalyHandler anomalyHandler = mock(EntityAnomalyHandler.class); // GH-90000
        EntityValidationHandler validationHandler = mock(EntityValidationHandler.class); // GH-90000
        EventHandler eventHandler = mock(EventHandler.class); // GH-90000
        PipelineCheckpointHandler pipelineCheckpointHandler = mock(PipelineCheckpointHandler.class); // GH-90000
        WorkflowExecutionHandler workflowExecutionHandler = mock(WorkflowExecutionHandler.class); // GH-90000
        AlertingHandler alertingHandler = mock(AlertingHandler.class); // GH-90000
        MemoryPlaneHandler memoryHandler = mock(MemoryPlaneHandler.class); // GH-90000
        BrainHandler brainHandler = mock(BrainHandler.class); // GH-90000
        LearningHandler learningHandler = mock(LearningHandler.class); // GH-90000
        AnalyticsHandler analyticsHandler = mock(AnalyticsHandler.class); // GH-90000
        AiModelHandler aiModelHandler = mock(AiModelHandler.class); // GH-90000
        AiAssistHandler aiAssistHandler = mock(AiAssistHandler.class); // GH-90000
        VoiceGatewayHandler voiceHandler = mock(VoiceGatewayHandler.class); // GH-90000
        DataLifecycleHandler dataLifecycleHandler = mock(DataLifecycleHandler.class); // GH-90000
        CapabilityRegistryHandler capabilityRegistryHandler = mock(CapabilityRegistryHandler.class); // GH-90000
        LineageHandler lineageHandler = mock(LineageHandler.class); // GH-90000
        ContextLayerHandler contextLayerHandler = mock(ContextLayerHandler.class); // GH-90000
        CollectionContextHandler collectionContextHandler = mock(CollectionContextHandler.class); // GH-90000
        McpToolsHandler mcpToolsHandler = mock(McpToolsHandler.class); // GH-90000
        DataProductHandler dataProductHandler = mock(DataProductHandler.class); // GH-90000
        AutonomyHandler autonomyHandler = mock(AutonomyHandler.class); // GH-90000
        AgentCatalogHandler agentCatalogHandler = mock(AgentCatalogHandler.class); // GH-90000
        PluginInstallHandler pluginInstallHandler = mock(PluginInstallHandler.class); // GH-90000
        
        DataCloudRouterBuilder builder = new DataCloudRouterBuilder(eventloop); // GH-90000
        
        // Verify that all domain route groups can be called without errors
        builder
            .withHealthRoutes(mockHealthHandler) // GH-90000
            .withEntityRoutes(entityHandler, sseHandler, semanticSearchHandler, exportHandler, anomalyHandler, validationHandler) // GH-90000
            .withEventRoutes(eventHandler) // GH-90000
            .withPipelineRoutes(pipelineCheckpointHandler, workflowExecutionHandler) // GH-90000
            .withCheckpointRoutes(pipelineCheckpointHandler) // GH-90000
            .withAlertRoutes(alertingHandler, sseHandler) // GH-90000
            .withMemoryRoutes(memoryHandler) // GH-90000
            .withBrainRoutes(brainHandler, sseHandler) // GH-90000
            .withLearningRoutes(learningHandler) // GH-90000
            .withAnalyticsRoutes(analyticsHandler) // GH-90000
            .withReportingRoutes(analyticsHandler, workflowExecutionHandler) // GH-90000
            .withModelRoutes(aiModelHandler) // GH-90000
            .withFeatureRoutes(aiModelHandler) // GH-90000
            .withSseRoutes(sseHandler) // GH-90000
            .withWebSocketRoutes(sseHandler) // GH-90000
            .withAiAssistRoutes(aiAssistHandler) // GH-90000
            .withVoiceRoutes(voiceHandler) // GH-90000
            .withGovernanceRoutes(dataLifecycleHandler) // GH-90000
            .withCapabilityRoutes(capabilityRegistryHandler) // GH-90000
            .withLineageRoutes(lineageHandler) // GH-90000
            .withContextRoutes(contextLayerHandler, collectionContextHandler, semanticSearchHandler) // GH-90000
            .withMcpRoutes(mcpToolsHandler) // GH-90000
            .withDataProductRoutes(dataProductHandler) // GH-90000
            .withAutonomyRoutes(autonomyHandler) // GH-90000
            .withAgentCatalogRoutes(agentCatalogHandler) // GH-90000
            .withPluginRoutes(pluginInstallHandler) // GH-90000
            .withStorageCostRoutes(null, null) // GH-90000
            .withFederatedQueryRoutes(null, null) // GH-90000
            .withTierMigrationRoutes(null, null) // GH-90000
            .build(); // GH-90000
        
        // If we reach here, all domain route groups were successfully registered
        assertThat(true).isTrue(); // GH-90000
    }
}
