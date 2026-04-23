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
 * Contract tests for the User Profile Service OpenAPI specification.
 *
 * <p>This shared service is validated against its published contract here rather than through a
 * Spring Boot harness that is not part of the module's current build graph.</p>
 */
@DisplayName("User Profile Service Contract Tests")
public class UserProfileServiceContractTest {

    private static final Path SPEC_PATH = Path.of( // GH-90000
            "..",
            "..",
            "platform",
            "contracts",
            "openapi",
            "user-profile-service.yaml"
    );

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()); // GH-90000

    private static final String API_VERSION = "1.0.0";

    @Test
    @DisplayName("OpenAPI contract file exists and is readable")
    void openApiContractFileExists() { // GH-90000
        assertThat(SPEC_PATH) // GH-90000
                .as("User Profile OpenAPI contract should exist at %s", SPEC_PATH) // GH-90000
                .exists() // GH-90000
                .isReadable(); // GH-90000
    }

    @Test
    @DisplayName("OpenAPI metadata matches expected user profile service contract")
    void openApiMetadataIsConsistent() throws Exception { // GH-90000
        JsonNode root = readSpec(); // GH-90000

        assertThat(root.path("openapi").asText()).isEqualTo("3.1.0");
        assertThat(root.path("info").path("title").asText())
                .isEqualTo("Ghatana User Profile Service API");
        assertThat(root.path("info").path("version").asText()).isEqualTo(API_VERSION);
        assertThat(root.path("servers")).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("OpenAPI exposes the expected user profile and observability paths")
    void openApiContainsExpectedPaths() throws Exception { // GH-90000
        JsonNode paths = readSpec().path("paths");

        assertThat(paths.has("/health")).isTrue();
        assertThat(paths.has("/metrics")).isTrue();
        assertThat(paths.has("/profiles/{userId}")).isTrue();
    }

    @Test
    @DisplayName("OpenAPI defines expected user profile schemas and error responses")
    void openApiContainsExpectedSchemas() throws Exception { // GH-90000
        JsonNode schemas = readSpec().path("components").path("schemas");

        assertThat(schemas.has("HealthResponse")).isTrue();
        assertThat(schemas.has("UserProfile")).isTrue();
        assertThat(schemas.has("UpsertProfileRequest")).isTrue();
        assertThat(schemas.has("ErrorResponse")).isTrue();
    }

    @Test
    @DisplayName("OpenAPI declares required tenant and auth security boundaries")
    void openApiDefinesTenantAndSecurityRequirements() throws Exception { // GH-90000
        JsonNode root = readSpec(); // GH-90000
        JsonNode components = root.path("components");
        JsonNode profilePath = root.path("paths").path("/profiles/{userId}");

        assertThat(components.path("parameters").has("TenantId")).isTrue();
        assertThat(components.path("securitySchemes").has("bearerAuth")).isTrue();
        assertThat(components.path("securitySchemes").has("internalKey")).isTrue();
        assertThat(profilePath.path("get").path("security")).isNotEmpty();
        assertThat(profilePath.path("put").path("security")).isNotEmpty();
        assertThat(profilePath.path("delete").path("security")).isNotEmpty();
    }

    private JsonNode readSpec() throws IOException { // GH-90000
        try (var inputStream = Files.newInputStream(SPEC_PATH)) { // GH-90000
            return objectMapper.readTree(inputStream); // GH-90000
        }
    }
}
