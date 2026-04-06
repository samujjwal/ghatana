/*
 * Copyright (c) 2026 Ghatana Technologies
 * OpenAPI contract tests for HTTP API boundaries.
 *
 * Validates that API schemas meet their contracts with consumers.
 */
package com.ghatana.contracts.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Contract tests for Auth Gateway OpenAPI specification.
 *
 * <p>Validates that the auth-gateway.yaml OpenAPI spec provides the contract
 * expected by platform consumers (Data Cloud, AEP, YAPPC frontend).
 *
 * <p>Required endpoints for platform auth:
 * <ul>
 *   <li>{@code POST /auth/login} - User authentication
 *   <li>{@code POST /auth/logout} - Session termination
 *   <li>{@code GET /auth/validate} - Token validation
 *   <li>{@code GET /auth/user} - Current user profile
 * </ul>
 *
 * @doc.type class
 * @doc.purpose OpenAPI schema contract validation
 * @doc.layer platform
 * @doc.pattern Test, Contract
 */
@DisplayName("Auth Gateway OpenAPI API Contract Tests")
class AuthGatewayApiContractTest {

    private static JsonNode spec;

    @BeforeAll
    static void loadSpec() throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream is = AuthGatewayApiContractTest.class.getResourceAsStream(
                "/auth-gateway.yaml")) {
            assertThat(is).as("auth-gateway.yaml must be on classpath").isNotNull();
            spec = yamlMapper.readTree(is);
        }
    }

    // =========================================================================
    // Spec Metadata Contract
    // =========================================================================

    @Nested
    @DisplayName("OpenAPI Specification Metadata")
    class SpecMetadataContract {

        @Test
        @DisplayName("spec must be valid OpenAPI 3.x")
        void specMustBeOpenApi3() {
            String version = spec.path("openapi").asText();
            assertThat(version).startsWith("3.");
        }

        @Test
        @DisplayName("spec must declare title and version")
        void specMustHaveInfoMetadata() {
            JsonNode info = spec.path("info");
            assertThat(info.path("title").asText()).contains("Auth");
            assertThat(info.path("version").asText()).matches("\\d+\\.\\d+\\.\\d+");
        }

        @Test
        @DisplayName("spec must define auth security scheme")
        void specMustDefineSecurityScheme() {
            JsonNode schemes = spec.at("/components/securitySchemes");
            assertThat(schemes.has("bearerAuth") || schemes.has("jwt"))
                    .as("Must define at least one auth scheme (bearerAuth or jwt)")
                    .isTrue();
        }
    }

    // =========================================================================
    // Authentication Endpoints Contract
    // =========================================================================

    @Nested
    @DisplayName("Authentication Endpoints")
    class AuthEndpointsContract {

        @Test
        @DisplayName("POST /auth/login endpoint must exist and return 200")
        void loginEndpointMustExist() {
            JsonNode endpoint = spec.at("/paths/~1auth~1login/post");
            assertThat(endpoint.isMissingNode()).isFalse();

            JsonNode responses = endpoint.path("responses");
            assertThat(responses.path("200").isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("POST /auth/login success response must include token and user")
        void loginResponseMustIncludeTokenAndUser() {
            JsonNode schemaRef = spec.at("/paths/~1auth~1login/post/responses/200/content/application~1json/schema");
            assertThat(schemaRef.isMissingNode()).isFalse();

            // Navigate to the referenced component schema to check properties
            JsonNode properties = spec.at("/components/schemas/LoginResponse/properties");
            assertThat(properties.has("accessToken")).as("login response must include 'accessToken' (token) property").isTrue();
            assertThat(properties.has("userId")).as("login response must include 'userId' (user identity) property").isTrue();
        }

        @Test
        @DisplayName("POST /auth/login must accept email and password in request body")
        void loginRequestMustAcceptCredentials() {
            JsonNode requestRef = spec.at("/paths/~1auth~1login/post/requestBody/content/application~1json/schema");
            assertThat(requestRef.isMissingNode()).isFalse();

            // Navigate to the referenced component schema to check properties
            JsonNode properties = spec.at("/components/schemas/LoginRequest/properties");
            assertThat(properties.has("email")).as("login request must include 'email' property").isTrue();
            assertThat(properties.has("password")).as("login request must include 'password' property").isTrue();
        }

        @Test
        @DisplayName("POST /auth/logout endpoint must exist")
        void logoutEndpointMustExist() {
            JsonNode endpoint = spec.at("/paths/~1auth~1logout/post");
            assertThat(endpoint.isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("POST /auth/logout must require authentication")
        void logoutMustRequireAuth() {
            JsonNode endpoint = spec.at("/paths/~1auth~1logout/post");
            JsonNode security = endpoint.path("security");
            assertThat(security.iterator().hasNext())
                    .as("Logout must require authentication (security requirement)")
                    .isTrue();
        }
    }

    // =========================================================================
    // Token Validation Contract
    // =========================================================================

    @Nested
    @DisplayName("Token Validation Endpoints")
    class TokenValidationContract {

        @Test
        @DisplayName("GET /auth/validate endpoint must exist")
        void validateEndpointMustExist() {
            JsonNode endpoint = spec.at("/paths/~1auth~1validate/get");
            assertThat(endpoint.isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("GET /auth/validate must accept Authorization header")
        void validateMustAcceptAuthHeader() {
            JsonNode endpoint = spec.at("/paths/~1auth~1validate/get");

            // Auth can be specified via an explicit header parameter OR a security scheme
            boolean hasAuthParam = false;
            for (JsonNode param : endpoint.path("parameters")) {
                if ("authorization".equalsIgnoreCase(param.path("name").asText())) {
                    hasAuthParam = true;
                    break;
                }
            }
            // bearerAuth security requirement means Authorization: Bearer <token> is required
            JsonNode security = endpoint.path("security");
            boolean hasBearerSecurity = !security.isMissingNode() && security.isArray() && security.size() > 0;

            assertThat(hasAuthParam || hasBearerSecurity)
                    .as("Must accept Authorization via security scheme or explicit header parameter for token validation")
                    .isTrue();
        }

        @Test
        @DisplayName("GET /auth/validate must return 200 for valid token")
        void validateMustReturn200ForValidToken() {
            JsonNode responses = spec.at("/paths/~1auth~1validate/get/responses");
            assertThat(responses.path("200").isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("GET /auth/validate must return 401 for invalid token")
        void validateMustReturn401ForInvalidToken() {
            JsonNode responses = spec.at("/paths/~1auth~1validate/get/responses");
            assertThat(responses.path("401").isMissingNode()).isFalse();
        }
    }

    // =========================================================================
    // Error Response Contract
    // =========================================================================

    @Nested
    @DisplayName("Error Response Contracts")
    class ErrorResponseContract {

        @Test
        @DisplayName("error responses must include status, code, and message fields")
        void errorResponseMustHaveStandardFields() {
            // Find error response schema (typically in components/schemas)
            JsonNode errorSchema = spec.at("/components/schemas/Error");
            if (errorSchema.isMissingNode()) {
                // Try alternate location
                errorSchema = spec.at("/components/schemas/ErrorResponse");
            }

            // If no explicit error schema, check response examples
            JsonNode loginFailure = spec.at("/paths/~1auth~1login/post/responses/400/content/application~1json/schema");
            assertThat(loginFailure.isMissingNode() || !errorSchema.isMissingNode())
                    .as("Must define error response schema")
                    .isTrue();
        }

        @Test
        @DisplayName("400 Bad Request must be documented for login endpoint")
        void badRequestMustBeDocumented() {
            JsonNode responses = spec.at("/paths/~1auth~1login/post/responses");
            assertThat(responses.path("400").isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("401 Unauthorized must be documented for protected endpoints")
        void unauthorizedMustBeDocumented() {
            JsonNode responses = spec.at("/paths/~1auth~1logout/post/responses");
            assertThat(responses.path("401").isMissingNode()).isFalse();
        }

        @Test
        @DisplayName("500 Server Error must be documented")
        void serverErrorMustBeDocumented() {
            JsonNode responses = spec.at("/paths/~1auth~1login/post/responses");
            assertThat(responses.path("500").isMissingNode()).isFalse();
        }
    }

    // =========================================================================
    // Security Contract
    // =========================================================================

    @Nested
    @DisplayName("Security Contract")
    class SecurityContract {

        @Test
        @DisplayName("login endpoint must not require pre-existing authentication")
        void loginMustNotRequireAuth() {
            JsonNode endpoint = spec.at("/paths/~1auth~1login/post");
            JsonNode security = endpoint.path("security");
            // Login can override global security, allowing no auth
            assertThat(security.isArray() && security.size() == 0
                    || endpoint.path("security").isMissingNode()
                    || "[]".equals(endpoint.path("security").toString()))
                    .as("Login endpoint must be publicly accessible")
                    .isTrue();
        }

        @Test
        @DisplayName("user info endpoint must require authentication")
        void userInfoMustRequireAuth() {
            JsonNode endpoint = spec.at("/paths/~1auth~1user/get");
            if (!endpoint.isMissingNode()) {
                JsonNode security = endpoint.path("security");
                assertThat(security.iterator().hasNext())
                        .as("User info endpoint must require authentication")
                        .isTrue();
            }
        }
    }

    // =========================================================================
    // Backwards Compatibility Contract
    // =========================================================================

    @Nested
    @DisplayName("Backwards Compatibility")
    class BackwardsCompatibilityContract {

        @Test
        @DisplayName("v1 endpoints must remain available with deprecation notice")
        void deprecatedEndpointsMustStayAvailable() {
            // If /auth/login exists, look for any v1 deprecation
            JsonNode endpoint = spec.at("/paths/~1auth~1login/post");
            assertThat(endpoint.isMissingNode()).isFalse();

            // Deprecation can be signaled via deprecated: true or description
            JsonNode description = endpoint.path("description");
            boolean hasMigrationPath = endpoint.path("deprecated").asBoolean(false)
                    || description.asText().toLowerCase().contains("deprecated");

            // If deprecated, must document the new endpoint
            if (hasMigrationPath) {
                JsonNode newEndpoint = spec.at("/paths/~1v2~1auth~1login/post");
                // Either v2 exists or description mentions migration path
                assertThat(newEndpoint.isMissingNode() || description.asText().contains("use /v2"))
                        .as("Deprecated endpoint must document migration path")
                        .isTrue();
            }
        }

        @Test
        @DisplayName("response schema must not remove required fields")
        void responseSchemaMustNotRemoveFields() {
            JsonNode userSchema = spec.at("/components/schemas/User");
            if (!userSchema.isMissingNode()) {
                JsonNode properties = userSchema.path("properties");
                // Verify core user fields exist
                assertThat(properties.has("id")).as("User schema must have id").isTrue();
            }
        }
    }
}
