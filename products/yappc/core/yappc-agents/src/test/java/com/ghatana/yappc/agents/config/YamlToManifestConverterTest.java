package com.ghatana.yappc.agents.config;

import com.ghatana.contracts.agent.v1.AgentManifestProto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link YamlToManifestConverter}.
 *
 * @doc.type class
 * @doc.purpose Verify YAML-to-AgentManifest conversion accuracy
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YamlToManifestConverter Tests")
class YamlToManifestConverterTest {

    private YamlToManifestConverter converter;

    @BeforeEach
    void setUp() {
        converter = new YamlToManifestConverter();
    }

    @Test
    @DisplayName("should convert basic config to manifest with correct metadata")
    void shouldConvertBasicConfigToManifest() {
        // GIVEN
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("expert.java")
            .name("Java Expert")
            .description("Generates Java code")
            .version("2.0.0")
            .build();

        // WHEN
        AgentManifestProto manifest = converter.convert(config);

        // THEN
        assertThat(manifest.getMetadata().getId()).isEqualTo("expert.java");
        assertThat(manifest.getMetadata().getName()).isEqualTo("Java Expert");
        assertThat(manifest.getMetadata().getDescription()).isEqualTo("Generates Java code");
        assertThat(manifest.getMetadata().getVersion()).isEqualTo("2.0.0");
    }

    @Test
    @DisplayName("should map tags as labels and input event types")
    void shouldMapTagsAsLabelsAndEventTypes() {
        // GIVEN
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("expert.java")
            .name("Java Expert")
            .description("Generates Java code")
            .tags(Set.of("java", "code-gen"))
            .build();

        // WHEN
        AgentManifestProto manifest = converter.convert(config);

        // THEN
        assertThat(manifest.getMetadata().getLabelsMap())
            .containsKey("tag.java")
            .containsKey("tag.code-gen");
        assertThat(manifest.getSpec().getInputEventTypesList())
            .contains("tag:java", "tag:code-gen");
    }

    @Test
    @DisplayName("should map capabilities to spec")
    void shouldMapCapabilitiesToSpec() {
        // GIVEN
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("expert.java")
            .name("Java Expert")
            .description("Generates Java code")
            .capabilities(Set.of("code-generation", "refactoring"))
            .build();

        // WHEN
        AgentManifestProto manifest = converter.convert(config);

        // THEN
        assertThat(manifest.getSpec().getCapabilitiesList())
            .contains("code-generation", "refactoring");
    }

    @Test
    @DisplayName("should set LLM_BASED runtime type for llm generator")
    void shouldSetLlmBasedRuntimeType() {
        // GIVEN
        YamlAgentConfig.GeneratorConfig gen = new YamlAgentConfig.GeneratorConfig(
            "llm", "you are a java expert", "gpt-4o", 0.2, 2048, 3, Map.of()
        );
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("expert.java")
            .name("Java Expert")
            .description("Generates Java code")
            .generator(gen)
            .build();

        // WHEN
        AgentManifestProto manifest = converter.convert(config);

        // THEN
        assertThat(manifest.getSpec().getRuntime().getType()).isEqualTo("LLM_BASED");
        assertThat(manifest.getSpec().getRuntime().getConfigMap())
            .containsEntry("model", "gpt-4o")
            .containsEntry("temperature", "0.2")
            .containsEntry("max_tokens", "2048")
            .containsEntry("prompt_template", "you are a java expert");
    }

    @Test
    @DisplayName("should set RULE_BASED runtime type for rule_based generator")
    void shouldSetRuleBasedRuntimeType() {
        // GIVEN
        YamlAgentConfig.GeneratorConfig gen = new YamlAgentConfig.GeneratorConfig(
            "rule_based", null, null, 0.0, 0, 0, Map.of()
        );
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("validator.rules")
            .name("Rules Validator")
            .description("Validates by rules")
            .generator(gen)
            .build();

        // WHEN
        AgentManifestProto manifest = converter.convert(config);

        // THEN
        assertThat(manifest.getSpec().getRuntime().getType()).isEqualTo("RULE_BASED");
    }

    @Test
    @DisplayName("should set TEMPLATE_BASED runtime type for template generator")
    void shouldSetTemplateBasedRuntimeType() {
        // GIVEN
        YamlAgentConfig.GeneratorConfig gen = new YamlAgentConfig.GeneratorConfig(
            "template", "my-template", null, 0.0, 0, 0, Map.of()
        );
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("scaffold.template")
            .name("Scaffolder")
            .description("Generates from templates")
            .generator(gen)
            .build();

        // WHEN
        AgentManifestProto manifest = converter.convert(config);

        // THEN
        assertThat(manifest.getSpec().getRuntime().getType()).isEqualTo("TEMPLATE_BASED");
    }

    @Test
    @DisplayName("should set CUSTOM runtime type for unknown generator type")
    void shouldSetCustomRuntimeTypeForUnknownGenerator() {
        // GIVEN
        YamlAgentConfig.GeneratorConfig gen = new YamlAgentConfig.GeneratorConfig(
            "fuzzy_logic", null, null, 0.0, 0, 0, Map.of()
        );
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("custom.agent")
            .name("Custom Agent")
            .description("Custom logic agent")
            .generator(gen)
            .build();

        // WHEN
        AgentManifestProto manifest = converter.convert(config);

        // THEN
        assertThat(manifest.getSpec().getRuntime().getType()).isEqualTo("CUSTOM");
    }

    @Test
    @DisplayName("should add generator-specific event types using agent id prefix")
    void shouldAddGeneratorEventTypes() {
        // GIVEN
        YamlAgentConfig.GeneratorConfig gen = new YamlAgentConfig.GeneratorConfig(
            "llm", "", "gpt-4o", 0.2, 1000, 2, Map.of()
        );
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("expert.java")
            .name("Java Expert")
            .description("Generates Java code")
            .capabilities(Set.of("code-generation"))
            .generator(gen)
            .build();

        // WHEN
        AgentManifestProto manifest = converter.convert(config);

        // THEN - "expert" extracted from "expert.java"
        assertThat(manifest.getSpec().getInputEventTypesList()).contains("expert.request");
        assertThat(manifest.getSpec().getOutputEventTypesList()).contains("expert.response");
        assertThat(manifest.getSpec().getInputEventTypesList()).contains("code-generation.requested");
        assertThat(manifest.getSpec().getOutputEventTypesList()).contains("code-generation.completed");
    }

    @Test
    @DisplayName("should include validation rules when ValidationConfig present")
    void shouldIncludeValidationRules() {
        // GIVEN
        YamlAgentConfig.ValidationConfig validation = new YamlAgentConfig.ValidationConfig(
            "agents/schemas/input.json", "agents/schemas/output.json", true
        );
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("strict.validator")
            .name("Strict Validator")
            .description("Validates strictly")
            .validation(validation)
            .build();

        // WHEN
        AgentManifestProto manifest = converter.convert(config);

        // THEN
        assertThat(manifest.getSpec().getValidationList())
            .contains("input_schema=agents/schemas/input.json")
            .contains("output_schema=agents/schemas/output.json")
            .contains("strict_mode=true");
    }

    @Test
    @DisplayName("should include cache config in runtime when cache is enabled")
    void shouldIncludeCacheConfigInRuntime() {
        // GIVEN
        YamlAgentConfig.CacheConfig cache = new YamlAgentConfig.CacheConfig(
            true, 3600L, Set.of("input", "model")
        );
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("cached.agent")
            .name("Cached Agent")
            .description("Results are cached")
            .cache(cache)
            .build();

        // WHEN
        AgentManifestProto manifest = converter.convert(config);

        // THEN
        assertThat(manifest.getSpec().getRuntime().getConfigMap())
            .containsEntry("cache.enabled", "true")
            .containsEntry("cache.ttl", "3600");
    }

    @Test
    @DisplayName("should not add cache config when cache is disabled")
    void shouldNotAddCacheConfigWhenDisabled() {
        // GIVEN
        YamlAgentConfig.CacheConfig cache = new YamlAgentConfig.CacheConfig(false, 0L, Set.of());
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("nocache.agent")
            .name("No Cache Agent")
            .description("No caching")
            .cache(cache)
            .build();

        // WHEN
        AgentManifestProto manifest = converter.convert(config);

        // THEN
        assertThat(manifest.getSpec().getRuntime().getConfigMap()).doesNotContainKey("cache.enabled");
    }

    @Test
    @DisplayName("should include extra metadata fields as labels")
    void shouldIncludeMetadataAsLabels() {
        // GIVEN
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("meta.agent")
            .name("Meta Agent")
            .description("Has custom metadata")
            .metadata(Map.of("owner", "team-alpha", "env", "production"))
            .build();

        // WHEN
        AgentManifestProto manifest = converter.convert(config);

        // THEN
        assertThat(manifest.getMetadata().getLabelsMap())
            .containsEntry("metadata.owner", "team-alpha")
            .containsEntry("metadata.env", "production");
    }

    @Test
    @DisplayName("should convert all configs in a list")
    void shouldConvertAll() {
        // GIVEN
        List<YamlAgentConfig> configs = List.of(
            YamlAgentConfig.builder().id("agent.one").name("One").description("First agent").build(),
            YamlAgentConfig.builder().id("agent.two").name("Two").description("Second agent").build(),
            YamlAgentConfig.builder().id("agent.three").name("Three").description("Third agent").build()
        );

        // WHEN
        List<AgentManifestProto> manifests = converter.convertAll(configs);

        // THEN
        assertThat(manifests).hasSize(3);
        assertThat(manifests)
            .extracting(m -> m.getMetadata().getId())
            .containsExactlyInAnyOrder("agent.one", "agent.two", "agent.three");
    }

    @Test
    @DisplayName("should return empty list when convertAll given empty input")
    void shouldReturnEmptyListForEmptyInput() {
        // WHEN
        List<AgentManifestProto> manifests = converter.convertAll(List.of());

        // THEN
        assertThat(manifests).isEmpty();
    }

    @Test
    @DisplayName("should use default version 1.0.0 when not specified")
    void shouldUseDefaultVersion() {
        // GIVEN
        YamlAgentConfig config = YamlAgentConfig.builder()
            .id("agent.default")
            .name("Default Version Agent")
            .description("Uses default version")
            .build();

        // WHEN
        AgentManifestProto manifest = converter.convert(config);

        // THEN
        assertThat(manifest.getMetadata().getVersion()).isEqualTo("1.0.0");
        assertThat(manifest.getSpec().getRuntime().getVersion()).isEqualTo("1.0.0");
    }
}
