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
    void shouldBuildBasicConfig() {
        // Given
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("expert.java")
            .name("Java Expert")
            .description("Expert Java engineer")
            .version("1.0.0")
            .tags(Set.of("java", "code-review"))
            .capabilities(Set.of("analysis", "review"))
            .build();

        // Then
        assertThat(config.getId()).isEqualTo("expert.java");
        assertThat(config.getName()).isEqualTo("Java Expert");
        assertThat(config.getDescription()).isEqualTo("Expert Java engineer");
        assertThat(config.getVersion()).isEqualTo("1.0.0");
        assertThat(config.getTags()).containsExactlyInAnyOrder("java", "code-review");
        assertThat(config.getCapabilities()).containsExactlyInAnyOrder("analysis", "review");
    }

    @Test
    @DisplayName("Should support LLM generator configuration")
    void shouldSupportLlmGenerator() {
        // Given
        YamlAgentConfig.GeneratorConfig genConfig = new YamlAgentConfig.GeneratorConfig(
            "llm",
            "prompts/java-expert.txt",
            "gpt-4",
            0.7,
            2000,
            3,
            Map.of("system_message", "You are a Java expert")
        );

        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("expert.java")
            .name("Java Expert")
            .generator(genConfig)
            .build();

        // Then
        assertThat(config.getGenerator()).isNotNull();
        assertThat(config.getGenerator().getType()).isEqualTo("llm");
        assertThat(config.getGenerator().getModel()).isEqualTo("gpt-4");
        assertThat(config.getGenerator().getTemperature()).isEqualTo(0.7);
        assertThat(config.getGenerator().getMaxTokens()).isEqualTo(2000);
        assertThat(config.getGenerator().getMaxRetries()).isEqualTo(3);
        assertThat(config.getGenerator().getProperty("system_message", ""))
            .isEqualTo("You are a Java expert");
    }

    @Test
    @DisplayName("Should support rule-based generator configuration")
    void shouldSupportRuleBasedGenerator() {
        // Given
        YamlAgentConfig.GeneratorConfig genConfig = new YamlAgentConfig.GeneratorConfig(
            "rule_based",
            null,
            null,
            0.0,
            0,
            0,
            Map.of("rules_file", "rules/compliance.yaml")
        );

        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("compliance.check")
            .name("Compliance Check")
            .generator(genConfig)
            .build();

        // Then
        assertThat(config.getGenerator().getType()).isEqualTo("rule_based");
        assertThat(config.getGenerator().getProperty("rules_file", ""))
            .isEqualTo("rules/compliance.yaml");
    }

    @Test
    @DisplayName("Should support validation configuration")
    void shouldSupportValidationConfig() {
        // Given
        YamlAgentConfig.ValidationConfig valConfig = new YamlAgentConfig.ValidationConfig(
            "schemas/input.json",
            "schemas/output.json",
            true
        );

        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("test.agent")
            .validation(valConfig)
            .build();

        // Then
        assertThat(config.getValidation()).isNotNull();
        assertThat(config.getValidation().getInputSchema()).isEqualTo("schemas/input.json");
        assertThat(config.getValidation().getOutputSchema()).isEqualTo("schemas/output.json");
        assertThat(config.getValidation().isStrict()).isTrue();
    }

    @Test
    @DisplayName("Should support cache configuration")
    void shouldSupportCacheConfig() {
        // Given
        YamlAgentConfig.CacheConfig cacheConfig = new YamlAgentConfig.CacheConfig(
            true,
            3600,
            Set.of("codeContext", "question")
        );

        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("test.agent")
            .cache(cacheConfig)
            .build();

        // Then
        assertThat(config.getCache()).isNotNull();
        assertThat(config.getCache().isEnabled()).isTrue();
        assertThat(config.getCache().getTtlSeconds()).isEqualTo(3600);
        assertThat(config.getCache().getKeyFields()).containsExactlyInAnyOrder("codeContext", "question");
    }

    @Test
    @DisplayName("Should support metadata storage")
    void shouldSupportMetadata() {
        // Given
        Map<String, Object> metadata = Map.of(
            "cost_per_request", 0.03,
            "average_tokens", 1500,
            "category", "code-analysis"
        );

        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("test.agent")
            .metadata(metadata)
            .build();

        // Then
        assertThat(config.getMetadata()).containsAllEntriesOf(metadata);
        assertThat(config.getMetadata("cost_per_request", 0.0)).isEqualTo(0.03);
        assertThat(config.getMetadata("average_tokens", 0)).isEqualTo(1500);
        assertThat(config.getMetadata("category", "")).isEqualTo("code-analysis");
    }

    @Test
    @DisplayName("Should check for tag presence")
    void shouldCheckTagPresence() {
        // Given
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("test.agent")
            .tags(Set.of("java", "code-review", "backend"))
            .build();

        // Then
        assertThat(config.hasTag("java")).isTrue();
        assertThat(config.hasTag("code-review")).isTrue();
        assertThat(config.hasTag("frontend")).isFalse();
    }

    @Test
    @DisplayName("Should check for capability presence")
    void shouldCheckCapabilityPresence() {
        // Given
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("test.agent")
            .capabilities(Set.of("analysis", "generation", "review"))
            .build();

        // Then
        assertThat(config.hasCapability("analysis")).isTrue();
        assertThat(config.hasCapability("generation")).isTrue();
        assertThat(config.hasCapability("deployment")).isFalse();
    }

    @Test
    @DisplayName("Should handle default values correctly")
    void shouldHandleDefaults() {
        // Given - minimal config
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("minimal.agent")
            .build();

        // Then
        assertThat(config.getVersion()).isEqualTo("1.0.0");  // Default
        assertThat(config.getTags()).isEmpty();
        assertThat(config.getCapabilities()).isEmpty();
        assertThat(config.getGenerator()).isNull();
        assertThat(config.getValidation()).isNull();
        assertThat(config.getCache()).isNull();
        assertThat(config.getMetadata()).isEmpty();
    }

    @Test
    @DisplayName("Should create immutable copies of collections")
    void shouldCreateImmutableCollections() {
        // Given
        Set<String> tags = new java.util.HashSet<>();
        tags.add("tag1");

        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("test.agent")
            .tags(tags)
            .build();

        // When - modify original
        tags.add("tag2");

        // Then - config should not be affected
        assertThat(config.getTags()).hasSize(1).contains("tag1");
    }
}
