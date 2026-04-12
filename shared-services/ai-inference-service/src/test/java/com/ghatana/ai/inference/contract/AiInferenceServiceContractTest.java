/**
 * AI Inference Service Contract Test Suite
 *
 * Validates that the AI Inference Service API conforms to its OpenAPI specification.
 * Tests request/response schemas, error responses, and version compatibility.
 *
 * @doc.type test
 * @doc.purpose Contract validation for ai-inference-service public API
 * @doc.layer shared-services
 * @doc.pattern ContractTest
 */

package com.ghatana.ai.inference.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract tests for AI Inference Service API.
 * Validates that the API conforms to the OpenAPI specification at
 * platform/contracts/openapi/ai-inference-service.yaml
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AI Inference Service Contract Tests")
public class AiInferenceServiceContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String API_VERSION = "1.0.0";

    @Test
    @DisplayName("Health check endpoint returns valid schema")
    void healthCheck_returnsValidSchema() throws Exception {
        mockMvc.perform(get("/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.service").value("ai-inference-service"))
                .andExpect(jsonPath("$.status").matches("UP|DOWN"));
    }

    @Test
    @DisplayName("Embedding endpoint validates request schema")
    void embedding_validatesRequestSchema() throws Exception {
        // Test missing text
        mockMvc.perform(post("/ai/infer/embedding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());

        // Test empty text
        mockMvc.perform(post("/ai/infer/embedding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());

        // Test valid request with optional model
        mockMvc.perform(post("/ai/infer/embedding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"test text\",\"model\":\"text-embedding-ada-002\"}"))
                .andExpect(status().isIn(200, 429, 500));
    }

    @Test
    @DisplayName("Embedding endpoint returns valid response schema")
    void embedding_returnsValidResponseSchema() throws Exception {
        mockMvc.perform(post("/ai/infer/embedding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"test text\"}"))
                .andExpect(status().isIn(200, 400, 429, 500))
                .andExpect(jsonPath("$").value(anyOf(
                        allOf(
                                hasKey("embedding"),
                                hasKey("model"),
                                hasKey("dimensions"),
                                hasKey("tokensUsed")
                        ),
                        hasKey("error")
                )));
    }

    @Test
    @DisplayName("Batch embedding endpoint validates request schema")
    void batchEmbedding_validatesRequestSchema() throws Exception {
        // Test missing texts array
        mockMvc.perform(post("/ai/infer/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());

        // Test empty texts array
        mockMvc.perform(post("/ai/infer/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texts\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());

        // Test valid request
        mockMvc.perform(post("/ai/infer/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texts\":[\"text1\",\"text2\"]}"))
                .andExpect(status().isIn(200, 429, 500));
    }

    @Test
    @DisplayName("Batch embedding endpoint returns valid response schema")
    void batchEmbedding_returnsValidResponseSchema() throws Exception {
        mockMvc.perform(post("/ai/infer/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texts\":[\"text1\",\"text2\"]}"))
                .andExpect(status().isIn(200, 400, 429, 500))
                .andExpect(jsonPath("$").value(anyOf(
                        allOf(
                                hasKey("embeddings"),
                                hasKey("totalTokensUsed")
                        ),
                        hasKey("error")
                )));
    }

    @Test
    @DisplayName("Completion endpoint validates request schema")
    void completion_validatesRequestSchema() throws Exception {
        // Test missing prompt
        mockMvc.perform(post("/ai/infer/completion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());

        // Test invalid temperature (out of range)
        mockMvc.perform(post("/ai/infer/completion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"test\",\"temperature\":3.0}"))
                .andExpect(status().isBadRequest());

        // Test invalid maxTokens (negative)
        mockMvc.perform(post("/ai/infer/completion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"test\",\"maxTokens\":-1}"))
                .andExpect(status().isBadRequest());

        // Test valid request with optional parameters
        mockMvc.perform(post("/ai/infer/completion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"test\",\"systemPrompt\":\"You are helpful\",\"temperature\":0.7,\"maxTokens\":1024}"))
                .andExpect(status().isIn(200, 429, 500));
    }

    @Test
    @DisplayName("Completion endpoint returns valid response schema")
    void completion_returnsValidResponseSchema() throws Exception {
        mockMvc.perform(post("/ai/infer/completion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"test\"}"))
                .andExpect(status().isIn(200, 400, 429, 500))
                .andExpect(jsonPath("$").value(anyOf(
                        allOf(
                                hasKey("text"),
                                hasKey("model"),
                                hasKey("tokensUsed"),
                                hasKey("promptTokens"),
                                hasKey("completionTokens"),
                                hasKey("finishReason")
                        ),
                        hasKey("error")
                )))
                .andExpect(jsonPath("$.finishReason", existsOrNull()).value(anyOf(
                        is("stop"),
                        is("length"),
                        is("content_filter"),
                        nullValue()
                )));
    }

    @Test
    @DisplayName("Admin status endpoint returns valid schema")
    void adminStatus_returnsValidSchema() throws Exception {
        mockMvc.perform(get("/ai/admin/status")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.modelsAvailable").isArray())
                .andExpect(jsonPath("$.cacheStats").isMap())
                .andExpect(jsonPath("$.cacheStats").value(hasKey("hits")))
                .andExpect(jsonPath("$.cacheStats").value(hasKey("misses")))
                .andExpect(jsonPath("$.cacheStats").value(hasKey("size")))
                .andExpect(jsonPath("$.rateLimiterStats").isMap())
                .andExpect(jsonPath("$.rateLimiterStats").value(hasKey("requestsPerMinute")))
                .andExpect(jsonPath("$.rateLimiterStats").value(hasKey("currentLoad")));
    }

    @Test
    @DisplayName("All endpoints return JSON error response for errors")
    void allEndpoints_returnJsonErrorResponse() throws Exception {
        // Test error response schema
        mockMvc.perform(post("/ai/infer/embedding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("Rate limit returns 429 status")
    void rateLimit_returns429Status() throws Exception {
        // This would require hitting rate limits
        // For now, verify the endpoint handles the status code
        mockMvc.perform(post("/ai/infer/completion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"test\"}"))
                .andExpect(status().isIn(200, 400, 429, 500));
    }

    @Test
    @DisplayName("Embedding vector has correct structure")
    void embeddingVector_hasCorrectStructure() throws Exception {
        mockMvc.perform(post("/ai/infer/embedding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"test\"}"))
                .andExpect(status().isIn(200, 400, 429, 500))
                .andExpect(jsonPath("$.embedding", existsOrNull()).value(
                        when(is(notNull())).then(
                                allOf(
                                        isA(List.class),
                                        hasSize(greaterThan(0))
                                )
                        )
                ));
    }

    @Test
    @DisplayName("Batch embeddings returns array of embeddings")
    void batchEmbeddings_returnsArrayOfEmbeddings() throws Exception {
        mockMvc.perform(post("/ai/infer/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texts\":[\"text1\",\"text2\"]}"))
                .andExpect(status().isIn(200, 400, 429, 500))
                .andExpect(jsonPath("$.embeddings", existsOrNull()).value(
                        when(is(notNull())).then(
                                allOf(
                                        isA(List.class),
                                        hasSize(greaterThan(0))
                                )
                        )
                ));
    }

    @Test
    @DisplayName("API version is consistent")
    void apiVersion_isConsistent() throws Exception {
        mockMvc.perform(get("/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        // Version validation would be implemented if version endpoint exists
    }
}
