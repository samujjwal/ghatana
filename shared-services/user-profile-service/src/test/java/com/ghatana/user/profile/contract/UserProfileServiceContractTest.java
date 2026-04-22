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
@DisplayName("User Profile Service Contract Tests [GH-90000]")
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
    @DisplayName("OpenAPI contract file exists and is readable [GH-90000]")
    void openApiContractFileExists() { // GH-90000
        assertThat(SPEC_PATH) // GH-90000
                .as("User Profile OpenAPI contract should exist at %s", SPEC_PATH) // GH-90000
                .exists() // GH-90000
                .isReadable(); // GH-90000
    }

    @Test
    @DisplayName("OpenAPI metadata matches expected user profile service contract [GH-90000]")
    void openApiMetadataIsConsistent() throws Exception { // GH-90000
        JsonNode root = readSpec(); // GH-90000

        assertThat(root.path("openapi [GH-90000]").asText()).isEqualTo("3.1.0 [GH-90000]");
        assertThat(root.path("info [GH-90000]").path("title [GH-90000]").asText())
                .isEqualTo("Ghatana User Profile Service API [GH-90000]");
        assertThat(root.path("info [GH-90000]").path("version [GH-90000]").asText()).isEqualTo(API_VERSION);
        assertThat(root.path("servers [GH-90000]")).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("OpenAPI exposes the expected user profile and observability paths [GH-90000]")
    void openApiContainsExpectedPaths() throws Exception { // GH-90000
        JsonNode paths = readSpec().path("paths [GH-90000]");

        assertThat(paths.has("/health [GH-90000]")).isTrue();
        assertThat(paths.has("/metrics [GH-90000]")).isTrue();
        assertThat(paths.has("/profiles/{userId} [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("OpenAPI defines expected user profile schemas and error responses [GH-90000]")
    void openApiContainsExpectedSchemas() throws Exception { // GH-90000
        JsonNode schemas = readSpec().path("components [GH-90000]").path("schemas [GH-90000]");

        assertThat(schemas.has("HealthResponse [GH-90000]")).isTrue();
        assertThat(schemas.has("UserProfile [GH-90000]")).isTrue();
        assertThat(schemas.has("UpsertProfileRequest [GH-90000]")).isTrue();
        assertThat(schemas.has("ErrorResponse [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("OpenAPI declares required tenant and auth security boundaries [GH-90000]")
    void openApiDefinesTenantAndSecurityRequirements() throws Exception { // GH-90000
        JsonNode root = readSpec(); // GH-90000
        JsonNode components = root.path("components [GH-90000]");
        JsonNode profilePath = root.path("paths [GH-90000]").path("/profiles/{userId} [GH-90000]");

        assertThat(components.path("parameters [GH-90000]").has("TenantId [GH-90000]")).isTrue();
        assertThat(components.path("securitySchemes [GH-90000]").has("bearerAuth [GH-90000]")).isTrue();
        assertThat(components.path("securitySchemes [GH-90000]").has("internalKey [GH-90000]")).isTrue();
        assertThat(profilePath.path("get [GH-90000]").path("security [GH-90000]")).isNotEmpty();
        assertThat(profilePath.path("put [GH-90000]").path("security [GH-90000]")).isNotEmpty();
        assertThat(profilePath.path("delete [GH-90000]").path("security [GH-90000]")).isNotEmpty();
    }

    private JsonNode readSpec() throws IOException { // GH-90000
        try (var inputStream = Files.newInputStream(SPEC_PATH)) { // GH-90000
            return objectMapper.readTree(inputStream); // GH-90000
        }
    }
}
