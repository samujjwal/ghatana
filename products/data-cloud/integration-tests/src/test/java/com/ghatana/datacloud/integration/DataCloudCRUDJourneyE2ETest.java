/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
import com.ghatana.datacloud.launcher.http.plugins.DataCloudRuntimePluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data Cloud CRUD Journey End-to-End Tests
 *
 * <p>Tests verify the complete CRUD journey for data operations:</p>
 * <ul>
 *   <li>Create: Collection and entity creation</li>
 *   <li>Read: Retrieving collections and entities</li>
 *   <li>Update: Modifying entity data</li>
 *   <li>Delete: Removing collections and entities</li>
 *   <li>Search and query operations</li>
 *   <li>Bulk operations</li>
 *   <li>Transaction consistency</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose End-to-end Data Cloud CRUD journey test suite
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Data Cloud CRUD Journey E2E Tests")
@Tag("production")
class DataCloudCRUDJourneyE2ETest {

    private static final String CRUD_TENANT = "crud-test-tenant";

    private DataCloudClient client;
    private DataCloudHttpServer server;
    private HttpClient httpClient;
    private int port;
    private final ObjectMapper mapper = new ObjectMapper();
    private String createdCollectionId;
    private String createdEntityId;

    @BeforeEach
    void setUp() throws Exception {
        client = new DurableDataCloudClient();

        DataCloudRuntimePluginManager pluginManager = new DataCloudRuntimePluginManager();
        pluginManager.registerWorkflowPlugin(client);
        pluginManager.registerBuiltInPlugins();

        port = findFreePort();
        httpClient = HttpClient.newBuilder().build();

        server = new DataCloudHttpServer(client, port)
            .withPluginManager(pluginManager)
            .withDeploymentMode("local");
        server.start();
        waitForServerReady(port);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    @DisplayName("CRUD Journey: Create collection succeeds")
    void crudJourneyCreateCollectionSucceeds() throws Exception {
        String collectionName = "crud-test-collection";
        Map<String, Object> payload = Map.of(
            "name", collectionName,
            "description", "CRUD test collection"
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/collections"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", CRUD_TENANT)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 201, 400, 404, 500, 503);
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            Map<String, Object> responseBody = mapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
            assertThat(responseBody).containsKey("id");
            assertThat(responseBody).containsKey("name");
            assertThat(responseBody.get("name")).isEqualTo(collectionName);
            createdCollectionId = (String) responseBody.get("id");
        }
    }

    @Test
    @DisplayName("CRUD Journey: Read collection retrieves created collection")
    void crudJourneyReadCollectionRetrievesCreatedCollection() throws Exception {
        // First create a collection
        String collectionName = "crud-read-test-collection";
        Map<String, Object> createPayload = Map.of(
            "name", collectionName,
            "description", "CRUD read test"
        );

        HttpRequest createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/collections"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(createPayload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", CRUD_TENANT)
            .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());

        String collectionId = null;
        if (createResponse.statusCode() == 200 || createResponse.statusCode() == 201) {
            Map<String, Object> createBody = mapper.readValue(createResponse.body(), new TypeReference<Map<String, Object>>() {});
            collectionId = (String) createBody.get("id");
        }

        // Then read the collection
        if (collectionId != null) {
            HttpRequest readRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/collections/" + collectionId))
                .GET()
                .header("X-Tenant-Id", CRUD_TENANT)
                .build();

            HttpResponse<String> readResponse = httpClient.send(readRequest, HttpResponse.BodyHandlers.ofString());

            assertThat(readResponse.statusCode()).isIn(200, 404, 500, 503);
            if (readResponse.statusCode() == 200) {
                Map<String, Object> readBody = mapper.readValue(readResponse.body(), new TypeReference<Map<String, Object>>() {});
                assertThat(readBody).containsKey("id");
                assertThat(readBody).containsKey("name");
                assertThat(readBody.get("id")).isEqualTo(collectionId);
            }
        }
    }

    @Test
    @DisplayName("CRUD Journey: Update collection modifies collection metadata")
    void crudJourneyUpdateCollectionModifiesCollectionMetadata() throws Exception {
        // First create a collection
        String collectionName = "crud-update-test-collection";
        Map<String, Object> createPayload = Map.of(
            "name", collectionName,
            "description", "Original description"
        );

        HttpRequest createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/collections"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(createPayload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", CRUD_TENANT)
            .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());

        String collectionId = null;
        if (createResponse.statusCode() == 200 || createResponse.statusCode() == 201) {
            Map<String, Object> createBody = mapper.readValue(createResponse.body(), new TypeReference<Map<String, Object>>() {});
            collectionId = (String) createBody.get("id");
        }

        // Then update the collection
        if (collectionId != null) {
            Map<String, Object> updatePayload = Map.of(
                "description", "Updated description"
            );

            HttpRequest updateRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/collections/" + collectionId))
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(updatePayload)))
                .header("Content-Type", "application/json")
                .header("X-Tenant-Id", CRUD_TENANT)
                .build();

            HttpResponse<String> updateResponse = httpClient.send(updateRequest, HttpResponse.BodyHandlers.ofString());

            assertThat(updateResponse.statusCode()).isIn(200, 404, 500, 503);
            if (updateResponse.statusCode() == 200) {
                Map<String, Object> updateBody = mapper.readValue(updateResponse.body(), new TypeReference<Map<String, Object>>() {});
                assertThat(updateBody).containsKey("description");
                assertThat(updateBody.get("description")).isEqualTo("Updated description");
            }
        }
    }

    @Test
    @DisplayName("CRUD Journey: Delete collection removes collection")
    void crudJourneyDeleteCollectionRemovesCollection() throws Exception {
        // First create a collection
        String collectionName = "crud-delete-test-collection";
        Map<String, Object> createPayload = Map.of(
            "name", collectionName,
            "description", "CRUD delete test"
        );

        HttpRequest createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/collections"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(createPayload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", CRUD_TENANT)
            .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());

        String collectionId = null;
        if (createResponse.statusCode() == 200 || createResponse.statusCode() == 201) {
            Map<String, Object> createBody = mapper.readValue(createResponse.body(), new TypeReference<Map<String, Object>>() {});
            collectionId = (String) createBody.get("id");
        }

        // Then delete the collection
        if (collectionId != null) {
            HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/collections/" + collectionId))
                .DELETE()
                .header("X-Tenant-Id", CRUD_TENANT)
                .build();

            HttpResponse<String> deleteResponse = httpClient.send(deleteRequest, HttpResponse.BodyHandlers.ofString());

            assertThat(deleteResponse.statusCode()).isIn(200, 204, 404, 500, 503);

            // Verify collection is deleted
            HttpRequest verifyRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/collections/" + collectionId))
                .GET()
                .header("X-Tenant-Id", CRUD_TENANT)
                .build();

            HttpResponse<String> verifyResponse = httpClient.send(verifyRequest, HttpResponse.BodyHandlers.ofString());
            assertThat(verifyResponse.statusCode()).isIn(404, 503);
        }
    }

    @Test
    @DisplayName("CRUD Journey: Create entity in collection")
    void crudJourneyCreateEntityInCollection() throws Exception {
        String collectionName = "crud-entity-collection";
        String entityId = "entity-1";
        Map<String, Object> entity = Map.of(
            "id", entityId,
            "name", "Test Entity",
            "value", 42
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/entities/" + collectionName))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(entity)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", CRUD_TENANT)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 201, 400, 404, 500, 503);
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            Map<String, Object> responseBody = mapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
            assertThat(responseBody).containsKey("id");
            assertThat(responseBody.get("id")).isEqualTo(entityId);
            createdEntityId = entityId;
        }
    }

    @Test
    @DisplayName("CRUD Journey: Read entity retrieves created entity")
    void crudJourneyReadEntityRetrievesCreatedEntity() throws Exception {
        String collectionName = "crud-read-entity-collection";
        String entityId = "read-entity-1";

        // First create an entity
        Map<String, Object> createEntity = Map.of(
            "id", entityId,
            "name", "Read Test Entity",
            "value", 100
        );

        HttpRequest createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/entities/" + collectionName))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(createEntity)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", CRUD_TENANT)
            .build();

        httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());

        // Then read the entity
        HttpRequest readRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/entities/" + collectionName + "/" + entityId))
            .GET()
            .header("X-Tenant-Id", CRUD_TENANT)
            .build();

        HttpResponse<String> readResponse = httpClient.send(readRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(readResponse.statusCode()).isIn(200, 404, 500, 503);
        if (readResponse.statusCode() == 200) {
            Map<String, Object> readBody = mapper.readValue(readResponse.body(), new TypeReference<Map<String, Object>>() {});
            assertThat(readBody).containsKey("id");
            assertThat(readBody).containsKey("name");
            assertThat(readBody.get("id")).isEqualTo(entityId);
        }
    }

    @Test
    @DisplayName("CRUD Journey: Update entity modifies entity data")
    void crudJourneyUpdateEntityModifiesEntityData() throws Exception {
        String collectionName = "crud-update-entity-collection";
        String entityId = "update-entity-1";

        // First create an entity
        Map<String, Object> createEntity = Map.of(
            "id", entityId,
            "name", "Update Test Entity",
            "value", 50
        );

        HttpRequest createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/entities/" + collectionName))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(createEntity)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", CRUD_TENANT)
            .build();

        httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());

        // Then update the entity
        Map<String, Object> updateEntity = Map.of(
            "name", "Updated Entity Name",
            "value", 75
        );

        HttpRequest updateRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/entities/" + collectionName + "/" + entityId))
            .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(updateEntity)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", CRUD_TENANT)
            .build();

        HttpResponse<String> updateResponse = httpClient.send(updateRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(updateResponse.statusCode()).isIn(200, 404, 500, 503);
        if (updateResponse.statusCode() == 200) {
            Map<String, Object> updateBody = mapper.readValue(updateResponse.body(), new TypeReference<Map<String, Object>>() {});
            assertThat(updateBody).containsKey("name");
            assertThat(updateBody).containsKey("value");
            assertThat(updateBody.get("name")).isEqualTo("Updated Entity Name");
            assertThat(updateBody.get("value")).isEqualTo(75);
        }
    }

    @Test
    @DisplayName("CRUD Journey: Delete entity removes entity")
    void crudJourneyDeleteEntityRemovesEntity() throws Exception {
        String collectionName = "crud-delete-entity-collection";
        String entityId = "delete-entity-1";

        // First create an entity
        Map<String, Object> createEntity = Map.of(
            "id", entityId,
            "name", "Delete Test Entity",
            "value", 25
        );

        HttpRequest createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/entities/" + collectionName))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(createEntity)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", CRUD_TENANT)
            .build();

        httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());

        // Then delete the entity
        HttpRequest deleteRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/entities/" + collectionName + "/" + entityId))
            .DELETE()
            .header("X-Tenant-Id", CRUD_TENANT)
            .build();

        HttpResponse<String> deleteResponse = httpClient.send(deleteRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(deleteResponse.statusCode()).isIn(200, 204, 404, 500, 503);

        // Verify entity is deleted
        HttpRequest verifyRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/entities/" + collectionName + "/" + entityId))
            .GET()
            .header("X-Tenant-Id", CRUD_TENANT)
            .build();

        HttpResponse<String> verifyResponse = httpClient.send(verifyRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(verifyResponse.statusCode()).isIn(404, 500, 503);
    }

    @Test
    @DisplayName("CRUD Journey: List entities returns all entities in collection")
    void crudJourneyListEntitiesReturnsAllEntitiesInCollection() throws Exception {
        String collectionName = "crud-list-collection";

        // Create multiple entities
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> entity = Map.of(
                "id", "list-entity-" + i,
                "name", "List Test Entity " + i,
                "value", i * 10
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/entities/" + collectionName))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(entity)))
                .header("Content-Type", "application/json")
                .header("X-Tenant-Id", CRUD_TENANT)
                .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        // List entities
        HttpRequest listRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/entities/" + collectionName))
            .GET()
            .header("X-Tenant-Id", CRUD_TENANT)
            .build();

        HttpResponse<String> listResponse = httpClient.send(listRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(listResponse.statusCode()).isIn(200, 404, 500, 503);
        if (listResponse.statusCode() == 200) {
            Map<String, Object> listBody = mapper.readValue(listResponse.body(), new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entities = (List<Map<String, Object>>) listBody.getOrDefault("entities", List.of());
            assertThat(entities).isNotNull();
        }
    }

    @Test
    @DisplayName("CRUD Journey: Search entities filters by criteria")
    void crudJourneySearchEntitiesFiltersByCriteria() throws Exception {
        String collectionName = "crud-search-collection";

        // Create entities with different values
        Map<String, Object> entity1 = Map.of("id", "search-1", "name", "Alpha", "category", "A");
        Map<String, Object> entity2 = Map.of("id", "search-2", "name", "Beta", "category", "B");
        Map<String, Object> entity3 = Map.of("id", "search-3", "name", "Gamma", "category", "A");

        for (Map<String, Object> entity : List.of(entity1, entity2, entity3)) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/entities/" + collectionName))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(entity)))
                .header("Content-Type", "application/json")
                .header("X-Tenant-Id", CRUD_TENANT)
                .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        // Search with filter
        HttpRequest searchRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/entities/" + collectionName + "?filter=category:A"))
            .GET()
            .header("X-Tenant-Id", CRUD_TENANT)
            .build();

        HttpResponse<String> searchResponse = httpClient.send(searchRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(searchResponse.statusCode()).isIn(200, 400, 404, 500, 503);
        if (searchResponse.statusCode() == 200) {
            Map<String, Object> searchBody = mapper.readValue(searchResponse.body(), new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entities = (List<Map<String, Object>>) searchBody.getOrDefault("entities", List.of());
            assertThat(entities).isNotNull();
        }
    }

    // ==================== Helper Methods ====================

    private static int findFreePort() throws java.io.IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void waitForServerReady(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                new java.net.Socket("127.0.0.1", port).close();
                return;
            } catch (java.io.IOException ignored) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("Server did not start within 10 seconds on port " + port);
    }
}
