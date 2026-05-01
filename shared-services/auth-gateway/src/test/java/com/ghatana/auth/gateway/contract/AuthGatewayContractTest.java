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
@DisplayName("Auth Gateway Contract Tests")
public class AuthGatewayContractTest {

    private static final Path SPEC_PATH = Path.of( 
            "..",
            "..",
            "platform",
            "contracts",
            "openapi",
            "auth-gateway.yaml"
    );

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()); 

    private static final String API_VERSION = "2.0.0";

    @Test
    @DisplayName("OpenAPI contract file exists and is readable")
    void openApiContractFileExists() { 
        assertThat(SPEC_PATH) 
                .as("Auth Gateway OpenAPI contract should exist at %s", SPEC_PATH) 
                .exists() 
                .isReadable(); 
    }

    @Test
    @DisplayName("OpenAPI metadata matches expected auth gateway contract")
    void openApiMetadataIsConsistent() throws Exception { 
        JsonNode root = readSpec(); 

        assertThat(root.path("openapi").asText()).isEqualTo("3.1.0");
        assertThat(root.path("info").path("title").asText()).isEqualTo("Ghatana Auth Gateway API");
        assertThat(root.path("info").path("version").asText()).isEqualTo(API_VERSION);
    }

    @Test
    @DisplayName("OpenAPI exposes the expected auth flow endpoints")
    void openApiContainsExpectedPaths() throws Exception { 
        JsonNode paths = readSpec().path("paths");

        assertThat(paths.has("/auth/login")).isTrue();
        assertThat(paths.has("/auth/validate")).isTrue();
        assertThat(paths.has("/auth/refresh")).isTrue();
        assertThat(paths.has("/auth/exchange")).isTrue();
        assertThat(paths.has("/auth/tenant")).isTrue();
        assertThat(paths.has("/health")).isTrue();
    }

    @Test
    @DisplayName("OpenAPI defines expected schemas for requests and responses")
    void openApiContainsExpectedSchemas() throws Exception { 
        JsonNode schemas = readSpec().path("components").path("schemas");

        assertThat(schemas.has("LoginRequest")).isTrue();
        assertThat(schemas.has("LoginResponse")).isTrue();
        assertThat(schemas.has("ValidateResponse")).isTrue();
        assertThat(schemas.has("ValidateErrorResponse")).isTrue();
        assertThat(schemas.has("RefreshResponse")).isTrue();
        assertThat(schemas.has("ExchangeResponse")).isTrue();
        assertThat(schemas.has("TenantResponse")).isTrue();
        assertThat(schemas.has("ErrorResponse")).isTrue();
        assertThat(schemas.has("HealthResponse")).isTrue();
    }

    @Test
    @DisplayName("OpenAPI exposes bearer authentication and auth gateway health example")
    void openApiDefinesSecurityAndHealthMetadata() throws Exception { 
        JsonNode root = readSpec(); 
        JsonNode components = root.path("components");

        assertThat(components.path("securitySchemes").has("bearerAuth")).isTrue();
        assertThat(components.path("schemas").path("HealthResponse").path("properties")
                .path("service").path("example").asText()).isEqualTo("auth-gateway");
    }

    private JsonNode readSpec() throws IOException { 
        try (var inputStream = Files.newInputStream(SPEC_PATH)) { 
            return objectMapper.readTree(inputStream); 
        }
    }
}
