/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.test.fixture;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Mock fixtures for DataCloudClient with lenient stubbing pattern.
 *
 * <p>Provides pre-configured mock behaviors for common DataCloudClient operations.
 * Uses {@code lenient().when()} to avoid UnnecessaryStubbingException when not all
 * stubs are used in every test.
 *
 * <p><strong>Usage:</strong>
 * <pre>
 * {@code
 * @Mock DataCloudClient mockClient;
 *
 * @BeforeEach
 * void setUp() {
 *     DataCloudClientFixtures.configureDefaultStubs(mockClient);
 *     // Or configure specific stubs:
 *     DataCloudClientFixtures.configureEntityStubs(mockClient, "products", testEntities);
 * }
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Mock fixtures for DataCloudClient with lenient stub pattern
 * @doc.layer product
 * @doc.pattern Test Fixture, Mock Configuration
 */
public final class DataCloudClientFixtures {

    private DataCloudClientFixtures() {}

    /**
     * Configure default stubs that return empty/success responses.
     * Safe to use when you don't need specific return values.
     *
     * @param mockClient mocked DataCloudClient
     */
    public static void configureDefaultStubs(DataCloudClient mockClient) {
        // Entity operations
        lenient().when(mockClient.get(anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Map.of()));
        
        lenient().when(mockClient.query(anyString(), anyString(), any()))
            .thenReturn(Promise.of(List.of()));
        
        lenient().when(mockClient.create(anyString(), anyString(), any()))
            .thenReturn(Promise.of(Map.of("id", "generated-id", "created", true)));
        
        lenient().when(mockClient.update(anyString(), anyString(), anyString(), any()))
            .thenReturn(Promise.of(Map.of("updated", true)));
        
        lenient().when(mockClient.delete(anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Map.of("deleted", true)));

        // Collection operations
        lenient().when(mockClient.listCollections(anyString()))
            .thenReturn(Promise.of(List.of()));
        
        lenient().when(mockClient.createCollection(anyString(), any()))
            .thenReturn(Promise.of(Map.of("name", "test-collection", "created", true)));

        // Event operations
        lenient().when(mockClient.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(Map.of("offset", 1L, "appended", true)));
        
        lenient().when(mockClient.queryEvents(anyString(), anyString(), anyInt(), any()))
            .thenReturn(Promise.of(List.of()));

        // Pipeline operations
        lenient().when(mockClient.getPipeline(anyString(), anyString()))
            .thenReturn(Promise.of(Map.of()));
        
        lenient().when(mockClient.listPipelines(anyString()))
            .thenReturn(Promise.of(List.of()));

        // Health check
        lenient().when(mockClient.health())
            .thenReturn(Promise.of(Map.of("status", "healthy")));
    }

    /**
     * Configure entity stubs with specific return values.
     *
     * @param mockClient mocked DataCloudClient
     * @param collection collection name
     * @param entities list of entities to return from query
     */
    public static void configureEntityStubs(
            DataCloudClient mockClient,
            String collection,
            List<Map<String, Object>> entities) {
        
        lenient().when(mockClient.query(anyString(), anyString(), any()))
            .thenReturn(Promise.of(entities));

        // Return first entity for get by ID
        if (!entities.isEmpty()) {
            Map<String, Object> firstEntity = entities.get(0);
            String entityId = (String) firstEntity.getOrDefault("id", "test-id");
            
            lenient().when(mockClient.get(anyString(), anyString(), anyString()))
                .thenReturn(Promise.of(firstEntity));
        }
    }

    /**
     * Configure entity stubs with single entity.
     *
     * @param mockClient mocked DataCloudClient
     * @param collection collection name
     * @param entity entity to return
     */
    public static void configureEntityStub(
            DataCloudClient mockClient,
            String collection,
            Map<String, Object> entity) {
        
        configureEntityStubs(mockClient, collection, List.of(entity));
    }

    /**
     * Configure collection stubs.
     *
     * @param mockClient mocked DataCloudClient
     * @param collections list of collections to return
     */
    public static void configureCollectionStubs(
            DataCloudClient mockClient,
            List<Map<String, Object>> collections) {
        
        lenient().when(mockClient.listCollections(anyString()))
            .thenReturn(Promise.of(collections));
    }

    /**
     * Configure event stubs.
     *
     * @param mockClient mocked DataCloudClient
     * @param events list of events to return from query
     */
    public static void configureEventStubs(
            DataCloudClient mockClient,
            List<Map<String, Object>> events) {
        
        lenient().when(mockClient.queryEvents(anyString(), anyString(), anyInt(), any()))
            .thenReturn(Promise.of(events));
    }

    /**
     * Configure pipeline stubs.
     *
     * @param mockClient mocked DataCloudClient
     * @param pipelines list of pipelines to return
     */
    public static void configurePipelineStubs(
            DataCloudClient mockClient,
            List<Map<String, Object>> pipelines) {
        
        lenient().when(mockClient.listPipelines(anyString()))
            .thenReturn(Promise.of(pipelines));
    }

    /**
     * Configure error responses for testing failure paths.
     *
     * @param mockClient mocked DataCloudClient
     * @param errorMessage error message to return
     */
    public static void configureErrorStubs(
            DataCloudClient mockClient,
            String errorMessage) {
        
        RuntimeException exception = new RuntimeException(errorMessage);
        
        lenient().when(mockClient.get(anyString(), anyString(), anyString()))
            .thenReturn(Promise.ofException(exception));
        
        lenient().when(mockClient.query(anyString(), anyString(), any()))
            .thenReturn(Promise.ofException(exception));
        
        lenient().when(mockClient.create(anyString(), anyString(), any()))
            .thenReturn(Promise.ofException(exception));
        
        lenient().when(mockClient.update(anyString(), anyString(), anyString(), any()))
            .thenReturn(Promise.ofException(exception));
    }

    /**
     * Configure tenant isolation - return empty for other tenants.
     *
     * @param mockClient mocked DataCloudClient
     * @param allowedTenant tenant that has data
     * @param entities entities for allowed tenant
     */
    public static void configureTenantIsolation(
            DataCloudClient mockClient,
            String allowedTenant,
            List<Map<String, Object>> entities) {
        
        lenient().when(mockClient.query(any(), any(), any()))
            .thenAnswer(invocation -> {
                String tenantId = invocation.getArgument(0);
                if (allowedTenant.equals(tenantId)) {
                    return Promise.of(entities);
                }
                return Promise.of(List.of()); // Empty for other tenants
            });
        
        lenient().when(mockClient.get(any(), any(), any()))
            .thenAnswer(invocation -> {
                String tenantId = invocation.getArgument(0);
                if (allowedTenant.equals(tenantId) && !entities.isEmpty()) {
                    return Promise.of(entities.get(0));
                }
                return Promise.ofException(new RuntimeException("Not found"));
            });
    }
}
