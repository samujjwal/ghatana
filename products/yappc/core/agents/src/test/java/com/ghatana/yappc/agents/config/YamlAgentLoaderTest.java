package com.ghatana.yappc.agents.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for {@link YamlAgentLoader}.
 *
 * Validates that the YAML loader correctly:
 * - Parses YAML agent definitions
 * - Handles various generator types
 * - Validates required fields
 * - Supports all configuration options
 *
 * @doc.pattern Test
 * @doc.purpose Unit tests for YAML agent loader
 * @doc.layer test
 */
class YamlAgentLoaderTest {

    @TempDir
    Path tempDir;

    private YamlAgentLoader loader;

    @BeforeEach
    void setUp() { // GH-90000
        loader = new YamlAgentLoader(); // GH-90000
    }

    @Test
    @DisplayName("Should load YAML from string [GH-90000]")
    void shouldLoadFromString() throws IOException { // GH-90000
        // Given
        String yaml = """
            agent:
              id: expert.java
              name: "Java Expert"
              description: "Expert Java engineer"
              version: "1.0.0"
              tags: [java, code-review]
              capabilities: [analysis, review]
              generator:
                type: llm
                prompt_template: prompts/java.txt
                model: gpt-4
                temperature: 0.7
                max_tokens: 2000
                max_retries: 3
            """;

        // When
        YamlAgentConfig config = loader.loadFromString(yaml); // GH-90000

        // Then
        assertThat(config.getId()).isEqualTo("expert.java [GH-90000]");
        assertThat(config.getName()).isEqualTo("Java Expert [GH-90000]");
        assertThat(config.getDescription()).isEqualTo("Expert Java engineer [GH-90000]");
        assertThat(config.getTags()).contains("java", "code-review"); // GH-90000
        assertThat(config.getCapabilities()).contains("analysis", "review"); // GH-90000

        assertThat(config.getGenerator()).isNotNull(); // GH-90000
        assertThat(config.getGenerator().getType()).isEqualTo("llm [GH-90000]");
        assertThat(config.getGenerator().getModel()).isEqualTo("gpt-4 [GH-90000]");
        assertThat(config.getGenerator().getTemperature()).isEqualTo(0.7); // GH-90000
    }

    @Test
    @DisplayName("Should load YAML from file [GH-90000]")
    void shouldLoadFromFile() throws IOException { // GH-90000
        // Given
        Path yamlFile = tempDir.resolve("test-agent.yaml [GH-90000]");
        Files.writeString(yamlFile, """
            agent:
              id: test.agent
              name: "Test Agent"
              generator:
                type: rule_based
            """);

        // When
        YamlAgentConfig config = loader.loadFromFile(yamlFile); // GH-90000

        // Then
        assertThat(config.getId()).isEqualTo("test.agent [GH-90000]");
        assertThat(config.getName()).isEqualTo("Test Agent [GH-90000]");
        assertThat(config.getGenerator().getType()).isEqualTo("rule_based [GH-90000]");
    }

    @Test
    @DisplayName("Should load all agents from directory [GH-90000]")
    void shouldLoadFromDirectory() throws IOException { // GH-90000
        // Given
        Path agentsDir = tempDir.resolve("agents [GH-90000]");
        Files.createDirectories(agentsDir); // GH-90000

        Files.writeString(agentsDir.resolve("agent1.yaml [GH-90000]"), """
            agent:
              id: agent.one
              name: "Agent One"
              generator:
                type: llm
            """);

        Files.writeString(agentsDir.resolve("agent2.yaml [GH-90000]"), """
            agent:
              id: agent.two
              name: "Agent Two"
              generator:
                type: template
            """);

        // When
        List<YamlAgentConfig> configs = loader.loadFromDirectory(agentsDir); // GH-90000

        // Then
        assertThat(configs).hasSize(2); // GH-90000
        assertThat(configs).extracting(YamlAgentConfig::getId) // GH-90000
            .containsExactlyInAnyOrder("agent.one", "agent.two"); // GH-90000
    }

    @Test
    @DisplayName("Should parse validation configuration [GH-90000]")
    void shouldParseValidationConfig() throws IOException { // GH-90000
        // Given
        String yaml = """
            agent:
              id: validated.agent
              validation:
                input_schema: schemas/input.json
                output_schema: schemas/output.json
                strict: true
            """;

        // When
        YamlAgentConfig config = loader.loadFromString(yaml); // GH-90000

        // Then
        assertThat(config.getValidation()).isNotNull(); // GH-90000
        assertThat(config.getValidation().getInputSchema()).isEqualTo("schemas/input.json [GH-90000]");
        assertThat(config.getValidation().getOutputSchema()).isEqualTo("schemas/output.json [GH-90000]");
        assertThat(config.getValidation().isStrict()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should parse cache configuration [GH-90000]")
    void shouldParseCacheConfig() throws IOException { // GH-90000
        // Given
        String yaml = """
            agent:
              id: cached.agent
              cache:
                enabled: true
                ttl: 7200
                key_fields: [field1, field2]
            """;

        // When
        YamlAgentConfig config = loader.loadFromString(yaml); // GH-90000

        // Then
        assertThat(config.getCache()).isNotNull(); // GH-90000
        assertThat(config.getCache().isEnabled()).isTrue(); // GH-90000
        assertThat(config.getCache().getTtlSeconds()).isEqualTo(7200); // GH-90000
        assertThat(config.getCache().getKeyFields()).contains("field1", "field2"); // GH-90000
    }

    @Test
    @DisplayName("Should parse generator properties [GH-90000]")
    void shouldParseGeneratorProperties() throws IOException { // GH-90000
        // Given
        String yaml = """
            agent:
              id: props.agent
              generator:
                type: custom
                model: gpt-4
                properties:
                  custom_field: custom_value
                  timeout: 30
                  retry_policy: exponential
            """;

        // When
        YamlAgentConfig config = loader.loadFromString(yaml); // GH-90000

        // Then
        assertThat(config.getGenerator().getProperty("custom_field", "")) // GH-90000
            .isEqualTo("custom_value [GH-90000]");
        assertThat(config.getGenerator().getProperty("timeout", 0)) // GH-90000
            .isEqualTo(30); // GH-90000
        assertThat(config.getGenerator().getProperty("retry_policy", "")) // GH-90000
            .isEqualTo("exponential [GH-90000]");
    }

    @Test
    @DisplayName("Should parse metadata [GH-90000]")
    void shouldParseMetadata() throws IOException { // GH-90000
        // Given
        String yaml = """
            agent:
              id: meta.agent
              metadata:
                cost_per_request: 0.03
                category: code-analysis
                team: backend
            """;

        // When
        YamlAgentConfig config = loader.loadFromString(yaml); // GH-90000

        // Then
        assertThat(config.getMetadata()).containsEntry("cost_per_request", 0.03); // GH-90000
        assertThat(config.getMetadata()).containsEntry("category", "code-analysis"); // GH-90000
        assertThat(config.getMetadata()).containsEntry("team", "backend"); // GH-90000
    }

    @Test
    @DisplayName("Should handle empty directory gracefully [GH-90000]")
    void shouldHandleEmptyDirectory() { // GH-90000
        // Given
        Path emptyDir = tempDir.resolve("empty [GH-90000]");

        // When
        List<YamlAgentConfig> configs = loader.loadFromDirectory(emptyDir); // GH-90000

        // Then
        assertThat(configs).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should skip non-YAML files [GH-90000]")
    void shouldSkipNonYamlFiles() throws IOException { // GH-90000
        // Given
        Path agentsDir = tempDir.resolve("agents [GH-90000]");
        Files.createDirectories(agentsDir); // GH-90000

        Files.writeString(agentsDir.resolve("agent.yaml [GH-90000]"), """
            agent:
              id: valid.agent
            """);

        Files.writeString(agentsDir.resolve("readme.txt [GH-90000]"), "Not a YAML file");
        Files.writeString(agentsDir.resolve("config.json [GH-90000]"), "{\"not\": \"yaml\"}");

        // When
        List<YamlAgentConfig> configs = loader.loadFromDirectory(agentsDir); // GH-90000

        // Then
        assertThat(configs).hasSize(1); // GH-90000
        assertThat(configs.get(0).getId()).isEqualTo("valid.agent [GH-90000]");
    }

    @Test
    @DisplayName("Should use defaults for optional fields [GH-90000]")
    void shouldUseDefaults() throws IOException { // GH-90000
        // Given - minimal YAML
        String yaml = """
            agent:
              id: minimal.agent
            """;

        // When
        YamlAgentConfig config = loader.loadFromString(yaml); // GH-90000

        // Then
        assertThat(config.getVersion()).isEqualTo("1.0.0 [GH-90000]");
        assertThat(config.getTags()).isEmpty(); // GH-90000
        assertThat(config.getCapabilities()).isEmpty(); // GH-90000
        assertThat(config.getGenerator()).isNull(); // GH-90000
        assertThat(config.getValidation()).isNull(); // GH-90000
        assertThat(config.getCache()).isNull(); // GH-90000
    }

    @Test
    @DisplayName("Should handle complex nested structures [GH-90000]")
    void shouldHandleComplexStructures() throws IOException { // GH-90000
        // Given
        String yaml = """
            agent:
              id: complex.agent
              name: "Complex Agent"
              generator:
                type: composed
                properties:
                  steps:
                    - name: validation
                      type: rule_based
                    - name: generation
                      type: llm
                      model: gpt-4
              metadata:
                nested:
                  field1: value1
                  field2:
                    - item1
                    - item2
            """;

        // When
        YamlAgentConfig config = loader.loadFromString(yaml); // GH-90000

        // Then - should parse without error
        assertThat(config.getId()).isEqualTo("complex.agent [GH-90000]");
        assertThat(config.getGenerator().getType()).isEqualTo("composed [GH-90000]");
        assertThat(config.getMetadata()).containsKey("nested [GH-90000]");
    }
}
