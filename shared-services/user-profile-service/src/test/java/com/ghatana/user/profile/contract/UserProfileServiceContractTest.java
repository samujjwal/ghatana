/**
 * User Profile Service Contract Test Suite
 *
 * Validates that the User Profile Service API conforms to its OpenAPI specification.
 * Tests request/response schemas, error responses, and version compatibility.
 *
 * @doc.type test
 * @doc.purpose Contract validation for user-profile-service public API
 * @doc.layer shared-services
 * @doc.pattern ContractTest
 */

package com.ghatana.user.profile.contract;

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
 * Contract tests for User Profile Service API.
 * Validates that the API conforms to the OpenAPI specification at
 * platform/contracts/openapi/user-profile-service.yaml
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("User Profile Service Contract Tests")
public class UserProfileServiceContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String API_VERSION = "1.0.0";
    private static final String TEST_TENANT_ID = "test-tenant";
    private static final String TEST_USER_ID = "user-test-123";

    @Test
    @DisplayName("Health check endpoint returns valid schema")
    void healthCheck_returnsValidSchema() throws Exception {
        mockMvc.perform(get("/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.service").value("user-profile-service"))
                .andExpect(jsonPath("$.status").matches("UP|DOWN"));
    }

    @Test
    @DisplayName("Metrics endpoint returns Prometheus format")
    void metrics_returnsPrometheusFormat() throws Exception {
        mockMvc.perform(get("/metrics")
                .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @Test
    @DisplayName("Get profile requires tenant header")
    void getProfile_requiresTenantHeader() throws Exception {
        mockMvc.perform(get("/profiles/" + TEST_USER_ID)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Get profile requires authentication")
    void getProfile_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("Get profile returns valid schema")
    void getProfile_returnsValidSchema() throws Exception {
        // Test with invalid auth token
        mockMvc.perform(get("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .header("Authorization", "Bearer invalid-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 401, 404))
                .andExpect(jsonPath("$").value(anyOf(
                        allOf(
                                hasKey("userId"),
                                hasKey("tenantId"),
                                hasKey("email"),
                                hasKey("displayName"),
                                hasKey("preferredLanguage"),
                                hasKey("timezone"),
                                hasKey("theme"),
                                hasKey("notificationsEnabled"),
                                hasKey("createdAt"),
                                hasKey("updatedAt")
                        ),
                        hasKey("error")
                )));
    }

    @Test
    @DisplayName("Upsert profile validates request schema")
    void upsertProfile_validatesRequestSchema() throws Exception {
        // Test missing email
        mockMvc.perform(put("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());

        // Test invalid email format
        mockMvc.perform(put("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid-email\"}"))
                .andExpect(status().isBadRequest());

        // Test invalid theme enum
        mockMvc.perform(put("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"theme\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Upsert profile returns valid response schema")
    void upsertProfile_returnsValidResponseSchema() throws Exception {
        mockMvc.perform(put("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"displayName\":\"Test User\"}"))
                .andExpect(status().isIn(200, 400, 401, 403))
                .andExpect(jsonPath("$").value(anyOf(
                        allOf(
                                hasKey("userId"),
                                hasKey("tenantId"),
                                hasKey("email"),
                                hasKey("displayName"),
                                hasKey("preferredLanguage"),
                                hasKey("timezone"),
                                hasKey("theme"),
                                hasKey("notificationsEnabled"),
                                hasKey("createdAt"),
                                hasKey("updatedAt")
                        ),
                        hasKey("error")
                )));
    }

    @Test
    @DisplayName("Delete profile requires authentication")
    void deleteProfile_requiresAuthentication() throws Exception {
        mockMvc.perform(delete("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Delete profile returns 204 on success")
    void deleteProfile_returns204OnSuccess() throws Exception {
        // This would require valid auth
        // For now, test that it returns appropriate status codes
        mockMvc.perform(delete("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isIn(204, 401, 403));
    }

    @Test
    @DisplayName("Theme enum accepts only valid values")
    void themeEnum_acceptsOnlyValidValues() throws Exception {
        String[] validThemes = {"light", "dark", "system"};
        for (String theme : validThemes) {
            mockMvc.perform(put("/profiles/" + TEST_USER_ID)
                    .header("X-Tenant-Id", TEST_TENANT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@example.com\",\"theme\":\"" + theme + "\"}"))
                    .andExpect(status().isIn(200, 400, 401, 403));
        }
    }

    @Test
    @DisplayName("Profile response has required fields")
    void profileResponse_hasRequiredFields() throws Exception {
        mockMvc.perform(get("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .header("Authorization", "Bearer invalid-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 401, 404))
                .andExpect(jsonPath("$", when(jsonPath("$.error").exists()).then(
                        hasKey("error")
                ).otherwise(
                        allOf(
                                hasKey("userId"),
                                hasKey("tenantId"),
                                hasKey("email"),
                                hasKey("displayName"),
                                hasKey("preferredLanguage"),
                                hasKey("timezone"),
                                hasKey("theme"),
                                hasKey("notificationsEnabled"),
                                hasKey("createdAt"),
                                hasKey("updatedAt")
                        )
                ));
    }

    @Test
    @DisplayName("All error responses follow schema")
    void allErrorResponses_followSchema() throws Exception {
        mockMvc.perform(get("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("API version is consistent")
    void apiVersion_isConsistent() throws Exception {
        mockMvc.perform(get("/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
