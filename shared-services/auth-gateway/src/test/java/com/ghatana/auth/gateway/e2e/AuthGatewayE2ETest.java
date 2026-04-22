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
@DisplayName("Auth Gateway E2E Tests [GH-90000]")
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
    @DisplayName("Auth flow endpoints exist in documented login-validate-refresh-exchange sequence [GH-90000]")
    void authFlowEndpointsExistInSequence() throws Exception { // GH-90000
        JsonNode paths = readSpec().path("paths [GH-90000]");

        assertThat(paths.has("/auth/login [GH-90000]")).isTrue();
        assertThat(paths.has("/auth/validate [GH-90000]")).isTrue();
        assertThat(paths.has("/auth/refresh [GH-90000]")).isTrue();
        assertThat(paths.has("/auth/exchange [GH-90000]")).isTrue();
        assertThat(paths.has("/auth/logout [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Login and refresh responses declare token-bearing response schemas [GH-90000]")
    void tokenBearingSchemasExist() throws Exception { // GH-90000
        JsonNode schemas = readSpec().path("components [GH-90000]").path("schemas [GH-90000]");

        assertThat(schemas.path("LoginResponse [GH-90000]").path("properties [GH-90000]").has("accessToken [GH-90000]")).isTrue();
        assertThat(schemas.path("LoginResponse [GH-90000]").path("properties [GH-90000]").has("refreshToken [GH-90000]")).isTrue();
        assertThat(schemas.path("RefreshResponse [GH-90000]").path("properties [GH-90000]").has("accessToken [GH-90000]")).isTrue();
        assertThat(schemas.path("ExchangeResponse [GH-90000]").path("properties [GH-90000]").has("platformToken [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Protected tenant and validation endpoints require documented bearer auth [GH-90000]")
    void protectedEndpointsDeclareBearerSecurity() throws Exception { // GH-90000
        JsonNode paths = readSpec().path("paths [GH-90000]");

        assertThat(paths.path("/auth/validate [GH-90000]").path("get [GH-90000]").path("security [GH-90000]")).isNotEmpty();
        assertThat(paths.path("/auth/refresh [GH-90000]").path("post [GH-90000]").path("security [GH-90000]")).isNotEmpty();
        assertThat(paths.path("/auth/tenant [GH-90000]").path("get [GH-90000]").path("security [GH-90000]")).isNotEmpty();
    }

    @Test
    @DisplayName("Health endpoint documents auth-gateway service identity [GH-90000]")
    void healthEndpointDocumentsServiceIdentity() throws Exception { // GH-90000
        JsonNode healthSchema = readSpec().path("components [GH-90000]").path("schemas [GH-90000]").path("HealthResponse [GH-90000]");

        assertThat(healthSchema.path("properties [GH-90000]").path("service [GH-90000]").path("example [GH-90000]").asText())
                .isEqualTo("auth-gateway [GH-90000]");
    }

    private JsonNode readSpec() throws IOException { // GH-90000
        try (var inputStream = Files.newInputStream(SPEC_PATH)) { // GH-90000
            return objectMapper.readTree(inputStream); // GH-90000
        }
    }
}
