/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Data Cloud HTTP entity CRUD endpoints.
 *
 * <p>Extends {@link DataCloudHttpServerTestBase} to inherit reusable HTTP
 * helpers, tenant context management, and response parsing utilities.
 * All tests share the same server startup and HTTP client infrastructure.
 *
 * <p>Covers: POST/GET/DELETE single entity, GET query, batch save/delete,
 * export (501 without service), anomaly detection (501 without detector), // GH-90000
 * full-text search (501 without connector), request validation and tenant // GH-90000
 * isolation headers.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/entities/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Entity CRUD Endpoints [GH-90000]")
class DataCloudHttpServerEntityTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        port = findFreePort(); // GH-90000
    }

    @Override
    protected void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/entities/:collection  — save entity
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/entities/:collection – save entity [GH-90000]")
    class SaveEntityTests {

        /**
         * Requirement A001: Save Entity with Valid Data
         * Route: POST /api/v1/entities/{collection}
         */
        @Test
        @DisplayName("returns 200 with saved entity id when client save succeeds [GH-90000]")
        void saveEntity_validPayload_returns200() throws Exception { // GH-90000
            DataCloudClient.Entity saved = DataCloudClient.Entity.of( // GH-90000
                    "ent-1", TestConstants.COLLECTION_PRODUCTS,
                    Map.of("name", "Widget", "price", 9.99)); // GH-90000
            when(mockClient.save(anyString(), eq(TestConstants.COLLECTION_PRODUCTS), any())) // GH-90000
                    .thenReturn(Promise.of(saved)); // GH-90000
            when(mockClient.appendEvent(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(1))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = postJson("/api/v1/entities/" + TestConstants.COLLECTION_PRODUCTS, // GH-90000
                    Map.of("name", "Widget", "price", 9.99)); // GH-90000

            assertStatusCode(resp, TestConstants.HTTP_OK); // GH-90000
            Map<String, Object> body = parseJsonResponse(resp); // GH-90000
            assertThat(body.get("id [GH-90000]")).isEqualTo("ent-1 [GH-90000]");
            assertThat(body.get("collection [GH-90000]")).isEqualTo(TestConstants.COLLECTION_PRODUCTS);
        }

        /**
         * Requirement A002: Reject Invalid Content-Type
         * Route: POST /api/v1/entities/{collection}
         */
        @Test
        @DisplayName("returns 415 when Content-Type is not application/json [GH-90000]")
        void saveEntity_wrongContentType_returns415() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpRequest req = HttpRequest.newBuilder() // GH-90000
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"x\"}")) // GH-90000
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/entities/" + TestConstants.COLLECTION_PRODUCTS)) // GH-90000
                    .header("Content-Type", "text/plain") // GH-90000
                    .build(); // GH-90000
            HttpResponse<String> resp = httpClient.send(req, BodyHandlers.ofString()); // GH-90000

            assertStatusCode(resp, 415); // GH-90000
        }

        @Test
        @DisplayName("returns 415 when Content-Type header is missing [GH-90000]")
        void saveEntity_missingContentType_returns415() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpRequest req = HttpRequest.newBuilder() // GH-90000
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"x\"}")) // GH-90000
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/entities/" + TestConstants.COLLECTION_PRODUCTS)) // GH-90000
                    .build(); // GH-90000
            HttpResponse<String> resp = httpClient.send(req, BodyHandlers.ofString()); // GH-90000

            assertStatusCode(resp, 415); // GH-90000
        }

        @Test
        @DisplayName("returns 400 when request body is empty [GH-90000]")
        void saveEntity_emptyBody_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = postRaw("/api/v1/entities/products", "{}"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 400 when collection name contains path traversal [GH-90000]")
        void saveEntity_maliciousCollection_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = postJson("/api/v1/entities/../admin", // GH-90000
                    Map.of("name", "hack")); // GH-90000

            // ActiveJ routing won't match the path, returns 404 or 400
            assertThat(resp.statusCode()).isIn(400, 404); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/entities/:collection/:id  — get entity by id
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/entities/:collection/:id – get entity [GH-90000]")
    class GetEntityTests {

        @Test
        @DisplayName("returns 200 with entity data when found [GH-90000]")
        void getEntity_exists_returns200() throws Exception { // GH-90000
            DataCloudClient.Entity entity = DataCloudClient.Entity.of( // GH-90000
                    "ent-42", "products", Map.of("name", "Gadget", "sku", "SKU-001")); // GH-90000
            when(mockClient.findById(anyString(), eq("products [GH-90000]"), eq("ent-42 [GH-90000]")))
                    .thenReturn(Promise.of(Optional.of(entity))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/entities/products/ent-42 [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("id [GH-90000]")).isEqualTo("ent-42 [GH-90000]");
            assertThat(body.get("collection [GH-90000]")).isEqualTo("products [GH-90000]");
        }

        @Test
        @DisplayName("returns 404 when entity does not exist [GH-90000]")
        void getEntity_notFound_returns404() throws Exception { // GH-90000
            when(mockClient.findById(anyString(), eq("products [GH-90000]"), eq("missing-id [GH-90000]")))
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/entities/products/missing-id [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("tenant is resolved from X-Tenant-ID header when present [GH-90000]")
        void getEntity_withTenantHeader_usesTenantId() throws Exception { // GH-90000
            DataCloudClient.Entity entity = DataCloudClient.Entity.of( // GH-90000
                    "ent-7", "orders", Map.of("status", "pending")); // GH-90000
            when(mockClient.findById(eq("acme [GH-90000]"), eq("orders [GH-90000]"), eq("ent-7 [GH-90000]")))
                    .thenReturn(Promise.of(Optional.of(entity))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = getWithHeader( // GH-90000
                    "/api/v1/entities/orders/ent-7", "X-Tenant-ID", "acme");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            verify(mockClient).findById(eq("acme [GH-90000]"), eq("orders [GH-90000]"), eq("ent-7 [GH-90000]"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/entities/:collection  — query entities
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/entities/:collection – query entities [GH-90000]")
    class QueryEntitiesTests {

        @Test
        @DisplayName("returns 200 with entities list and count [GH-90000]")
        void queryEntities_returns200WithList() throws Exception { // GH-90000
            List<DataCloudClient.Entity> entities = List.of( // GH-90000
                    DataCloudClient.Entity.of("e1", "products", Map.of("name", "A")), // GH-90000
                    DataCloudClient.Entity.of("e2", "products", Map.of("name", "B"))); // GH-90000
            when(mockClient.query(anyString(), eq("products [GH-90000]"), any()))
                    .thenReturn(Promise.of(entities)); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/entities/products [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            List<?> items = (List<?>) body.get("entities [GH-90000]");
            assertThat(items).hasSize(2); // GH-90000
            assertThat(((Number) body.get("count [GH-90000]")).intValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("returns 200 with empty list when no entities exist [GH-90000]")
        void queryEntities_empty_returns200EmptyList() throws Exception { // GH-90000
            when(mockClient.query(anyString(), eq("sensors [GH-90000]"), any()))
                    .thenReturn(Promise.of(List.of())); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/entities/sensors [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat((List<?>) body.get("entities [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("returns 400 when limit parameter is invalid [GH-90000]")
        void queryEntities_invalidLimit_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/entities/products?limit=-5 [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/entities/:collection/:id  — delete entity
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/v1/entities/:collection/:id – delete entity [GH-90000]")
    class DeleteEntityTests {

        @Test
        @DisplayName("returns 200 with deleted=true when client confirms deletion [GH-90000]")
        void deleteEntity_exists_returns200() throws Exception { // GH-90000
            DataCloudClient.Entity toDelete = DataCloudClient.Entity.of( // GH-90000
                    "ent-1", "products", Map.of("name", "Widget")); // GH-90000
            when(mockClient.findById(anyString(), eq("products [GH-90000]"), eq("ent-1 [GH-90000]")))
                    .thenReturn(Promise.of(Optional.of(toDelete))); // GH-90000
            when(mockClient.delete(anyString(), eq("products [GH-90000]"), eq("ent-1 [GH-90000]")))
                    .thenReturn(Promise.of((Void) null)); // GH-90000
            when(mockClient.appendEvent(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(1))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = delete("/api/v1/entities/products/ent-1 [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("deleted [GH-90000]")).isEqualTo(Boolean.TRUE);
            assertThat(body.get("id [GH-90000]")).isEqualTo("ent-1 [GH-90000]");
        }

        @Test
        @DisplayName("returns 404 when entity does not exist [GH-90000]")
        void deleteEntity_notFound_returns404() throws Exception { // GH-90000
            when(mockClient.findById(anyString(), eq("products [GH-90000]"), eq("ghost [GH-90000]")))
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = delete("/api/v1/entities/products/ghost [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/entities/:collection/batch  — batch upsert
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/entities/:collection/batch – batch save [GH-90000]")
    class BatchSaveTests {

        @Test
        @DisplayName("returns 200 with saved count when entities are valid [GH-90000]")
        void batchSave_validEntities_returns200() throws Exception { // GH-90000
            DataCloudClient.Entity e1 = DataCloudClient.Entity.of("b1", "sensors", Map.of("v", 1)); // GH-90000
            DataCloudClient.Entity e2 = DataCloudClient.Entity.of("b2", "sensors", Map.of("v", 2)); // GH-90000
            when(mockClient.save(anyString(), eq("sensors [GH-90000]"), any()))
                    .thenReturn(Promise.of(e1)) // GH-90000
                    .thenReturn(Promise.of(e2)); // GH-90000
            when(mockClient.appendEvent(anyString(), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(1))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = postJson("/api/v1/entities/sensors/batch", // GH-90000
                    Map.of("entities", List.of(Map.of("v", 1), Map.of("v", 2)))); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("saved [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("returns 400 when entities list is missing [GH-90000]")
        void batchSave_missingEntities_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = postJson("/api/v1/entities/sensors/batch", // GH-90000
                    Map.of("notEntities", List.of())); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/entities/:collection/batch  — batch delete
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/v1/entities/:collection/batch – batch delete [GH-90000]")
    class BatchDeleteTests {

        @Test
        @DisplayName("returns 200 with deletedCount when ids are provided [GH-90000]")
        void batchDelete_withIds_returns200() throws Exception { // GH-90000
            when(mockClient.delete(anyString(), eq("products [GH-90000]"), anyString()))
                    .thenReturn(Promise.of((Void) null)); // GH-90000
            when(mockClient.appendEvent(anyString(), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(1))); // GH-90000

            startServer(); // GH-90000

            HttpRequest req = HttpRequest.newBuilder() // GH-90000
                    .method("DELETE", HttpRequest.BodyPublishers.ofString( // GH-90000
                            "{\"ids\":[\"id-1\",\"id-2\"]}"))
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/entities/products/batch")) // GH-90000
                    .header("Content-Type", "application/json") // GH-90000
                    .build(); // GH-90000
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/entities/:collection/export  — export (501 without service) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/entities/:collection/export – export [GH-90000]")
    class ExportTests {

        @Test
        @DisplayName("returns 501 when export service is not configured [GH-90000]")
        void export_noService_returns501() throws Exception { // GH-90000
            startServer(); // no withExportService() // GH-90000

            HttpResponse<String> resp = get("/api/v1/entities/products/export [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(501); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/entities/:collection/anomalies  — anomaly detection
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/entities/:collection/anomalies – anomaly detection [GH-90000]")
    class AnomalyTests {

        @Test
        @DisplayName("returns 501 when anomaly detector is not configured [GH-90000]")
        void anomalies_noDetector_returns501() throws Exception { // GH-90000
            startServer(); // no withAnomalyDetector() // GH-90000

            HttpResponse<String> resp = postJson( // GH-90000
                    "/api/v1/entities/sensors/anomalies", Map.of("field", "temperature")); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(501); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/entities/:collection/search  — full-text search
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/entities/:collection/search – full-text search [GH-90000]")
    class FullTextSearchTests {

        @Test
        @DisplayName("returns 501 when OpenSearch connector is not configured [GH-90000]")
        void search_noConnector_returns501() throws Exception { // GH-90000
            startServer(); // no withOpenSearchConnector() // GH-90000

            HttpResponse<String> resp = get("/api/v1/entities/products/search?q=widget [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(501); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Request Validation — OWASP injection guards
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Request Validation – security and input guards [GH-90000]")
    class ValidationTests {

        @Test
        @DisplayName("returns 413 when Content-Length exceeds 10 MB limit [GH-90000]")
        void payloadSize_exceeds10MB_returns413() throws Exception { // GH-90000
            startServer(); // GH-90000

            // Use a raw socket to send a request with a fake oversized Content-Length header.
            // Java's HttpClient does not allow overriding Content-Length, so we craft the
            // HTTP/1.1 request directly — the server's payloadSizeLimitFilter checks the
            // Content-Length header before loading the body and returns 413 immediately.
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) { // GH-90000
                socket.setSoTimeout(5_000); // GH-90000
                java.io.OutputStream out = socket.getOutputStream(); // GH-90000
                String rawRequest = "POST /api/v1/entities/products HTTP/1.1\r\n"
                        + "Host: 127.0.0.1:" + port + "\r\n"
                        + "Content-Type: application/json\r\n"
                        + "Content-Length: " + (11 * 1024 * 1024) + "\r\n" // GH-90000
                        + "\r\n";
                out.write(rawRequest.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); // GH-90000
                out.flush(); // GH-90000

                byte[] buf = new byte[512];
                int n = socket.getInputStream().read(buf); // GH-90000
                String statusLine = new String(buf, 0, n, java.nio.charset.StandardCharsets.US_ASCII) // GH-90000
                        .split("\r\n [GH-90000]")[0];
                assertThat(statusLine).containsIgnoringCase("413 [GH-90000]");
            }
        }

        @Test
        @DisplayName("OPTIONS preflight returns 200 with CORS headers [GH-90000]")
        void preflight_returnsOkWithCors() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpRequest req = HttpRequest.newBuilder() // GH-90000
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody()) // GH-90000
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/entities/products")) // GH-90000
                    .build(); // GH-90000
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            assertThat(resp.headers().firstValue("Access-Control-Allow-Origin [GH-90000]")).isPresent();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers (inherited from DataCloudHttpServerTestBase) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────
}
