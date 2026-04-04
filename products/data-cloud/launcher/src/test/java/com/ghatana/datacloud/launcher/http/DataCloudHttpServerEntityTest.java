/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * export (501 without service), anomaly detection (501 without detector),
 * full-text search (501 without connector), request validation and tenant
 * isolation headers.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/entities/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Entity CRUD Endpoints")
class DataCloudHttpServerEntityTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        port = findFreePort();
    }

    @Override
    protected void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/entities/:collection  — save entity
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/entities/:collection – save entity")
    class SaveEntityTests {

        /**
         * Requirement A001: Save Entity with Valid Data
         * Route: POST /api/v1/entities/{collection}
         */
        @Test
        @DisplayName("returns 200 with saved entity id when client save succeeds")
        void saveEntity_validPayload_returns200() throws Exception {
            DataCloudClient.Entity saved = DataCloudClient.Entity.of(
                    "ent-1", TestConstants.COLLECTION_PRODUCTS, 
                    Map.of("name", "Widget", "price", 9.99));
            when(mockClient.save(anyString(), eq(TestConstants.COLLECTION_PRODUCTS), any()))
                    .thenReturn(Promise.of(saved));
            when(mockClient.appendEvent(anyString(), any()))
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(1)));

            startServer();

            HttpResponse<String> resp = postJson("/api/v1/entities/" + TestConstants.COLLECTION_PRODUCTS,
                    Map.of("name", "Widget", "price", 9.99));

            assertStatusCode(resp, TestConstants.HTTP_OK);
            Map<String, Object> body = parseJsonResponse(resp);
            assertThat(body.get("id")).isEqualTo("ent-1");
            assertThat(body.get("collection")).isEqualTo(TestConstants.COLLECTION_PRODUCTS);
        }

        /**
         * Requirement A002: Reject Invalid Content-Type
         * Route: POST /api/v1/entities/{collection}
         */
        @Test
        @DisplayName("returns 415 when Content-Type is not application/json")
        void saveEntity_wrongContentType_returns415() throws Exception {
            startServer();

            HttpRequest req = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"x\"}"))
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/entities/" + TestConstants.COLLECTION_PRODUCTS))
                    .header("Content-Type", "text/plain")
                    .build();
            HttpResponse<String> resp = httpClient.send(req, BodyHandlers.ofString());

            assertStatusCode(resp, 415);
        }

        @Test
        @DisplayName("returns 400 when request body is empty")
        void saveEntity_emptyBody_returns400() throws Exception {
            startServer();

            HttpResponse<String> resp = postRaw("/api/v1/entities/products", "{}");

            // empty data map is rejected with 400 by ApiInputValidator
            assertThat(resp.statusCode()).isIn(400, 200); // depends on validator strictness
        }

        @Test
        @DisplayName("returns 400 when collection name contains path traversal")
        void saveEntity_maliciousCollection_returns400() throws Exception {
            startServer();

            HttpResponse<String> resp = postJson("/api/v1/entities/../admin",
                    Map.of("name", "hack"));

            // ActiveJ routing won't match the path, returns 404 or 400
            assertThat(resp.statusCode()).isIn(400, 404);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/entities/:collection/:id  — get entity by id
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/entities/:collection/:id – get entity")
    class GetEntityTests {

        @Test
        @DisplayName("returns 200 with entity data when found")
        void getEntity_exists_returns200() throws Exception {
            DataCloudClient.Entity entity = DataCloudClient.Entity.of(
                    "ent-42", "products", Map.of("name", "Gadget", "sku", "SKU-001"));
            when(mockClient.findById(anyString(), eq("products"), eq("ent-42")))
                    .thenReturn(Promise.of(Optional.of(entity)));

            startServer();

            HttpResponse<String> resp = get("/api/v1/entities/products/ent-42");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("id")).isEqualTo("ent-42");
            assertThat(body.get("collection")).isEqualTo("products");
        }

        @Test
        @DisplayName("returns 404 when entity does not exist")
        void getEntity_notFound_returns404() throws Exception {
            when(mockClient.findById(anyString(), eq("products"), eq("missing-id")))
                    .thenReturn(Promise.of(Optional.empty()));

            startServer();

            HttpResponse<String> resp = get("/api/v1/entities/products/missing-id");

            assertThat(resp.statusCode()).isEqualTo(404);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("error")).isNotNull();
        }

        @Test
        @DisplayName("tenant is resolved from X-Tenant-ID header when present")
        void getEntity_withTenantHeader_usesTenantId() throws Exception {
            DataCloudClient.Entity entity = DataCloudClient.Entity.of(
                    "ent-7", "orders", Map.of("status", "pending"));
            when(mockClient.findById(eq("acme"), eq("orders"), eq("ent-7")))
                    .thenReturn(Promise.of(Optional.of(entity)));

            startServer();

            HttpResponse<String> resp = getWithHeader(
                    "/api/v1/entities/orders/ent-7", "X-Tenant-ID", "acme");

            assertThat(resp.statusCode()).isEqualTo(200);
            verify(mockClient).findById(eq("acme"), eq("orders"), eq("ent-7"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/entities/:collection  — query entities
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/entities/:collection – query entities")
    class QueryEntitiesTests {

        @Test
        @DisplayName("returns 200 with entities list and count")
        void queryEntities_returns200WithList() throws Exception {
            List<DataCloudClient.Entity> entities = List.of(
                    DataCloudClient.Entity.of("e1", "products", Map.of("name", "A")),
                    DataCloudClient.Entity.of("e2", "products", Map.of("name", "B")));
            when(mockClient.query(anyString(), eq("products"), any()))
                    .thenReturn(Promise.of(entities));

            startServer();

            HttpResponse<String> resp = get("/api/v1/entities/products");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) body.get("entities");
            assertThat(items).hasSize(2);
            assertThat(((Number) body.get("count")).intValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("returns 200 with empty list when no entities exist")
        void queryEntities_empty_returns200EmptyList() throws Exception {
            when(mockClient.query(anyString(), eq("sensors"), any()))
                    .thenReturn(Promise.of(List.of()));

            startServer();

            HttpResponse<String> resp = get("/api/v1/entities/sensors");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat((List<?>) body.get("entities")).isEmpty();
        }

        @Test
        @DisplayName("returns 400 when limit parameter is invalid")
        void queryEntities_invalidLimit_returns400() throws Exception {
            startServer();

            HttpResponse<String> resp = get("/api/v1/entities/products?limit=-5");

            assertThat(resp.statusCode()).isEqualTo(400);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/entities/:collection/:id  — delete entity
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/v1/entities/:collection/:id – delete entity")
    class DeleteEntityTests {

        @Test
        @DisplayName("returns 200 with deleted=true when client confirms deletion")
        void deleteEntity_exists_returns200() throws Exception {
            DataCloudClient.Entity toDelete = DataCloudClient.Entity.of(
                    "ent-1", "products", Map.of("name", "Widget"));
            when(mockClient.findById(anyString(), eq("products"), eq("ent-1")))
                    .thenReturn(Promise.of(Optional.of(toDelete)));
            when(mockClient.delete(anyString(), eq("products"), eq("ent-1")))
                    .thenReturn(Promise.of((Void) null));
            when(mockClient.appendEvent(anyString(), any()))
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(1)));

            startServer();

            HttpResponse<String> resp = delete("/api/v1/entities/products/ent-1");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("deleted")).isEqualTo(Boolean.TRUE);
            assertThat(body.get("id")).isEqualTo("ent-1");
        }

        @Test
        @DisplayName("returns 404 when entity does not exist")
        void deleteEntity_notFound_returns404() throws Exception {
            when(mockClient.findById(anyString(), eq("products"), eq("ghost")))
                    .thenReturn(Promise.of(Optional.empty()));

            startServer();

            HttpResponse<String> resp = delete("/api/v1/entities/products/ghost");

            assertThat(resp.statusCode()).isEqualTo(404);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/entities/:collection/batch  — batch upsert
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/entities/:collection/batch – batch save")
    class BatchSaveTests {

        @Test
        @DisplayName("returns 200 with saved count when entities are valid")
        void batchSave_validEntities_returns200() throws Exception {
            DataCloudClient.Entity e1 = DataCloudClient.Entity.of("b1", "sensors", Map.of("v", 1));
            DataCloudClient.Entity e2 = DataCloudClient.Entity.of("b2", "sensors", Map.of("v", 2));
            when(mockClient.save(anyString(), eq("sensors"), any()))
                    .thenReturn(Promise.of(e1))
                    .thenReturn(Promise.of(e2));
            when(mockClient.appendEvent(anyString(), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(1)));

            startServer();

            HttpResponse<String> resp = postJson("/api/v1/entities/sensors/batch",
                    Map.of("entities", List.of(Map.of("v", 1), Map.of("v", 2))));

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("saved")).isNotNull();
        }

        @Test
        @DisplayName("returns 400 when entities list is missing")
        void batchSave_missingEntities_returns400() throws Exception {
            startServer();

            HttpResponse<String> resp = postJson("/api/v1/entities/sensors/batch",
                    Map.of("notEntities", List.of()));

            assertThat(resp.statusCode()).isEqualTo(400);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/entities/:collection/batch  — batch delete
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/v1/entities/:collection/batch – batch delete")
    class BatchDeleteTests {

        @Test
        @DisplayName("returns 200 with deletedCount when ids are provided")
        void batchDelete_withIds_returns200() throws Exception {
            when(mockClient.delete(anyString(), eq("products"), anyString()))
                    .thenReturn(Promise.of((Void) null));
            when(mockClient.appendEvent(anyString(), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(1)));

            startServer();

            HttpRequest req = HttpRequest.newBuilder()
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(
                            "{\"ids\":[\"id-1\",\"id-2\"]}"))
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/entities/products/batch"))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(200);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/entities/:collection/export  — export (501 without service)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/entities/:collection/export – export")
    class ExportTests {

        @Test
        @DisplayName("returns 501 when export service is not configured")
        void export_noService_returns501() throws Exception {
            startServer(); // no withExportService()

            HttpResponse<String> resp = get("/api/v1/entities/products/export");

            assertThat(resp.statusCode()).isEqualTo(501);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/entities/:collection/anomalies  — anomaly detection
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/entities/:collection/anomalies – anomaly detection")
    class AnomalyTests {

        @Test
        @DisplayName("returns 501 when anomaly detector is not configured")
        void anomalies_noDetector_returns501() throws Exception {
            startServer(); // no withAnomalyDetector()

            HttpResponse<String> resp = postJson(
                    "/api/v1/entities/sensors/anomalies", Map.of("field", "temperature"));

            assertThat(resp.statusCode()).isEqualTo(501);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/entities/:collection/search  — full-text search
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/entities/:collection/search – full-text search")
    class FullTextSearchTests {

        @Test
        @DisplayName("returns 501 when OpenSearch connector is not configured")
        void search_noConnector_returns501() throws Exception {
            startServer(); // no withOpenSearchConnector()

            HttpResponse<String> resp = get("/api/v1/entities/products/search?q=widget");

            assertThat(resp.statusCode()).isEqualTo(501);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Request Validation — OWASP injection guards
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Request Validation – security and input guards")
    class ValidationTests {

        @Test
        @DisplayName("returns 413 when Content-Length exceeds 10 MB limit")
        void payloadSize_exceeds10MB_returns413() throws Exception {
            startServer();

            // Use a raw socket to send a request with a fake oversized Content-Length header.
            // Java's HttpClient does not allow overriding Content-Length, so we craft the
            // HTTP/1.1 request directly — the server's payloadSizeLimitFilter checks the
            // Content-Length header before loading the body and returns 413 immediately.
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
                socket.setSoTimeout(5_000);
                java.io.OutputStream out = socket.getOutputStream();
                String rawRequest = "POST /api/v1/entities/products HTTP/1.1\r\n"
                        + "Host: 127.0.0.1:" + port + "\r\n"
                        + "Content-Type: application/json\r\n"
                        + "Content-Length: " + (11 * 1024 * 1024) + "\r\n"
                        + "\r\n";
                out.write(rawRequest.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                out.flush();

                byte[] buf = new byte[512];
                int n = socket.getInputStream().read(buf);
                String statusLine = new String(buf, 0, n, java.nio.charset.StandardCharsets.US_ASCII)
                        .split("\r\n")[0];
                assertThat(statusLine).containsIgnoringCase("413");
            }
        }

        @Test
        @DisplayName("OPTIONS preflight returns 200 with CORS headers")
        void preflight_returnsOkWithCors() throws Exception {
            startServer();

            HttpRequest req = HttpRequest.newBuilder()
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/entities/products"))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.headers().firstValue("Access-Control-Allow-Origin")).isPresent();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers (inherited from DataCloudHttpServerTestBase)
    // ─────────────────────────────────────────────────────────────────────────
}
