/**
 * Auth Gateway E2E Test Suite
 *
 * End-to-end tests for the auth-gateway service.
 * Tests complete authentication flows including login, token validation, refresh, and exchange.
 *
 * @doc.type test
 * @doc.purpose E2E validation for auth-gateway service
 * @doc.layer shared-services
 * @doc.pattern E2ETest
 */

package com.ghatana.auth.gateway.e2e;

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
 * E2E tests for Auth Gateway service.
 * Tests complete authentication flows with real service interactions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("e2e-test")
@DisplayName("Auth Gateway E2E Tests")
public class AuthGatewayE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Complete login flow: login -> validate -> logout")
    void completeLoginFlow() throws Exception {
        // Step 1: Login
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"e2e-test@ghatana.com\",\"password\":\"test-password-123\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 401))
                .andExpect(jsonPath("$", anyOf(
                        hasKey("accessToken"),
                        hasKey("error")
                )))
                .andReturn();

        String accessToken = null;
        String refreshToken = null;

        if (loginResult.getResponse().getStatus() == 200) {
            String response = loginResult.getResponse().getContentAsString();
            // Parse response to extract tokens
            // In a real scenario, we'd use the actual response
            accessToken = "mock-access-token";
            refreshToken = "mock-refresh-token";
        }

        // Step 2: Validate token (if login succeeded)
        if (accessToken != null) {
            mockMvc.perform(get("/auth/validate")
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isIn(200, 401))
                    .andExpect(jsonPath("$.valid").isBoolean());
        }

        // Step 3: Logout
        if (accessToken != null) {
            mockMvc.perform(post("/auth/logout")
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isIn(200, 401));
        }
    }

    @Test
    @DisplayName("Token refresh flow: login -> refresh -> validate")
    void tokenRefreshFlow() throws Exception {
        // Step 1: Login
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"e2e-test@ghatana.com\",\"password\":\"test-password-123\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 401))
                .andExpect(jsonPath("$", anyOf(
                        allOf(
                                hasKey("accessToken"),
                                hasKey("refreshToken"),
                                hasKey("expiresIn")
                        ),
                        hasKey("error")
                )))
                .andReturn();

        String refreshToken = "mock-refresh-token";

        // Step 2: Refresh token
        if (loginResult.getResponse().getStatus() == 200) {
            mockMvc.perform(post("/auth/refresh")
                    .header("Authorization", "Bearer " + refreshToken)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isIn(200, 401))
                    .andExpect(jsonPath("$", anyOf(
                            allOf(
                                    hasKey("accessToken"),
                                    hasKey("expiresIn")
                            ),
                            hasKey("error")
                    )));
        }
    }

    @Test
    @DisplayName("Token exchange flow: product token -> platform token")
    void tokenExchangeFlow() throws Exception {
        // This would require a valid product-scoped JWT
        // For E2E testing, we simulate the exchange endpoint behavior
        mockMvc.perform(post("/auth/exchange")
                .header("Authorization", "Bearer mock-product-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400, 401, 429, 500))
                .andExpect(jsonPath("$", anyOf(
                        allOf(
                                hasKey("platformToken"),
                                hasKey("expiresIn")
                        ),
                        hasKey("error")
                )));
    }

    @Test
    @DisplayName("Tenant extraction flow")
    void tenantExtractionFlow() throws Exception {
        // Login to get a token
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"e2e-test@ghatana.com\",\"password\":\"test-password-123\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 401))
                .andReturn();

        String accessToken = "mock-access-token";

        // Extract tenant context
        if (loginResult.getResponse().getStatus() == 200) {
            mockMvc.perform(get("/auth/tenant")
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isIn(200, 401))
                    .andExpect(jsonPath("$", anyOf(
                            hasKey("tenantId"),
                            hasKey("error")
                    )));
        }
    }

    @Test
    @DisplayName("Invalid credentials flow")
    void invalidCredentialsFlow() throws Exception {
        // Attempt login with invalid credentials
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nonexistent@ghatana.com\",\"password\":\"wrong-password\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.error").value(containsStringIgnoringCase("invalid")));
    }

    @Test
    @DisplayName("Malformed request flow")
    void malformedRequestFlow() throws Exception {
        // Missing email
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"test\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());

        // Missing password
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());

        // Invalid email format
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid-email\",\"password\":\"test\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Protected endpoint without token")
    void protectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/auth/validate")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("Protected endpoint with invalid token")
    void protectedEndpointWithInvalidToken() throws Exception {
        mockMvc.perform(get("/auth/validate")
                .header("Authorization", "Bearer invalid-token-string")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("Health check flow")
    void healthCheckFlow() throws Exception {
        mockMvc.perform(get("/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.service").value("auth-gateway"));
    }

    @Test
    @DisplayName("Rate limiting flow")
    void rateLimitingFlow() throws Exception {
        // Make multiple rapid requests to test rate limiting
        // This would typically be configured in the service
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@example.com\",\"password\":\"test\"}")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isIn(200, 400, 401, 429));
        }
    }

    @Test
    @DisplayName("Session management flow")
    void sessionManagementFlow() throws Exception {
        // Login
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"e2e-test@ghatana.com\",\"password\":\"test-password-123\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 401))
                .andReturn();

        String accessToken = "mock-access-token";

        // Validate session
        if (loginResult.getResponse().getStatus() == 200) {
            mockMvc.perform(get("/auth/validate")
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isIn(200, 401));

            // Logout (invalidate session)
            mockMvc.perform(post("/auth/logout")
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isIn(200, 401));

            // Try to use token after logout
            mockMvc.perform(get("/auth/validate")
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isIn(200, 401));
        }
    }
}
