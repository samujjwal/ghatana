package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Real HTTP contract tests for canonical collections metadata routes consumed by the Data Cloud UI
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Collections UI Contract")
class DataCloudHttpServerCollectionsUiContractTest extends DataCloudHttpServerTestBase {

    private DataCloudClient client;
    private DataCloudClient.Entity ordersCollection;
    private DataCloudClient.Entity customersCollection;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        client = mock(DataCloudClient.class); // GH-90000
        port = findFreePort(); // GH-90000

        ordersCollection = collectionEntity( // GH-90000
                "orders",
                "Orders",
                "Order collection",
                42,
                "active",
                Instant.parse("2026-04-17T08:00:00Z"),
                Instant.parse("2026-04-17T09:00:00Z"));
        customersCollection = collectionEntity( // GH-90000
                "customers",
                "Customers",
                "Customer master data",
                12,
                "draft",
                Instant.parse("2026-04-16T08:00:00Z"),
                Instant.parse("2026-04-16T09:00:00Z"));
    }

    @Override
    protected void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(client, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS); // GH-90000
    }

    @Nested
    @DisplayName("GET /api/v1/entities/dc_collections")
    class ListCollectionsTests {

        @Test
        @DisplayName("returns backend entity list shape expected by collectionsApi.list")
        void returnsBackendEntityListShapeExpectedByCollectionsApi() throws Exception { // GH-90000
            when(client.query(anyString(), eq("dc_collections"), any()))
                    .thenReturn(Promise.of(List.of(ordersCollection, customersCollection))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> response = getWithHeader( // GH-90000
                    "/api/v1/entities/dc_collections?limit=50&offset=0",
                    "X-Tenant-Id",
                    TestConstants.TENANT_DEFAULT);

            assertThat(response.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
            assertThat(body).containsKeys("entities", "count"); // GH-90000
            assertThat(body.get("count")).isEqualTo(2);

            List<?> entities = (List<?>) body.get("entities");
            assertThat(entities).hasSize(2); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> firstEntity = (Map<String, Object>) entities.get(0); // GH-90000
            assertThat(firstEntity) // GH-90000
                    .containsEntry("id", "orders") // GH-90000
                    .containsEntry("collection", "dc_collections") // GH-90000
                    .containsKey("data")
                    .containsEntry("createdAt", "2026-04-17T08:00:00Z") // GH-90000
                    .containsEntry("updatedAt", "2026-04-17T09:00:00Z"); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> firstEntityData = (Map<String, Object>) firstEntity.get("data");
            assertThat(firstEntityData) // GH-90000
                    .containsEntry("name", "Orders") // GH-90000
                    .containsEntry("description", "Order collection") // GH-90000
                    .containsEntry("schemaType", "entity") // GH-90000
                    .containsEntry("status", "active") // GH-90000
                    .containsEntry("isActive", true) // GH-90000
                    .containsEntry("entityCount", 42) // GH-90000
                    .containsEntry("createdBy", "integration-test"); // GH-90000
        }
    }

    @Nested
    @DisplayName("GET /api/v1/entities/dc_collections/:id")
    class GetCollectionTests {

        @Test
        @DisplayName("returns backend entity detail shape expected by collectionsApi.get")
        void returnsBackendEntityDetailShapeExpectedByCollectionsApi() throws Exception { // GH-90000
            when(client.findById(anyString(), eq("dc_collections"), eq("orders")))
                    .thenReturn(Promise.of(Optional.of(ordersCollection))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> response = getWithHeader( // GH-90000
                    "/api/v1/entities/dc_collections/orders",
                    "X-Tenant-Id",
                    TestConstants.TENANT_DEFAULT);

            assertThat(response.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
            assertThat(body) // GH-90000
                    .containsEntry("id", "orders") // GH-90000
                    .containsEntry("collection", "dc_collections") // GH-90000
                    .containsEntry("createdAt", "2026-04-17T08:00:00Z") // GH-90000
                    .containsEntry("updatedAt", "2026-04-17T09:00:00Z"); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> entityData = (Map<String, Object>) body.get("data");
            assertThat(entityData) // GH-90000
                    .containsEntry("name", "Orders") // GH-90000
                    .containsEntry("description", "Order collection") // GH-90000
                    .containsEntry("status", "active") // GH-90000
                    .containsEntry("entityCount", 42); // GH-90000
        }
    }

    private static DataCloudClient.Entity collectionEntity( // GH-90000
            String id,
            String name,
            String description,
            int entityCount,
            String status,
            Instant createdAt,
            Instant updatedAt) {
        return new DataCloudClient.Entity( // GH-90000
                id,
                "dc_collections",
                Map.of( // GH-90000
                        "name", name,
                        "description", description,
                        "schemaType", "entity",
                        "status", status,
                        "isActive", "active".equals(status), // GH-90000
                        "entityCount", entityCount,
                        "schema", Map.of( // GH-90000
                                "fields", List.of( // GH-90000
                                        Map.of("name", "id", "type", "string", "required", true), // GH-90000
                                        Map.of("name", "tenantId", "type", "string", "required", true))), // GH-90000
                        "tags", List.of("core"),
                        "createdBy", "integration-test"),
                createdAt,
                updatedAt,
                1L);
    }
}