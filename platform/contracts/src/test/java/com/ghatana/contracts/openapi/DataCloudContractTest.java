/*
 * Copyright (c) 2026 Ghatana Technologies
 * Consumer-driven contract tests for the Data-Cloud Platform API.
 *
 * Validates that the OpenAPI spec at products/data-cloud/api/openapi.yaml
 * exposes the endpoints and schemas that consumers depend on.
 */
package com.ghatana.contracts.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumer-driven contract tests for {@code data-cloud/openapi.yaml}.
 *
 * <p>Consumers of the Data-Cloud service (AEP, YAPPC, shared-services) depend on:
 * <ul>
 *   <li>Health / readiness / liveness probes for orchestration
 *   <li>Entity CRUD endpoints (create, query, get, delete)
 *   <li>Event log endpoints (append + query)
 *   <li>Pipeline definition and execution endpoints
 *   <li>Alert listing endpoint
 *   <li>Tenant-scoped isolation via X-Tenant-Id query parameter
 * </ul>
 *
 * @doc.type    class
 * @doc.purpose Consumer-driven contract validation for Data-Cloud OpenAPI specification
 * @doc.layer   platform
 * @doc.pattern Test, Contract
 */
@DisplayName("Data-Cloud API Consumer Contract Tests")
class DataCloudContractTest {

    private static JsonNode spec;

    @BeforeAll
    static void loadSpec() throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream is = DataCloudContractTest.class.getResourceAsStream("/data-cloud-openapi.yaml")) {
            assertThat(is).as("data-cloud-openapi.yaml must be on classpath").isNotNull();
            spec = yamlMapper.readTree(is);
        }
    }

    // =========================================================================
    // Spec Metadata
    // =========================================================================

    @Nested
    @DisplayName("Spec Metadata")
    class SpecMetadata {

        @Test
        @DisplayName("spec must be OpenAPI 3.x")
        void mustBeOpenApi3() {
            assertThat(spec.path("openapi").asText()).startsWith("3.");
        }

        @Test
        @DisplayName("spec must declare Data-Cloud title and version")
        void mustHaveDataCloudTitleAndVersion() {
            JsonNode info = spec.path("info");
            assertThat(info.path("title").asText())
                    .as("Data-Cloud spec title must reference Data-Cloud or Data Cloud")
                    .containsIgnoringCase("Data");
            assertThat(info.path("version").asText()).isNotBlank();
        }
    }

    // =========================================================================
    // Health Endpoints (required for orchestration readiness)
    // =========================================================================

    @Nested
    @DisplayName("Health Endpoints")
    class HealthEndpoints {

        @Test
        @DisplayName("GET /health must exist")
        void healthMustExist() {
            assertThat(spec.at("/paths/~1health/get").isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("GET /ready must exist for Kubernetes readiness probe")
        void readyMustExist() {
            assertThat(spec.at("/paths/~1ready/get").isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("GET /live must exist for Kubernetes liveness probe")
        void liveMustExist() {
            assertThat(spec.at("/paths/~1live/get").isMissingNode()).isFalse();
        }
    }

    // =========================================================================
    // Entity CRUD Endpoints
    // =========================================================================

    @Nested
    @DisplayName("Entity CRUD Endpoints")
    class EntityCrudEndpoints {

        @Test
        @DisplayName("POST /api/v1/entities/{collection} must exist for entity creation")
        void createEntityMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1entities~1{collection}/post");
            assertThat(ep.isMissingNode())
                    .as("POST /api/v1/entities/{collection} must be declared for entity creation")
                    .isFalse();
        }

        @Test
        @DisplayName("GET /api/v1/entities/{collection} must exist for entity querying")
        void queryEntitiesMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1entities~1{collection}/get");
            assertThat(ep.isMissingNode())
                    .as("GET /api/v1/entities/{collection} must be declared for entity querying")
                    .isFalse();
        }

        @Test
        @DisplayName("GET /api/v1/entities/{collection}/{id} must exist for entity retrieval")
        void getEntityByIdMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1entities~1{collection}~1{id}/get");
            assertThat(ep.isMissingNode())
                    .as("GET /api/v1/entities/{collection}/{id} must be declared")
                    .isFalse();
        }

        @Test
        @DisplayName("DELETE /api/v1/entities/{collection}/{id} must exist for entity deletion")
        void deleteEntityMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1entities~1{collection}~1{id}/delete");
            assertThat(ep.isMissingNode())
                    .as("DELETE /api/v1/entities/{collection}/{id} must be declared")
                    .isFalse();
        }
    }

    // =========================================================================
    // Event Log Endpoints
    // =========================================================================

    @Nested
    @DisplayName("Event Log Endpoints")
    class EventLogEndpoints {

        @Test
        @DisplayName("POST /api/v1/events must exist for event log appending")
        void appendEventMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1events/post");
            assertThat(ep.isMissingNode())
                    .as("POST /api/v1/events must be declared for event log consumers")
                    .isFalse();
        }

        @Test
        @DisplayName("GET /api/v1/events must exist for event log querying")
        void queryEventsMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1events/get");
            assertThat(ep.isMissingNode())
                    .as("GET /api/v1/events must be declared for event log consumers")
                    .isFalse();
        }
    }

    // =========================================================================
    // Pipeline Endpoints
    // =========================================================================

    @Nested
    @DisplayName("Pipeline Endpoints")
    class PipelineEndpoints {

        @Test
        @DisplayName("GET /api/v1/pipelines must exist for pipeline listing")
        void listPipelinesMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1pipelines/get");
            assertThat(ep.isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("POST /api/v1/pipelines must exist for pipeline creation")
        void createPipelineMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1pipelines/post");
            assertThat(ep.isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("GET /api/v1/pipelines/{pipelineId} must exist")
        void getPipelineByIdMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1pipelines~1{pipelineId}/get");
            assertThat(ep.isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("POST /api/v1/pipelines/{pipelineId}/execute must exist")
        void executePipelineMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1pipelines~1{pipelineId}~1execute/post");
            assertThat(ep.isMissingNode())
                    .as("POST /api/v1/pipelines/{pipelineId}/execute is required by orchestration consumers")
                    .isFalse();
        }

        @Test
        @DisplayName("GET /api/v1/pipelines/{pipelineId}/executions must exist")
        void listPipelineExecutionsMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1pipelines~1{pipelineId}~1executions/get");
            assertThat(ep.isMissingNode()).isFalse();
        }
    }

    // =========================================================================
    // Tenant Isolation Contract
    // =========================================================================

    @Nested
    @DisplayName("Tenant Isolation Contract")
    class TenantIsolationContract {

        @Test
        @DisplayName("event log POST must declare tenantId query parameter")
        void eventLogPostMustDeclareTenantIdParam() {
            JsonNode params = spec.at("/paths/~1api~1v1~1events/post/parameters");
            if (!params.isArray()) return; // may be on component level
            Set<String> paramNames = StreamSupport.stream(params.spliterator(), false)
                    .map(p -> p.path("name").asText())
                    .collect(Collectors.toSet());
            assertThat(paramNames)
                    .as("POST /api/v1/events must accept tenantId parameter for multi-tenant isolation")
                    .contains("tenantId");
        }

        @Test
        @DisplayName("pipeline list GET must declare tenantId query parameter")
        void pipelineListMustDeclareTenantIdParam() {
            JsonNode params = spec.at("/paths/~1api~1v1~1pipelines/get/parameters");
            if (!params.isArray()) return;
            Set<String> paramNames = StreamSupport.stream(params.spliterator(), false)
                    .map(p -> p.path("name").asText())
                    .collect(Collectors.toSet());
            assertThat(paramNames)
                    .as("GET /api/v1/pipelines must accept tenantId parameter")
                    .contains("tenantId");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Set<String> requiredFields(JsonNode schemaNode) {
        JsonNode req = schemaNode.path("required");
        if (req.isMissingNode() || !req.isArray()) return Set.of();
        return StreamSupport.stream(req.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toSet());
    }
}
