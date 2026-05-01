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
@DisplayName("App Platform Kernel Contract Tests")
public class AppPlatformKernelContractTest {

    private static final Path SPEC_PATH = Path.of( 
            "..",
            "..",
            "platform",
            "contracts",
            "openapi",
            "app-platform-kernel.yaml"
    );

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()); 

    private static final String API_VERSION = "1.0.0";

    @Test
    @DisplayName("OpenAPI contract file exists and is readable")
    void openApiContractFileExists() { 
        assertThat(SPEC_PATH) 
                .as("Kernel OpenAPI contract should exist at %s", SPEC_PATH) 
                .exists() 
                .isReadable(); 
    }

    @Test
    @DisplayName("OpenAPI metadata matches expected kernel contract")
    void openApiMetadataIsConsistent() throws Exception { 
        JsonNode root = readSpec(); 

        assertThat(root.path("openapi").asText()).isEqualTo("3.1.0");
        assertThat(root.path("info").path("title").asText()).isEqualTo("AppPlatform Kernel API");
        assertThat(root.path("info").path("version").asText()).isEqualTo(API_VERSION);
        assertThat(root.path("servers")).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("OpenAPI exposes all required kernel paths")
    void openApiContainsExpectedPaths() throws Exception { 
        JsonNode paths = readSpec().path("paths");

        assertThat(paths.has("/health")).isTrue();
        assertThat(paths.has("/api/v1/rules/evaluate")).isTrue();
        assertThat(paths.has("/api/v1/rules/packs")).isTrue();
        assertThat(paths.has("/api/v1/plugins")).isTrue();
        assertThat(paths.has("/api/v1/plugins/register")).isTrue();
        assertThat(paths.has("/api/v1/calendar/bs-to-gregorian")).isTrue();
        assertThat(paths.has("/api/v1/calendar/gregorian-to-bs")).isTrue();
        assertThat(paths.has("/auth/token")).isTrue();
    }

    @Test
    @DisplayName("OpenAPI defines bearer authentication and shared error schema")
    void openApiDefinesSharedSecurityAndErrorSchemas() throws Exception { 
        JsonNode root = readSpec(); 
        JsonNode components = root.path("components");

        assertThat(components.path("securitySchemes").has("BearerAuth")).isTrue();
        assertThat(components.path("securitySchemes").path("BearerAuth").path("type").asText())
                .isEqualTo("http");
        assertThat(components.path("schemas").has("ErrorResponse")).isTrue();
        assertThat(components.path("schemas").has("CalendarDate")).isTrue();
    }

    @Test
    @DisplayName("OpenAPI request and response schemas cover key kernel capabilities")
    void openApiContainsExpectedSchemas() throws Exception { 
        JsonNode schemas = readSpec().path("components").path("schemas");

        assertThat(schemas.has("EvaluateRuleRequest")).isTrue();
        assertThat(schemas.has("EvaluateRuleResponse")).isTrue();
        assertThat(schemas.has("RulePackRequest")).isTrue();
        assertThat(schemas.has("RegisterPluginRequest")).isTrue();
        assertThat(schemas.has("RegisterPluginResponse")).isTrue();
        assertThat(schemas.has("InvokePluginRequest")).isTrue();
        assertThat(schemas.has("InvokePluginResponse")).isTrue();
        assertThat(schemas.has("TokenRequest")).isTrue();
        assertThat(schemas.has("TokenResponse")).isTrue();
        assertThat(schemas.has("HealthResponse")).isTrue();
    }

    private JsonNode readSpec() throws IOException { 
        try (var inputStream = Files.newInputStream(SPEC_PATH)) { 
            return objectMapper.readTree(inputStream); 
        }
    }
}
