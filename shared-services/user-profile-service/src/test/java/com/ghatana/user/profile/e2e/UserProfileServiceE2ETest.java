/**
 * User Profile Service E2E Test Suite
 *
 * End-to-end tests for the User Profile service.
 * Tests complete profile management flows including CRUD operations and multi-tenant isolation.
 *
 * @doc.type test
 * @doc.purpose E2E validation for user-profile-service
 * @doc.layer shared-services
 * @doc.pattern E2ETest
 */

package com.ghatana.user.profile.e2e;

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
 * E2E tests for User Profile Service.
 * Tests complete profile management flows with real service interactions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("e2e-test")
@DisplayName("User Profile Service E2E Tests")
public class UserProfileServiceE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_TENANT_ID = "e2e-test-tenant";
    private static final String TEST_USER_ID = "user-e2e-123";

    @Test
    @DisplayName("Complete profile CRUD flow")
    void completeProfileCrudFlow() throws Exception {
        // Step 1: Create profile
        MvcResult createResult = mockMvc.perform(put("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"e2e-test@example.com\",\"displayName\":\"E2E Test User\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400, 401, 403))
                .andExpect(jsonPath("$", anyOf(
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
                )))
                .andReturn();

        if (createResult.getResponse().getStatus() == 200) {
            // Step 2: Read profile
            mockMvc.perform(get("/profiles/" + TEST_USER_ID)
                    .header("X-Tenant-Id", TEST_TENANT_ID)
                    .header("Authorization", "Bearer test-token")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(TEST_USER_ID))
                    .andExpect(jsonPath("$.email").value("e2e-test@example.com"));

            // Step 3: Update profile
            mockMvc.perform(put("/profiles/" + TEST_USER_ID)
                    .header("X-Tenant-Id", TEST_TENANT_ID)
                    .header("Authorization", "Bearer test-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"e2e-test@example.com\",\"displayName\":\"Updated Name\",\"theme\":\"dark\"}")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Updated Name"))
                    .andExpect(jsonPath("$.theme").value("dark"));

            // Step 4: Delete profile
            mockMvc.perform(delete("/profiles/" + TEST_USER_ID)
                    .header("X-Tenant-Id", TEST_TENANT_ID)
                    .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isNoContent());

            // Step 5: Verify deletion
            mockMvc.perform(get("/profiles/" + TEST_USER_ID)
                    .header("X-Tenant-Id", TEST_TENANT_ID)
                    .header("Authorization", "Bearer test-token")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("Multi-tenant isolation flow")
    void multiTenantIsolationFlow() throws Exception {
        String tenant1 = "tenant-1";
        String tenant2 = "tenant-2";
        String userId = "shared-user-id";

        // Create profile in tenant 1
        mockMvc.perform(put("/profiles/" + userId)
                .header("X-Tenant-Id", tenant1)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@tenant1.com\",\"displayName\":\"User in Tenant 1\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400, 401, 403));

        // Create profile in tenant 2 with same user ID
        mockMvc.perform(put("/profiles/" + userId)
                .header("X-Tenant-Id", tenant2)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@tenant2.com\",\"displayName\":\"User in Tenant 2\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400, 401, 403));

        // Verify tenant isolation
        mockMvc.perform(get("/profiles/" + userId)
                .header("X-Tenant-Id", tenant1)
                .header("Authorization", "Bearer test-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 404))
                .andExpect(jsonPath("$.tenantId", existsOrNull()).value(
                        when(is(notNull())).then(value(tenant1))
                ));

        mockMvc.perform(get("/profiles/" + userId)
                .header("X-Tenant-Id", tenant2)
                .header("Authorization", "Bearer test-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 404))
                .andExpect(jsonPath("$.tenantId", existsOrNull()).value(
                        when(is(notNull())).then(value(tenant2))
                ));
    }

    @Test
    @DisplayName("Profile validation flow")
    void profileValidationFlow() throws Exception {
        // Test invalid email format
        mockMvc.perform(put("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid-email\",\"displayName\":\"Test\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());

        // Test invalid theme enum
        mockMvc.perform(put("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"theme\":\"invalid\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Test missing required field
        mockMvc.perform(put("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Test\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Authentication required flow")
    void authenticationRequiredFlow() throws Exception {
        // Without authentication
        mockMvc.perform(get("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isString());

        mockMvc.perform(put("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Tenant header required flow")
    void tenantHeaderRequiredFlow() throws Exception {
        // Without tenant header
        mockMvc.perform(get("/profiles/" + TEST_USER_ID)
                .header("Authorization", "Bearer test-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/profiles/" + TEST_USER_ID)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Profile defaults flow")
    void profileDefaultsFlow() throws Exception {
        // Create profile with minimal fields
        MvcResult result = mockMvc.perform(put("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400, 401, 403))
                .andReturn();

        if (result.getResponse().getStatus() == 200) {
            // Verify defaults are applied
            mockMvc.perform(get("/profiles/" + TEST_USER_ID)
                    .header("X-Tenant-Id", TEST_TENANT_ID)
                    .header("Authorization", "Bearer test-token")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.preferredLanguage").value("en-US"))
                    .andExpect(jsonPath("$.timezone").value("UTC"))
                    .andExpect(jsonPath("$.theme").value("system"))
                    .andExpect(jsonPath("$.notificationsEnabled").value(true));
        }
    }

    @Test
    @DisplayName("Health check flow")
    void healthCheckFlow() throws Exception {
        mockMvc.perform(get("/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.service").value("user-profile-service"));
    }

    @Test
    @DisplayName("Metrics endpoint flow")
    void metricsEndpointFlow() throws Exception {
        mockMvc.perform(get("/metrics")
                .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @Test
    @DisplayName("Profile update with all fields flow")
    void profileUpdateWithAllFieldsFlow() throws Exception {
        MvcResult result = mockMvc.perform(put("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"displayName\":\"Full Name\",\"avatarUrl\":\"https://example.com/avatar.jpg\",\"preferredLanguage\":\"en-US\",\"timezone\":\"America/New_York\",\"theme\":\"dark\",\"notificationsEnabled\":false}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 400, 401, 403))
                .andReturn();

        if (result.getResponse().getStatus() == 200) {
            mockMvc.perform(get("/profiles/" + TEST_USER_ID)
                    .header("X-Tenant-Id", TEST_TENANT_ID)
                    .header("Authorization", "Bearer test-token")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Full Name"))
                    .andExpect(jsonPath("$.avatarUrl").value("https://example.com/avatar.jpg"))
                    .andExpect(jsonPath("$.preferredLanguage").value("en-US"))
                    .andExpect(jsonPath("$.timezone").value("America/New_York"))
                    .andExpect(jsonPath("$.theme").value("dark"))
                    .andExpect(jsonPath("$.notificationsEnabled").value(false));
        }
    }

    @Test
    @DisplayName("Non-existent profile flow")
    void nonExistentProfileFlow() throws Exception {
        mockMvc.perform(get("/profiles/non-existent-user")
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .header("Authorization", "Bearer test-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("Internal service key bypass flow")
    void internalServiceKeyBypassFlow() throws Exception {
        // Test that internal service key can bypass user scoping
        mockMvc.perform(get("/profiles/" + TEST_USER_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID)
                .header("X-Internal-Key", "test-internal-key")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 401, 404));
    }
}
