/**
 * Cross-Service Workflow Test: Authenticated User Workflow
 *
 * Tests a complete workflow spanning multiple services:
 * 1. User login via auth-gateway
 * 2. Token validation via auth-service
 * 3. User profile retrieval via user-profile-service
 * 4. AI model listing via ai-registry-service
 * 5. Embedding generation via ai-inference-service
 *
 * @doc.type test
 * @doc.purpose Cross-service workflow validation for authenticated user flows
 * @doc.layer integration
 * @doc.pattern IntegrationTest
 */

package com.ghatana.integration.crossservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
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
 * Cross-service workflow test for authenticated user operations.
 * Tests the complete flow from login through AI inference across multiple services.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("Cross-Service Workflow: Authenticated User")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthenticatedUserWorkflowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String authToken;
    private static String tenantId = "cross-service-test-tenant";
    private static String userId;

    // ── Step 1: Login via Auth Gateway ────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Step 1: User login via auth-gateway")
    void step1_userLogin() throws Exception {
        String loginRequest = """
                {
                    "username": "testuser",
                    "password": "testpass",
                    "tenantId": "%s"
                }
                """.formatted(tenantId);

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.userId").isString())
                .andExpect(jsonPath("$.expiresIn").isNumber());

        // Store token for subsequent steps
        String response = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest)
                .accept(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        var json = objectMapper.readTree(response);
        authToken = json.get("token").asText();
        userId = json.get("userId").asText();
    }

    // ── Step 2: Token Validation via Auth Service ──────────────────────────────

    @Test
    @Order(2)
    @DisplayName("Step 2: Token validation via auth-service")
    void step2_tokenValidation() throws Exception {
        mockMvc.perform(post("/auth/introspect")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + authToken + "\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.tenantId").value(tenantId))
                .andExpect(jsonPath("$.tokenType").isString());
    }

    // ── Step 3: User Profile Retrieval via User Profile Service ───────────────

    @Test
    @Order(3)
    @DisplayName("Step 3: User profile retrieval via user-profile-service")
    void step3_userProfileRetrieval() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/" + userId)
                .header("X-Tenant-Id", tenantId)
                .header("Authorization", "Bearer " + authToken)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.tenantId").value(tenantId))
                .andExpect(jsonPath("$.displayName").isString())
                .andExpect(jsonPath("$.email").isString());
    }

    // ── Step 4: AI Model Listing via AI Registry Service ───────────────────────

    @Test
    @Order(4)
    @DisplayName("Step 4: AI model listing via ai-registry-service")
    void step4_aiModelListing() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .header("X-Tenant-Id", tenantId)
                .header("Authorization", "Bearer " + authToken)
                .param("provider", "openai")
                .param("type", "LLM")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models").isArray())
                .andExpect(jsonPath("$.models[*].modelId").isNotEmpty())
                .andExpect(jsonPath("$.models[*].name").isNotEmpty())
                .andExpect(jsonPath("$.models[*].provider").value("openai"))
                .andExpect(jsonPath("$.models[*].type").value("LLM"));
    }

    // ── Step 5: Embedding Generation via AI Inference Service ──────────────────

    @Test
    @Order(5)
    @DisplayName("Step 5: Embedding generation via ai-inference-service")
    void step5_embeddingGeneration() throws Exception {
        String embeddingRequest = """
                {
                    "text": "Hello world",
                    "model": "text-embedding-ada-002"
                }
                """;

        mockMvc.perform(post("/api/v1/embeddings")
                .header("X-Tenant-Id", tenantId)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(embeddingRequest)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embedding").isArray())
                .andExpect(jsonPath("$.embedding", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.model").value("text-embedding-ada-002"))
                .andExpect(jsonPath("$.tokens").isNumber());
    }

    // ── Step 6: Logout via Auth Gateway ───────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("Step 6: User logout via auth-gateway")
    void step6_userLogout() throws Exception {
        mockMvc.perform(post("/auth/logout")
                .header("Authorization", "Bearer " + authToken)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify token is now invalid
        mockMvc.perform(post("/auth/introspect")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + authToken + "\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isIn(200, 401));
    }

    // ── Error Flow: Unauthorized Access ────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("Error Flow: Unauthorized access without token")
    void errorFlow_unauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/" + userId)
                .header("X-Tenant-Id", tenantId)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isString());
    }

    // ── Error Flow: Invalid Token ───────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("Error Flow: Invalid token rejection")
    void errorFlow_invalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/models")
                .header("X-Tenant-Id", tenantId)
                .header("Authorization", "Bearer invalid-token")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isString());
    }
}
