/*
 * Copyright (c) 2025 Ghatana Technologies
 * Consumer-driven contract tests for the AI Inference Service API.
 *
 * Validates that the OpenAPI spec at openapi/ai-inference-service.yaml is
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
 * Consumer-driven contract tests for <code>ai-inference-service.yaml</code>.
 *
 * <p>Products (YAPPC, AEP, Audio-Video) depend on the AI inference service for
 * LLM completions and embedding generation. These tests ensure the contract
 * cannot drift without detection.
 */
class AiInferenceServiceContractTest {

    private static JsonNode spec;

    @BeforeAll
    static void loadSpec() throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream is = AiInferenceServiceContractTest.class.getResourceAsStream(
                "/ai-inference-service.yaml")) {
            assertThat(is).as("ai-inference-service.yaml must be on classpath").isNotNull();
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
        assertThat(info.path("title").asText()).contains("AI Inference");
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
        void shouldExposeSingleEmbeddingEndpoint() {
            JsonNode ep = spec.at("/paths/~1ai~1infer~1embedding/post");
            assertThat(ep.isMissingNode()).isFalse();
            assertThat(ep.path("operationId").asText()).isEqualTo("generateEmbedding");
            assertThat(ep.at("/requestBody/required").asBoolean()).isTrue();
        }

        @Test
        void shouldExposeBatchEmbeddingEndpoint() {
            JsonNode ep = spec.at("/paths/~1ai~1infer~1embeddings/post");
            assertThat(ep.isMissingNode()).isFalse();
            assertThat(ep.path("operationId").asText()).isEqualTo("generateBatchEmbeddings");
        }

        @Test
        void shouldExposeCompletionEndpoint() {
            JsonNode ep = spec.at("/paths/~1ai~1infer~1completion/post");
            assertThat(ep.isMissingNode()).isFalse();
            assertThat(ep.path("operationId").asText()).isEqualTo("generateCompletion");
            assertThat(ep.at("/requestBody/required").asBoolean()).isTrue();
        }

        @Test
        void shouldExposeAdminStatusEndpoint() {
            JsonNode ep = spec.at("/paths/~1ai~1admin~1status/get");
            assertThat(ep.isMissingNode()).isFalse();
            assertThat(ep.path("operationId").asText()).isEqualTo("adminStatus");
        }

        @Test
        void completionEndpointShouldDeclareRateLimitResponse() {
            JsonNode responses = spec.at("/paths/~1ai~1infer~1completion/post/responses");
            assertThat(responses.has("429")).as("completion should declare 429 rate-limit response").isTrue();
        }

        @Test
        void embeddingEndpointShouldDeclareRateLimitResponse() {
            JsonNode responses = spec.at("/paths/~1ai~1infer~1embedding/post/responses");
            assertThat(responses.has("429")).as("embedding should declare 429 rate-limit response").isTrue();
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
        void embeddingRequestShouldRequireText() {
            Set<String> required = requiredFields(schema("EmbeddingRequest"));
            assertThat(required).contains("text");
        }

        @Test
        void embeddingResponseShouldRequireEmbedding() {
            Set<String> required = requiredFields(schema("EmbeddingResponse"));
            assertThat(required).contains("embedding");
            // embedding must be an array of numbers
            JsonNode items = schema("EmbeddingResponse").at("/properties/embedding/items");
            assertThat(items.path("type").asText()).isEqualTo("number");
        }

        @Test
        void batchEmbeddingRequestShouldRequireTexts() {
            Set<String> required = requiredFields(schema("BatchEmbeddingRequest"));
            assertThat(required).contains("texts");
            // texts must be an array of strings
            JsonNode items = schema("BatchEmbeddingRequest").at("/properties/texts/items");
            assertThat(items.path("type").asText()).isEqualTo("string");
        }

        @Test
        void batchEmbeddingResponseShouldRequireEmbeddings() {
            Set<String> required = requiredFields(schema("BatchEmbeddingResponse"));
            assertThat(required).contains("embeddings");
        }

        @Test
        void completionRequestShouldRequirePrompt() {
            Set<String> required = requiredFields(schema("CompletionRequest"));
            assertThat(required).contains("prompt");
            // Should have optional parameters
            JsonNode props = schema("CompletionRequest").path("properties");
            assertThat(props.has("systemPrompt")).isTrue();
            assertThat(props.has("model")).isTrue();
            assertThat(props.has("temperature")).isTrue();
            assertThat(props.has("maxTokens")).isTrue();
            assertThat(props.has("tenantId")).isTrue();
        }

        @Test
        void completionResponseShouldRequireText() {
            Set<String> required = requiredFields(schema("CompletionResponse"));
            assertThat(required).contains("text");
            // Should expose token usage fields
            JsonNode props = schema("CompletionResponse").path("properties");
            assertThat(props.has("tokensUsed")).isTrue();
            assertThat(props.has("promptTokens")).isTrue();
            assertThat(props.has("completionTokens")).isTrue();
            assertThat(props.has("finishReason")).isTrue();
        }

        @Test
        void completionResponseFinishReasonShouldHaveExpectedValues() {
            JsonNode finishReason = schema("CompletionResponse")
                    .at("/properties/finishReason");
            Set<String> enumValues = StreamSupport.stream(
                    finishReason.path("enum").spliterator(), false)
                    .map(JsonNode::asText)
                    .collect(Collectors.toSet());
            assertThat(enumValues).containsExactlyInAnyOrder("stop", "length", "content_filter");
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
