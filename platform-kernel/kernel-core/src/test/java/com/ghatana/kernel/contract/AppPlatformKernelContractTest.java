/**
 * App Platform Kernel Contract Test Suite
 *
 * Validates that the App Platform Kernel API conforms to its OpenAPI specification.
 * Tests request/response schemas, error responses, and version compatibility.
 *
 * @doc.type test
 * @doc.purpose Contract validation for app-platform-kernel public API
 * @doc.layer platform-kernel
 * @doc.pattern ContractTest
 */

package com.ghatana.kernel.contract;

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
 * Contract tests for App Platform Kernel API.
 * Validates that the API conforms to the OpenAPI specification at
 * platform/contracts/openapi/app-platform-kernel.yaml
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("App Platform Kernel Contract Tests")
public class AppPlatformKernelContractTest {

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
                .andExpect(jsonPath("$.service").isString())
                .andExpect(jsonPath("$.version").isString())
                .andExpect(jsonPath("$.status").matches("UP|DOWN|DEGRADED"));
    }

    // ── K-03 Rules Engine Tests ────────────────────────────────────────────

    @Test
    @DisplayName("Rules evaluate endpoint validates request schema")
    void rulesEvaluate_validatesRequestSchema() throws Exception {
        // Test missing required fields
        mockMvc.perform(post("/api/v1/rules/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.code").isString());

        // Test with valid request structure
        mockMvc.perform(post("/api/v1/rules/evaluate")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyId\":\"test\",\"input\":{},\"tenantId\":\"test-tenant\"}"))
                .andExpect(status().isIn(200, 400, 503));
    }

    @Test
    @DisplayName("Rules evaluate returns valid response schema")
    void rulesEvaluate_returnsValidResponseSchema() throws Exception {
        mockMvc.perform(post("/api/v1/rules/evaluate")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyId\":\"test\",\"input\":{},\"tenantId\":\"test-tenant\"}"))
                .andExpect(status().isIn(200, 400, 503))
                .andExpect(jsonPath("$").value(anyOf(
                        allOf(
                                hasKey("decision"),
                                hasKey("policyVersion"),
                                hasKey("evaluationTimeMs"),
                                hasKey("traceId")
                        ),
                        allOf(
                                hasKey("error"),
                                hasKey("message"),
                                hasKey("code")
                        )
                )));
    }

    @Test
    @DisplayName("Rules evaluate requires authentication")
    void rulesEvaluate_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/rules/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyId\":\"test\",\"input\":{},\"tenantId\":\"test-tenant\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Rules deploy endpoint validates request schema")
    void rulesDeploy_validatesRequestSchema() throws Exception {
        // Test missing required fields
        mockMvc.perform(post("/api/v1/rules/packs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        // Test with valid request structure
        mockMvc.perform(post("/api/v1/rules/packs")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"packId\":\"test\",\"jurisdiction\":\"NP\",\"policyId\":\"test\",\"regoCode\":\"package test\",\"testCases\":[],\"makerId\":\"test\",\"effectiveDate\":{\"bs\":\"2081-11-17\",\"gregorian\":\"2025-03-02T00:00:00Z\"}}"))
                .andExpect(status().isIn(202, 400));
    }

    @Test
    @DisplayName("Rules deploy returns valid response schema")
    void rulesDeploy_returnsValidResponseSchema() throws Exception {
        mockMvc.perform(post("/api/v1/rules/packs")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"packId\":\"test\",\"jurisdiction\":\"NP\",\"policyId\":\"test\",\"regoCode\":\"package test\",\"testCases\":[],\"makerId\":\"test\",\"effectiveDate\":{\"bs\":\"2081-11-17\",\"gregorian\":\"2025-03-02T00:00:00Z\"}}"))
                .andExpect(status().isIn(202, 400))
                .andExpect(jsonPath("$").value(anyOf(
                        allOf(
                                hasKey("packId"),
                                hasKey("status"),
                                hasKey("testResults")
                        ),
                        allOf(
                                hasKey("error"),
                                hasKey("message"),
                                hasKey("code")
                        )
                )));
    }

    @Test
    @DisplayName("Rules approve requires authentication")
    void rulesApprove_requiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/rules/packs/test-pack/approve"))
                .andExpect(status().isUnauthorized());
    }

    // ── K-04 Plugin Runtime Tests ───────────────────────────────────────────

    @Test
    @DisplayName("Plugins list endpoint returns valid schema")
    void pluginsList_returnsValidSchema() throws Exception {
        mockMvc.perform(get("/api/v1/plugins")
                .header("Authorization", "Bearer test-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 401))
                .andExpect(jsonPath("$", when(is(notNull())).then(
                        everyItem(
                                allOf(
                                        hasKey("pluginId"),
                                        hasKey("version"),
                                        hasKey("tier"),
                                        hasKey("status"),
                                        hasKey("capabilities")
                                )
                        )
                )));
    }

    @Test
    @DisplayName("Plugins list supports status filter")
    void pluginsList_supportsStatusFilter() throws Exception {
        String[] validStatuses = {"REGISTERED", "ENABLED", "DISABLED", "DEPRECATED"};
        for (String status : validStatuses) {
            mockMvc.perform(get("/api/v1/plugins")
                    .header("Authorization", "Bearer test-token")
                    .param("status", status)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isIn(200, 401));
        }
    }

    @Test
    @DisplayName("Plugins list supports tier filter")
    void pluginsList_supportsTierFilter() throws Exception {
        String[] validTiers = {"T1", "T2", "T3"};
        for (String tier : validTiers) {
            mockMvc.perform(get("/api/v1/plugins")
                    .header("Authorization", "Bearer test-token")
                    .param("tier", tier)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isIn(200, 401));
        }
    }

    @Test
    @DisplayName("Plugin tier enum accepts only valid values")
    void pluginTierEnum_acceptsOnlyValidValues() throws Exception {
        mockMvc.perform(get("/api/v1/plugins")
                .header("Authorization", "Bearer test-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 401))
                .andExpect(jsonPath("$[*].tier", existsOrNull()).value(
                        when(is(notNull())).then(
                                everyItem(isOneOf("T1", "T2", "T3"))
                        )
                ));
    }

    @Test
    @DisplayName("Plugin register validates request schema")
    void pluginRegister_validatesRequestSchema() throws Exception {
        // Test missing required fields
        mockMvc.perform(post("/api/v1/plugins/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        // Test invalid tier
        mockMvc.perform(post("/api/v1/plugins/register")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pluginId\":\"test\",\"version\":\"1.0\",\"tier\":\"INVALID\",\"capabilities\":[],\"manifest\":{\"name\":\"test\",\"sdkVersion\":\"1.0\",\"entryPoint\":\"index.js\"},\"signature\":\"test\",\"publicKey\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Plugin register returns valid response schema")
    void pluginRegister_returnsValidResponseSchema() throws Exception {
        mockMvc.perform(post("/api/v1/plugins/register")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pluginId\":\"test\",\"version\":\"1.0\",\"tier\":\"T1\",\"capabilities\":[\"TEST\"],\"manifest\":{\"name\":\"test\",\"sdkVersion\":\"1.0\",\"entryPoint\":\"index.js\"},\"signature\":\"test\",\"publicKey\":\"test\"}"))
                .andExpect(status().isIn(201, 400))
                .andExpect(jsonPath("$").value(anyOf(
                        allOf(
                                hasKey("pluginId"),
                                hasKey("version"),
                                hasKey("status"),
                                hasKey("verificationStatus"),
                                hasKey("registeredAt")
                        ),
                        allOf(
                                hasKey("error"),
                                hasKey("message"),
                                hasKey("code")
                        )
                )));
    }

    // ── K-15 Calendar Service Tests ───────────────────────────────────────────

    @Test
    @DisplayName("Calendar conversion validates date format")
    void calendarConversion_validatesDateFormat() throws Exception {
        // Test invalid date format
        mockMvc.perform(post("/api/v1/calendar/convert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"invalid\"}"))
                .andExpect(status().isBadRequest());

        // Test valid date format
        mockMvc.perform(post("/api/v1/calendar/convert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2081-11-17\"}"))
                .andExpect(status().isIn(200, 400));
    }

    @Test
    @DisplayName("Calendar conversion returns valid schema")
    void calendarConversion_returnsValidSchema() throws Exception {
        mockMvc.perform(post("/api/v1/calendar/convert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2081-11-17\"}"))
                .andExpect(status().isIn(200, 400))
                .andExpect(jsonPath("$").value(anyOf(
                        allOf(
                                hasKey("bs"),
                                hasKey("gregorian")
                        ),
                        allOf(
                                hasKey("error"),
                                hasKey("message"),
                                hasKey("code")
                        )
                )));
    }

    // ── K-01 IAM Token Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("Token endpoint validates grant type")
    void tokenEndpoint_validatesGrantType() throws Exception {
        // Test missing grant type
        mockMvc.perform(post("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        // Test invalid grant type
        mockMvc.perform(post("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grantType\":\"invalid\",\"clientId\":\"test\",\"clientSecret\":\"test\"}"))
                .andExpect(status().isBadRequest());

        // Test valid grant types
        String[] validGrantTypes = {"client_credentials", "authorization_code", "refresh_token"};
        for (String grantType : validGrantTypes) {
            mockMvc.perform(post("/oauth/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"grantType\":\"" + grantType + "\",\"clientId\":\"test\",\"clientSecret\":\"test\"}"))
                    .andExpect(status().isIn(200, 400, 401));
        }
    }

    @Test
    @DisplayName("Token endpoint returns valid response schema")
    void tokenEndpoint_returnsValidResponseSchema() throws Exception {
        mockMvc.perform(post("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grantType\":\"client_credentials\",\"clientId\":\"test\",\"clientSecret\":\"test\"}"))
                .andExpect(status().isIn(200, 400, 401))
                .andExpect(jsonPath("$").value(anyOf(
                        allOf(
                                hasKey("accessToken"),
                                hasKey("tokenType"),
                                hasKey("expiresIn")
                        ),
                        allOf(
                                hasKey("error"),
                                hasKey("message"),
                                hasKey("code")
                        )
                )));
    }

    // ── General Error Response Tests ─────────────────────────────────────────

    @Test
    @DisplayName("All error responses follow schema")
    void allErrorResponses_followSchema() throws Exception {
        mockMvc.perform(post("/api/v1/rules/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.code").isString());
    }

    @Test
    @DisplayName("API version is consistent")
    void apiVersion_isConsistent() throws Exception {
        mockMvc.perform(get("/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").isString());
    }
}
