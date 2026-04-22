package com.ghatana.yappc.services.lifecycle.config;

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

@DisplayName("ConfigurationValidator Tests [GH-90000]")
class ConfigurationValidatorTest {

    private ConfigurationValidator validator;
    private File schemasDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException { // GH-90000
        schemasDir = tempDir.toFile(); // GH-90000
        validator = new ConfigurationValidator(schemasDir); // GH-90000

        // Create minimal test schemas
        createMinimalPoliciesSchema(); // GH-90000
        createMinimalAgentSchema(); // GH-90000
    }

    private void createMinimalPoliciesSchema() throws IOException { // GH-90000
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
        Files.write( // GH-90000
            new File(schemasDir, "policies-schema.json").toPath(), // GH-90000
            schema.getBytes(StandardCharsets.UTF_8) // GH-90000
        );
    }

    private void createMinimalAgentSchema() throws IOException { // GH-90000
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
        Files.write( // GH-90000
            new File(schemasDir, "agent-schema.json").toPath(), // GH-90000
            schema.getBytes(StandardCharsets.UTF_8) // GH-90000
        );
    }

    @Test
    @DisplayName("Should validate valid policies YAML [GH-90000]")
    void shouldValidateValidPoliciesYaml(@TempDir Path tempDir) throws IOException { // GH-90000
        String validYaml = """
            apiVersion: v1.0
            policies:
              - id: test_policy
                version: "1.0"
            """;

        File yamlFile = tempDir.resolve("valid-policies.yaml [GH-90000]").toFile();
        Files.write(yamlFile.toPath(), validYaml.getBytes(StandardCharsets.UTF_8)); // GH-90000

        validator = new ConfigurationValidator(schemasDir); // GH-90000
        ConfigurationValidator.ValidationResult result = validator.validatePoliciesFile(yamlFile); // GH-90000

        assertThat(result.isValid()).isTrue(); // GH-90000
        assertThat(result.getErrors()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should reject policies YAML with missing required fields [GH-90000]")
    void shouldRejectPoliciesWithMissingFields(@TempDir Path tempDir) throws IOException { // GH-90000
        String invalidYaml = """
            policies:
              - version: "1.0"
            """;

        File yamlFile = tempDir.resolve("invalid-policies.yaml [GH-90000]").toFile();
        Files.write(yamlFile.toPath(), invalidYaml.getBytes(StandardCharsets.UTF_8)); // GH-90000

        validator = new ConfigurationValidator(schemasDir); // GH-90000
        ConfigurationValidator.ValidationResult result = validator.validatePoliciesFile(yamlFile); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.getErrors()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should validate valid agent YAML [GH-90000]")
    void shouldValidateValidAgentYaml(@TempDir Path tempDir) throws IOException { // GH-90000
        String validYaml = """
            id: test-agent
            name: Test Agent
            version: "1.0"
            """;

        File yamlFile = tempDir.resolve("valid-agent.yaml [GH-90000]").toFile();
        Files.write(yamlFile.toPath(), validYaml.getBytes(StandardCharsets.UTF_8)); // GH-90000

        validator = new ConfigurationValidator(schemasDir); // GH-90000
        ConfigurationValidator.ValidationResult result = validator.validateAgentFile(yamlFile); // GH-90000

        assertThat(result.isValid()).isTrue(); // GH-90000
        assertThat(result.getErrors()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should reject agent YAML with missing required fields [GH-90000]")
    void shouldRejectAgentWithMissingFields(@TempDir Path tempDir) throws IOException { // GH-90000
        String invalidYaml = """
            name: Missing ID
            version: "1.0"
            """;

        File yamlFile = tempDir.resolve("invalid-agent.yaml [GH-90000]").toFile();
        Files.write(yamlFile.toPath(), invalidYaml.getBytes(StandardCharsets.UTF_8)); // GH-90000

        validator = new ConfigurationValidator(schemasDir); // GH-90000
        ConfigurationValidator.ValidationResult result = validator.validateAgentFile(yamlFile); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.getErrors()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should handle File not found gracefully [GH-90000]")
    void shouldHandleNotFoundFile() throws IOException { // GH-90000
        File nonExistentFile = new File("/nonexistent/file.yaml [GH-90000]");
        validator = new ConfigurationValidator(schemasDir); // GH-90000

        ConfigurationValidator.ValidationResult result = validator.validatePoliciesFile(nonExistentFile); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.getErrors()).isNotEmpty(); // GH-90000
        assertThat(result.getErrors().get(0)).contains("not found [GH-90000]");
    }

    @Test
    @DisplayName("Should handle malformed YAML gracefully [GH-90000]")
    void shouldHandleMalformedYaml(@TempDir Path tempDir) throws IOException { // GH-90000
        String malformedYaml = """
            invalid: [yaml
              unterminated: list
            """;

        File yamlFile = tempDir.resolve("malformed.yaml [GH-90000]").toFile();
        Files.write(yamlFile.toPath(), malformedYaml.getBytes(StandardCharsets.UTF_8)); // GH-90000

        validator = new ConfigurationValidator(schemasDir); // GH-90000
        ConfigurationValidator.ValidationResult result = validator.validatePoliciesFile(yamlFile); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.getErrors()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should provide error summary [GH-90000]")
    void shouldProvideErrorSummary(@TempDir Path tempDir) throws IOException { // GH-90000
        String invalidYaml = """
            policies: []
            """;

        File yamlFile = tempDir.resolve("test.yaml [GH-90000]").toFile();
        Files.write(yamlFile.toPath(), invalidYaml.getBytes(StandardCharsets.UTF_8)); // GH-90000

        validator = new ConfigurationValidator(schemasDir); // GH-90000
        ConfigurationValidator.ValidationResult result = validator.validatePoliciesFile(yamlFile); // GH-90000

        String summary = result.getErrorsSummary(); // GH-90000
        assertThat(summary).isNotEmpty(); // GH-90000
        assertThat(summary).doesNotContain("null [GH-90000]");
    }

    @Test
    @DisplayName("Should return warning when schema not loaded [GH-90000]")
    void shouldReturnWarningWhenSchemaNotLoaded(@TempDir Path tempDir) throws IOException { // GH-90000
        File emptySchemaDir = tempDir.resolve("empty [GH-90000]").toFile();
        emptySchemaDir.mkdirs(); // GH-90000

        validator = new ConfigurationValidator(emptySchemaDir); // GH-90000
        String validYaml = "test: value";
        File yamlFile = tempDir.resolve("test.yaml [GH-90000]").toFile();
        Files.write(yamlFile.toPath(), validYaml.getBytes(StandardCharsets.UTF_8)); // GH-90000

        ConfigurationValidator.ValidationResult result = validator.validatePoliciesFile(yamlFile); // GH-90000

        assertThat(result.isValid()).isTrue(); // GH-90000
        assertThat(result.isWarning()).isTrue(); // GH-90000
        assertThat(result.getErrors()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Success result should have no errors [GH-90000]")
    void successResultShouldHaveNoErrors() { // GH-90000
        ConfigurationValidator.ValidationResult result = ConfigurationValidator.ValidationResult.success(); // GH-90000

        assertThat(result.isValid()).isTrue(); // GH-90000
        assertThat(result.isWarning()).isFalse(); // GH-90000
        assertThat(result.getErrors()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Invalid result should contain errors [GH-90000]")
    void invalidResultShouldContainErrors() { // GH-90000
        List<String> errors = List.of("Error 1", "Error 2"); // GH-90000
        ConfigurationValidator.ValidationResult result = ConfigurationValidator.ValidationResult.invalid(errors); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.getErrors()).containsExactlyElementsOf(errors); // GH-90000
    }
}
