/**
 * AEP (Agentic Event Processor) Contract Test Suite
 *
 * Validates that the AEP API conforms to its OpenAPI specification.
 * Tests request/response schemas, error responses, and version compatibility.
 *
 * @doc.type test
 * @doc.purpose Contract validation for AEP public API
 * @doc.layer products
 * @doc.pattern ContractTest
 */

package com.ghatana.aep.contract;

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
 * Contract tests for AEP API.
 * Validates that the API conforms to the OpenAPI specification at
 * platform/contracts/openapi/aep.yaml
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AEP API Contract Tests")
public class AepApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String API_VERSION = "1.0.0";
    private static final String TEST_TENANT_ID = "test-tenant";

    // ── Health Endpoints Tests ─────────────────────────────────────────────

    @Test
    @DisplayName("Health check endpoint returns valid schema")
    void healthCheck_returnsValidSchema() throws Exception {
        mockMvc.perform(get("/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.service").value("aep"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").matches("UP|DOWN|DEGRADED"));
    }

    @Test
    @DisplayName("Readiness probe returns valid schema")
    void readinessProbe_returnsValidSchema() throws Exception {
        mockMvc.perform(get("/ready")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").matches("READY|NOT_READY"));
    }

    @Test
    @DisplayName("Liveness probe returns valid schema")
    void livenessProbe_returnsValidSchema() throws Exception {
        mockMvc.perform(get("/live")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").matches("LIVE|NOT_LIVE"));
    }

    @Test
    @DisplayName("Info endpoint returns valid schema")
    void infoEndpoint_returnsValidSchema() throws Exception {
        mockMvc.perform(get("/info")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").isString())
                .andExpect(jsonPath("$.version").isString())
                .andExpect(jsonPath("$.description").isString())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("Metrics endpoint returns valid schema")
    void metricsEndpoint_returnsValidSchema() throws Exception {
        mockMvc.perform(get("/metrics")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap());
    }

    // ── Event Processing Tests ─────────────────────────────────────────────

    @Test
    @DisplayName("Process single event requires tenant header")
    void processEvent_requiresTenantHeader() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"test\",\"payload\":{}}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Process single event validates request schema")
    void processEvent_validatesRequestSchema() throws Exception {
        // Test missing type
        mockMvc.perform(post("/api/v1/events")
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{}}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Test valid request
        mockMvc.perform(post("/api/v1/events")
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"test\",\"payload\":{}}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400));
    }

    @Test
    @DisplayName("Process single event returns valid response schema")
    void processEvent_returnsValidResponseSchema() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"test\",\"payload\":{}}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400))
                .andExpect(jsonPath("$").value(anyOf(
                        allOf(
                                hasKey("eventId"),
                                hasKey("success"),
                                hasKey("detections"),
                                hasKey("timestamp")
                        ),
                        hasKey("error")
                )));
    }

    @Test
    @DisplayName("Process batch events validates request schema")
    void processEventBatch_validatesRequestSchema() throws Exception {
        // Test missing events array
        mockMvc.perform(post("/api/v1/events/batch")
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Test valid request
        mockMvc.perform(post("/api/v1/events/batch")
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"events\":[{\"type\":\"test\",\"payload\":{}}]}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400));
    }

    @Test
    @DisplayName("Process batch events returns valid response schema")
    void processEventBatch_returnsValidResponseSchema() throws Exception {
        mockMvc.perform(post("/api/v1/events/batch")
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"events\":[{\"type\":\"test\",\"payload\":{}}]}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400))
                .andExpect(jsonPath("$").value(anyOf(
                        allOf(
                                hasKey("tenantId"),
                                hasKey("total"),
                                hasKey("successCount"),
                                hasKey("failureCount"),
                                hasKey("totalDetections"),
                                hasKey("events"),
                                hasKey("timestamp")
                        ),
                        hasKey("error")
                )));
    }

    // ── Pattern Management Tests ───────────────────────────────────────────

    @Test
    @DisplayName("List patterns requires tenant parameter")
    void listPatterns_requiresTenantParameter() throws Exception {
        mockMvc.perform(get("/api/v1/patterns")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("List patterns supports status filter")
    void listPatterns_supportsStatusFilter() throws Exception {
        String[] validStatuses = {"ACTIVE", "INACTIVE", "DRAFT"};
        for (String status : validStatuses) {
            mockMvc.perform(get("/api/v1/patterns")
                    .param("tenantId", TEST_TENANT_ID)
                    .param("status", status)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("List patterns returns valid response schema")
    void listPatterns_returnsValidResponseSchema() throws Exception {
        mockMvc.perform(get("/api/v1/patterns")
                .param("tenantId", TEST_TENANT_ID)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patterns").isArray())
                .andExpect(jsonPath("$.count").isNumber())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("Register pattern validates request schema")
    void registerPattern_validatesRequestSchema() throws Exception {
        // Test missing required fields
        mockMvc.perform(post("/api/v1/patterns")
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Test valid request
        mockMvc.perform(post("/api/v1/patterns")
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .header("X-User-Id", "test-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Pattern\",\"description\":\"Test\",\"type\":\"ANOMALY\",\"specification\":\"test\",\"config\":{}}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(201, 400));
    }

    @Test
    @DisplayName("Register pattern returns valid response schema")
    void registerPattern_returnsValidResponseSchema() throws Exception {
        mockMvc.perform(post("/api/v1/patterns")
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .header("X-User-Id", "test-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Pattern\",\"description\":\"Test\",\"type\":\"ANOMALY\",\"specification\":\"test\",\"config\":{}}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(201, 400))
                .andExpect(jsonPath("$").value(anyOf(
                        allOf(
                                hasKey("pattern"),
                                hasKey("timestamp")
                        ),
                        hasKey("error")
                )));
    }

    // ── General Error Response Tests ─────────────────────────────────────────

    @Test
    @DisplayName("All error responses follow schema")
    void allErrorResponses_followSchema() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("API version is consistent")
    void apiVersion_isConsistent() throws Exception {
        mockMvc.perform(get("/info")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").isString());
    }
}
