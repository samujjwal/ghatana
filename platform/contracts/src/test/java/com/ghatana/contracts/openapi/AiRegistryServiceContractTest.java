/*
 * Copyright (c) 2025 Ghatana Technologies
 * Consumer-driven contract tests for the AI Registry Service API.
 *
 * Validates that the OpenAPI spec at openapi/ai-registry-service.yaml is
 * structurally sound and that the schemas products depend on are present
 * with the expected required fields.
 */
package com.ghatana.contracts.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.*;

/**
 * Consumer-driven contract tests for <code>ai-registry-service.yaml</code>.
 *
 * <p>Products discover and select AI models through the registry.
 * These tests ensure the model listing and detail schemas cannot
 * drift without detection.
 */
class AiRegistryServiceContractTest {

    private static JsonNode spec;

    @BeforeAll
    static void loadSpec() throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream is = AiRegistryServiceContractTest.class.getResourceAsStream(
                "/ai-registry-service.yaml")) {
            assertThat(is).as("ai-registry-service.yaml must be on classpath").isNotNull();
            spec = yamlMapper.readTree(is);
        }
    }

    // =========================================================================
    // Spec metadata
    // =========================================================================

    @Test
    void shouldHaveCorrectOpenApiVersion() {
        assertThat(spec.path("openapi").asText()).startsWith("3.");
    }

    @Test
    void shouldHaveServiceInfo() {
        JsonNode info = spec.path("info");
        assertThat(info.path("title").asText()).contains("AI Registry");
        assertThat(info.path("version").asText()).isNotBlank();
    }

    // =========================================================================
    // Paths — products rely on these endpoints
    // =========================================================================

    @Nested
    class Paths {

        @Test
        void shouldExposeHealthEndpoint() {
            assertThat(spec.at("/paths/~1health/get/operationId").asText())
                    .isEqualTo("healthCheck");
        }

        @Test
        void shouldExposeListModelsEndpoint() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1models/get");
            assertThat(ep.isMissingNode()).isFalse();
            assertThat(ep.path("operationId").asText()).isEqualTo("listModels");
        }

        @Test
        void listModelsShouldSupportFiltering() {
            JsonNode params = spec.at("/paths/~1api~1v1~1models/get/parameters");
            assertThat(params.isArray()).isTrue();

            Set<String> paramNames = StreamSupport.stream(params.spliterator(), false)
                    .map(p -> p.path("name").asText())
                    .collect(Collectors.toSet());
            assertThat(paramNames).contains("provider", "type", "status");
        }

        @Test
        void shouldExposeGetModelByIdEndpoint() {
            JsonNode ep = spec.at("/paths/~1api~1v1~1models~1{id}/get");
            assertThat(ep.isMissingNode()).isFalse();
            assertThat(ep.path("operationId").asText()).isEqualTo("getModelById");
        }

        @Test
        void getModelByIdShouldDeclare404() {
            JsonNode responses = spec.at("/paths/~1api~1v1~1models~1{id}/get/responses");
            assertThat(responses.has("404")).as("getModelById should declare 404").isTrue();
        }
    }

    // =========================================================================
    // Schemas — required fields that consumers depend on
    // =========================================================================

    @Nested
    class Schemas {

        private JsonNode schema(String name) {
            return spec.at("/components/schemas/" + name);
        }

        @Test
        void modelListResponseShouldRequireModelsAndTotal() {
            Set<String> required = requiredFields(schema("ModelListResponse"));
            assertThat(required).containsExactlyInAnyOrder("models", "total");
        }

        @Test
        void modelRecordShouldRequireCoreFields() {
            Set<String> required = requiredFields(schema("ModelRecord"));
            assertThat(required).containsExactlyInAnyOrder("id", "name", "provider", "type", "status");
        }

        @Test
        void modelRecordShouldExposeCapabilitiesAndPricing() {
            JsonNode props = schema("ModelRecord").path("properties");
            assertThat(props.has("capabilities")).isTrue();
            assertThat(props.has("pricing")).isTrue();
            assertThat(props.has("metadata")).isTrue();
        }

        @Test
        void modelTypeShouldHaveExpectedValues() {
            JsonNode typeField = schema("ModelRecord").at("/properties/type");
            Set<String> enumValues = StreamSupport.stream(
                    typeField.path("enum").spliterator(), false)
                    .map(JsonNode::asText)
                    .collect(Collectors.toSet());
            assertThat(enumValues).containsExactlyInAnyOrder("LLM", "EMBEDDING", "IMAGE", "AUDIO");
        }

        @Test
        void modelStatusShouldHaveExpectedValues() {
            JsonNode statusField = schema("ModelRecord").at("/properties/status");
            Set<String> enumValues = StreamSupport.stream(
                    statusField.path("enum").spliterator(), false)
                    .map(JsonNode::asText)
                    .collect(Collectors.toSet());
            assertThat(enumValues).containsExactlyInAnyOrder("active", "deprecated", "disabled");
        }

        @Test
        void capabilitiesShouldIncludeExpectedFields() {
            JsonNode caps = schema("ModelRecord").at("/properties/capabilities/properties");
            assertThat(caps.has("maxTokens")).isTrue();
            assertThat(caps.has("supportsFunctionCalling")).isTrue();
            assertThat(caps.has("supportsVision")).isTrue();
            assertThat(caps.has("supportsStreaming")).isTrue();
        }

        @Test
        void pricingShouldIncludeTokenCosts() {
            JsonNode pricing = schema("ModelRecord").at("/properties/pricing/properties");
            assertThat(pricing.has("inputTokensPer1k")).isTrue();
            assertThat(pricing.has("outputTokensPer1k")).isTrue();
        }

        @Test
        void errorResponseShouldRequireError() {
            Set<String> required = requiredFields(schema("ErrorResponse"));
            assertThat(required).contains("error");
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
