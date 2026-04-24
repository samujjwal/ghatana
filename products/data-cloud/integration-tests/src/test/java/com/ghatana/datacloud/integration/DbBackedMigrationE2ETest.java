/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved.
 */
package com.ghatana.datacloud.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloud.DataCloudConfig;
import com.ghatana.datacloud.DataCloud.DataCloudConfig.DataCloudProfile;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * DB-backed migration and API E2E tests for data-cloud.
 *
 * <p>Exercises the full sovereign-backed persistence stack with a temporary on-disk data
 * directory (no external DB required). Tests are driven through the live HTTP API using
 * {@link DataCloudHttpServer}, matching the same pattern as {@link MultiTenantIsolationInMemoryTest}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Full CRUD API correctness: create → read → update → delete cycle</li>
 *   <li>Bulk create and paginated query covering all entities without gaps</li>
 *   <li>Schema evolution compatibility: v1 data readable after v2 adds optional fields</li>
 *   <li>Mixed-schema coexistence in the same collection</li>
 *   <li>Property-based query correctness: filter, sort, compound predicates, pagination</li>
 *   <li>Multi-tenant isolation: tenant B cannot read tenant A's data</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DB-backed migration and API E2E integration tests for data-cloud
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("DbBackedMigrationE2ETest")
@Tag("integration")
class DbBackedMigrationE2ETest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String TENANT = "e2e-tenant-001";
    private static final String COLLECTION = "e2e-records";

    @TempDir
    Path dataDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private DataCloudClient client;
    private DataCloudHttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        DataCloudConfig config = DataCloudConfig.builder()
                .profile(DataCloudProfile.SOVEREIGN)
                .customConfig(Map.of("sovereign.dataDir", dataDir.resolve("sovereign-store").toString()))
                .build();
        client = DataCloud.create(config);
        port = findFreePort();
        server = new DataCloudHttpServer(client, port);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
        if (client != null) client.close();
    }

    // =========================================================================
    // CRUD E2E correctness
    // =========================================================================

    @Nested
    @DisplayName("CRUD API E2E")
    class CrudE2ETests {

        @Test
        @DisplayName("create → read → update → delete cycle round-trips correctly")
        void fullCrudRoundTrip() throws Exception {
            // CREATE
            ParsedResponse created = api("POST", "/api/v1/entities/" + COLLECTION,
                    Map.of("name", "WidgetAlpha", "price", 19.99, "inStock", true), TENANT);
            assertThat(created.statusCode()).isEqualTo(201);
            String id = String.valueOf(created.body().get("id"));
            assertThat(id).isNotBlank();

            // READ
            ParsedResponse found = api("GET", "/api/v1/entities/" + COLLECTION + "/" + id, null, TENANT);
            assertThat(found.statusCode()).isEqualTo(200);
            assertThat(dataOf(found)).containsEntry("name", "WidgetAlpha");

            // UPDATE
            ParsedResponse updated = api("PATCH", "/api/v1/entities/" + COLLECTION + "/" + id,
                    Map.of("price", 24.99, "inStock", false), TENANT);
            assertThat(updated.statusCode()).isEqualTo(200);
            assertThat(dataOf(updated)).containsEntry("price", 24.99);

            // READ after update
            ParsedResponse afterUpdate = api("GET", "/api/v1/entities/" + COLLECTION + "/" + id, null, TENANT);
            assertThat(dataOf(afterUpdate)).containsEntry("price", 24.99);
            assertThat(dataOf(afterUpdate)).containsEntry("inStock", false);

            // DELETE
            ParsedResponse deleted = api("DELETE", "/api/v1/entities/" + COLLECTION + "/" + id, null, TENANT);
            assertThat(deleted.statusCode()).isIn(200, 204);

            // READ after delete
            ParsedResponse afterDelete = api("GET", "/api/v1/entities/" + COLLECTION + "/" + id, null, TENANT);
            assertThat(afterDelete.statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("bulk create 20 entities; query returns all of them via pagination")
        void bulkCreateAndQueryAll() throws Exception {
            String col = COLLECTION + "-bulk";
            int total = 20;

            for (int i = 0; i < total; i++) {
                ParsedResponse r = api("POST", "/api/v1/entities/" + col,
                        Map.of("idx", i, "batch", "bulk-test"), TENANT);
                assertThat(r.statusCode()).isEqualTo(201);
            }

            ParsedResponse query = api("GET",
                    "/api/v1/entities/" + col + "?filter=batch:eq:bulk-test&limit=100", null, TENANT);
            assertThat(query.statusCode()).isEqualTo(200);
            assertThat(entityList(query.body())).hasSize(total);
        }

        @Test
        @DisplayName("create with empty body returns a client error")
        void createWithEmptyBodyReturnsClientError() throws Exception {
            ParsedResponse r = api("POST", "/api/v1/entities/" + COLLECTION, Map.of(), TENANT);
            // 400 or 422 — must not be 5xx
            assertThat(r.statusCode()).isBetween(400, 499);
        }

        @Test
        @DisplayName("read non-existent entity returns 404")
        void readNonExistentEntityReturns404() throws Exception {
            ParsedResponse r = api("GET",
                    "/api/v1/entities/" + COLLECTION + "/" + UUID.randomUUID(), null, TENANT);
            assertThat(r.statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("update non-existent entity returns 404")
        void updateNonExistentEntityReturns404() throws Exception {
            ParsedResponse r = api("PATCH",
                    "/api/v1/entities/" + COLLECTION + "/" + UUID.randomUUID(),
                    Map.of("x", 1), TENANT);
            assertThat(r.statusCode()).isEqualTo(404);
        }
    }

    // =========================================================================
    // Migration compatibility (schema evolution)
    // =========================================================================

    @Nested
    @DisplayName("Migration compatibility")
    class MigrationCompatibilityTests {

        @Test
        @DisplayName("v1 entities are readable after v2 adds an optional field")
        void v1DataReadableAfterV2SchemaBroadens() throws Exception {
            String col = COLLECTION + "-migration";

            // V1 entity: { name, price }
            ParsedResponse v1 = api("POST", "/api/v1/entities/" + col,
                    Map.of("name", "LegacyProduct", "price", 9.99), TENANT);
            assertThat(v1.statusCode()).isEqualTo(201);
            String v1Id = String.valueOf(v1.body().get("id"));

            // V2 entity: adds "category"
            api("POST", "/api/v1/entities/" + col,
                    Map.of("name", "NewProduct", "price", 14.99, "category", "widgets"), TENANT);

            // V1 entity must still be readable
            ParsedResponse read = api("GET", "/api/v1/entities/" + col + "/" + v1Id, null, TENANT);
            assertThat(read.statusCode()).isEqualTo(200);
            assertThat(dataOf(read)).containsEntry("name", "LegacyProduct");
            assertThat(dataOf(read)).containsEntry("price", 9.99);
        }

        @Test
        @DisplayName("mixed v1 and v2 entities coexist and are both queryable")
        void mixedSchemaVersionsCoexist() throws Exception {
            String col = COLLECTION + "-mixed";

            api("POST", "/api/v1/entities/" + col,
                    Map.of("name", "V1Product", "price", 1.0), TENANT);
            api("POST", "/api/v1/entities/" + col,
                    Map.of("name", "V2Product", "price", 2.0, "category", "electronics"), TENANT);

            // Query all
            ParsedResponse all = api("GET", "/api/v1/entities/" + col + "?limit=10", null, TENANT);
            assertThat(all.statusCode()).isEqualTo(200);
            assertThat(entityList(all.body())).hasSize(2);

            // Query v2-only by category
            ParsedResponse v2Only = api("GET",
                    "/api/v1/entities/" + col + "?filter=category:eq:electronics&limit=10", null, TENANT);
            assertThat(v2Only.statusCode()).isEqualTo(200);
            assertThat(entityList(v2Only.body())).hasSize(1);
        }
    }

    // =========================================================================
    // Property-based query tests
    // =========================================================================

    @Nested
    @DisplayName("Property-based query tests")
    class PropertyBasedQueryTests {

        @Test
        @DisplayName("pagination: offset + limit covers all entities without duplication or gaps")
        void paginationCoversAllEntitiesWithoutGaps() throws Exception {
            String col = COLLECTION + "-pagination";
            int total = 30;
            int pageSize = 10;

            for (int i = 0; i < total; i++) {
                api("POST", "/api/v1/entities/" + col, Map.of("seq", i), TENANT);
            }

            Set<String> allIds = new HashSet<>();
            for (int offset = 0; offset < total; offset += pageSize) {
                ParsedResponse page = api("GET",
                        "/api/v1/entities/" + col + "?limit=" + pageSize + "&offset=" + offset,
                        null, TENANT);
                assertThat(page.statusCode()).isEqualTo(200);
                for (Map<String, Object> entity : entityList(page.body())) {
                    String eid = String.valueOf(entity.get("id"));
                    assertThat(allIds.add(eid))
                            .as("entity %s must not appear in multiple pages".formatted(eid))
                            .isTrue();
                }
            }

            assertThat(allIds)
                    .as("all %d entities must be covered by pagination".formatted(total))
                    .hasSize(total);
        }

        @Test
        @DisplayName("sort by numeric field: results are monotonically non-decreasing")
        void sortByNumericFieldIsMonotone() throws Exception {
            String col = COLLECTION + "-sort";
            List<Integer> values = new ArrayList<>(IntStream.rangeClosed(1, 20).boxed().toList());
            Collections.shuffle(values, new Random(42));

            for (int v : values) {
                api("POST", "/api/v1/entities/" + col, Map.of("score", v), TENANT);
            }

            ParsedResponse sorted = api("GET",
                    "/api/v1/entities/" + col + "?sort=score:asc&limit=100", null, TENANT);
            assertThat(sorted.statusCode()).isEqualTo(200);

            List<Map<String, Object>> entities = entityList(sorted.body());
            assertThat(entities).hasSize(20);

            for (int i = 1; i < entities.size(); i++) {
                double prev = ((Number) dataFieldOf(entities.get(i - 1), "score")).doubleValue();
                double curr = ((Number) dataFieldOf(entities.get(i), "score")).doubleValue();
                assertThat(curr)
                        .as("position %d score must be >= position %d".formatted(i, i - 1))
                        .isGreaterThanOrEqualTo(prev);
            }
        }

        @Test
        @DisplayName("filter by boolean: only matching entities returned")
        void filterByBooleanField() throws Exception {
            String col = COLLECTION + "-bool-filter";
            for (int i = 0; i < 10; i++) {
                api("POST", "/api/v1/entities/" + col,
                        Map.of("active", (i % 2 == 0), "idx", i), TENANT);
            }

            ParsedResponse active = api("GET",
                    "/api/v1/entities/" + col + "?filter=active:eq:true&limit=50", null, TENANT);
            assertThat(active.statusCode()).isEqualTo(200);
            List<Map<String, Object>> results = entityList(active.body());
            assertThat(results).hasSize(5);
            results.forEach(e ->
                    assertThat(dataFieldOf(e, "active"))
                            .as("every returned entity must have active=true")
                            .isEqualTo(true));
        }

        @Test
        @DisplayName("query on empty collection returns empty list")
        void queryOnEmptyCollectionReturnsEmpty() throws Exception {
            String col = COLLECTION + "-empty-" + UUID.randomUUID();
            ParsedResponse r = api("GET", "/api/v1/entities/" + col + "?limit=10", null, TENANT);
            assertThat(r.statusCode()).isEqualTo(200);
            assertThat(entityList(r.body())).isEmpty();
        }
    }

    // =========================================================================
    // Multi-tenant isolation via sovereign storage
    // =========================================================================

    @Nested
    @DisplayName("Multi-tenant isolation")
    class MultiTenantIsolationTests {

        @Test
        @DisplayName("tenant B cannot read tenant A's records by query")
        void tenantBCannotQueryTenantAData() throws Exception {
            String col = COLLECTION + "-isolation";
            String tenantA = "e2e-isolation-a";
            String tenantB = "e2e-isolation-b";

            api("POST", "/api/v1/entities/" + col,
                    Map.of("secret", "tenant-A-only"), tenantA);

            ParsedResponse fromB = api("GET",
                    "/api/v1/entities/" + col + "?filter=secret:eq:tenant-A-only&limit=10",
                    null, tenantB);
            assertThat(fromB.statusCode()).isEqualTo(200);
            assertThat(entityList(fromB.body()))
                    .as("tenant B must not see tenant A's records")
                    .isEmpty();
        }

        @Test
        @DisplayName("tenant B cannot read tenant A's entity by direct ID")
        void tenantBCannotReadTenantAEntityById() throws Exception {
            String col = COLLECTION + "-id-isolation";
            String tenantA = "e2e-iso-a";
            String tenantB = "e2e-iso-b";

            ParsedResponse created = api("POST", "/api/v1/entities/" + col,
                    Map.of("data", "confidential"), tenantA);
            String entityId = String.valueOf(created.body().get("id"));

            ParsedResponse crossRead = api("GET",
                    "/api/v1/entities/" + col + "/" + entityId, null, tenantB);
            assertThat(crossRead.statusCode()).isEqualTo(404);
        }
    }

    // =========================================================================
    // HTTP helper infrastructure
    // =========================================================================

    private ParsedResponse api(String method, String path,
                               Map<String, Object> body, String tenantId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "application/json")
                .header("X-Tenant-Id", tenantId);

        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body)));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());

        Map<String, Object> parsed = (response.body() == null || response.body().isBlank())
                ? Map.of()
                : objectMapper.readValue(response.body(), MAP_TYPE);
        return new ParsedResponse(response.statusCode(), parsed);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(ParsedResponse r) {
        return (Map<String, Object>) r.body().get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> entityList(Map<String, Object> body) {
        Object entities = body.get("entities");
        if (entities == null) return List.of();
        return (List<Map<String, Object>>) entities;
    }

    @SuppressWarnings("unchecked")
    private Object dataFieldOf(Map<String, Object> entity, String field) {
        Map<String, Object> data = (Map<String, Object>) entity.get("data");
        return data != null ? data.get(field) : null;
    }

    private int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private record ParsedResponse(int statusCode, Map<String, Object> body) {}
}
