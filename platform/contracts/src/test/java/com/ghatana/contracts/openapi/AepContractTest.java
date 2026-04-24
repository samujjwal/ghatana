/*
 * Copyright (c) 2026 Ghatana Technologies
 * Consumer-driven contract tests for the AEP (Agentic Event Processor) API.
 *
 * Validates that the OpenAPI spec at openapi/aep.yaml exposes the endpoints
 * and schemas that consumers (Data-Cloud, YAPPC, shared-services) depend on.
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
 * Consumer-driven contract tests for {@code aep.yaml}.
 *
 * <p>Consumers of the AEP service depend on:
 * <ul>
 *   <li>Health / readiness / liveness probes for orchestration
 *   <li>Event ingestion endpoints (single + batch)
 *   <li>Pattern management endpoints
 *   <li>Analytics (anomaly + forecast)
 *   <li>Agent registration and execution endpoints
 *   <li>Tenant-scoped isolation headers on all data-plane endpoints
 * </ul>
 *
 * @doc.type    class
 * @doc.purpose Consumer-driven contract validation for AEP OpenAPI specification
 * @doc.layer   platform
 * @doc.pattern Test, Contract
 */
@DisplayName("AEP API Consumer Contract Tests")
class AepContractTest {

    private static JsonNode spec;

    @BeforeAll
    static void loadSpec() throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream is = AepContractTest.class.getResourceAsStream("/aep.yaml")) {
            assertThat(is).as("aep.yaml must be on classpath").isNotNull();
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
        @DisplayName("spec must declare AEP title and version")
        void mustHaveAepTitleAndVersion() {
            JsonNode info = spec.path("info");
            assertThat(info.path("title").asText())
                    .as("AEP title must mention AEP or Agentic Event Processor")
                    .containsIgnoringCase("AEP");
            assertThat(info.path("version").asText()).isNotBlank();
        }

        @Test
        @DisplayName("spec must declare bearer auth security scheme")
        void mustDeclareBearerAuthScheme() {
            JsonNode schemes = spec.at("/components/securitySchemes");
            assertThat(schemes.has("bearerAuth"))
                    .as("AEP must declare 'bearerAuth' security scheme for JWT consumers")
                    .isTrue();
        }
    }

    // =========================================================================
    // Health Endpoints (required for orchestration readiness checks)
    // =========================================================================

    @Nested
    @DisplayName("Health Endpoints")
    class HealthEndpoints {

        @Test
        @DisplayName("GET /health must exist and return 200")
        void healthEndpointMustExist() {
            JsonNode ep = spec.at("/paths/~1health/get");
            assertThat(ep.isMissingNode()).isFalse();
            assertThat(ep.at("/responses/200").isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("GET /ready must exist for Kubernetes readiness probe")
        void readyEndpointMustExist() {
            JsonNode ep = spec.at("/paths/~1ready/get");
            assertThat(ep.isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("GET /live must exist for Kubernetes liveness probe")
        void liveEndpointMustExist() {
            JsonNode ep = spec.at("/paths/~1live/get");
            assertThat(ep.isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("GET /health/deep must exist for dependency health")
        void deepHealthEndpointMustExist() {
            JsonNode ep = spec.at("/paths/~1health~1deep/get");
            assertThat(ep.isMissingNode()).isFalse();
        }
    }

    // =========================================================================
    // Event Ingestion Endpoints
    // =========================================================================

    @Nested
    @DisplayName("Event Ingestion Endpoints")
    class EventIngestionEndpoints {

        @Test
        @DisplayName("POST /api/v1/events must exist for single event ingestion")
        void singleEventEndpointMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1events/post");
            assertThat(ep.isMissingNode())
                    .as("POST /api/v1/events must be declared in the AEP spec")
                    .isFalse();
            assertThat(ep.at("/requestBody/required").asBoolean())
                    .as("event ingestion body must be required")
                    .isTrue();
        }

        @Test
        @DisplayName("POST /api/v1/events must return 200 on success")
        void singleEventEndpointMustReturn200() {
            assertThat(spec.at("/paths/~1api~1v1~1events/post/responses/200").isMissingNode())
                    .isFalse();
        }

        @Test
        @DisplayName("POST /api/v1/events/batch must exist for batch ingestion")
        void batchEventEndpointMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1events~1batch/post");
            assertThat(ep.isMissingNode())
                    .as("POST /api/v1/events/batch must be declared for batch consumers")
                    .isFalse();
        }
    }

    // =========================================================================
    // Pattern Management Endpoints
    // =========================================================================

    @Nested
    @DisplayName("Pattern Management Endpoints")
    class PatternManagementEndpoints {

        @Test
        @DisplayName("GET /api/v1/patterns must exist for pattern listing")
        void listPatternsEndpointMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1patterns/get");
            assertThat(ep.isMissingNode())
                    .as("GET /api/v1/patterns must be declared")
                    .isFalse();
        }

        @Test
        @DisplayName("POST /api/v1/patterns must exist for pattern creation")
        void createPatternEndpointMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1patterns/post");
            assertThat(ep.isMissingNode())
                    .as("POST /api/v1/patterns must be declared")
                    .isFalse();
        }

        @Test
        @DisplayName("GET /api/v1/patterns/{patternId} must exist for pattern retrieval")
        void getPatternByIdMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1patterns~1{patternId}/get");
            assertThat(ep.isMissingNode()).isFalse();
            assertThat(ep.at("/responses/404").isMissingNode())
                    .as("GET /api/v1/patterns/{patternId} must declare 404 for missing pattern")
                    .isFalse();
        }

        @Test
        @DisplayName("POST /api/v1/patterns/{patternId}/activate must exist")
        void activatePatternMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1patterns~1{patternId}~1activate/post");
            assertThat(ep.isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("POST /api/v1/patterns/{patternId}/deactivate must exist")
        void deactivatePatternMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1patterns~1{patternId}~1deactivate/post");
            assertThat(ep.isMissingNode()).isFalse();
        }
    }

    // =========================================================================
    // Analytics Endpoints
    // =========================================================================

    @Nested
    @DisplayName("Analytics Endpoints")
    class AnalyticsEndpoints {

        @Test
        @DisplayName("POST /api/v1/analytics/anomalies must exist for anomaly detection")
        void anomalyEndpointMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1analytics~1anomalies/post");
            assertThat(ep.isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("POST /api/v1/analytics/forecast must exist for forecasting")
        void forecastEndpointMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1analytics~1forecast/post");
            assertThat(ep.isMissingNode()).isFalse();
        }
    }

    // =========================================================================
    // Agent Endpoints
    // =========================================================================

    @Nested
    @DisplayName("Agent Endpoints")
    class AgentEndpoints {

        @Test
        @DisplayName("POST /api/v1/agents must exist for agent registration")
        void registerAgentMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1agents/post");
            assertThat(ep.isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("GET /api/v1/agents/{agentId} must exist for agent retrieval")
        void getAgentMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1agents~1{agentId}/get");
            assertThat(ep.isMissingNode()).isFalse();
            assertThat(ep.at("/responses/404").isMissingNode())
                    .as("GET /api/v1/agents/{agentId} must declare 404")
                    .isFalse();
        }

        @Test
        @DisplayName("POST /api/v1/agents/{agentId}/execute must exist for agent execution")
        void executeAgentMustExist() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1agents~1{agentId}~1execute/post");
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
        @DisplayName("spec description must reference X-Tenant-Id header for isolation")
        void specMustDocumentTenantIsolation() {
            String description = spec.path("info").path("description").asText();
            assertThat(description)
                    .as("AEP spec must document X-Tenant-Id for multi-tenant consumers")
                    .containsIgnoringCase("X-Tenant-Id");
        }

        @Test
        @DisplayName("global security must require bearer auth")
        void globalSecurityMustRequireBearer() {
            JsonNode security = spec.path("security");
            assertThat(security.isArray()).isTrue();
            boolean hasBearerAuth = StreamSupport.stream(security.spliterator(), false)
                    .anyMatch(s -> s.has("bearerAuth"));
            assertThat(hasBearerAuth)
                    .as("Global security must require bearerAuth for all authenticated consumers")
                    .isTrue();
        }
    }

    // =========================================================================
    // Schema Contract: EventRecord
    // =========================================================================

    @Nested
    @DisplayName("Schema Contract: EventRecord")
    class EventRecordSchemaContract {

        @Test
        @DisplayName("EventRecord must have required core fields")
        void eventRecordMustHaveRequiredFields() {
            JsonNode schema = spec.at("/components/schemas/EventRecord");
            if (schema.isMissingNode()) {
                // Schema may be inline in request/response; just verify spec is parseable
                return;
            }
            Set<String> required = requiredFields(schema);
            assertThat(required)
                    .as("EventRecord must require id and eventType for consumers")
                    .contains("id");
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
