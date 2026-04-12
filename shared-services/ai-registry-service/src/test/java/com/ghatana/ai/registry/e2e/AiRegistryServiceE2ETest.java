/**
 * AI Registry Service E2E Test Suite
 *
 * End-to-end tests for the AI Registry service.
 * Tests complete model registry flows including listing, filtering, and model metadata retrieval.
 *
 * @doc.type test
 * @doc.purpose E2E validation for ai-registry-service
 * @doc.layer shared-services
 * @doc.pattern E2ETest
 */

package com.ghatana.ai.registry.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E tests for AI Registry Service.
 * Tests complete model registry flows with real service interactions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("e2e-test")
@DisplayName("AI Registry Service E2E Tests")
public class AiRegistryServiceE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("List all models flow")
    void listAllModelsFlow() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models").isArray())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Filter models by provider flow")
    void filterModelsByProviderFlow() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .param("provider", "openai")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models").isArray())
                .andExpect(jsonPath("$.total").isNumber());

        mockMvc.perform(get("/api/v1/models")
                .param("provider", "anthropic")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models").isArray());
    }

    @Test
    @DisplayName("Filter models by type flow")
    void filterModelsByTypeFlow() throws Exception {
        String[] validTypes = {"LLM", "EMBEDDING", "IMAGE", "AUDIO"};
        for (String type : validTypes) {
            mockMvc.perform(get("/api/v1/models")
                    .param("type", type)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.models").isArray())
                    .andExpect(jsonPath("$.models[*].type").value(everyItem(is(type))));
        }
    }

    @Test
    @DisplayName("Filter models by status flow")
    void filterModelsByStatusFlow() throws Exception {
        String[] validStatuses = {"active", "deprecated", "disabled"};
        for (String status : validStatuses) {
            mockMvc.perform(get("/api/v1/models")
                    .param("status", status)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.models").isArray())
                    .andExpect(jsonPath("$.models[*].status").value(everyItem(is(status))));
        }
    }

    @Test
    @DisplayName("Combined filters flow")
    void combinedFiltersFlow() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .param("provider", "openai")
                .param("type", "LLM")
                .param("status", "active")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models").isArray())
                .andExpect(jsonPath("$.models[*].provider").value(everyItem(is("openai"))))
                .andExpect(jsonPath("$.models[*].type").value(everyItem(is("LLM"))))
                .andExpect(jsonPath("$.models[*].status").value(everyItem(is("active"))));
    }

    @Test
    @DisplayName("Get model by ID flow")
    void getModelByIdFlow() throws Exception {
        // First list models to get a valid ID
        mockMvc.perform(get("/api/v1/models")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models").isArray());

        // Try to get a specific model (may or may not exist)
        mockMvc.perform(get("/api/v1/models/gpt-4")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 404))
                .andExpect(jsonPath("$", anyOf(
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
    @DisplayName("Non-existent model flow")
    void nonExistentModelFlow() throws Exception {
        mockMvc.perform(get("/api/v1/models/non-existent-model-id")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("Model capabilities structure flow")
    void modelCapabilitiesStructureFlow() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0].capabilities", existsOrNull()).value(
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
    @DisplayName("Model pricing structure flow")
    void modelPricingStructureFlow() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0].pricing", existsOrNull()).value(
                        when(is(notNull())).then(
                                allOf(
                                        hasKey("inputTokensPer1k"),
                                        hasKey("outputTokensPer1k")
                                )
                        )
                ));
    }

    @Test
    @DisplayName("Invalid filter value flow")
    void invalidFilterValueFlow() throws Exception {
        // Invalid type
        mockMvc.perform(get("/api/v1/models")
                .param("type", "invalid-type")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Invalid status
        mockMvc.perform(get("/api/v1/models")
                .param("status", "invalid-status")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Health check flow")
    void healthCheckFlow() throws Exception {
        mockMvc.perform(get("/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.service").value("ai-registry"));
    }

    @Test
    @DisplayName("Model type enum validation flow")
    void modelTypeEnumValidationFlow() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[*].type").value(
                        everyItem(isOneOf("LLM", "EMBEDDING", "IMAGE", "AUDIO"))
                ));
    }

    @Test
    @DisplayName("Model status enum validation flow")
    void modelStatusEnumValidationFlow() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[*].status").value(
                        everyItem(isOneOf("active", "deprecated", "disabled"))
                ));
    }

    @Test
    @DisplayName("Model metadata flexibility flow")
    void modelMetadataFlexibilityFlow() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0].metadata", existsOrNull()).value(
                        when(is(notNull())).then(isMap())
                ));
    }

    @Test
    @DisplayName("Empty result set flow")
    void emptyResultSetFlow() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .param("provider", "non-existent-provider")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models").isArray())
                .andExpect(jsonPath("$.models").value(hasSize(0)))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("Model version field flow")
    void modelVersionFieldFlow() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0].version", existsOrNull()).value(
                        when(is(notNull())).then(isString())
                ));
    }
}
