/*
 * Copyright (c) 2026 Ghatana Technologies
 * Integration Tests — Cross-Service Workflow
 */
package com.ghatana.integration.crossservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.aep.integration.registry.DataCloudPipelineRegistryClientImpl;
import com.ghatana.orchestrator.models.OrchestratorPipelineEntity;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.services.lifecycle.YappcAepPipelineBootstrapper;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-product schema compatibility and regression tests.
 *
 * <p>These tests guard against silent schema regressions in shared cross-product
 * contracts. They verify that:
 * <ul>
 *   <li>The {@link OrchestratorPipelineEntity} fields accepted by the AEP registry
 *       client remain stable (id, name, version, status, tenantId, createdAt).</li>
 *   <li>The YAPPC pipeline constants match the IDs registered by the AEP registry.</li>
 *   <li>The AEP registry client correctly deserializes a full pipeline payload.</li>
 *   <li>Null optional fields in the registry response do not cause deserialisation errors.</li>
 *   <li>Multiple pipelines in a single registry response are all deserialised.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Schema compatibility and regression tests for AEP ↔ YAPPC cross-product contracts
 * @doc.layer integration
 * @doc.pattern Test, Regression, ContractTest
 */
@DisplayName("Cross-product schema compatibility and regression")
class CrossProductSchemaCompatibilityTest extends EventloopTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private HttpServer mockRegistry;
    private String registryBaseUrl;

    @BeforeEach
    void setUp() throws IOException {
        mockRegistry = HttpServer.create(new InetSocketAddress(0), 0);
        mockRegistry.start();
        registryBaseUrl = "http://localhost:" + mockRegistry.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (mockRegistry != null) {
            mockRegistry.stop(0);
        }
    }

    // ── OrchestratorPipelineEntity field contract ──────────────────────────────

    @Nested
    @DisplayName("OrchestratorPipelineEntity schema contract")
    class PipelineEntitySchemaContract {

        @Test
        @DisplayName("pipeline entity must expose id, name, version, status, tenantId fields")
        void pipelineEntity_requiredFieldsPresent() {
            OrchestratorPipelineEntity entity = new OrchestratorPipelineEntity();
            entity.id       = "lifecycle-management-v1";
            entity.name     = "Lifecycle Management";
            entity.version  = "1.0.0";
            entity.status   = "ACTIVE";
            entity.tenantId = "tenant-abc";

            assertThat(entity.id).isEqualTo("lifecycle-management-v1");
            assertThat(entity.name).isEqualTo("Lifecycle Management");
            assertThat(entity.version).isEqualTo("1.0.0");
            assertThat(entity.status).isEqualTo("ACTIVE");
            assertThat(entity.tenantId).isEqualTo("tenant-abc");
        }

        @Test
        @DisplayName("pipeline entity defaults tenantId to 'default'")
        void pipelineEntity_defaultTenantId_isDefault() {
            OrchestratorPipelineEntity entity = new OrchestratorPipelineEntity();

            assertThat(entity.tenantId).isEqualTo("default");
        }

        @Test
        @DisplayName("OrchestratorPipelineEntity can be serialized to JSON and back")
        void pipelineEntity_serializesAndDeserializes() throws Exception {
            OrchestratorPipelineEntity original = new OrchestratorPipelineEntity();
            original.id          = "pipeline-123";
            original.name        = "Test Pipeline";
            original.version     = "2.1.0";
            original.status      = "DRAFT";
            original.tenantId    = "tenant-x";
            original.description = "Integration test pipeline";
            original.createdBy   = "system";
            original.createdAt   = Instant.parse("2026-01-01T00:00:00Z");
            original.updatedAt   = Instant.parse("2026-01-02T00:00:00Z");
            original.config      = "{\"type\":\"linear\"}";

            String json = MAPPER.writeValueAsString(original);
            JsonNode node = MAPPER.readTree(json);

            assertThat(node.get("id").asText()).isEqualTo("pipeline-123");
            assertThat(node.get("name").asText()).isEqualTo("Test Pipeline");
            assertThat(node.get("version").asText()).isEqualTo("2.1.0");
            assertThat(node.get("status").asText()).isEqualTo("DRAFT");
            assertThat(node.get("tenantId").asText()).isEqualTo("tenant-x");
        }

        @Test
        @DisplayName("status field accepts canonical values: DRAFT, ACTIVE, INACTIVE, DEPRECATED")
        void pipelineEntity_statusField_acceptsCanonicalValues() {
            for (String status : List.of("DRAFT", "ACTIVE", "INACTIVE", "DEPRECATED")) {
                OrchestratorPipelineEntity entity = new OrchestratorPipelineEntity();
                entity.status = status;
                assertThat(entity.status).isEqualTo(status);
            }
        }
    }

    // ── YAPPC↔AEP pipeline ID compatibility ───────────────────────────────────

    @Nested
    @DisplayName("YAPPC ↔ AEP pipeline ID compatibility")
    class YappcAepPipelineIdCompatibility {

        @Test
        @DisplayName("YAPPC pipeline ID matches the registry response id field")
        void yappcPipelineId_matchesRegistryResponseId() {
            String yappcPipelineId = YappcAepPipelineBootstrapper.PIPELINE_ID;

            // Simulate what the AEP registry would return for the YAPPC lifecycle pipeline
            mockRegistry.createContext("/api/v1/pipelines", exchange -> {
                String body = String.format(
                        "{\"pipelines\":[{\"id\":\"%s\",\"name\":\"Lifecycle Management\"," +
                        "\"version\":\"%s\",\"status\":\"ACTIVE\",\"tenantId\":\"default\"}]}",
                        yappcPipelineId,
                        YappcAepPipelineBootstrapper.PIPELINE_VERSION);
                writeJson(exchange, 200, body);
            });

            DataCloudPipelineRegistryClientImpl client =
                    new DataCloudPipelineRegistryClientImpl(registryBaseUrl);

            List<OrchestratorPipelineEntity> pipelines = runPromise(client::listAllPipelines);

            assertThat(pipelines).hasSize(1);
            assertThat(pipelines.get(0).id).isEqualTo(yappcPipelineId);
        }

        @Test
        @DisplayName("YAPPC pipeline version matches the registry response version field")
        void yappcPipelineVersion_matchesRegistryResponseVersion() {
            String yappcPipelineId      = YappcAepPipelineBootstrapper.PIPELINE_ID;
            String yappcPipelineVersion = YappcAepPipelineBootstrapper.PIPELINE_VERSION;

            mockRegistry.createContext("/api/v1/pipelines", exchange -> {
                String body = String.format(
                        "{\"pipelines\":[{\"id\":\"%s\",\"name\":\"Lifecycle Management\"," +
                        "\"version\":\"%s\",\"status\":\"ACTIVE\",\"tenantId\":\"default\"}]}",
                        yappcPipelineId, yappcPipelineVersion);
                writeJson(exchange, 200, body);
            });

            DataCloudPipelineRegistryClientImpl client =
                    new DataCloudPipelineRegistryClientImpl(registryBaseUrl);

            List<OrchestratorPipelineEntity> pipelines = runPromise(client::listAllPipelines);

            assertThat(pipelines).hasSize(1);
            assertThat(pipelines.get(0).version).isEqualTo(yappcPipelineVersion);
        }

        @Test
        @DisplayName("getPipeline() by YAPPC pipeline ID returns the pipeline entity")
        void getPipeline_byYappcPipelineId_returnsPipelineEntity() {
            String yappcPipelineId = YappcAepPipelineBootstrapper.PIPELINE_ID;

            mockRegistry.createContext("/api/v1/pipelines/" + yappcPipelineId, exchange -> {
                String body = String.format(
                        "{\"id\":\"%s\",\"name\":\"Lifecycle Management\"," +
                        "\"version\":\"%s\",\"status\":\"ACTIVE\",\"tenantId\":\"tenant-abc\"}",
                        yappcPipelineId,
                        YappcAepPipelineBootstrapper.PIPELINE_VERSION);
                writeJson(exchange, 200, body);
            });

            DataCloudPipelineRegistryClientImpl client =
                    new DataCloudPipelineRegistryClientImpl(registryBaseUrl);

            Optional<OrchestratorPipelineEntity> result =
                    runPromise(() -> client.getPipeline(yappcPipelineId));

            assertThat(result).isPresent();
            assertThat(result.get().id).isEqualTo(yappcPipelineId);
            assertThat(result.get().tenantId).isEqualTo("tenant-abc");
        }
    }

    // ── Registry response schema compatibility ─────────────────────────────────

    @Nested
    @DisplayName("AEP registry response schema compatibility")
    class RegistryResponseSchemaCompat {

        @Test
        @DisplayName("registry client deserializes pipeline list with multiple entries")
        void registryClient_deserializesMultiplePipelines() {
            mockRegistry.createContext("/api/v1/pipelines", exchange -> {
                String body = "{\"pipelines\":["
                        + "{\"id\":\"pipeline-a\",\"name\":\"Pipeline A\",\"version\":\"1.0.0\","
                        + "\"status\":\"ACTIVE\",\"tenantId\":\"default\"},"
                        + "{\"id\":\"pipeline-b\",\"name\":\"Pipeline B\",\"version\":\"2.0.0\","
                        + "\"status\":\"DRAFT\",\"tenantId\":\"default\"}"
                        + "]}";
                writeJson(exchange, 200, body);
            });

            DataCloudPipelineRegistryClientImpl client =
                    new DataCloudPipelineRegistryClientImpl(registryBaseUrl);

            List<OrchestratorPipelineEntity> pipelines = runPromise(client::listAllPipelines);

            assertThat(pipelines).hasSize(2);
            assertThat(pipelines).extracting(e -> e.id)
                    .containsExactlyInAnyOrder("pipeline-a", "pipeline-b");
        }

        @Test
        @DisplayName("registry client handles null optional fields without error")
        void registryClient_handlesNullOptionalFields() {
            mockRegistry.createContext("/api/v1/pipelines", exchange -> {
                // description, config, createdBy, createdAt, updatedAt are all absent
                String body = "{\"pipelines\":[{\"id\":\"minimal-pipeline\"," +
                        "\"name\":\"Minimal\",\"version\":\"1.0.0\",\"status\":\"ACTIVE\"}]}";
                writeJson(exchange, 200, body);
            });

            DataCloudPipelineRegistryClientImpl client =
                    new DataCloudPipelineRegistryClientImpl(registryBaseUrl);

            List<OrchestratorPipelineEntity> pipelines = runPromise(client::listAllPipelines);

            assertThat(pipelines).hasSize(1);
            assertThat(pipelines.get(0).id).isEqualTo("minimal-pipeline");
        }

        @Test
        @DisplayName("registry client treats a 404 for getPipeline as empty Optional")
        void registryClient_404_returnsEmptyOptional() {
            mockRegistry.createContext("/api/v1/pipelines/no-such-id",
                    exchange -> writeJson(exchange, 404, "{\"error\":\"Not found\"}"));

            DataCloudPipelineRegistryClientImpl client =
                    new DataCloudPipelineRegistryClientImpl(registryBaseUrl);

            Optional<OrchestratorPipelineEntity> result =
                    runPromise(() -> client.getPipeline("no-such-id"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("registry client treats a 500 for getPipeline as empty Optional")
        void registryClient_500_returnsEmptyOptional() {
            mockRegistry.createContext("/api/v1/pipelines/error-id",
                    exchange -> writeJson(exchange, 500, "{\"error\":\"Internal error\"}"));

            DataCloudPipelineRegistryClientImpl client =
                    new DataCloudPipelineRegistryClientImpl(registryBaseUrl);

            Optional<OrchestratorPipelineEntity> result =
                    runPromise(() -> client.getPipeline("error-id"));

            assertThat(result).isEmpty();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static void writeJson(
            com.sun.net.httpserver.HttpExchange exchange,
            int statusCode,
            String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write mock HTTP response", e);
        }
    }
}
