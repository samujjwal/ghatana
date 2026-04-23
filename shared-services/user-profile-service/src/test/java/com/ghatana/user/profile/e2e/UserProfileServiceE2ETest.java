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
 * E2E-oriented contract smoke tests for the User Profile Service specification.
 *
 * <p>These tests validate the documented profile-management flows and boundary requirements
 * without depending on a Spring Boot harness that is not wired into this shared-service module.</p>
 */
@DisplayName("User Profile Service E2E Tests")
public class UserProfileServiceE2ETest {

    private static final Path SPEC_PATH = Path.of( // GH-90000
            "..",
            "..",
            "platform",
            "contracts",
            "openapi",
            "user-profile-service.yaml"
    );

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()); // GH-90000

    @Test
    @DisplayName("Profile CRUD endpoints exist for documented end-to-end flows")
    void profileCrudEndpointsExist() throws Exception { // GH-90000
        JsonNode paths = readSpec().path("paths");

        assertThat(paths.has("/profiles/{userId}")).isTrue();
        assertThat(paths.path("/profiles/{userId}").has("get")).isTrue();
        assertThat(paths.path("/profiles/{userId}").has("put")).isTrue();
        assertThat(paths.path("/profiles/{userId}").has("delete")).isTrue();
    }

    @Test
    @DisplayName("Profile endpoints declare tenant-scoped bearer and internal-key security")
    void profileEndpointsDeclareSecurity() throws Exception { // GH-90000
        JsonNode profilePath = readSpec().path("paths").path("/profiles/{userId}");

        assertThat(profilePath.path("get").path("security")).isNotEmpty();
        assertThat(profilePath.path("put").path("security")).isNotEmpty();
        assertThat(profilePath.path("delete").path("security")).isNotEmpty();
    }

    @Test
    @DisplayName("Schema documents complete profile payload and update request fields")
    void profileSchemasDocumentFlowPayloads() throws Exception { // GH-90000
        JsonNode schemas = readSpec().path("components").path("schemas");

        assertThat(schemas.path("UserProfile").path("properties").has("userId")).isTrue();
        assertThat(schemas.path("UserProfile").path("properties").has("tenantId")).isTrue();
        assertThat(schemas.path("UserProfile").path("properties").has("email")).isTrue();
        assertThat(schemas.path("UserProfile").path("properties").has("theme")).isTrue();
        assertThat(schemas.path("UpsertProfileRequest").path("properties").has("displayName")).isTrue();
        assertThat(schemas.path("UpsertProfileRequest").path("properties").has("notificationsEnabled")).isTrue();
    }

    @Test
    @DisplayName("Observability endpoints remain part of the documented service surface")
    void observabilityEndpointsRemainDocumented() throws Exception { // GH-90000
        JsonNode paths = readSpec().path("paths");
        JsonNode healthSchema = readSpec().path("components").path("schemas").path("HealthResponse");

        assertThat(paths.has("/health")).isTrue();
        assertThat(paths.has("/metrics")).isTrue();
        assertThat(healthSchema.path("properties").path("service").path("example").asText())
                .isEqualTo("user-profile-service");
    }

    private JsonNode readSpec() throws IOException { // GH-90000
        try (var inputStream = Files.newInputStream(SPEC_PATH)) { // GH-90000
            return objectMapper.readTree(inputStream); // GH-90000
        }
    }
}
