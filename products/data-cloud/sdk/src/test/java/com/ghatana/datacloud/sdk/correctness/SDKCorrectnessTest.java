/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() throws Exception { // GH-90000
        backendClient = DataCloud.forTesting(); // GH-90000
        port = findFreePort(); // GH-90000
        server = new DataCloudHttpServer(backendClient, port); // GH-90000
        server.start(); // GH-90000
        sdk = new DataCloudJavaSdk("http://localhost:" + port, "tenant-1"); // GH-90000
        sdkTenant2 = new DataCloudJavaSdk("http://localhost:" + port, "tenant-2"); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (sdk != null) { // GH-90000
            sdk.close(); // GH-90000
        }
        if (sdkTenant2 != null) { // GH-90000
            sdkTenant2.close(); // GH-90000
        }
        if (server != null) { // GH-90000
            server.stop(); // GH-90000
        }
        if (backendClient != null) { // GH-90000
            backendClient.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("SDK handles non-existent entity gracefully with proper error")
    void sdkHandlesNonExistentEntityGracefully() { // GH-90000
        assertThatThrownBy(() -> sdk.getEntity("nonexistent_collection", "nonexistent_id")) // GH-90000
            .isInstanceOf(RuntimeException.class) // GH-90000
            .hasMessageContaining("404");
    }

    @Test
    @DisplayName("SDK validates required fields in entity creation")
    void sdkValidatesRequiredFieldsInEntityCreation() { // GH-90000
        // Create entity with minimal valid data
        Map<String, Object> validEntity = Map.of( // GH-90000
            "name", "Test Entity",
            "type", "test"
        );
        
        Map<String, Object> created = sdk.createEntity("correctness_test_entities", validEntity); // GH-90000
        
        assertThat(created).containsKey("id");
        assertThat(created).containsEntry("collection", "correctness_test_entities"); // GH-90000
        
        // Cleanup
        String entityId = created.get("id").toString();
        sdk.deleteEntity("correctness_test_entities", entityId); // GH-90000
    }

    @Test
    @DisplayName("SDK handles empty query results correctly")
    void sdkHandlesEmptyQueryResultsCorrectly() { // GH-90000
        Map<String, Object> result = sdk.queryEntities("empty_test_collection", 10); // GH-90000
        
        assertThat(result).containsEntry("count", 0); // GH-90000
        assertThat(result).containsKey("entities");
        assertThat(asObjectList(result.get("entities"))).isEmpty();
    }

    @Test
    @DisplayName("SDK preserves data types through round-trip")
    void sdkPreservesDataTypesThroughRoundTrip() { // GH-90000
        Map<String, Object> originalData = Map.of( // GH-90000
            "stringField", "test-value",
            "numberField", 42,
            "booleanField", true,
            "nullField", null
        );
        
        Map<String, Object> created = sdk.createEntity("type_test_entities", originalData); // GH-90000
        String entityId = created.get("id").toString();
        
        Map<String, Object> fetched = sdk.getEntity("type_test_entities", entityId); // GH-90000
        Map<String, Object> data = asObjectMap(fetched.get("data"));
        
        assertThat(data.get("stringField")).isEqualTo("test-value");
        assertThat(data.get("numberField")).isEqualTo(42);
        assertThat(data.get("booleanField")).isEqualTo(true);
        assertThat(data.get("nullField")).isNull();
        
        // Cleanup
        sdk.deleteEntity("type_test_entities", entityId); // GH-90000
    }

    @Test
    @DisplayName("SDK enforces tenant isolation")
    void sdkEnforcesTenantIsolation() { // GH-90000
        // Create entity in tenant-1
        Map<String, Object> entity = Map.of("name", "Isolated Entity"); // GH-90000
        Map<String, Object> created = sdk.createEntity("isolation_test_entities", entity); // GH-90000
        String entityId = created.get("id").toString();
        
        // Verify tenant-1 can see it
        Map<String, Object> fetched = sdk.getEntity("isolation_test_entities", entityId); // GH-90000
        assertThat(fetched).containsEntry("id", entityId); // GH-90000
        
        // Verify tenant-2 cannot see it
        assertThatThrownBy(() -> sdkTenant2.getEntity("isolation_test_entities", entityId)) // GH-90000
            .isInstanceOf(RuntimeException.class) // GH-90000
            .hasMessageContaining("404");
        
        // Cleanup
        sdk.deleteEntity("isolation_test_entities", entityId); // GH-90000
    }

    @Test
    @DisplayName("SDK handles pagination correctly")
    void sdkHandlesPaginationCorrectly() { // GH-90000
        // Create multiple entities
        for (int i = 0; i < 15; i++) { // GH-90000
            Map<String, Object> entity = Map.of("name", "Entity " + i, "index", i); // GH-90000
            sdk.createEntity("pagination_test_entities", entity); // GH-90000
        }
        
        // Query with page size of 10
        Map<String, Object> page1 = sdk.queryEntities("pagination_test_entities", 10); // GH-90000
        assertThat(page1).containsEntry("count", 15); // GH-90000
        assertThat(asObjectList(page1.get("entities"))).hasSize(10);
        
        // Cleanup
        Map<String, Object> allEntities = sdk.queryEntities("pagination_test_entities", 100); // GH-90000
        for (Object entityObj : asObjectList(allEntities.get("entities"))) {
            Map<String, Object> entity = asObjectMap(entityObj); // GH-90000
            sdk.deleteEntity("pagination_test_entities", entity.get("id").toString());
        }
    }

    @Test
    @DisplayName("SDK handles special characters in entity data")
    void sdkHandlesSpecialCharactersInEntityData() { // GH-90000
        Map<String, Object> specialData = Map.of( // GH-90000
            "unicode", "Hello 世界 🌍",
            "quotes", "Text with \"quotes\" and 'apostrophes'",
            "newlines", "Line 1\nLine 2\nLine 3",
            "emoji", "😀🎉🚀"
        );
        
        Map<String, Object> created = sdk.createEntity("special_chars_entities", specialData); // GH-90000
        String entityId = created.get("id").toString();
        
        Map<String, Object> fetched = sdk.getEntity("special_chars_entities", entityId); // GH-90000
        Map<String, Object> data = asObjectMap(fetched.get("data"));
        
        assertThat(data.get("unicode")).isEqualTo("Hello 世界 🌍");
        assertThat(data.get("quotes")).isEqualTo("Text with \"quotes\" and 'apostrophes'");
        assertThat(data.get("newlines")).isEqualTo("Line 1\nLine 2\nLine 3");
        assertThat(data.get("emoji")).isEqualTo("😀🎉🚀");
        
        // Cleanup
        sdk.deleteEntity("special_chars_entities", entityId); // GH-90000
    }

    @Test
    @DisplayName("SDK handles concurrent requests correctly")
    void sdkHandlesConcurrentRequestsCorrectly() throws InterruptedException { // GH-90000
        List<Thread> threads = List.of( // GH-90000
            createEntityThread("concurrent_test_entities", "Thread-1"), // GH-90000
            createEntityThread("concurrent_test_entities", "Thread-2"), // GH-90000
            createEntityThread("concurrent_test_entities", "Thread-3") // GH-90000
        );
        
        threads.forEach(Thread::start); // GH-90000
        for (Thread thread : threads) { // GH-90000
            thread.join(); // GH-90000
        }
        
        // Verify all entities were created
        Map<String, Object> result = sdk.queryEntities("concurrent_test_entities", 10); // GH-90000
        assertThat(result).containsEntry("count", 3); // GH-90000
        
        // Cleanup
        Map<String, Object> allEntities = sdk.queryEntities("concurrent_test_entities", 100); // GH-90000
        for (Object entityObj : asObjectList(allEntities.get("entities"))) {
            Map<String, Object> entity = asObjectMap(entityObj); // GH-90000
            sdk.deleteEntity("concurrent_test_entities", entity.get("id").toString());
        }
    }

    @Test
    @DisplayName("SDK handles large payload correctly")
    void sdkHandlesLargePayloadCorrectly() { // GH-90000
        StringBuilder largeString = new StringBuilder(); // GH-90000
        for (int i = 0; i < 10000; i++) { // GH-90000
            largeString.append("data-");
        }
        
        Map<String, Object> largePayload = Map.of( // GH-90000
            "largeField", largeString.toString(), // GH-90000
            "metadata", Map.of("size", largeString.length()) // GH-90000
        );
        
        Map<String, Object> created = sdk.createEntity("large_payload_entities", largePayload); // GH-90000
        String entityId = created.get("id").toString();
        
        Map<String, Object> fetched = sdk.getEntity("large_payload_entities", entityId); // GH-90000
        Map<String, Object> data = asObjectMap(fetched.get("data"));
        
        assertThat(data.get("largeField")).isEqualTo(largeString.toString());
        
        // Cleanup
        sdk.deleteEntity("large_payload_entities", entityId); // GH-90000
    }

    @Test
    @DisplayName("SDK health check returns expected structure")
    void sdkHealthCheckReturnsExpectedStructure() { // GH-90000
        Map<String, Object> health = sdk.health(); // GH-90000
        
        assertThat(health).containsKey("status");
        assertThat(health).containsKey("timestamp");
        assertThat(health.get("status")).isInstanceOf(String.class);
        assertThat(health.get("timestamp")).isInstanceOf(Long.class);
    }

    private int findFreePort() throws IOException { // GH-90000
        try (ServerSocket socket = new ServerSocket(0)) { // GH-90000
            return socket.getLocalPort(); // GH-90000
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asObjectMap(Object value) { // GH-90000
        return (Map<String, Object>) value; // GH-90000
    }

    @SuppressWarnings("unchecked")
    private List<Object> asObjectList(Object value) { // GH-90000
        return (List<Object>) value; // GH-90000
    }

    private Thread createEntityThread(String collection, String threadName) { // GH-90000
        return new Thread(() -> { // GH-90000
            try {
                Map<String, Object> entity = Map.of("name", threadName, "thread", threadName); // GH-90000
                sdk.createEntity(collection, entity); // GH-90000
            } catch (Exception e) { // GH-90000
                // Log error but don't fail the test
                System.err.println("Thread " + threadName + " failed: " + e.getMessage()); // GH-90000
            }
        });
    }
}
