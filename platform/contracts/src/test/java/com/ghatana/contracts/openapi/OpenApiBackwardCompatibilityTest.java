package com.ghatana.contracts.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backward-compatibility guard for platform OpenAPI contracts.
 *
 * @doc.type class
 * @doc.purpose Enforce non-breaking OpenAPI operation compatibility for shared consumers
 * @doc.layer platform
 * @doc.pattern Test, Contract
 */
@DisplayName("OpenAPI Backward Compatibility")
class OpenApiBackwardCompatibilityTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    // Baseline contract surface that must remain compatible for current consumers.
    private static final Map<String, Map<String, Set<String>>> REQUIRED_OPERATIONS = Map.of(
            "auth-gateway.yaml", Map.of(
                    "/auth/login", Set.of("post"),
                    "/auth/validate", Set.of("get"),
                    "/auth/refresh", Set.of("post"),
                    "/auth/tenant", Set.of("get")
            ),
            "ai-registry-service.yaml", Map.of(
                    "/api/v1/models", Set.of("get"),
                    "/api/v1/models/{id}", Set.of("get")
            )
    );

    @Test
    @DisplayName("required baseline operations must remain present")
    void requiredOperationsMustRemainPresent() throws IOException {
        for (Map.Entry<String, Map<String, Set<String>>> specEntry : REQUIRED_OPERATIONS.entrySet()) {
            JsonNode spec = loadSpec(specEntry.getKey());
            JsonNode paths = spec.path("paths");

            assertThat(paths.isMissingNode())
                    .as("%s must define paths", specEntry.getKey())
                    .isFalse();

            for (Map.Entry<String, Set<String>> opEntry : specEntry.getValue().entrySet()) {
                JsonNode pathNode = paths.path(opEntry.getKey());
                assertThat(pathNode.isMissingNode())
                        .as("%s must keep path %s", specEntry.getKey(), opEntry.getKey())
                        .isFalse();

                for (String method : opEntry.getValue()) {
                    JsonNode methodNode = pathNode.path(method);
                    assertThat(methodNode.isMissingNode())
                            .as("%s must keep %s %s", specEntry.getKey(), method.toUpperCase(), opEntry.getKey())
                            .isFalse();
                }
            }
        }
    }

    @Test
    @DisplayName("OpenAPI specs must remain on v3 major")
    void specsMustRemainOpenApi3() throws IOException {
        for (String specName : REQUIRED_OPERATIONS.keySet()) {
            JsonNode spec = loadSpec(specName);
            assertThat(spec.path("openapi").asText())
                    .as("%s must remain OpenAPI v3.x", specName)
                    .startsWith("3.");
        }
    }

    private JsonNode loadSpec(String fileName) throws IOException {
        Path specPath = Path.of("openapi", fileName);
        assertThat(Files.exists(specPath))
                .as("OpenAPI file missing: %s", specPath)
                .isTrue();
        try (var reader = Files.newBufferedReader(specPath)) {
            return YAML.readTree(reader);
        }
    }
}
