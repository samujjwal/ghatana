package com.ghatana.yappc.services.lifecycle.config;

import io.activej.test.ExpectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ConfigurationValidator Tests")
class ConfigurationValidatorTest {

    private ConfigurationValidator validator;
    private File schemasDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        schemasDir = tempDir.toFile();
        validator = new ConfigurationValidator(schemasDir);

        // Create minimal test schemas
        createMinimalPoliciesSchema();
        createMinimalAgentSchema();
    }

    private void createMinimalPoliciesSchema() throws IOException {
        String schema = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "required": ["apiVersion", "policies"],
              "properties": {
                "apiVersion": { "type": "string" },
                "policies": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "required": ["id", "version"],
                    "properties": {
                      "id": { "type": "string" },
                      "version": { "type": "string" }
                    }
                  }
                }
              }
            }
            """;
        Files.write(
            new File(schemasDir, "policies-schema.json").toPath(),
            schema.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void createMinimalAgentSchema() throws IOException {
        String schema = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "required": ["id", "name", "version"],
              "properties": {
                "id": { "type": "string" },
                "name": { "type": "string" },
                "version": { "type": "string" }
              }
            }
            """;
        Files.write(
            new File(schemasDir, "agent-schema.json").toPath(),
            schema.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Test
    @DisplayName("Should validate valid policies YAML")
    void shouldValidateValidPoliciesYaml(@TempDir Path tempDir) throws IOException {
        String validYaml = """
            apiVersion: v1.0
            policies:
              - id: test_policy
                version: "1.0"
            """;

        File yamlFile = tempDir.resolve("valid-policies.yaml").toFile();
        Files.write(yamlFile.toPath(), validYaml.getBytes(StandardCharsets.UTF_8));

        validator = new ConfigurationValidator(schemasDir);
        ConfigurationValidator.ValidationResult result = validator.validatePoliciesFile(yamlFile);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("Should reject policies YAML with missing required fields")
    void shouldRejectPoliciesWithMissingFields(@TempDir Path tempDir) throws IOException {
        String invalidYaml = """
            policies:
              - version: "1.0"
            """;

        File yamlFile = tempDir.resolve("invalid-policies.yaml").toFile();
        Files.write(yamlFile.toPath(), invalidYaml.getBytes(StandardCharsets.UTF_8));

        validator = new ConfigurationValidator(schemasDir);
        ConfigurationValidator.ValidationResult result = validator.validatePoliciesFile(yamlFile);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    @DisplayName("Should validate valid agent YAML")
    void shouldValidateValidAgentYaml(@TempDir Path tempDir) throws IOException {
        String validYaml = """
            id: test-agent
            name: Test Agent
            version: "1.0"
            """;

        File yamlFile = tempDir.resolve("valid-agent.yaml").toFile();
        Files.write(yamlFile.toPath(), validYaml.getBytes(StandardCharsets.UTF_8));

        validator = new ConfigurationValidator(schemasDir);
        ConfigurationValidator.ValidationResult result = validator.validateAgentFile(yamlFile);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("Should reject agent YAML with missing required fields")
    void shouldRejectAgentWithMissingFields(@TempDir Path tempDir) throws IOException {
        String invalidYaml = """
            name: Missing ID
            version: "1.0"
            """;

        File yamlFile = tempDir.resolve("invalid-agent.yaml").toFile();
        Files.write(yamlFile.toPath(), invalidYaml.getBytes(StandardCharsets.UTF_8));

        validator = new ConfigurationValidator(schemasDir);
        ConfigurationValidator.ValidationResult result = validator.validateAgentFile(yamlFile);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle File not found gracefully")
    void shouldHandleNotFoundFile() throws IOException {
        File nonExistentFile = new File("/nonexistent/file.yaml");
        validator = new ConfigurationValidator(schemasDir);

        ConfigurationValidator.ValidationResult result = validator.validatePoliciesFile(nonExistentFile);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0)).contains("not found");
    }

    @Test
    @DisplayName("Should handle malformed YAML gracefully")
    void shouldHandleMalformedYaml(@TempDir Path tempDir) throws IOException {
        String malformedYaml = """
            invalid: [yaml
              unterminated: list
            """;

        File yamlFile = tempDir.resolve("malformed.yaml").toFile();
        Files.write(yamlFile.toPath(), malformedYaml.getBytes(StandardCharsets.UTF_8));

        validator = new ConfigurationValidator(schemasDir);
        ConfigurationValidator.ValidationResult result = validator.validatePoliciesFile(yamlFile);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    @DisplayName("Should provide error summary")
    void shouldProvideErrorSummary(@TempDir Path tempDir) throws IOException {
        String invalidYaml = """
            policies: []
            """;

        File yamlFile = tempDir.resolve("test.yaml").toFile();
        Files.write(yamlFile.toPath(), invalidYaml.getBytes(StandardCharsets.UTF_8));

        validator = new ConfigurationValidator(schemasDir);
        ConfigurationValidator.ValidationResult result = validator.validatePoliciesFile(yamlFile);

        String summary = result.getErrorsSummary();
        assertThat(summary).isNotEmpty();
        assertThat(summary).doesNotContain("null");
    }

    @Test
    @DisplayName("Should return warning when schema not loaded")
    void shouldReturnWarningWhenSchemaNotLoaded(@TempDir Path tempDir) throws IOException {
        File emptySchemaDir = tempDir.resolve("empty").toFile();
        emptySchemaDir.mkdirs();

        validator = new ConfigurationValidator(emptySchemaDir);
        String validYaml = "test: value";
        File yamlFile = tempDir.resolve("test.yaml").toFile();
        Files.write(yamlFile.toPath(), validYaml.getBytes(StandardCharsets.UTF_8));

        ConfigurationValidator.ValidationResult result = validator.validatePoliciesFile(yamlFile);

        assertThat(result.isValid()).isTrue();
        assertThat(result.isWarning()).isTrue();
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    @DisplayName("Success result should have no errors")
    void successResultShouldHaveNoErrors() {
        ConfigurationValidator.ValidationResult result = ConfigurationValidator.ValidationResult.success();

        assertThat(result.isValid()).isTrue();
        assertThat(result.isWarning()).isFalse();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("Invalid result should contain errors")
    void invalidResultShouldContainErrors() {
        List<String> errors = List.of("Error 1", "Error 2");
        ConfigurationValidator.ValidationResult result = ConfigurationValidator.ValidationResult.invalid(errors);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).containsExactlyElementsOf(errors);
    }
}
