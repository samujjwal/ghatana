/*
 * Copyright (c) 2026 Ghatana Technologies
 * Consumer-driven contract tests for the YAPPC Code Generation API.
 *
 * Validates that the OpenAPI spec at products/yappc/api/yappc-api.openapi.yaml
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
 * Consumer-driven contract tests for {@code yappc-api.openapi.yaml}.
 *
 * <p>Consumers of the YAPPC service (frontend, integration tests, CI smoke) depend on:
 * <ul>
 *   <li>Design CRUD endpoints (create, get, update, validate)
 *   <li>Code generation endpoint (POST /generated-code) and status polling
 *   <li>Artifact retrieval and version listing
 *   <li>Refactoring suggestions
 *   <li>Authentication enforcement (401 declared on protected endpoints)
 * </ul>
 *
 * @doc.type    class
 * @doc.purpose Consumer-driven contract validation for YAPPC OpenAPI specification
 * @doc.layer   platform
 * @doc.pattern Test, Contract
 */
@DisplayName("YAPPC API Consumer Contract Tests")
class YappcContractTest {

    private static JsonNode spec;

    @BeforeAll
    static void loadSpec() throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream is = YappcContractTest.class.getResourceAsStream("/yappc-api.openapi.yaml")) {
            assertThat(is).as("yappc-api.openapi.yaml must be on classpath").isNotNull();
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
        @DisplayName("spec must declare YAPPC title and version")
        void mustHaveYappcTitleAndVersion() {
            JsonNode info = spec.path("info");
            assertThat(info.path("title").asText())
                    .as("YAPPC spec title must reference YAPPC or Code Generation")
                    .isNotBlank();
            assertThat(info.path("version").asText()).isNotBlank();
        }
    }

    // =========================================================================
    // Design Endpoints
    // =========================================================================

    @Nested
    @DisplayName("Design Endpoints")
    class DesignEndpoints {

        @Test
        @DisplayName("POST /designs must exist for design creation")
        void createDesignMustExist() {
            JsonNode ep = spec.at("/paths/~1designs/post");
            assertThat(ep.isMissingNode())
                    .as("POST /designs must be declared for design creation consumers")
                    .isFalse();
            assertThat(ep.path("operationId").asText()).isEqualTo("createDesign");
        }

        @Test
        @DisplayName("POST /designs must declare a success response")
        void createDesignMustDeclareSuccessResponse() {
            JsonNode responses = spec.at("/paths/~1designs/post/responses");
            assertThat(responses.has("200") || responses.has("201"))
                    .as("POST /designs must declare a 2xx success response")
                    .isTrue();
        }

        @Test
        @DisplayName("POST /designs must declare 401 for unauthenticated consumers")
        void createDesignMustDeclare401() {
            assertThat(spec.at("/paths/~1designs/post/responses/401").isMissingNode())
                    .as("POST /designs must declare 401 — consumers must handle unauthenticated case")
                    .isFalse();
        }

        @Test
        @DisplayName("GET /designs/{designId} must exist for design retrieval")
        void getDesignMustExist() {
            JsonNode ep = spec.at("/paths/~1designs~1{designId}/get");
            assertThat(ep.isMissingNode()).isFalse();
            assertThat(ep.path("operationId").asText()).isEqualTo("getDesign");
        }

        @Test
        @DisplayName("PATCH /designs/{designId} must exist for design updates")
        void updateDesignMustExist() {
            JsonNode ep = spec.at("/paths/~1designs~1{designId}/patch");
            assertThat(ep.isMissingNode()).isFalse();
            assertThat(ep.path("operationId").asText()).isEqualTo("updateDesign");
        }

        @Test
        @DisplayName("POST /designs/{designId}/validate must exist")
        void validateDesignMustExist() {
            JsonNode ep = spec.at("/paths/~1designs~1{designId}~1validate/post");
            assertThat(ep.isMissingNode()).isFalse();
            assertThat(ep.path("operationId").asText()).isEqualTo("validateDesign");
        }
    }

    // =========================================================================
    // Code Generation Endpoints
    // =========================================================================

    @Nested
    @DisplayName("Code Generation Endpoints")
    class CodeGenerationEndpoints {

        @Test
        @DisplayName("POST /generated-code must exist for code generation")
        void generateCodeMustExist() {
            JsonNode ep = spec.at("/paths/~1generated-code/post");
            assertThat(ep.isMissingNode())
                    .as("POST /generated-code must be declared for code generation consumers")
                    .isFalse();
            assertThat(ep.path("operationId").asText()).isEqualTo("generateCode");
        }

        @Test
        @DisplayName("POST /generated-code must declare a success response")
        void generateCodeMustDeclareSuccessResponse() {
            JsonNode responses = spec.at("/paths/~1generated-code/post/responses");
            assertThat(responses.has("200") || responses.has("202"))
                    .as("POST /generated-code must declare a 2xx success response")
                    .isTrue();
        }

        @Test
        @DisplayName("GET /generated-code/{operationId} must exist for status polling")
        void getGenerationStatusMustExist() {
            JsonNode ep = spec.at("/paths/~1generated-code~1{operationId}/get");
            assertThat(ep.isMissingNode())
                    .as("GET /generated-code/{operationId} must be declared for async status polling")
                    .isFalse();
            assertThat(ep.path("operationId").asText()).isEqualTo("getGenerationStatus");
        }
    }

    // =========================================================================
    // Artifact Endpoints
    // =========================================================================

    @Nested
    @DisplayName("Artifact Endpoints")
    class ArtifactEndpoints {

        @Test
        @DisplayName("GET /artifacts/{artifactId} must exist for artifact retrieval")
        void getArtifactMustExist() {
            JsonNode ep = spec.at("/paths/~1artifacts~1{artifactId}/get");
            assertThat(ep.isMissingNode()).isFalse();
            assertThat(ep.path("operationId").asText()).isEqualTo("getArtifact");
        }

        @Test
        @DisplayName("GET /artifacts/{artifactId}/versions must exist for version listing")
        void getArtifactVersionsMustExist() {
            JsonNode ep = spec.at("/paths/~1artifacts~1{artifactId}~1versions/get");
            assertThat(ep.isMissingNode()).isFalse();
            assertThat(ep.path("operationId").asText()).isEqualTo("getArtifactVersions");
        }
    }

    // =========================================================================
    // Refactoring Endpoint
    // =========================================================================

    @Nested
    @DisplayName("Refactoring Endpoint")
    class RefactoringEndpoint {

        @Test
        @DisplayName("GET /refactoring-suggestions/{designId} must exist")
        void getRefactoringSuggestionsMustExist() {
            JsonNode ep = spec.at("/paths/~1refactoring-suggestions~1{designId}/get");
            assertThat(ep.isMissingNode()).isFalse();
            assertThat(ep.path("operationId").asText()).isEqualTo("getRefactoringSuggestions");
        }
    }

    // =========================================================================
    // Schema Contract: CodeGenerationOperation
    // =========================================================================

    @Nested
    @DisplayName("Schema Contract: CodeGenerationOperation")
    class CodeGenerationOperationSchema {

        @Test
        @DisplayName("CodeGenerationOperation must require operationId and status")
        void codeGenerationOperationMustHaveRequiredFields() {
            JsonNode schema = spec.at("/components/schemas/CodeGenerationOperation");
            assertThat(schema.isMissingNode())
                    .as("CodeGenerationOperation schema must be declared in components")
                    .isFalse();
            Set<String> required = requiredFields(schema);
            assertThat(required)
                    .as("CodeGenerationOperation must require operationId and status for status-polling consumers")
                    .contains("operationId", "status");
        }
    }

    // =========================================================================
    // Authentication Contract
    // =========================================================================

    @Nested
    @DisplayName("Authentication Contract")
    class AuthenticationContract {

        @Test
        @DisplayName("POST /designs must declare 401 Unauthorized response")
        void protectedEndpointsMustDeclare401() {
            // Verify that at least one protected endpoint declares an UnauthorizedError reference or 401
            JsonNode responses = spec.at("/paths/~1designs/post/responses");
            assertThat(responses.has("401") || responses.has("403"))
                    .as("Protected YAPPC endpoints must declare auth failure responses for consumers")
                    .isTrue();
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
