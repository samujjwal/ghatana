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
    void setUp() throws Exception {
        client = mock(DataCloudClient.class);
        port = findFreePort();

        ordersCollection = collectionEntity(
                "orders",
                "Orders",
                "Order collection",
                42,
                "active",
                Instant.parse("2026-04-17T08:00:00Z"),
                Instant.parse("2026-04-17T09:00:00Z"));
        customersCollection = collectionEntity(
                "customers",
                "Customers",
                "Customer master data",
                12,
                "draft",
                Instant.parse("2026-04-16T08:00:00Z"),
                Instant.parse("2026-04-16T09:00:00Z"));
    }

    @Override
    protected void startServer() throws Exception {
        server = new DataCloudHttpServer(client, port);
        server.start();
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS);
    }

    @Nested
    @DisplayName("GET /api/v1/entities/dc_collections")
    class ListCollectionsTests {

        @Test
        @DisplayName("returns backend entity list shape expected by collectionsApi.list")
        void returnsBackendEntityListShapeExpectedByCollectionsApi() throws Exception {
            when(client.query(anyString(), eq("dc_collections"), any()))
                    .thenReturn(Promise.of(List.of(ordersCollection, customersCollection)));

            startServer();

            HttpResponse<String> response = getWithHeader(
                    "/api/v1/entities/dc_collections?limit=50&offset=0",
                    "X-Tenant-Id",
                    TestConstants.TENANT_DEFAULT);

            assertThat(response.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            assertThat(body).containsKeys("entities", "count");
            assertThat(body.get("count")).isEqualTo(2);

            List<?> entities = (List<?>) body.get("entities");
            assertThat(entities).hasSize(2);
            @SuppressWarnings("unchecked")
            Map<String, Object> firstEntity = (Map<String, Object>) entities.get(0);
            assertThat(firstEntity)
                    .containsEntry("id", "orders")
                    .containsEntry("collection", "dc_collections")
                    .containsKey("data")
                    .containsEntry("createdAt", "2026-04-17T08:00:00Z")
                    .containsEntry("updatedAt", "2026-04-17T09:00:00Z");
            @SuppressWarnings("unchecked")
            Map<String, Object> firstEntityData = (Map<String, Object>) firstEntity.get("data");
            assertThat(firstEntityData)
                    .containsEntry("name", "Orders")
                    .containsEntry("description", "Order collection")
                    .containsEntry("schemaType", "entity")
                    .containsEntry("status", "active")
                    .containsEntry("isActive", true)
                    .containsEntry("entityCount", 42)
                    .containsEntry("createdBy", "integration-test");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/entities/dc_collections/:id")
    class GetCollectionTests {

        @Test
        @DisplayName("returns backend entity detail shape expected by collectionsApi.get")
        void returnsBackendEntityDetailShapeExpectedByCollectionsApi() throws Exception {
            when(client.findById(anyString(), eq("dc_collections"), eq("orders")))
                    .thenReturn(Promise.of(Optional.of(ordersCollection)));

            startServer();

            HttpResponse<String> response = getWithHeader(
                    "/api/v1/entities/dc_collections/orders",
                    "X-Tenant-Id",
                    TestConstants.TENANT_DEFAULT);

            assertThat(response.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            assertThat(body)
                    .containsEntry("id", "orders")
                    .containsEntry("collection", "dc_collections")
                    .containsEntry("createdAt", "2026-04-17T08:00:00Z")
                    .containsEntry("updatedAt", "2026-04-17T09:00:00Z");
            @SuppressWarnings("unchecked")
            Map<String, Object> entityData = (Map<String, Object>) body.get("data");
            assertThat(entityData)
                    .containsEntry("name", "Orders")
                    .containsEntry("description", "Order collection")
                    .containsEntry("status", "active")
                    .containsEntry("entityCount", 42);
        }
    }

    private static DataCloudClient.Entity collectionEntity(
            String id,
            String name,
            String description,
            int entityCount,
            String status,
            Instant createdAt,
            Instant updatedAt) {
        return new DataCloudClient.Entity(
                id,
                "dc_collections",
                Map.of(
                        "name", name,
                        "description", description,
                        "schemaType", "entity",
                        "status", status,
                        "isActive", "active".equals(status),
                        "entityCount", entityCount,
                        "schema", Map.of(
                                "fields", List.of(
                                        Map.of("name", "id", "type", "string", "required", true),
                                        Map.of("name", "tenantId", "type", "string", "required", true))),
                        "tags", List.of("core"),
                        "createdBy", "integration-test"),
                createdAt,
                updatedAt,
                1L);
    }
}