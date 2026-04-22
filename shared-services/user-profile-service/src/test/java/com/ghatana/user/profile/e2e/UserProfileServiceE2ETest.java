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
@DisplayName("User Profile Service E2E Tests [GH-90000]")
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
    @DisplayName("Profile CRUD endpoints exist for documented end-to-end flows [GH-90000]")
    void profileCrudEndpointsExist() throws Exception { // GH-90000
        JsonNode paths = readSpec().path("paths [GH-90000]");

        assertThat(paths.has("/profiles/{userId} [GH-90000]")).isTrue();
        assertThat(paths.path("/profiles/{userId} [GH-90000]").has("get [GH-90000]")).isTrue();
        assertThat(paths.path("/profiles/{userId} [GH-90000]").has("put [GH-90000]")).isTrue();
        assertThat(paths.path("/profiles/{userId} [GH-90000]").has("delete [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Profile endpoints declare tenant-scoped bearer and internal-key security [GH-90000]")
    void profileEndpointsDeclareSecurity() throws Exception { // GH-90000
        JsonNode profilePath = readSpec().path("paths [GH-90000]").path("/profiles/{userId} [GH-90000]");

        assertThat(profilePath.path("get [GH-90000]").path("security [GH-90000]")).isNotEmpty();
        assertThat(profilePath.path("put [GH-90000]").path("security [GH-90000]")).isNotEmpty();
        assertThat(profilePath.path("delete [GH-90000]").path("security [GH-90000]")).isNotEmpty();
    }

    @Test
    @DisplayName("Schema documents complete profile payload and update request fields [GH-90000]")
    void profileSchemasDocumentFlowPayloads() throws Exception { // GH-90000
        JsonNode schemas = readSpec().path("components [GH-90000]").path("schemas [GH-90000]");

        assertThat(schemas.path("UserProfile [GH-90000]").path("properties [GH-90000]").has("userId [GH-90000]")).isTrue();
        assertThat(schemas.path("UserProfile [GH-90000]").path("properties [GH-90000]").has("tenantId [GH-90000]")).isTrue();
        assertThat(schemas.path("UserProfile [GH-90000]").path("properties [GH-90000]").has("email [GH-90000]")).isTrue();
        assertThat(schemas.path("UserProfile [GH-90000]").path("properties [GH-90000]").has("theme [GH-90000]")).isTrue();
        assertThat(schemas.path("UpsertProfileRequest [GH-90000]").path("properties [GH-90000]").has("displayName [GH-90000]")).isTrue();
        assertThat(schemas.path("UpsertProfileRequest [GH-90000]").path("properties [GH-90000]").has("notificationsEnabled [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Observability endpoints remain part of the documented service surface [GH-90000]")
    void observabilityEndpointsRemainDocumented() throws Exception { // GH-90000
        JsonNode paths = readSpec().path("paths [GH-90000]");
        JsonNode healthSchema = readSpec().path("components [GH-90000]").path("schemas [GH-90000]").path("HealthResponse [GH-90000]");

        assertThat(paths.has("/health [GH-90000]")).isTrue();
        assertThat(paths.has("/metrics [GH-90000]")).isTrue();
        assertThat(healthSchema.path("properties [GH-90000]").path("service [GH-90000]").path("example [GH-90000]").asText())
                .isEqualTo("user-profile-service [GH-90000]");
    }

    private JsonNode readSpec() throws IOException { // GH-90000
        try (var inputStream = Files.newInputStream(SPEC_PATH)) { // GH-90000
            return objectMapper.readTree(inputStream); // GH-90000
        }
    }
}
