package com.ghatana.yappc.agents.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link YamlAgentConfig}.
 *
 * Validates that the YAML configuration system correctly:
 * - Parses agent definitions
 * - Validates configuration structure
 * - Supports all generator types
 * - Handles metadata and caching
 *
 * @doc.pattern Test
 * @doc.purpose Unit tests for YAML agent configuration
 * @doc.layer test
 */
class YamlAgentConfigTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should build basic agent configuration")
    void shouldBuildBasicConfig() { // GH-90000
        // Given
        YamlAgentConfig config = YamlAgentConfig.builder() // GH-90000
            .id("expert.java")
            .name("Java Expert")
            .description("Expert Java engineer")
            .version("1.0.0")
            .tags(Set.of("java", "code-review")) // GH-90000
            .capabilities(Set.of("analysis", "review")) // GH-90000
            .build(); // GH-90000

        // Then
        assertThat(config.getId()).isEqualTo("expert.java");
        assertThat(config.getName()).isEqualTo("Java Expert");
        assertThat(config.getDescription()).isEqualTo("Expert Java engineer");
        assertThat(config.getVersion()).isEqualTo("1.0.0");
        assertThat(config.getTags()).containsExactlyInAnyOrder("java", "code-review"); // GH-90000
        assertThat(config.getCapabilities()).containsExactlyInAnyOrder("analysis", "review"); // GH-90000
    }

    @Test
    @DisplayName("Should support LLM generator configuration")
    void shouldSupportLlmGenerator() { // GH-90000
        // Given
        YamlAgentConfig.GeneratorConfig genConfig = new YamlAgentConfig.GeneratorConfig( // GH-90000
            "llm",
            "prompts/java-expert.txt",
            "gpt-4",
            0.7,
            2000,
            3,
            Map.of("system_message", "You are a Java expert") // GH-90000
        );

        YamlAgentConfig config = YamlAgentConfig.builder() // GH-90000
            .id("expert.java")
            .name("Java Expert")
            .generator(genConfig) // GH-90000
            .build(); // GH-90000

        // Then
        assertThat(config.getGenerator()).isNotNull(); // GH-90000
        assertThat(config.getGenerator().getType()).isEqualTo("llm");
        assertThat(config.getGenerator().getModel()).isEqualTo("gpt-4");
        assertThat(config.getGenerator().getTemperature()).isEqualTo(0.7); // GH-90000
        assertThat(config.getGenerator().getMaxTokens()).isEqualTo(2000); // GH-90000
        assertThat(config.getGenerator().getMaxRetries()).isEqualTo(3); // GH-90000
        assertThat(config.getGenerator().getProperty("system_message", "")) // GH-90000
            .isEqualTo("You are a Java expert");
    }

    @Test
    @DisplayName("Should support rule-based generator configuration")
    void shouldSupportRuleBasedGenerator() { // GH-90000
        // Given
        YamlAgentConfig.GeneratorConfig genConfig = new YamlAgentConfig.GeneratorConfig( // GH-90000
            "rule_based",
            null,
            null,
            0.0,
            0,
            0,
            Map.of("rules_file", "rules/compliance.yaml") // GH-90000
        );

        YamlAgentConfig config = YamlAgentConfig.builder() // GH-90000
            .id("compliance.check")
            .name("Compliance Check")
            .generator(genConfig) // GH-90000
            .build(); // GH-90000

        // Then
        assertThat(config.getGenerator().getType()).isEqualTo("rule_based");
        assertThat(config.getGenerator().getProperty("rules_file", "")) // GH-90000
            .isEqualTo("rules/compliance.yaml");
    }

    @Test
    @DisplayName("Should support validation configuration")
    void shouldSupportValidationConfig() { // GH-90000
        // Given
        YamlAgentConfig.ValidationConfig valConfig = new YamlAgentConfig.ValidationConfig( // GH-90000
            "schemas/input.json",
            "schemas/output.json",
            true
        );

        YamlAgentConfig config = YamlAgentConfig.builder() // GH-90000
            .id("test.agent")
            .validation(valConfig) // GH-90000
            .build(); // GH-90000

        // Then
        assertThat(config.getValidation()).isNotNull(); // GH-90000
        assertThat(config.getValidation().getInputSchema()).isEqualTo("schemas/input.json");
        assertThat(config.getValidation().getOutputSchema()).isEqualTo("schemas/output.json");
        assertThat(config.getValidation().isStrict()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should support cache configuration")
    void shouldSupportCacheConfig() { // GH-90000
        // Given
        YamlAgentConfig.CacheConfig cacheConfig = new YamlAgentConfig.CacheConfig( // GH-90000
            true,
            3600,
            Set.of("codeContext", "question") // GH-90000
        );

        YamlAgentConfig config = YamlAgentConfig.builder() // GH-90000
            .id("test.agent")
            .cache(cacheConfig) // GH-90000
            .build(); // GH-90000

        // Then
        assertThat(config.getCache()).isNotNull(); // GH-90000
        assertThat(config.getCache().isEnabled()).isTrue(); // GH-90000
        assertThat(config.getCache().getTtlSeconds()).isEqualTo(3600); // GH-90000
        assertThat(config.getCache().getKeyFields()).containsExactlyInAnyOrder("codeContext", "question"); // GH-90000
    }

    @Test
    @DisplayName("Should support metadata storage")
    void shouldSupportMetadata() { // GH-90000
        // Given
        Map<String, Object> metadata = Map.of( // GH-90000
            "cost_per_request", 0.03,
            "average_tokens", 1500,
            "category", "code-analysis"
        );

        YamlAgentConfig config = YamlAgentConfig.builder() // GH-90000
            .id("test.agent")
            .metadata(metadata) // GH-90000
            .build(); // GH-90000

        // Then
        assertThat(config.getMetadata()).containsAllEntriesOf(metadata); // GH-90000
        assertThat(config.getMetadata("cost_per_request", 0.0)).isEqualTo(0.03); // GH-90000
        assertThat(config.getMetadata("average_tokens", 0)).isEqualTo(1500); // GH-90000
        assertThat(config.getMetadata("category", "")).isEqualTo("code-analysis");
    }

    @Test
    @DisplayName("Should check for tag presence")
    void shouldCheckTagPresence() { // GH-90000
        // Given
        YamlAgentConfig config = YamlAgentConfig.builder() // GH-90000
            .id("test.agent")
            .tags(Set.of("java", "code-review", "backend")) // GH-90000
            .build(); // GH-90000

        // Then
        assertThat(config.hasTag("java")).isTrue();
        assertThat(config.hasTag("code-review")).isTrue();
        assertThat(config.hasTag("frontend")).isFalse();
    }

    @Test
    @DisplayName("Should check for capability presence")
    void shouldCheckCapabilityPresence() { // GH-90000
        // Given
        YamlAgentConfig config = YamlAgentConfig.builder() // GH-90000
            .id("test.agent")
            .capabilities(Set.of("analysis", "generation", "review")) // GH-90000
            .build(); // GH-90000

        // Then
        assertThat(config.hasCapability("analysis")).isTrue();
        assertThat(config.hasCapability("generation")).isTrue();
        assertThat(config.hasCapability("deployment")).isFalse();
    }

    @Test
    @DisplayName("Should handle default values correctly")
    void shouldHandleDefaults() { // GH-90000
        // Given - minimal config
        YamlAgentConfig config = YamlAgentConfig.builder() // GH-90000
            .id("minimal.agent")
            .build(); // GH-90000

        // Then
        assertThat(config.getVersion()).isEqualTo("1.0.0");  // Default
        assertThat(config.getTags()).isEmpty(); // GH-90000
        assertThat(config.getCapabilities()).isEmpty(); // GH-90000
        assertThat(config.getGenerator()).isNull(); // GH-90000
        assertThat(config.getValidation()).isNull(); // GH-90000
        assertThat(config.getCache()).isNull(); // GH-90000
        assertThat(config.getMetadata()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should create immutable copies of collections")
    void shouldCreateImmutableCollections() { // GH-90000
        // Given
        Set<String> tags = new java.util.HashSet<>(); // GH-90000
        tags.add("tag1");

        YamlAgentConfig config = YamlAgentConfig.builder() // GH-90000
            .id("test.agent")
            .tags(tags) // GH-90000
            .build(); // GH-90000

        // When - modify original
        tags.add("tag2");

        // Then - config should not be affected
        assertThat(config.getTags()).hasSize(1).contains("tag1");
    }
}
