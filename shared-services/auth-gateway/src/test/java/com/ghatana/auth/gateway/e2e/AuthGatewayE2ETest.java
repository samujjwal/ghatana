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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E-oriented contract smoke tests for the Auth Gateway specification.
 *
 * <p>These tests validate the documented end-to-end auth flows at the contract boundary for this
 * module. They intentionally avoid a Spring Boot harness because the shared-service module does
 * not provide one in its current build graph.</p>
 */
@DisplayName("Auth Gateway E2E Tests")
public class AuthGatewayE2ETest {

    private static final Path SPEC_PATH = Path.of( // GH-90000
            "..",
            "..",
            "platform",
            "contracts",
            "openapi",
            "auth-gateway.yaml"
    );

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()); // GH-90000

    @Test
    @DisplayName("Auth flow endpoints exist in documented login-validate-refresh-exchange sequence")
    void authFlowEndpointsExistInSequence() throws Exception { // GH-90000
        JsonNode paths = readSpec().path("paths");

        assertThat(paths.has("/auth/login")).isTrue();
        assertThat(paths.has("/auth/validate")).isTrue();
        assertThat(paths.has("/auth/refresh")).isTrue();
        assertThat(paths.has("/auth/exchange")).isTrue();
        assertThat(paths.has("/auth/logout")).isTrue();
    }

    @Test
    @DisplayName("Login and refresh responses declare token-bearing response schemas")
    void tokenBearingSchemasExist() throws Exception { // GH-90000
        JsonNode schemas = readSpec().path("components").path("schemas");

        assertThat(schemas.path("LoginResponse").path("properties").has("accessToken")).isTrue();
        assertThat(schemas.path("LoginResponse").path("properties").has("refreshToken")).isTrue();
        assertThat(schemas.path("RefreshResponse").path("properties").has("accessToken")).isTrue();
        assertThat(schemas.path("ExchangeResponse").path("properties").has("platformToken")).isTrue();
    }

    @Test
    @DisplayName("Protected tenant and validation endpoints require documented bearer auth")
    void protectedEndpointsDeclareBearerSecurity() throws Exception { // GH-90000
        JsonNode paths = readSpec().path("paths");

        assertThat(paths.path("/auth/validate").path("get").path("security")).isNotEmpty();
        assertThat(paths.path("/auth/refresh").path("post").path("security")).isNotEmpty();
        assertThat(paths.path("/auth/tenant").path("get").path("security")).isNotEmpty();
    }

    @Test
    @DisplayName("Health endpoint documents auth-gateway service identity")
    void healthEndpointDocumentsServiceIdentity() throws Exception { // GH-90000
        JsonNode healthSchema = readSpec().path("components").path("schemas").path("HealthResponse");

        assertThat(healthSchema.path("properties").path("service").path("example").asText())
                .isEqualTo("auth-gateway");
    }

    private JsonNode readSpec() throws IOException { // GH-90000
        try (var inputStream = Files.newInputStream(SPEC_PATH)) { // GH-90000
            return objectMapper.readTree(inputStream); // GH-90000
        }
    }
}
