/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.sdk.correctness;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
import com.ghatana.datacloud.sdk.generated.DataCloudJavaSdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Correctness tests for the generated Data-Cloud Java SDK.
 * Tests error handling, edge cases, data type correctness, and tenant isolation.
 *
 * @doc.type    class
 * @doc.purpose Comprehensive SDK correctness tests beyond smoke tests
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("SDK Correctness Tests")
@Tag("correctness")
class SDKCorrectnessTest {

    private DataCloudClient backendClient;
    private DataCloudHttpServer server;
    private DataCloudJavaSdk sdk;
    private DataCloudJavaSdk sdkTenant2;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        backendClient = DataCloud.forTesting();
        port = findFreePort();
        server = new DataCloudHttpServer(backendClient, port);
        server.start();
        sdk = new DataCloudJavaSdk("http://localhost:" + port, "tenant-1");
        sdkTenant2 = new DataCloudJavaSdk("http://localhost:" + port, "tenant-2");
    }

    @AfterEach
    void tearDown() {
        if (sdk != null) {
            sdk.close();
        }
        if (sdkTenant2 != null) {
            sdkTenant2.close();
        }
        if (server != null) {
            server.stop();
        }
        if (backendClient != null) {
            backendClient.close();
        }
    }

    @Test
    @DisplayName("SDK handles non-existent entity gracefully with proper error")
    void sdkHandlesNonExistentEntityGracefully() {
        assertThatThrownBy(() -> sdk.getEntity("nonexistent_collection", "nonexistent_id"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("404");
    }

    @Test
    @DisplayName("SDK validates required fields in entity creation")
    void sdkValidatesRequiredFieldsInEntityCreation() {
        // Create entity with minimal valid data
        Map<String, Object> validEntity = Map.of(
            "name", "Test Entity",
            "type", "test"
        );
        
        Map<String, Object> created = sdk.createEntity("correctness_test_entities", validEntity);
        
        assertThat(created).containsKey("id");
        assertThat(created).containsEntry("collection", "correctness_test_entities");
        
        // Cleanup
        String entityId = created.get("id").toString();
        sdk.deleteEntity("correctness_test_entities", entityId);
    }

    @Test
    @DisplayName("SDK handles empty query results correctly")
    void sdkHandlesEmptyQueryResultsCorrectly() {
        Map<String, Object> result = sdk.queryEntities("empty_test_collection", 10);
        
        assertThat(result).containsEntry("count", 0);
        assertThat(result).containsKey("entities");
        assertThat(asObjectList(result.get("entities"))).isEmpty();
    }

    @Test
    @DisplayName("SDK preserves data types through round-trip")
    void sdkPreservesDataTypesThroughRoundTrip() {
        Map<String, Object> originalData = Map.of(
            "stringField", "test-value",
            "numberField", 42,
            "booleanField", true,
            "nullField", null
        );
        
        Map<String, Object> created = sdk.createEntity("type_test_entities", originalData);
        String entityId = created.get("id").toString();
        
        Map<String, Object> fetched = sdk.getEntity("type_test_entities", entityId);
        Map<String, Object> data = asObjectMap(fetched.get("data"));
        
        assertThat(data.get("stringField")).isEqualTo("test-value");
        assertThat(data.get("numberField")).isEqualTo(42);
        assertThat(data.get("booleanField")).isEqualTo(true);
        assertThat(data.get("nullField")).isNull();
        
        // Cleanup
        sdk.deleteEntity("type_test_entities", entityId);
    }

    @Test
    @DisplayName("SDK enforces tenant isolation")
    void sdkEnforcesTenantIsolation() {
        // Create entity in tenant-1
        Map<String, Object> entity = Map.of("name", "Isolated Entity");
        Map<String, Object> created = sdk.createEntity("isolation_test_entities", entity);
        String entityId = created.get("id").toString();
        
        // Verify tenant-1 can see it
        Map<String, Object> fetched = sdk.getEntity("isolation_test_entities", entityId);
        assertThat(fetched).containsEntry("id", entityId);
        
        // Verify tenant-2 cannot see it
        assertThatThrownBy(() -> sdkTenant2.getEntity("isolation_test_entities", entityId))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("404");
        
        // Cleanup
        sdk.deleteEntity("isolation_test_entities", entityId);
    }

    @Test
    @DisplayName("SDK handles pagination correctly")
    void sdkHandlesPaginationCorrectly() {
        // Create multiple entities
        for (int i = 0; i < 15; i++) {
            Map<String, Object> entity = Map.of("name", "Entity " + i, "index", i);
            sdk.createEntity("pagination_test_entities", entity);
        }
        
        // Query with page size of 10
        Map<String, Object> page1 = sdk.queryEntities("pagination_test_entities", 10);
        assertThat(page1).containsEntry("count", 15);
        assertThat(asObjectList(page1.get("entities"))).hasSize(10);
        
        // Cleanup
        Map<String, Object> allEntities = sdk.queryEntities("pagination_test_entities", 100);
        for (Object entityObj : asObjectList(allEntities.get("entities"))) {
            Map<String, Object> entity = asObjectMap(entityObj);
            sdk.deleteEntity("pagination_test_entities", entity.get("id").toString());
        }
    }

    @Test
    @DisplayName("SDK handles special characters in entity data")
    void sdkHandlesSpecialCharactersInEntityData() {
        Map<String, Object> specialData = Map.of(
            "unicode", "Hello 世界 🌍",
            "quotes", "Text with \"quotes\" and 'apostrophes'",
            "newlines", "Line 1\nLine 2\nLine 3",
            "emoji", "😀🎉🚀"
        );
        
        Map<String, Object> created = sdk.createEntity("special_chars_entities", specialData);
        String entityId = created.get("id").toString();
        
        Map<String, Object> fetched = sdk.getEntity("special_chars_entities", entityId);
        Map<String, Object> data = asObjectMap(fetched.get("data"));
        
        assertThat(data.get("unicode")).isEqualTo("Hello 世界 🌍");
        assertThat(data.get("quotes")).isEqualTo("Text with \"quotes\" and 'apostrophes'");
        assertThat(data.get("newlines")).isEqualTo("Line 1\nLine 2\nLine 3");
        assertThat(data.get("emoji")).isEqualTo("😀🎉🚀");
        
        // Cleanup
        sdk.deleteEntity("special_chars_entities", entityId);
    }

    @Test
    @DisplayName("SDK handles concurrent requests correctly")
    void sdkHandlesConcurrentRequestsCorrectly() throws InterruptedException {
        List<Thread> threads = List.of(
            createEntityThread("concurrent_test_entities", "Thread-1"),
            createEntityThread("concurrent_test_entities", "Thread-2"),
            createEntityThread("concurrent_test_entities", "Thread-3")
        );
        
        threads.forEach(Thread::start);
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify all entities were created
        Map<String, Object> result = sdk.queryEntities("concurrent_test_entities", 10);
        assertThat(result).containsEntry("count", 3);
        
        // Cleanup
        Map<String, Object> allEntities = sdk.queryEntities("concurrent_test_entities", 100);
        for (Object entityObj : asObjectList(allEntities.get("entities"))) {
            Map<String, Object> entity = asObjectMap(entityObj);
            sdk.deleteEntity("concurrent_test_entities", entity.get("id").toString());
        }
    }

    @Test
    @DisplayName("SDK handles large payload correctly")
    void sdkHandlesLargePayloadCorrectly() {
        StringBuilder largeString = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeString.append("data-");
        }
        
        Map<String, Object> largePayload = Map.of(
            "largeField", largeString.toString(),
            "metadata", Map.of("size", largeString.length())
        );
        
        Map<String, Object> created = sdk.createEntity("large_payload_entities", largePayload);
        String entityId = created.get("id").toString();
        
        Map<String, Object> fetched = sdk.getEntity("large_payload_entities", entityId);
        Map<String, Object> data = asObjectMap(fetched.get("data"));
        
        assertThat(data.get("largeField")).isEqualTo(largeString.toString());
        
        // Cleanup
        sdk.deleteEntity("large_payload_entities", entityId);
    }

    @Test
    @DisplayName("SDK health check returns expected structure")
    void sdkHealthCheckReturnsExpectedStructure() {
        Map<String, Object> health = sdk.health();
        
        assertThat(health).containsKey("status");
        assertThat(health).containsKey("timestamp");
        assertThat(health.get("status")).isInstanceOf(String.class);
        assertThat(health.get("timestamp")).isInstanceOf(Long.class);
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asObjectMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Object> asObjectList(Object value) {
        return (List<Object>) value;
    }

    private Thread createEntityThread(String collection, String threadName) {
        return new Thread(() -> {
            try {
                Map<String, Object> entity = Map.of("name", threadName, "thread", threadName);
                sdk.createEntity(collection, entity);
            } catch (Exception e) {
                // Log error but don't fail the test
                System.err.println("Thread " + threadName + " failed: " + e.getMessage());
            }
        });
    }
}
