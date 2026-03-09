/*
 * Copyright (c) 2025 Ghatana Technologies
 * Consumer-driven contract tests for the Auth Gateway API.
 *
 * Validates that the OpenAPI spec at openapi/auth-gateway.yaml is
 * structurally sound and that the schemas products depend on are present
 * with the expected required fields.
 */
package com.ghatana.contracts.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.*;

/**
 * Consumer-driven contract tests for <code>auth-gateway.yaml</code>.
 *
 * <p>Products (YAPPC, AEP, Data-Cloud) depend on the auth gateway for
 * login, token validation, token refresh, and tenant extraction.
 * These tests ensure the contract cannot drift without detection.
 */
class AuthGatewayContractTest {

    private static JsonNode spec;

    @BeforeAll
    static void loadSpec() throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream is = AuthGatewayContractTest.class.getResourceAsStream(
                "/auth-gateway.yaml")) {
            assertThat(is).as("auth-gateway.yaml must be on classpath").isNotNull();
            spec = yamlMapper.readTree(is);
        }
    }

    // =========================================================================
    // Spec metadata
    // =========================================================================

    @Test
    void shouldHaveCorrectOpenApiVersion() {
        assertThat(spec.path("openapi").asText()).startsWith("3.");
    }

    @Test
    void shouldHaveServiceInfo() {
        JsonNode info = spec.path("info");
        assertThat(info.path("title").asText()).isNotBlank();
        assertThat(info.path("version").asText()).isNotBlank();
    }

    // =========================================================================
    // Paths — products rely on these endpoints
    // =========================================================================

    @Nested
    class Paths {

        @Test
        void shouldExposeHealthEndpoint() {
            assertThat(spec.at("/paths/~1health/get")).isNotNull();
            assertThat(spec.at("/paths/~1health/get/operationId").asText())
                    .isEqualTo("healthCheck");
        }

        @Test
        void shouldExposeLoginEndpoint() {
            JsonNode login = spec.at("/paths/~1auth~1login/post");
            assertThat(login.isMissingNode()).isFalse();
            assertThat(login.path("operationId").asText()).isEqualTo("login");
            assertThat(login.at("/requestBody/required").asBoolean()).isTrue();
        }

        @Test
        void shouldExposeValidateEndpoint() {
            JsonNode validate = spec.at("/paths/~1auth~1validate/get");
            assertThat(validate.isMissingNode()).isFalse();
            assertThat(validate.path("operationId").asText()).isEqualTo("validateToken");
        }

        @Test
        void shouldExposeRefreshEndpoint() {
            JsonNode refresh = spec.at("/paths/~1auth~1refresh/post");
            assertThat(refresh.isMissingNode()).isFalse();
            assertThat(refresh.path("operationId").asText()).isEqualTo("refreshToken");
        }

        @Test
        void shouldExposeTenantEndpoint() {
            JsonNode tenant = spec.at("/paths/~1auth~1tenant/get");
            assertThat(tenant.isMissingNode()).isFalse();
            assertThat(tenant.path("operationId").asText()).isEqualTo("extractTenant");
        }
    }

    // =========================================================================
    // Schemas — required fields that consumers depend on
    // =========================================================================

    @Nested
    class Schemas {

        private JsonNode schema(String name) {
            return spec.at("/components/schemas/" + name);
        }

        @Test
        void loginRequestShouldRequireEmailAndPassword() {
            Set<String> required = requiredFields(schema("LoginRequest"));
            assertThat(required).containsExactlyInAnyOrder("email", "password");
        }

        @Test
        void loginResponseShouldHaveTokenFields() {
            JsonNode lr = schema("LoginResponse");
            Set<String> required = requiredFields(lr);
            assertThat(required).contains("accessToken", "refreshToken", "expiresIn");
            // Properties must include userId and roles even if not required
            assertThat(lr.path("properties").has("userId")).isTrue();
            assertThat(lr.path("properties").has("roles")).isTrue();
        }

        @Test
        void validateResponseShouldRequireValid() {
            Set<String> required = requiredFields(schema("ValidateResponse"));
            assertThat(required).contains("valid");
        }

        @Test
        void refreshResponseShouldRequireAccessTokenAndExpiry() {
            Set<String> required = requiredFields(schema("RefreshResponse"));
            assertThat(required).containsExactlyInAnyOrder("accessToken", "expiresIn");
        }

        @Test
        void tenantResponseShouldRequireTenantId() {
            Set<String> required = requiredFields(schema("TenantResponse"));
            assertThat(required).contains("tenantId");
        }

        @Test
        void errorResponseShouldRequireError() {
            Set<String> required = requiredFields(schema("ErrorResponse"));
            assertThat(required).contains("error");
        }
    }

    // =========================================================================
    // Security scheme
    // =========================================================================

    @Test
    void shouldDefineBearerAuthScheme() {
        JsonNode bearer = spec.at("/components/securitySchemes/bearerAuth");
        assertThat(bearer.isMissingNode()).isFalse();
        assertThat(bearer.path("type").asText()).isEqualTo("http");
        assertThat(bearer.path("scheme").asText()).isEqualTo("bearer");
        assertThat(bearer.path("bearerFormat").asText()).isEqualTo("JWT");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Set<String> requiredFields(JsonNode schemaNode) {
        JsonNode req = schemaNode.path("required");
        if (req.isMissingNode() || !req.isArray()) return Set.of();
        return StreamSupport.stream(req.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toSet());
    }
}
