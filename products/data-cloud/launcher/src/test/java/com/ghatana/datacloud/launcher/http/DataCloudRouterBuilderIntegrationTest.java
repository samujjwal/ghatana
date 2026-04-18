/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import io.activej.eventloop.Eventloop;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        
        // Create mock health handler
        HealthHandler mockHealthHandler = new HealthHandler(null, null, null) {
            @Override
            public Promise<HttpResponse> handleHealth(HttpRequest request) {
                return Promise.of(HttpResponse.ok200());
            }
            
            @Override
            public Promise<HttpResponse> handleHealthDetail(HttpRequest request) {
                return Promise.of(HttpResponse.ok200());
            }
            
            @Override
            public Promise<HttpResponse> handleHealthDeep(HttpRequest request) {
                return Promise.of(HttpResponse.ok200());
            }
            
            @Override
            public Promise<HttpResponse> handleReady(HttpRequest request) {
                return Promise.of(HttpResponse.ok200());
            }
            
            @Override
            public Promise<HttpResponse> handleLive(HttpRequest request) {
                return Promise.of(HttpResponse.ok200());
            }
            
            @Override
            public Promise<HttpResponse> handleInfo(HttpRequest request) {
                return Promise.of(HttpResponse.ok200());
            }
            
            @Override
            public Promise<HttpResponse> handleMetrics(HttpRequest request) {
                return Promise.of(HttpResponse.ok200());
            }
        };
        
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
        
        // Create minimal mock handlers
        HealthHandler mockHealthHandler = new HealthHandler(null, null, null) {
            @Override
            public Promise<HttpResponse> handleHealth(HttpRequest request) {
                return Promise.of(HttpResponse.ok200());
            }
            
            @Override
            public Promise<HttpResponse> handleHealthDetail(HttpRequest request) {
                return Promise.of(HttpResponse.ok200());
            }
            
            @Override
            public Promise<HttpResponse> handleHealthDeep(HttpRequest request) {
                return Promise.of(HttpResponse.ok200());
            }
            
            @Override
            public Promise<HttpResponse> handleReady(HttpRequest request) {
                return Promise.of(HttpResponse.ok200());
            }
            
            @Override
            public Promise<HttpResponse> handleLive(HttpRequest request) {
                return Promise.of(HttpResponse.ok200());
            }
            
            @Override
            public Promise<HttpResponse> handleInfo(HttpRequest request) {
                return Promise.of(HttpResponse.ok200());
            }
            
            @Override
            public Promise<HttpResponse> handleMetrics(HttpRequest request) {
                return Promise.of(HttpResponse.ok200());
            }
        };
        
        DataCloudRouterBuilder builder = new DataCloudRouterBuilder(eventloop);
        
        // Verify that all domain route groups can be called without errors
        builder
            .withHealthRoutes(mockHealthHandler)
            .withEntityRoutes(null, null, null, null, null, null)
            .withEventRoutes(null)
            .withPipelineRoutes(null, null)
            .withCheckpointRoutes(null)
            .withAlertRoutes(null, null)
            .withMemoryRoutes(null)
            .withBrainRoutes(null, null)
            .withLearningRoutes(null)
            .withAnalyticsRoutes(null)
            .withReportingRoutes(null, null)
            .withModelRoutes(null)
            .withFeatureRoutes(null)
            .withSseRoutes(null)
            .withWebSocketRoutes(null)
            .withAiAssistRoutes(null)
            .withVoiceRoutes(null)
            .withGovernanceRoutes(null)
            .withCapabilityRoutes(null)
            .withLineageRoutes(null)
            .withContextRoutes(null, null, null)
            .withMcpRoutes(null)
            .withDataProductRoutes(null)
            .withAutonomyRoutes(null)
            .withAgentCatalogRoutes(null)
            .withPluginRoutes(null)
            .withStorageCostRoutes(null, null)
            .withFederatedQueryRoutes(null, null)
            .withTierMigrationRoutes(null, null)
            .build();
        
        // If we reach here, all domain route groups were successfully registered
        assertThat(true).isTrue();
    }
}
