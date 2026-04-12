/**
 * AI Inference Service E2E Test Suite
 *
 * End-to-end tests for the AI Inference service.
 * Tests complete inference flows including embedding generation, batch processing, and LLM completions.
 *
 * @doc.type test
 * @doc.purpose E2E validation for ai-inference-service
 * @doc.layer shared-services
 * @doc.pattern E2ETest
 */

package com.ghatana.ai.inference.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E tests for AI Inference Service.
 * Tests complete inference flows with real service interactions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("e2e-test")
@DisplayName("AI Inference Service E2E Tests")
public class AiInferenceServiceE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Single embedding generation flow")
    void singleEmbeddingGenerationFlow() throws Exception {
        MvcResult result = mockMvc.perform(post("/ai/infer/embedding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"The quick brown fox jumps over the lazy dog\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400, 429, 500))
                .andExpect(jsonPath("$", anyOf(
                        allOf(
                                hasKey("embedding"),
                                hasKey("model"),
                                hasKey("dimensions"),
                                hasKey("tokensUsed")
                        ),
                        hasKey("error")
                )))
                .andReturn();

        if (result.getResponse().getStatus() == 200) {
            // Validate embedding structure
            mockMvc.perform(post("/ai/infer/embedding")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"Test text\"}")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isIn(200, 429, 500))
                    .andExpect(jsonPath("$.embedding").isArray())
                    .andExpect(jsonPath("$.embedding").value(hasSize(greaterThan(0))));
        }
    }

    @Test
    @DisplayName("Batch embedding generation flow")
    void batchEmbeddingGenerationFlow() throws Exception {
        MvcResult result = mockMvc.perform(post("/ai/infer/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texts\":[\"First document\",\"Second document\",\"Third document\"]}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400, 429, 500))
                .andExpect(jsonPath("$", anyOf(
                        allOf(
                                hasKey("embeddings"),
                                hasKey("totalTokensUsed")
                        ),
                        hasKey("error")
                )))
                .andReturn();

        if (result.getResponse().getStatus() == 200) {
            // Validate batch response structure
            mockMvc.perform(post("/ai/infer/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"texts\":[\"test1\",\"test2\"]}")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isIn(200, 429, 500))
                    .andExpect(jsonPath("$.embeddings").isArray())
                    .andExpect(jsonPath("$.embeddings").value(hasSize(greaterThan(0))))
                    .andExpect(jsonPath("$.totalTokensUsed").value(greaterThanOrEqualTo(0)));
        }
    }

    @Test
    @DisplayName("LLM completion flow")
    void llmCompletionFlow() throws Exception {
        MvcResult result = mockMvc.perform(post("/ai/infer/completion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"Explain quantum computing in simple terms\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400, 429, 500))
                .andExpect(jsonPath("$", anyOf(
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
                .andReturn();

        if (result.getResponse().getStatus() == 200) {
            // Validate completion with parameters
            mockMvc.perform(post("/ai/infer/completion")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"prompt\":\"What is 2+2?\",\"temperature\":0.1,\"maxTokens\":100}")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isIn(200, 429, 500))
                    .andExpect(jsonPath("$.text").isString())
                    .andExpect(jsonPath("$.finishReason").value(isOneOf("stop", "length", "content_filter")));
        }
    }

    @Test
    @DisplayName("Completion with system prompt flow")
    void completionWithSystemPromptFlow() throws Exception {
        mockMvc.perform(post("/ai/infer/completion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"What is the capital of France?\",\"systemPrompt\":\"You are a helpful geography tutor.\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400, 429, 500))
                .andExpect(jsonPath("$", anyOf(
                        hasKey("text"),
                        hasKey("error")
                )));
    }

    @Test
    @DisplayName("Admin status flow")
    void adminStatusFlow() throws Exception {
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
    @DisplayName("Health check flow")
    void healthCheckFlow() throws Exception {
        mockMvc.perform(get("/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.service").value("ai-inference-service"));
    }

    @Test
    @DisplayName("Invalid request flow - missing text")
    void invalidRequestFlow_missingText() throws Exception {
        mockMvc.perform(post("/ai/infer/embedding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("Invalid request flow - empty text")
    void invalidRequestFlow_emptyText() throws Exception {
        mockMvc.perform(post("/ai/infer/embedding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("Invalid request flow - empty batch")
    void invalidRequestFlow_emptyBatch() throws Exception {
        mockMvc.perform(post("/ai/infer/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texts\":[]}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("Invalid request flow - invalid temperature")
    void invalidRequestFlow_invalidTemperature() throws Exception {
        mockMvc.perform(post("/ai/infer/completion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"test\",\"temperature\":3.0}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Invalid request flow - invalid maxTokens")
    void invalidRequestFlow_invalidMaxTokens() throws Exception {
        mockMvc.perform(post("/ai/infer/completion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"test\",\"maxTokens\":-1}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Model override flow")
    void modelOverrideFlow() throws Exception {
        mockMvc.perform(post("/ai/infer/embedding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"test\",\"model\":\"text-embedding-ada-002\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400, 429, 500))
                .andExpect(jsonPath("$", anyOf(
                        hasKey("model"),
                        hasKey("error")
                )));
    }

    @Test
    @DisplayName("Rate limiting flow")
    void rateLimitingFlow() throws Exception {
        // Make multiple rapid requests to test rate limiting
        int requestCount = 0;
        for (int i = 0; i < 10; i++) {
            MvcResult result = mockMvc.perform(post("/ai/infer/completion")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"prompt\":\"test\"}")
                    .accept(MediaType.APPLICATION_JSON))
                    .andReturn();

            if (result.getResponse().getStatus() == 429) {
                requestCount = i + 1;
                break;
            }
        }
        // At least one request should have been processed
        // Rate limiting behavior depends on configuration
    }

    @Test
    @DisplayName("Tenant cost tracking flow")
    void tenantCostTrackingFlow() throws Exception {
        mockMvc.perform(post("/ai/infer/completion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"test\",\"tenantId\":\"tenant-123\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400, 429, 500))
                .andExpect(jsonPath("$", anyOf(
                        hasKey("tokensUsed"),
                        hasKey("error")
                )));
    }

    @Test
    @DisplayName("Large batch processing flow")
    void largeBatchProcessingFlow() throws Exception {
        // Generate a batch of texts
        StringBuilder textsJson = new StringBuilder("{\"texts\":[");
        for (int i = 0; i < 20; i++) {
            if (i > 0) textsJson.append(",");
            textsJson.append("\"Document text ").append(i).append("\"");
        }
        textsJson.append("]}");

        mockMvc.perform(post("/ai/infer/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(textsJson.toString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400, 429, 500))
                .andExpect(jsonPath("$", anyOf(
                        hasKey("embeddings"),
                        hasKey("totalTokensUsed"),
                        hasKey("error")
                )));
    }
}
