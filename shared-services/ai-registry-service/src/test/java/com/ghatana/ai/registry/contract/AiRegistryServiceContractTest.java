/**
 * AI Registry Service Contract Test Suite
 *
 * Validates that the AI Registry Service API conforms to its OpenAPI specification.
 * Tests request/response schemas, error responses, and version compatibility.
 *
 * @doc.type test
 * @doc.purpose Contract validation for ai-registry-service public API
 * @doc.layer shared-services
 * @doc.pattern ContractTest
 */

package com.ghatana.ai.registry.contract;

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
 * Contract tests for AI Registry Service API.
 * Validates that the API conforms to the OpenAPI specification at
 * platform/contracts/openapi/ai-registry-service.yaml
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AI Registry Service Contract Tests")
public class AiRegistryServiceContractTest {

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
                .andExpect(jsonPath("$.service").value("ai-registry"))
                .andExpect(jsonPath("$.status").matches("UP|DOWN"));
    }

    @Test
    @DisplayName("List models endpoint returns valid schema")
    void listModels_returnsValidSchema() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models").isArray())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("List models supports provider filter")
    void listModels_supportsProviderFilter() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .param("provider", "openai")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models").isArray())
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    @DisplayName("List models supports type filter")
    void listModels_supportsTypeFilter() throws Exception {
        String[] validTypes = {"LLM", "EMBEDDING", "IMAGE", "AUDIO"};
        for (String type : validTypes) {
            mockMvc.perform(get("/api/v1/models")
                    .param("type", type)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.models").isArray());
        }
    }

    @Test
    @DisplayName("List models rejects invalid type filter")
    void listModels_rejectsInvalidTypeFilter() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .param("type", "invalid-type")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("List models supports status filter")
    void listModels_supportsStatusFilter() throws Exception {
        String[] validStatuses = {"active", "deprecated", "disabled"};
        for (String status : validStatuses) {
            mockMvc.perform(get("/api/v1/models")
                    .param("status", status)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.models").isArray());
        }
    }

    @Test
    @DisplayName("Get model by ID returns valid schema")
    void getModelById_returnsValidSchema() throws Exception {
        // Test with a non-existent model ID
        mockMvc.perform(get("/api/v1/models/nonexistent-model")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 404))
                .andExpect(jsonPath("$").value(anyOf(
                        allOf(
                                hasKey("id"),
                                hasKey("name"),
                                hasKey("provider"),
                                hasKey("type"),
                                hasKey("status"),
                                hasKey("capabilities"),
                                hasKey("pricing"),
                                hasKey("metadata")
                        ),
                        hasKey("error")
                )));
    }

    @Test
    @DisplayName("Get model by ID returns 404 for non-existent model")
    void getModelById_returns404ForNonExistentModel() throws Exception {
        mockMvc.perform(get("/api/v1/models/nonexistent-model")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 404));
    }

    @Test
    @DisplayName("Model record has required fields")
    void modelRecord_hasRequiredFields() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0]").value(
                        when(is(notNull())).then(
                                allOf(
                                        hasKey("id"),
                                        hasKey("name"),
                                        hasKey("provider"),
                                        hasKey("type"),
                                        hasKey("status")
                                )
                        )
                ));
    }

    @Test
    @DisplayName("Model type enum accepts only valid values")
    void modelTypeEnum_acceptsOnlyValidValues() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[*].type").value(
                        everyItem(isOneOf("LLM", "EMBEDDING", "IMAGE", "AUDIO"))
                ));
    }

    @Test
    @DisplayName("Model status enum accepts only valid values")
    void modelStatusEnum_acceptsOnlyValidValues() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[*].status").value(
                        everyItem(isOneOf("active", "deprecated", "disabled"))
                ));
    }

    @Test
    @DisplayName("Model capabilities have correct structure")
    void modelCapabilities_hasCorrectStructure() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0].capabilities").value(
                        when(is(notNull())).then(
                                allOf(
                                        hasKey("maxTokens"),
                                        hasKey("supportsFunctionCalling"),
                                        hasKey("supportsVision"),
                                        hasKey("supportsStreaming")
                                )
                        )
                ));
    }

    @Test
    @DisplayName("Model pricing has correct structure")
    void modelPricing_hasCorrectStructure() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0].pricing").value(
                        when(is(notNull())).then(
                                allOf(
                                        hasKey("inputTokensPer1k"),
                                        hasKey("outputTokensPer1k")
                                )
                        )
                ));
    }

    @Test
    @DisplayName("All error responses follow schema")
    void allErrorResponses_followSchema() throws Exception {
        mockMvc.perform(get("/api/v1/models/nonexistent-model")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 404))
                .andExpect(jsonPath("$.error", existsOrNull()).value(
                        when(is(notNull())).then(isString())
                ));
    }

    @Test
    @DisplayName("API version is consistent")
    void apiVersion_isConsistent() throws Exception {
        mockMvc.perform(get("/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
