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
    void setUp() {
        loader = new YamlAgentLoader();
    }
    
    @Test
    @DisplayName("Should load YAML from string")
    void shouldLoadFromString() throws IOException {
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
        YamlAgentConfig config = loader.loadFromString(yaml);
        
        // Then
        assertThat(config.getId()).isEqualTo("expert.java");
        assertThat(config.getName()).isEqualTo("Java Expert");
        assertThat(config.getDescription()).isEqualTo("Expert Java engineer");
        assertThat(config.getTags()).contains("java", "code-review");
        assertThat(config.getCapabilities()).contains("analysis", "review");
        
        assertThat(config.getGenerator()).isNotNull();
        assertThat(config.getGenerator().getType()).isEqualTo("llm");
        assertThat(config.getGenerator().getModel()).isEqualTo("gpt-4");
        assertThat(config.getGenerator().getTemperature()).isEqualTo(0.7);
    }
    
    @Test
    @DisplayName("Should load YAML from file")
    void shouldLoadFromFile() throws IOException {
        // Given
        Path yamlFile = tempDir.resolve("test-agent.yaml");
        Files.writeString(yamlFile, """
            agent:
              id: test.agent
              name: "Test Agent"
              generator:
                type: rule_based
            """);
        
        // When
        YamlAgentConfig config = loader.loadFromFile(yamlFile);
        
        // Then
        assertThat(config.getId()).isEqualTo("test.agent");
        assertThat(config.getName()).isEqualTo("Test Agent");
        assertThat(config.getGenerator().getType()).isEqualTo("rule_based");
    }
    
    @Test
    @DisplayName("Should load all agents from directory")
    void shouldLoadFromDirectory() throws IOException {
        // Given
        Path agentsDir = tempDir.resolve("agents");
        Files.createDirectories(agentsDir);
        
        Files.writeString(agentsDir.resolve("agent1.yaml"), """
            agent:
              id: agent.one
              name: "Agent One"
              generator:
                type: llm
            """);
        
        Files.writeString(agentsDir.resolve("agent2.yaml"), """
            agent:
              id: agent.two
              name: "Agent Two"
              generator:
                type: template
            """);
        
        // When
        List<YamlAgentConfig> configs = loader.loadFromDirectory(agentsDir);
        
        // Then
        assertThat(configs).hasSize(2);
        assertThat(configs).extracting(YamlAgentConfig::getId)
            .containsExactlyInAnyOrder("agent.one", "agent.two");
    }
    
    @Test
    @DisplayName("Should parse validation configuration")
    void shouldParseValidationConfig() throws IOException {
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
        YamlAgentConfig config = loader.loadFromString(yaml);
        
        // Then
        assertThat(config.getValidation()).isNotNull();
        assertThat(config.getValidation().getInputSchema()).isEqualTo("schemas/input.json");
        assertThat(config.getValidation().getOutputSchema()).isEqualTo("schemas/output.json");
        assertThat(config.getValidation().isStrict()).isTrue();
    }
    
    @Test
    @DisplayName("Should parse cache configuration")
    void shouldParseCacheConfig() throws IOException {
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
        YamlAgentConfig config = loader.loadFromString(yaml);
        
        // Then
        assertThat(config.getCache()).isNotNull();
        assertThat(config.getCache().isEnabled()).isTrue();
        assertThat(config.getCache().getTtlSeconds()).isEqualTo(7200);
        assertThat(config.getCache().getKeyFields()).contains("field1", "field2");
    }
    
    @Test
    @DisplayName("Should parse generator properties")
    void shouldParseGeneratorProperties() throws IOException {
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
        YamlAgentConfig config = loader.loadFromString(yaml);
        
        // Then
        assertThat(config.getGenerator().getProperty("custom_field", ""))
            .isEqualTo("custom_value");
        assertThat(config.getGenerator().getProperty("timeout", 0))
            .isEqualTo(30);
        assertThat(config.getGenerator().getProperty("retry_policy", ""))
            .isEqualTo("exponential");
    }
    
    @Test
    @DisplayName("Should parse metadata")
    void shouldParseMetadata() throws IOException {
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
        YamlAgentConfig config = loader.loadFromString(yaml);
        
        // Then
        assertThat(config.getMetadata()).containsEntry("cost_per_request", 0.03);
        assertThat(config.getMetadata()).containsEntry("category", "code-analysis");
        assertThat(config.getMetadata()).containsEntry("team", "backend");
    }
    
    @Test
    @DisplayName("Should handle empty directory gracefully")
    void shouldHandleEmptyDirectory() {
        // Given
        Path emptyDir = tempDir.resolve("empty");
        
        // When
        List<YamlAgentConfig> configs = loader.loadFromDirectory(emptyDir);
        
        // Then
        assertThat(configs).isEmpty();
    }
    
    @Test
    @DisplayName("Should skip non-YAML files")
    void shouldSkipNonYamlFiles() throws IOException {
        // Given
        Path agentsDir = tempDir.resolve("agents");
        Files.createDirectories(agentsDir);
        
        Files.writeString(agentsDir.resolve("agent.yaml"), """
            agent:
              id: valid.agent
            """);
        
        Files.writeString(agentsDir.resolve("readme.txt"), "Not a YAML file");
        Files.writeString(agentsDir.resolve("config.json"), "{\"not\": \"yaml\"}");
        
        // When
        List<YamlAgentConfig> configs = loader.loadFromDirectory(agentsDir);
        
        // Then
        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).getId()).isEqualTo("valid.agent");
    }
    
    @Test
    @DisplayName("Should use defaults for optional fields")
    void shouldUseDefaults() throws IOException {
        // Given - minimal YAML
        String yaml = """
            agent:
              id: minimal.agent
            """;
        
        // When
        YamlAgentConfig config = loader.loadFromString(yaml);
        
        // Then
        assertThat(config.getVersion()).isEqualTo("1.0.0");
        assertThat(config.getTags()).isEmpty();
        assertThat(config.getCapabilities()).isEmpty();
        assertThat(config.getGenerator()).isNull();
        assertThat(config.getValidation()).isNull();
        assertThat(config.getCache()).isNull();
    }
    
    @Test
    @DisplayName("Should handle complex nested structures")
    void shouldHandleComplexStructures() throws IOException {
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
        YamlAgentConfig config = loader.loadFromString(yaml);
        
        // Then - should parse without error
        assertThat(config.getId()).isEqualTo("complex.agent");
        assertThat(config.getGenerator().getType()).isEqualTo("composed");
        assertThat(config.getMetadata()).containsKey("nested");
    }
}
