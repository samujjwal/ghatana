/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.FailureMode;
import com.ghatana.agent.adaptive.AdaptiveAgentConfig;
import com.ghatana.agent.composite.CompositeAgentConfig;
import com.ghatana.agent.deterministic.DeterministicAgentConfig;
import com.ghatana.agent.deterministic.DeterministicSubtype;
import com.ghatana.agent.hybrid.HybridAgentConfig;
import com.ghatana.agent.probabilistic.ProbabilisticAgentConfig;
import com.ghatana.agent.probabilistic.ProbabilisticSubtype;
import com.ghatana.agent.reactive.ReactiveAgentConfig;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * Materializes agent YAML configurations into the immutable
 * {@link AgentConfig} hierarchy via builder pattern.
 *
 * <h2>Architecture</h2>
 * <pre>
 *   YAML file → Jackson ObjectMapper → AgentConfigDto (mutable DTO)
 *             → AgentConfigMaterializer → AgentConfig subtype (immutable)
 * </pre>
 *
 * <p>The two-phase approach is necessary because the {@code AgentConfig}
 * hierarchy uses Lombok {@code @Value @SuperBuilder} which is not
 * directly Jackson-deserializable. The DTO layer handles JSON/YAML
 * parsing, then the materializer drives the type-specific builders.
 *
 * <h2>Type Dispatch</h2>
 * The {@code type} field in YAML maps to {@link AgentType}, which selects
 * the appropriate builder:
 * <ul>
 *   <li>{@code DETERMINISTIC} → {@link DeterministicAgentConfig.DeterministicAgentConfigBuilder}</li>
 *   <li>{@code PROBABILISTIC} → {@link ProbabilisticAgentConfig.ProbabilisticAgentConfigBuilder}</li>
 *   <li>{@code HYBRID} → {@link HybridAgentConfig.HybridAgentConfigBuilder}</li>
 *   <li>{@code ADAPTIVE} → {@link AdaptiveAgentConfig.AdaptiveAgentConfigBuilder}</li>
 *   <li>{@code COMPOSITE} → {@link CompositeAgentConfig.CompositeAgentConfigBuilder}</li>
 *   <li>{@code REACTIVE} → {@link ReactiveAgentConfig.ReactiveAgentConfigBuilder}</li>
 *   <li>{@code CUSTOM} / registered custom names → base {@link AgentConfig} builder</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose YAML → AgentConfig materializer
 * @doc.layer platform
 * @doc.pattern Materializer, Factory
 *
 * @author Ghatana AI Platform
 * @since 3.0.0
 */
public class AgentConfigMaterializer {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigMaterializer.class);

    private final ObjectMapper yamlMapper;
    private final Map<String, AgentConfigTemplate> templateRegistry = new LinkedHashMap<>();

    /**
     * Creates a materializer with a default YAML ObjectMapper.
     */
    public AgentConfigMaterializer() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Creates a materializer with a custom ObjectMapper.
     *
     * @param yamlMapper pre-configured ObjectMapper (should support YAML)
     */
    public AgentConfigMaterializer(@NotNull ObjectMapper yamlMapper) {
        this.yamlMapper = Objects.requireNonNull(yamlMapper);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Template Management
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Registers a configuration template by ID.
     *
     * @param template the template to register
     * @return this materializer for chaining
     */
    @NotNull
    public AgentConfigMaterializer registerTemplate(@NotNull AgentConfigTemplate template) {
        Objects.requireNonNull(template.getTemplateId(), "templateId is required");
        templateRegistry.put(template.getTemplateId(), template);
        log.info("Registered config template: {}", template.getTemplateId());
        return this;
    }

    /**
     * Loads and registers a template from YAML content.
     *
     * @param yaml YAML string defining the template
     * @return this materializer for chaining
     */
    @NotNull
    public AgentConfigMaterializer registerTemplateFromYaml(@NotNull String yaml) {
        try {
            AgentConfigTemplate template = yamlMapper.readValue(yaml, AgentConfigTemplate.class);
            return registerTemplate(template);
        } catch (IOException e) {
            throw new AgentMaterializationException("Failed to parse template YAML", e);
        }
    }

    /**
     * Loads and registers a template from a YAML file.
     *
     * @param yamlPath path to the template YAML file
     * @return this materializer for chaining
     */
    @NotNull
    public AgentConfigMaterializer registerTemplateFromPath(@NotNull Path yamlPath) {
        try (InputStream is = Files.newInputStream(yamlPath)) {
            AgentConfigTemplate template = yamlMapper.readValue(is, AgentConfigTemplate.class);
            return registerTemplate(template);
        } catch (IOException e) {
            throw new AgentMaterializationException(
                    "Failed to read template from " + yamlPath, e);
        }
    }

    /**
     * Materializes a DTO with template inheritance. If the DTO has an {@code extends}
     * field, the referenced template's defaults are applied before materialization.
     *
     * @param template the template to apply
     * @param dto      the agent configuration DTO (overrides)
     * @return typed AgentConfig with template defaults + DTO overrides
     */
    @NotNull
    public AgentConfig materialize(@NotNull AgentConfigTemplate template, @NotNull AgentConfigDto dto) {
        template.applyTo(dto);
        return materializeFromDto(dto);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Public API
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Materializes YAML content into an immutable AgentConfig.
     *
     * @param yaml YAML string
     * @return typed AgentConfig (DeterministicAgentConfig, etc.)
     * @throws AgentMaterializationException if parsing or materialization fails
     */
    @NotNull
    public AgentConfig materialize(@NotNull String yaml) {
        try {
            AgentConfigDto dto = yamlMapper.readValue(yaml, AgentConfigDto.class);
            return materializeFromDto(dto);
        } catch (IOException e) {
            throw new AgentMaterializationException("Failed to parse YAML", e);
        }
    }

    /**
     * Materializes from a YAML file path.
     *
     * @param yamlPath path to YAML file
     * @return typed AgentConfig
     */
    @NotNull
    public AgentConfig materialize(@NotNull Path yamlPath) {
        try (InputStream is = Files.newInputStream(yamlPath)) {
            AgentConfigDto dto = yamlMapper.readValue(is, AgentConfigDto.class);
            return materializeFromDto(dto);
        } catch (IOException e) {
            throw new AgentMaterializationException(
                    "Failed to read YAML from " + yamlPath, e);
        }
    }

    /**
     * Materializes from a pre-parsed DTO.
     *
     * <p>If the DTO has an {@code extends} field referencing a registered template,
     * the template defaults are applied before materialization (child values
     * take precedence over template values).
     *
     * @param dto the deserialized DTO
     * @return typed AgentConfig
     */
    @NotNull
    public AgentConfig materializeFromDto(@NotNull AgentConfigDto dto) {
        // Resolve template inheritance
        if (dto.getExtendsTemplate() != null && !dto.getExtendsTemplate().isBlank()) {
            AgentConfigTemplate template = templateRegistry.get(dto.getExtendsTemplate());
            if (template == null) {
                throw new AgentMaterializationException(
                        "Template not found: '" + dto.getExtendsTemplate() +
                                "'. Registered templates: " + templateRegistry.keySet());
            }
            log.debug("Applying template '{}' to agent '{}'", dto.getExtendsTemplate(), dto.getAgentId());
            template.applyTo(dto);
        }

        Objects.requireNonNull(dto.getAgentId(), "agentId is required");
        Objects.requireNonNull(dto.getType(), "type is required");

        AgentType agentType = parseAgentType(dto.getType());

        AgentConfig config = switch (agentType) {
            case DETERMINISTIC -> materializeDeterministic(dto);
            case PROBABILISTIC, LLM -> materializeProbabilistic(dto);
            case HYBRID -> materializeHybrid(dto);
            case ADAPTIVE -> materializeAdaptive(dto);
            case COMPOSITE -> materializeComposite(dto);
            case REACTIVE -> materializeReactive(dto);
            case CUSTOM -> materializeCustom(dto);
        };

        log.info("Materialized agent '{}' (type={}, version={})",
                config.getAgentId(), config.getType(), config.getVersion());

        return config;
    }

    /**
     * Materializes multiple agent configs from a directory.
     *
     * @param directory path containing YAML files
     * @return list of materialized configs
     */
    @NotNull
    public List<AgentConfig> materializeDirectory(@NotNull Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new AgentMaterializationException("Not a directory: " + directory);
        }

        List<AgentConfig> configs = new ArrayList<>();
        try (var walk = Files.walk(directory, 1)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".yaml") || name.endsWith(".yml");
                    })
                    .forEach(file -> {
                        try {
                            configs.add(materialize(file));
                        } catch (Exception e) {
                            log.error("Failed to materialize {}: {}", file, e.getMessage());
                            throw new AgentMaterializationException(
                                    "Failed to materialize " + file, e);
                        }
                    });
        } catch (IOException e) {
            throw new AgentMaterializationException(
                    "Failed to walk directory " + directory, e);
        }

        log.info("Materialized {} agent configs from {}", configs.size(), directory);
        return configs;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Type-Specific Materializers
    // ═════════════════════════════════════════════════════════════════════════

    @NotNull
    private DeterministicAgentConfig materializeDeterministic(AgentConfigDto dto) {
        var builder = DeterministicAgentConfig.builder();
        applyBaseFields(builder, dto, AgentType.DETERMINISTIC);

        String subtype = dto.getExtraString("subtype");
        if (subtype != null) {
            builder.subtype(DeterministicSubtype.valueOf(subtype));
        }

        Boolean evaluateAll = dto.getExtraBoolean("evaluateAllRules");
        if (evaluateAll != null) {
            builder.evaluateAllRules(evaluateAll);
        }

        String exactMatchField = dto.getExtraString("exactMatchField");
        if (exactMatchField != null) {
            builder.exactMatchField(exactMatchField);
        }

        String fsmEntityKeyField = dto.getExtraString("fsmEntityKeyField");
        if (fsmEntityKeyField != null) {
            builder.fsmEntityKeyField(fsmEntityKeyField);
        }

        return builder.build();
    }

    @NotNull
    private ProbabilisticAgentConfig materializeProbabilistic(AgentConfigDto dto) {
        var builder = ProbabilisticAgentConfig.builder();
        applyBaseFields(builder, dto, AgentType.PROBABILISTIC);

        String subtype = dto.getExtraString("subtype");
        if (subtype != null) {
            builder.subtype(ProbabilisticSubtype.valueOf(subtype));
        }

        String modelName = dto.getExtraString("modelName");
        if (modelName != null) builder.modelName(modelName);

        String modelVersion = dto.getExtraString("modelVersion");
        if (modelVersion != null) builder.modelVersion(modelVersion);

        String modelEndpoint = dto.getExtraString("modelEndpoint");
        if (modelEndpoint != null) builder.modelEndpoint(modelEndpoint);

        String inferenceTimeout = dto.getExtraString("inferenceTimeout");
        if (inferenceTimeout != null) {
            builder.inferenceTimeout(AgentConfigDto.parseDuration(inferenceTimeout,
                    Duration.ofMillis(100)));
        }

        Integer batchSize = dto.getExtraInt("batchSize");
        if (batchSize != null) builder.batchSize(batchSize);

        Boolean shadowMode = dto.getExtraBoolean("shadowMode");
        if (shadowMode != null) builder.shadowMode(shadowMode);

        List<String> fallbacks = dto.getExtraList("fallbackEndpoints");
        if (fallbacks != null) {
            fallbacks.forEach(builder::fallbackEndpoint);
        }

        return builder.build();
    }

    @NotNull
    private HybridAgentConfig materializeHybrid(AgentConfigDto dto) {
        var builder = HybridAgentConfig.builder();
        applyBaseFields(builder, dto, AgentType.HYBRID);

        String strategy = dto.getExtraString("strategy");
        if (strategy != null) {
            builder.strategy(HybridAgentConfig.RoutingStrategy.valueOf(strategy));
        }

        String deterministicId = dto.getExtraString("deterministicAgentId");
        if (deterministicId != null) builder.deterministicAgentId(deterministicId);

        String probabilisticId = dto.getExtraString("probabilisticAgentId");
        if (probabilisticId != null) builder.probabilisticAgentId(probabilisticId);

        Double escalationThreshold = dto.getExtraDouble("escalationConfidenceThreshold");
        if (escalationThreshold != null) {
            builder.escalationConfidenceThreshold(escalationThreshold);
        }

        String fallbackPath = dto.getExtraString("fallbackPath");
        if (fallbackPath != null) {
            builder.fallbackPath(HybridAgentConfig.RoutingStrategy.valueOf(fallbackPath));
        }

        return builder.build();
    }

    @NotNull
    private AdaptiveAgentConfig materializeAdaptive(AgentConfigDto dto) {
        var builder = AdaptiveAgentConfig.builder();
        applyBaseFields(builder, dto, AgentType.ADAPTIVE);

        String subtype = dto.getExtraString("subtype");
        if (subtype != null) {
            builder.subtype(AdaptiveAgentConfig.AdaptiveSubtype.valueOf(subtype));
        }

        String banditAlgorithm = dto.getExtraString("banditAlgorithm");
        if (banditAlgorithm != null) {
            builder.banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.valueOf(banditAlgorithm));
        }

        Double explorationRate = dto.getExtraDouble("explorationRate");
        if (explorationRate != null) builder.explorationRate(explorationRate);

        String tunedParameter = dto.getExtraString("tunedParameter");
        if (tunedParameter != null) builder.tunedParameter(tunedParameter);

        Double paramMin = dto.getExtraDouble("parameterMin");
        if (paramMin != null) builder.parameterMin(paramMin);

        Double paramMax = dto.getExtraDouble("parameterMax");
        if (paramMax != null) builder.parameterMax(paramMax);

        Integer armCount = dto.getExtraInt("armCount");
        if (armCount != null) builder.armCount(armCount);

        String objectiveMetric = dto.getExtraString("objectiveMetric");
        if (objectiveMetric != null) builder.objectiveMetric(objectiveMetric);

        Boolean maximize = dto.getExtraBoolean("maximize");
        if (maximize != null) builder.maximize(maximize);

        return builder.build();
    }

    @NotNull
    private CompositeAgentConfig materializeComposite(AgentConfigDto dto) {
        var builder = CompositeAgentConfig.builder();
        applyBaseFields(builder, dto, AgentType.COMPOSITE);

        String subtype = dto.getExtraString("subtype");
        if (subtype != null) {
            builder.subtype(CompositeAgentConfig.CompositeSubtype.valueOf(subtype));
        }

        String strategy = dto.getExtraString("aggregationStrategy");
        if (strategy != null) {
            builder.aggregationStrategy(CompositeAgentConfig.AggregationStrategy.valueOf(strategy));
        }

        List<String> subAgentIds = dto.getExtraList("subAgentIds");
        if (subAgentIds != null) {
            subAgentIds.forEach(builder::subAgentId);
        }

        List<?> weights = dto.getExtraList("weights");
        if (weights != null) {
            weights.stream()
                    .map(w -> w instanceof Number n ? n.doubleValue() : Double.parseDouble(w.toString()))
                    .forEach(builder::weight);
        }

        String votingField = dto.getExtraString("votingField");
        if (votingField != null) builder.votingField(votingField);

        String numericField = dto.getExtraString("numericField");
        if (numericField != null) builder.numericField(numericField);

        return builder.build();
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private ReactiveAgentConfig materializeReactive(AgentConfigDto dto) {
        var builder = ReactiveAgentConfig.builder();
        applyBaseFields(builder, dto, AgentType.REACTIVE);

        String subtype = dto.getExtraString("subtype");
        if (subtype != null) {
            builder.subtype(ReactiveAgentConfig.ReactiveSubtype.valueOf(subtype));
        }

        List<Map<String, Object>> triggerMaps = dto.getExtraList("triggers");
        if (triggerMaps != null) {
            for (Map<String, Object> tm : triggerMaps) {
                var triggerBuilder = ReactiveAgentConfig.TriggerDefinition.builder()
                        .name(Objects.toString(tm.get("name"), "unnamed"))
                        .eventTypeField(Objects.toString(tm.get("eventTypeField"), "type"))
                        .eventTypeValue(Objects.toString(tm.get("eventTypeValue"), "*"));

                if (tm.containsKey("conditionField")) {
                    triggerBuilder.conditionField(tm.get("conditionField").toString());
                }
                if (tm.containsKey("conditionOperator")) {
                    triggerBuilder.conditionOperator(tm.get("conditionOperator").toString());
                }
                if (tm.containsKey("conditionValue")) {
                    triggerBuilder.conditionValue(tm.get("conditionValue"));
                }
                if (tm.containsKey("threshold")) {
                    triggerBuilder.threshold(((Number) tm.get("threshold")).intValue());
                }
                if (tm.containsKey("countingWindow")) {
                    triggerBuilder.countingWindow(
                            AgentConfigDto.parseDuration(tm.get("countingWindow").toString(),
                                    Duration.ofMinutes(5)));
                }
                if (tm.containsKey("cooldown")) {
                    triggerBuilder.cooldown(
                            AgentConfigDto.parseDuration(tm.get("cooldown").toString(),
                                    Duration.ZERO));
                }
                if (tm.containsKey("priority")) {
                    triggerBuilder.priority(((Number) tm.get("priority")).intValue());
                }
                if (tm.containsKey("actions") && tm.get("actions") instanceof Map<?, ?> actMap) {
                    ((Map<String, Object>) actMap).forEach(triggerBuilder::action);
                }

                builder.trigger(triggerBuilder.build());
            }
        }

        return builder.build();
    }

    /**
     * Materializes a custom-typed agent configuration.
     *
     * <p>Custom types use the base {@link AgentConfig} builder. The original
     * type string from the DTO is preserved in the {@code properties} map
     * under the key {@code "customType"} so it can be propagated to the
     * agent descriptor's subtype or labels.
     */
    @NotNull
    private AgentConfig materializeCustom(AgentConfigDto dto) {
        var builder = AgentConfig.builder();
        applyBaseFields(builder, dto, AgentType.CUSTOM);

        // Preserve the original custom type name for downstream use
        String originalType = dto.getType();
        if (originalType != null && !originalType.equalsIgnoreCase("CUSTOM")) {
            Map<String, Object> props = new LinkedHashMap<>(dto.getProperties());
            props.put("customType", originalType.trim().toUpperCase());
            builder.properties(props);
        }

        return builder.build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Common Base Fields
    // ═════════════════════════════════════════════════════════════════════════

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyBaseFields(
            AgentConfig.AgentConfigBuilder builder, AgentConfigDto dto, AgentType type) {

        builder.agentId(dto.getAgentId())
                .type(type)
                .version(dto.getVersion());

        if (dto.getTimeout() != null) {
            builder.timeout(AgentConfigDto.parseDuration(dto.getTimeout(), Duration.ofSeconds(5)));
        }
        if (dto.getConfidenceThreshold() != null) {
            builder.confidenceThreshold(dto.getConfidenceThreshold());
        }
        if (dto.getMaxRetries() != null) {
            builder.maxRetries(dto.getMaxRetries());
        }
        if (dto.getRetryBackoff() != null) {
            builder.retryBackoff(AgentConfigDto.parseDuration(dto.getRetryBackoff(),
                    Duration.ofMillis(100)));
        }
        if (dto.getMaxRetryBackoff() != null) {
            builder.maxRetryBackoff(AgentConfigDto.parseDuration(dto.getMaxRetryBackoff(),
                    Duration.ofSeconds(5)));
        }
        if (dto.getFailureMode() != null) {
            builder.failureMode(FailureMode.valueOf(dto.getFailureMode()));
        }
        if (dto.getCircuitBreakerThreshold() != null) {
            builder.circuitBreakerThreshold(dto.getCircuitBreakerThreshold());
        }
        if (dto.getCircuitBreakerReset() != null) {
            builder.circuitBreakerReset(AgentConfigDto.parseDuration(
                    dto.getCircuitBreakerReset(), Duration.ofSeconds(30)));
        }
        if (dto.getMetricsEnabled() != null) {
            builder.metricsEnabled(dto.getMetricsEnabled());
        }
        if (dto.getTracingEnabled() != null) {
            builder.tracingEnabled(dto.getTracingEnabled());
        }
        if (dto.getTracingSampleRate() != null) {
            builder.tracingSampleRate(dto.getTracingSampleRate());
        }
        if (!dto.getProperties().isEmpty()) {
            builder.properties(dto.getProperties());
        }
        if (!dto.getLabels().isEmpty()) {
            builder.labels(dto.getLabels());
        }
        if (!dto.getRequiredCapabilities().isEmpty()) {
            builder.requiredCapabilities(dto.getRequiredCapabilities());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    @NotNull
    private AgentType parseAgentType(@NotNull String typeName) {
        try {
            return AgentType.resolve(typeName);
        } catch (IllegalArgumentException e) {
            throw new AgentMaterializationException(
                    "Unknown agent type: '" + typeName +
                            "'. Valid types: " + Arrays.toString(AgentType.values()) +
                            ", custom types: " + AgentType.registeredCustomTypes());
        }
    }
}
