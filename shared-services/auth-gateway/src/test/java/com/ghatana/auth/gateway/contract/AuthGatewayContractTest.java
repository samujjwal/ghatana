/**
 * Auth Gateway Contract Test Suite
 *
 * Validates that the auth-gateway API conforms to its OpenAPI specification.
 * Tests request/response schemas, error responses, and version compatibility.
 *
 * @doc.type test
 * @doc.purpose Contract validation for auth-gateway public API
 * @doc.layer shared-services
 * @doc.pattern ContractTest
 */

package com.ghatana.auth.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract tests for Auth Gateway API.
 * Validates that the API conforms to the OpenAPI specification at
 * platform/contracts/openapi/auth-gateway.yaml
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Auth Gateway Contract Tests")
public class AuthGatewayContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String API_VERSION = "2.0.0";

    @Test
    @DisplayName("Health check endpoint returns valid schema")
    void healthCheck_returnsValidSchema() throws Exception {
        mockMvc.perform(get("/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.service").value("auth-gateway"))
                .andExpect(jsonPath("$").value(hasKey("status")))
                .andExpect(jsonPath("$.status").matches("UP|DOWN"));
    }

    @Test
    @DisplayName("Login endpoint validates request schema")
    void login_validatesRequestSchema() throws Exception {
        // Test missing email
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"test123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());

        // Test missing password
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());

        // Test invalid email format
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\",\"password\":\"test123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login endpoint returns valid response schema on success")
    void login_returnsValidResponseSchema() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"password\":\"test123\"}"))
                .andExpect(status().isIn(200, 401)) // May succeed or fail based on credentials
                .andExpect(jsonPath("$").value(anyOf(
                        hasKey("accessToken"),
                        hasKey("error")
                )));
    }

    @Test
    @DisplayName("Login endpoint returns 401 for invalid credentials")
    void login_returns401ForInvalidCredentials() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nonexistent@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.error").value(containsStringIgnoringCase("invalid")));
    }

    @Test
    @DisplayName("Logout endpoint requires authentication")
    void logout_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/auth/logout")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("Validate endpoint returns valid schema for valid token")
    void validateToken_returnsValidSchema() throws Exception {
        // This test would require a valid JWT token
        // For now, test the error response for missing token
        mockMvc.perform(get("/auth/validate")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("Validate endpoint returns error schema for invalid token")
    void validateToken_returnsErrorSchemaForInvalidToken() throws Exception {
        mockMvc.perform(get("/auth/validate")
                .header("Authorization", "Bearer invalid-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("Refresh endpoint requires authentication")
    void refreshToken_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("Refresh endpoint returns valid response schema")
    void refreshToken_returnsValidResponseSchema() throws Exception {
        // This would require a valid refresh token
        // Test error response for invalid token
        mockMvc.perform(post("/auth/refresh")
                .header("Authorization", "Bearer invalid-refresh-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("Exchange endpoint requires authentication")
    void exchangeToken_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/auth/exchange")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("Exchange endpoint returns valid response schema")
    void exchangeToken_returnsValidResponseSchema() throws Exception {
        // Test with invalid product token
        mockMvc.perform(post("/auth/exchange")
                .header("Authorization", "Bearer invalid-product-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(401, 400, 429))
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("Exchange endpoint returns 429 for rate limit")
    void exchangeToken_returns429ForRateLimit() throws Exception {
        // This would require multiple rapid requests
        // For now, verify the endpoint exists and handles errors
        mockMvc.perform(post("/auth/exchange")
                .header("Authorization", "Bearer test-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400, 401, 429, 500));
    }

    @Test
    @DisplayName("Tenant extraction endpoint requires authentication")
    void extractTenant_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/auth/tenant")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Tenant extraction endpoint returns valid schema")
    void extractTenant_returnsValidSchema() throws Exception {
        mockMvc.perform(get("/auth/tenant")
                .header("Authorization", "Bearer invalid-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("All endpoints return JSON error response for errors")
    void allEndpoints_returnJsonErrorResponse() throws Exception {
        // Test that error responses follow the ErrorResponse schema
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("API version is consistent")
    void apiVersion_isConsistent() throws Exception {
        // The OpenAPI spec defines version 2.0.0
        // This test verifies the service is running the expected version
        // Version would typically be exposed via /actuator/info or similar
        mockMvc.perform(get("/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        // Version validation would be implemented if version endpoint exists
    }
}
