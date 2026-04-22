/**
 * App Platform Kernel Contract Test Suite
 *
 * Validates that the App Platform Kernel API conforms to its OpenAPI specification.
 * Tests request/response schemas, error responses, and version compatibility.
 *
 * @doc.type test
 * @doc.purpose Contract validation for app-platform-kernel public API
 * @doc.layer platform-kernel
 * @doc.pattern ContractTest
 */

package com.ghatana.kernel.contract;

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
 * Contract tests for the App Platform Kernel OpenAPI specification.
 *
 * <p>This module is a library, not a Spring Boot application, so contract validation here
 * focuses on the published OpenAPI document rather than spinning up an HTTP application
 * context that does not exist in this project.</p>
 */
@DisplayName("App Platform Kernel Contract Tests [GH-90000]")
public class AppPlatformKernelContractTest {

    private static final Path SPEC_PATH = Path.of( // GH-90000
            "..",
            "..",
            "platform",
            "contracts",
            "openapi",
            "app-platform-kernel.yaml"
    );

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()); // GH-90000

    private static final String API_VERSION = "1.0.0";

    @Test
    @DisplayName("OpenAPI contract file exists and is readable [GH-90000]")
    void openApiContractFileExists() { // GH-90000
        assertThat(SPEC_PATH) // GH-90000
                .as("Kernel OpenAPI contract should exist at %s", SPEC_PATH) // GH-90000
                .exists() // GH-90000
                .isReadable(); // GH-90000
    }

    @Test
    @DisplayName("OpenAPI metadata matches expected kernel contract [GH-90000]")
    void openApiMetadataIsConsistent() throws Exception { // GH-90000
        JsonNode root = readSpec(); // GH-90000

        assertThat(root.path("openapi [GH-90000]").asText()).isEqualTo("3.1.0 [GH-90000]");
        assertThat(root.path("info [GH-90000]").path("title [GH-90000]").asText()).isEqualTo("AppPlatform Kernel API [GH-90000]");
        assertThat(root.path("info [GH-90000]").path("version [GH-90000]").asText()).isEqualTo(API_VERSION);
        assertThat(root.path("servers [GH-90000]")).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("OpenAPI exposes all required kernel paths [GH-90000]")
    void openApiContainsExpectedPaths() throws Exception { // GH-90000
        JsonNode paths = readSpec().path("paths [GH-90000]");

        assertThat(paths.has("/health [GH-90000]")).isTrue();
        assertThat(paths.has("/api/v1/rules/evaluate [GH-90000]")).isTrue();
        assertThat(paths.has("/api/v1/rules/packs [GH-90000]")).isTrue();
        assertThat(paths.has("/api/v1/plugins [GH-90000]")).isTrue();
        assertThat(paths.has("/api/v1/plugins/register [GH-90000]")).isTrue();
        assertThat(paths.has("/api/v1/calendar/bs-to-gregorian [GH-90000]")).isTrue();
        assertThat(paths.has("/api/v1/calendar/gregorian-to-bs [GH-90000]")).isTrue();
        assertThat(paths.has("/auth/token [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("OpenAPI defines bearer authentication and shared error schema [GH-90000]")
    void openApiDefinesSharedSecurityAndErrorSchemas() throws Exception { // GH-90000
        JsonNode root = readSpec(); // GH-90000
        JsonNode components = root.path("components [GH-90000]");

        assertThat(components.path("securitySchemes [GH-90000]").has("BearerAuth [GH-90000]")).isTrue();
        assertThat(components.path("securitySchemes [GH-90000]").path("BearerAuth [GH-90000]").path("type [GH-90000]").asText())
                .isEqualTo("http [GH-90000]");
        assertThat(components.path("schemas [GH-90000]").has("ErrorResponse [GH-90000]")).isTrue();
        assertThat(components.path("schemas [GH-90000]").has("CalendarDate [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("OpenAPI request and response schemas cover key kernel capabilities [GH-90000]")
    void openApiContainsExpectedSchemas() throws Exception { // GH-90000
        JsonNode schemas = readSpec().path("components [GH-90000]").path("schemas [GH-90000]");

        assertThat(schemas.has("EvaluateRuleRequest [GH-90000]")).isTrue();
        assertThat(schemas.has("EvaluateRuleResponse [GH-90000]")).isTrue();
        assertThat(schemas.has("RulePackRequest [GH-90000]")).isTrue();
        assertThat(schemas.has("RegisterPluginRequest [GH-90000]")).isTrue();
        assertThat(schemas.has("RegisterPluginResponse [GH-90000]")).isTrue();
        assertThat(schemas.has("InvokePluginRequest [GH-90000]")).isTrue();
        assertThat(schemas.has("InvokePluginResponse [GH-90000]")).isTrue();
        assertThat(schemas.has("TokenRequest [GH-90000]")).isTrue();
        assertThat(schemas.has("TokenResponse [GH-90000]")).isTrue();
        assertThat(schemas.has("HealthResponse [GH-90000]")).isTrue();
    }

    private JsonNode readSpec() throws IOException { // GH-90000
        try (var inputStream = Files.newInputStream(SPEC_PATH)) { // GH-90000
            return objectMapper.readTree(inputStream); // GH-90000
        }
    }
}
