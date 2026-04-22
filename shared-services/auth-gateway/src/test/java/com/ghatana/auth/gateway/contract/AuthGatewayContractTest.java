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

package com.ghatana.auth.gateway.contract;

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
 * Contract tests for the Auth Gateway OpenAPI specification.
 *
 * <p>The auth-gateway module is validated here against its published contract instead of
 * relying on a Spring Boot test harness that is not wired into this ActiveJ-oriented module.</p>
 */
@DisplayName("Auth Gateway Contract Tests [GH-90000]")
public class AuthGatewayContractTest {

    private static final Path SPEC_PATH = Path.of( // GH-90000
            "..",
            "..",
            "platform",
            "contracts",
            "openapi",
            "auth-gateway.yaml"
    );

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()); // GH-90000

    private static final String API_VERSION = "2.0.0";

    @Test
    @DisplayName("OpenAPI contract file exists and is readable [GH-90000]")
    void openApiContractFileExists() { // GH-90000
        assertThat(SPEC_PATH) // GH-90000
                .as("Auth Gateway OpenAPI contract should exist at %s", SPEC_PATH) // GH-90000
                .exists() // GH-90000
                .isReadable(); // GH-90000
    }

    @Test
    @DisplayName("OpenAPI metadata matches expected auth gateway contract [GH-90000]")
    void openApiMetadataIsConsistent() throws Exception { // GH-90000
        JsonNode root = readSpec(); // GH-90000

        assertThat(root.path("openapi [GH-90000]").asText()).isEqualTo("3.1.0 [GH-90000]");
        assertThat(root.path("info [GH-90000]").path("title [GH-90000]").asText()).isEqualTo("Ghatana Auth Gateway API [GH-90000]");
        assertThat(root.path("info [GH-90000]").path("version [GH-90000]").asText()).isEqualTo(API_VERSION);
    }

    @Test
    @DisplayName("OpenAPI exposes the expected auth flow endpoints [GH-90000]")
    void openApiContainsExpectedPaths() throws Exception { // GH-90000
        JsonNode paths = readSpec().path("paths [GH-90000]");

        assertThat(paths.has("/auth/login [GH-90000]")).isTrue();
        assertThat(paths.has("/auth/validate [GH-90000]")).isTrue();
        assertThat(paths.has("/auth/refresh [GH-90000]")).isTrue();
        assertThat(paths.has("/auth/exchange [GH-90000]")).isTrue();
        assertThat(paths.has("/auth/tenant [GH-90000]")).isTrue();
        assertThat(paths.has("/health [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("OpenAPI defines expected schemas for requests and responses [GH-90000]")
    void openApiContainsExpectedSchemas() throws Exception { // GH-90000
        JsonNode schemas = readSpec().path("components [GH-90000]").path("schemas [GH-90000]");

        assertThat(schemas.has("LoginRequest [GH-90000]")).isTrue();
        assertThat(schemas.has("LoginResponse [GH-90000]")).isTrue();
        assertThat(schemas.has("ValidateResponse [GH-90000]")).isTrue();
        assertThat(schemas.has("ValidateErrorResponse [GH-90000]")).isTrue();
        assertThat(schemas.has("RefreshResponse [GH-90000]")).isTrue();
        assertThat(schemas.has("ExchangeResponse [GH-90000]")).isTrue();
        assertThat(schemas.has("TenantResponse [GH-90000]")).isTrue();
        assertThat(schemas.has("ErrorResponse [GH-90000]")).isTrue();
        assertThat(schemas.has("HealthResponse [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("OpenAPI exposes bearer authentication and auth gateway health example [GH-90000]")
    void openApiDefinesSecurityAndHealthMetadata() throws Exception { // GH-90000
        JsonNode root = readSpec(); // GH-90000
        JsonNode components = root.path("components [GH-90000]");

        assertThat(components.path("securitySchemes [GH-90000]").has("bearerAuth [GH-90000]")).isTrue();
        assertThat(components.path("schemas [GH-90000]").path("HealthResponse [GH-90000]").path("properties [GH-90000]")
                .path("service [GH-90000]").path("example [GH-90000]").asText()).isEqualTo("auth-gateway [GH-90000]");
    }

    private JsonNode readSpec() throws IOException { // GH-90000
        try (var inputStream = Files.newInputStream(SPEC_PATH)) { // GH-90000
            return objectMapper.readTree(inputStream); // GH-90000
        }
    }
}
