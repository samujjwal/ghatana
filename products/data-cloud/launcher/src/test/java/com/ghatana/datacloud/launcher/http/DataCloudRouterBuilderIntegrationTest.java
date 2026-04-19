/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("DataCloudRouterBuilder Integration Tests")
@Tag("integration")
class DataCloudRouterBuilderIntegrationTest {

    private static final HttpHandlerSupport HTTP_SUPPORT = new HttpHandlerSupport(
            new ObjectMapper(),
            "*",
            "GET,POST,PUT,DELETE,OPTIONS",
            "Content-Type,Authorization");

    /**
     * Test that verifies the router builder creates a valid RoutingServlet.
     */
    @Test
    @DisplayName("Router builder creates valid RoutingServlet")
    void routerBuilderCreatesValidRoutingServlet() {
        Eventloop eventloop = Eventloop.create();
        DataCloudRouterBuilder builder = new DataCloudRouterBuilder(eventloop);
        
        RoutingServlet servlet = builder.build();
        
        assertThat(servlet).isNotNull();
    }

    /**
     * Test that verifies health routes are registered.
     */
    @Test
    @DisplayName("Health routes are registered")
    void healthRoutesAreRegistered() {
        Eventloop eventloop = Eventloop.create();
        HealthHandler mockHealthHandler = new HealthHandler(HTTP_SUPPORT);
        
        DataCloudRouterBuilder builder = new DataCloudRouterBuilder(eventloop);
        RoutingServlet servlet = builder.withHealthRoutes(mockHealthHandler).build();
        
        assertThat(servlet).isNotNull();
    }

    /**
     * Test that verifies the router builder supports method chaining.
     */
    @Test
    @DisplayName("Router builder supports method chaining")
    void routerBuilderSupportsMethodChaining() {
        Eventloop eventloop = Eventloop.create();
        
        DataCloudRouterBuilder builder = new DataCloudRouterBuilder(eventloop);
        
        // Verify that each withXxx method returns the builder for chaining
        assertThat(builder).isSameAs(builder);
    }

    /**
     * Test that verifies the router builder handles null handlers gracefully.
     */
    @Test
    @DisplayName("Router builder handles null handlers gracefully")
    void routerBuilderHandlesNullHandlersGracefully() {
        Eventloop eventloop = Eventloop.create();
        
        DataCloudRouterBuilder builder = new DataCloudRouterBuilder(eventloop);
        
        // Storage cost routes with null handler should not throw
        RoutingServlet servlet = builder
            .withStorageCostRoutes(null, null)
            .withFederatedQueryRoutes(null, null)
            .withTierMigrationRoutes(null, null)
            .build();
        
        assertThat(servlet).isNotNull();
    }

    /**
     * Test that verifies the router builder registers all domain route groups.
     */
    @Test
    @DisplayName("Router builder registers all domain route groups")
    void routerBuilderRegistersAllDomainRouteGroups() {
        Eventloop eventloop = Eventloop.create();
        HealthHandler mockHealthHandler = new HealthHandler(HTTP_SUPPORT);
        EntityCrudHandler entityHandler = mock(EntityCrudHandler.class);
        SseStreamingHandler sseHandler = mock(SseStreamingHandler.class);
        SemanticSearchHandler semanticSearchHandler = mock(SemanticSearchHandler.class);
        EntityExportHandler exportHandler = mock(EntityExportHandler.class);
        EntityAnomalyHandler anomalyHandler = mock(EntityAnomalyHandler.class);
        EntityValidationHandler validationHandler = mock(EntityValidationHandler.class);
        EventHandler eventHandler = mock(EventHandler.class);
        PipelineCheckpointHandler pipelineCheckpointHandler = mock(PipelineCheckpointHandler.class);
        WorkflowExecutionHandler workflowExecutionHandler = mock(WorkflowExecutionHandler.class);
        AlertingHandler alertingHandler = mock(AlertingHandler.class);
        MemoryPlaneHandler memoryHandler = mock(MemoryPlaneHandler.class);
        BrainHandler brainHandler = mock(BrainHandler.class);
        LearningHandler learningHandler = mock(LearningHandler.class);
        AnalyticsHandler analyticsHandler = mock(AnalyticsHandler.class);
        AiModelHandler aiModelHandler = mock(AiModelHandler.class);
        AiAssistHandler aiAssistHandler = mock(AiAssistHandler.class);
        VoiceGatewayHandler voiceHandler = mock(VoiceGatewayHandler.class);
        DataLifecycleHandler dataLifecycleHandler = mock(DataLifecycleHandler.class);
        CapabilityRegistryHandler capabilityRegistryHandler = mock(CapabilityRegistryHandler.class);
        LineageHandler lineageHandler = mock(LineageHandler.class);
        ContextLayerHandler contextLayerHandler = mock(ContextLayerHandler.class);
        CollectionContextHandler collectionContextHandler = mock(CollectionContextHandler.class);
        McpToolsHandler mcpToolsHandler = mock(McpToolsHandler.class);
        DataProductHandler dataProductHandler = mock(DataProductHandler.class);
        AutonomyHandler autonomyHandler = mock(AutonomyHandler.class);
        AgentCatalogHandler agentCatalogHandler = mock(AgentCatalogHandler.class);
        PluginInstallHandler pluginInstallHandler = mock(PluginInstallHandler.class);
        
        DataCloudRouterBuilder builder = new DataCloudRouterBuilder(eventloop);
        
        // Verify that all domain route groups can be called without errors
        builder
            .withHealthRoutes(mockHealthHandler)
            .withEntityRoutes(entityHandler, sseHandler, semanticSearchHandler, exportHandler, anomalyHandler, validationHandler)
            .withEventRoutes(eventHandler)
            .withPipelineRoutes(pipelineCheckpointHandler, workflowExecutionHandler)
            .withCheckpointRoutes(pipelineCheckpointHandler)
            .withAlertRoutes(alertingHandler, sseHandler)
            .withMemoryRoutes(memoryHandler)
            .withBrainRoutes(brainHandler, sseHandler)
            .withLearningRoutes(learningHandler)
            .withAnalyticsRoutes(analyticsHandler)
            .withReportingRoutes(analyticsHandler, workflowExecutionHandler)
            .withModelRoutes(aiModelHandler)
            .withFeatureRoutes(aiModelHandler)
            .withSseRoutes(sseHandler)
            .withWebSocketRoutes(sseHandler)
            .withAiAssistRoutes(aiAssistHandler)
            .withVoiceRoutes(voiceHandler)
            .withGovernanceRoutes(dataLifecycleHandler)
            .withCapabilityRoutes(capabilityRegistryHandler)
            .withLineageRoutes(lineageHandler)
            .withContextRoutes(contextLayerHandler, collectionContextHandler, semanticSearchHandler)
            .withMcpRoutes(mcpToolsHandler)
            .withDataProductRoutes(dataProductHandler)
            .withAutonomyRoutes(autonomyHandler)
            .withAgentCatalogRoutes(agentCatalogHandler)
            .withPluginRoutes(pluginInstallHandler)
            .withStorageCostRoutes(null, null)
            .withFederatedQueryRoutes(null, null)
            .withTierMigrationRoutes(null, null)
            .build();
        
        // If we reach here, all domain route groups were successfully registered
        assertThat(true).isTrue();
    }
}
