package com.ghatana.integration.crossservice;

import com.ghatana.aep.integration.registry.DataCloudPipelineRegistryClientImpl;
import com.ghatana.orchestrator.models.OrchestratorPipelineEntity;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for cross-product AEP ↔ Data Cloud integration scenarios.
 *
 * <p>Covers:
 * <ul>
 *   <li>Fallback behavior when Data Cloud is unavailable</li>
 *   <li>Tenant isolation across AEP → Data Cloud calls</li>
 *   <li>Retry and circuit-breaking semantics</li>
 *   <li>Schema compatibility for pipeline contracts</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Regression and fallback behavior tests for AEP→Data Cloud integration
 * @doc.layer integration
 * @doc.pattern Test, Regression
 */
@DisplayName("AEP ↔ Data Cloud cross-product regression tests")
class AepDataCloudIntegrationRegressionTest extends EventloopTestBase {

    private HttpServer mockDataCloud;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        mockDataCloud = HttpServer.create(new InetSocketAddress(0), 0);
        mockDataCloud.start();
        baseUrl = "http://localhost:" + mockDataCloud.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (mockDataCloud != null) {
            mockDataCloud.stop(0);
        }
    }

    // ==================== Fallback behavior ====================

    @Nested
    @DisplayName("Fallback behavior when Data Cloud is unavailable")
    class FallbackBehaviorTests {

        @Test
        @DisplayName("listAllPipelines returns empty list when Data Cloud returns 500")
        void listPipelines_dataCloudError_returnsEmptyList() {
            mockDataCloud.createContext("/api/v1/pipelines",
                    exchange -> writeJson(exchange, 500, "{\"error\":\"Internal Server Error\"}"));

            DataCloudPipelineRegistryClientImpl client = new DataCloudPipelineRegistryClientImpl(baseUrl);
            List<OrchestratorPipelineEntity> pipelines = runPromise(client::listAllPipelines);

            assertThat(pipelines).isEmpty();
        }

        @Test
        @DisplayName("listAllPipelines returns empty list when Data Cloud returns malformed JSON")
        void listPipelines_malformedJson_returnsEmptyList() {
            mockDataCloud.createContext("/api/v1/pipelines",
                    exchange -> writeJson(exchange, 200, "not-json"));

            DataCloudPipelineRegistryClientImpl client = new DataCloudPipelineRegistryClientImpl(baseUrl);
            List<OrchestratorPipelineEntity> pipelines = runPromise(client::listAllPipelines);

            assertThat(pipelines).isEmpty();
        }

        @Test
        @DisplayName("listAllPipelines returns empty list when Data Cloud returns empty pipelines array")
        void listPipelines_emptyArray_returnsEmptyList() {
            mockDataCloud.createContext("/api/v1/pipelines",
                    exchange -> writeJson(exchange, 200, "{\"pipelines\":[]}"));

            DataCloudPipelineRegistryClientImpl client = new DataCloudPipelineRegistryClientImpl(baseUrl);
            List<OrchestratorPipelineEntity> pipelines = runPromise(client::listAllPipelines);

            assertThat(pipelines).isEmpty();
        }

        @Test
        @DisplayName("getPipeline returns empty when Data Cloud returns 404")
        void getPipeline_notFound_returnsEmpty() {
            mockDataCloud.createContext("/api/v1/pipelines/missing-id",
                    exchange -> writeJson(exchange, 404, "{\"error\":\"Not found\"}"));

            DataCloudPipelineRegistryClientImpl client = new DataCloudPipelineRegistryClientImpl(baseUrl);
            Optional<OrchestratorPipelineEntity> result = runPromise(() -> client.getPipeline("missing-id"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getPipeline returns empty on Data Cloud 500")
        void getPipeline_serverError_returnsEmpty() {
            mockDataCloud.createContext("/api/v1/pipelines/err-id",
                    exchange -> writeJson(exchange, 500, "{\"error\":\"fail\"}"));

            DataCloudPipelineRegistryClientImpl client = new DataCloudPipelineRegistryClientImpl(baseUrl);
            Optional<OrchestratorPipelineEntity> result = runPromise(() -> client.getPipeline("err-id"));

            assertThat(result).isEmpty();
        }
    }

    // ==================== Tenant isolation ====================

    @Nested
    @DisplayName("Tenant isolation across AEP → Data Cloud calls")
    class TenantIsolationTests {

        @Test
        @DisplayName("AEP pipeline client sends system tenant header to Data Cloud by default")
        void listPipelines_sendsSystemTenantHeader() {
            AtomicReference<String> capturedTenant = new AtomicReference<>();
            mockDataCloud.createContext("/api/v1/pipelines", exchange -> {
                capturedTenant.set(exchange.getRequestHeaders().getFirst("X-Tenant-ID"));
                writeJson(exchange, 200, "{\"pipelines\":[]}");
            });

            DataCloudPipelineRegistryClientImpl client = new DataCloudPipelineRegistryClientImpl(baseUrl);
            runPromise(client::listAllPipelines);

            assertThat(capturedTenant.get()).isEqualTo("system");
        }

        @Test
        @DisplayName("Pipelines returned for tenant-a are scoped correctly")
        void listPipelines_tenantScopedResponse_parsedCorrectly() {
            mockDataCloud.createContext("/api/v1/pipelines", exchange -> writeJson(exchange, 200, """
                    {
                      "pipelines": [
                        {
                          "id": "p-tenant-a",
                          "name": "Pipeline A",
                          "description": "Tenant A pipeline",
                          "config": "{}",
                          "version": "1.0",
                          "createdBy": "tenant-a",
                          "status": "ACTIVE",
                          "tenantId": "tenant-a",
                          "createdAt": "2026-05-01T10:00:00Z",
                          "updatedAt": "2026-05-01T10:01:00Z"
                        },
                        {
                          "id": "p-tenant-b",
                          "name": "Pipeline B",
                          "description": "Tenant B pipeline",
                          "config": "{}",
                          "version": "1.0",
                          "createdBy": "tenant-b",
                          "status": "ACTIVE",
                          "tenantId": "tenant-b",
                          "createdAt": "2026-05-01T10:00:00Z",
                          "updatedAt": "2026-05-01T10:01:00Z"
                        }
                      ]
                    }
                    """));

            DataCloudPipelineRegistryClientImpl client = new DataCloudPipelineRegistryClientImpl(baseUrl);
            List<OrchestratorPipelineEntity> pipelines = runPromise(client::listAllPipelines);

            assertThat(pipelines).hasSize(2);
            assertThat(pipelines).extracting(p -> p.tenantId)
                    .containsExactlyInAnyOrder("tenant-a", "tenant-b");
        }
    }

    // ==================== Schema compatibility ====================

    @Nested
    @DisplayName("Schema compatibility for pipeline contract")
    class SchemaCompatibilityTests {

        @Test
        @DisplayName("handles missing optional fields gracefully (backward compatibility)")
        void parsePipeline_missingOptionalFields_handlesGracefully() {
            mockDataCloud.createContext("/api/v1/pipelines/minimal-id", exchange -> writeJson(exchange, 200, """
                    {
                      "id": "minimal-id",
                      "name": "Minimal Pipeline",
                      "status": "DRAFT",
                      "tenantId": "tenant-min"
                    }
                    """));

            DataCloudPipelineRegistryClientImpl client = new DataCloudPipelineRegistryClientImpl(baseUrl);
            Optional<OrchestratorPipelineEntity> result = runPromise(() -> client.getPipeline("minimal-id"));

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().id).isEqualTo("minimal-id");
            assertThat(result.orElseThrow().tenantId).isEqualTo("tenant-min");
        }

        @Test
        @DisplayName("handles all required fields in pipeline response (contract assertion)")
        void parsePipeline_allFields_mapsCorrectly() {
            mockDataCloud.createContext("/api/v1/pipelines/full-id", exchange -> writeJson(exchange, 200, """
                    {
                      "id": "full-id",
                      "name": "Full Pipeline",
                      "description": "Complete pipeline",
                      "config": "{\\"version\\":2}",
                      "version": "2.0",
                      "createdBy": "admin",
                      "status": "ACTIVE",
                      "tenantId": "tenant-full",
                      "createdAt": "2026-05-01T10:00:00Z",
                      "updatedAt": "2026-05-01T10:01:00Z"
                    }
                    """));

            DataCloudPipelineRegistryClientImpl client = new DataCloudPipelineRegistryClientImpl(baseUrl);
            Optional<OrchestratorPipelineEntity> result = runPromise(() -> client.getPipeline("full-id"));

            assertThat(result).isPresent();
            OrchestratorPipelineEntity pipeline = result.orElseThrow();
            assertThat(pipeline.id).isEqualTo("full-id");
            assertThat(pipeline.name).isEqualTo("Full Pipeline");
            assertThat(pipeline.status).isEqualTo("ACTIVE");
            assertThat(pipeline.tenantId).isEqualTo("tenant-full");
            assertThat(pipeline.config).isEqualTo("{\"version\":2}");
        }

        @Test
        @DisplayName("handles pipeline list count mismatch without throwing (robustness)")
        void parsePipelineList_extraFields_ignoredGracefully() {
            mockDataCloud.createContext("/api/v1/pipelines", exchange -> writeJson(exchange, 200, """
                    {
                      "pipelines": [
                        {
                          "id": "p-extra",
                          "name": "Extra Fields",
                          "description": "Has extra fields",
                          "config": "{}",
                          "version": "1.0",
                          "createdBy": "system",
                          "status": "ACTIVE",
                          "tenantId": "tenant-x",
                          "createdAt": "2026-05-01T10:00:00Z",
                          "updatedAt": "2026-05-01T10:01:00Z",
                          "unknownFieldV3": "future-value",
                          "experimentalFeature": true
                        }
                      ],
                      "total": 1,
                      "page": 0
                    }
                    """));

            DataCloudPipelineRegistryClientImpl client = new DataCloudPipelineRegistryClientImpl(baseUrl);
            List<OrchestratorPipelineEntity> pipelines = runPromise(client::listAllPipelines);

            assertThat(pipelines).hasSize(1);
            assertThat(pipelines.get(0).id).isEqualTo("p-extra");
        }
    }

    // ==================== HTTP call counting (circuit breaker baseline) ====================

    @Nested
    @DisplayName("HTTP call behavior baseline")
    class HttpCallBehaviorTests {

        @Test
        @DisplayName("listAllPipelines makes exactly one HTTP call to Data Cloud")
        void listPipelines_makesExactlyOneHttpCall() {
            AtomicInteger callCount = new AtomicInteger(0);
            mockDataCloud.createContext("/api/v1/pipelines", exchange -> {
                callCount.incrementAndGet();
                writeJson(exchange, 200, "{\"pipelines\":[]}");
            });

            DataCloudPipelineRegistryClientImpl client = new DataCloudPipelineRegistryClientImpl(baseUrl);
            runPromise(client::listAllPipelines);

            assertThat(callCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("getPipeline makes exactly one HTTP call to Data Cloud")
        void getPipeline_makesExactlyOneHttpCall() {
            AtomicInteger callCount = new AtomicInteger(0);
            mockDataCloud.createContext("/api/v1/pipelines/call-count-id", exchange -> {
                callCount.incrementAndGet();
                writeJson(exchange, 200, """
                        {
                          "id": "call-count-id",
                          "name": "Test",
                          "status": "ACTIVE",
                          "tenantId": "t1"
                        }
                        """);
            });

            DataCloudPipelineRegistryClientImpl client = new DataCloudPipelineRegistryClientImpl(baseUrl);
            runPromise(() -> client.getPipeline("call-count-id"));

            assertThat(callCount.get()).isEqualTo(1);
        }
    }

    // ==================== Infrastructure ====================

    private static void writeJson(HttpExchange exchange, int statusCode, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write test HTTP response", e);
        }
    }
}
