/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.agent.framework.loader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.FailureMode;
import com.ghatana.agent.StateMutability;
import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.core.template.TemplateContext;
import com.ghatana.core.template.YamlTemplateEngine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Loads {@link AgentDefinition} blueprints from YAML files with full template
 * variable substitution and {@code extends} inheritance support.
 *
 * <h2>Loading Pipeline</h2>
 * <pre>
 *   agent.yaml
 *       │
 *       ▼  YamlTemplateEngine.renderWithInheritance()
 *   resolved YAML string (extends chain merged, {{ vars }} substituted)
 *       │
 *       ▼  Jackson ObjectMapper
 *   AgentDefinitionDto (mutable DTO)
 *       │
 *       ▼  AgentDefinitionLoader.materialize()
 *   AgentDefinition (immutable)
 * </pre>
 *
 * <h2>Validation</h2>
 * <p>The following fields are mandatory in each YAML file:
 * <ul>
 *   <li>{@code id} — unique agent identifier</li>
 *   <li>{@code name} — human-readable display name</li>
 *   <li>{@code type} — one of the {@link AgentType} enum values</li>
 * </ul>
 * A missing required field throws {@link IllegalStateException}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AgentDefinitionLoader loader = new AgentDefinitionLoader(templateContext);
 *
 * // Load a single file (with inheritance & variable substitution):
 * AgentDefinition def = loader.load(Path.of("agents/fraud-detector.yaml"));
 *
 * // Scan a classpath directory for all agent YAML files:
 * List<AgentDefinition> all = loader.loadFromClasspath("agent-catalog");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Loads AgentDefinition objects from YAML templates with inheritance
 * @doc.layer platform
 * @doc.pattern Factory, Strategy
 * @doc.gaa.lifecycle perceive
 */
public final class AgentDefinitionLoader {

    private static final Logger log = LoggerFactory.getLogger(AgentDefinitionLoader.class);

    private final YamlTemplateEngine templateEngine;
    private final ObjectMapper yamlMapper;
    private final TemplateContext context;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /**
     * Creates a loader with an explicit template context.
     *
     * @param context variable bindings for {@code {{ varName }}} substitution
     */
    public AgentDefinitionLoader(@NotNull TemplateContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.templateEngine = new YamlTemplateEngine();
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Creates a loader with an empty template context (suitable for YAML files
     * with no {@code {{ … }}} placeholders).
     */
    public AgentDefinitionLoader() {
        this(TemplateContext.empty());
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Loads a single {@link AgentDefinition} from a YAML file.
     *
     * <p>The file is processed through the full template pipeline:
     * {@code extends} inheritance is resolved (max depth 3), then
     * {@code {{ varName }}} placeholders are substituted from this loader's
     * {@link TemplateContext}.
     *
     * @param yamlFile path to the agent definition YAML file
     * @return fully-materialised {@link AgentDefinition}
     * @throws IOException           if the file or any ancestor cannot be read
     * @throws IllegalStateException if required fields are missing or a template
     *                               variable has no binding
     */
    @NotNull
    public AgentDefinition load(@NotNull Path yamlFile) throws IOException {
        Objects.requireNonNull(yamlFile, "yamlFile must not be null");

        log.debug("Loading AgentDefinition from {}", yamlFile);
        String rendered = templateEngine.renderWithInheritance(yamlFile, context);
        AgentDefinitionDto dto = yamlMapper.readValue(rendered, AgentDefinitionDto.class);
        return materialize(dto, yamlFile.toString());
    }

    /**
     * Loads a single {@link AgentDefinition} from a raw YAML string.
     *
     * <p>No inheritance resolution is performed (no file path context).
     * {@code {{ varName }}} placeholders are still substituted.
     *
     * @param rawYaml raw YAML content
     * @return fully-materialised {@link AgentDefinition}
     * @throws IOException           if parsing fails
     * @throws IllegalStateException if required fields are missing or a placeholder
     *                               has no binding
     */
    @NotNull
    public AgentDefinition loadFromString(@NotNull String rawYaml) throws IOException {
        Objects.requireNonNull(rawYaml, "rawYaml must not be null");

        String rendered = templateEngine.render(rawYaml, context);
        AgentDefinitionDto dto = yamlMapper.readValue(rendered, AgentDefinitionDto.class);
        return materialize(dto, "<string>");
    }

    /**
     * Scans a classpath directory for all {@code *.yaml} files and loads each
     * as an {@link AgentDefinition}.
     *
     * <p>Files that fail to parse are logged as warnings and skipped; the rest
     * are returned.
     *
     * <p><b>Note:</b> classpath resources do not support {@code extends} chains
     * that reference sibling files on the filesystem. Use {@link #loadFromDirectory(Path)}
     * for that use case.
     *
     * @param classpathDir classpath-relative directory (e.g. {@code "agent-catalog"})
     * @return list of loaded definitions (may be empty if directory not found)
     */
    @NotNull
    public List<AgentDefinition> loadFromClasspath(@NotNull String classpathDir) {
        Objects.requireNonNull(classpathDir, "classpathDir must not be null");

        URL dirUrl = getClass().getClassLoader().getResource(classpathDir);
        if (dirUrl == null) {
            log.debug("Classpath directory '{}' not found — returning empty list", classpathDir);
            return List.of();
        }

        // Only "file:" URLs support directory listing without a custom URL file handler
        if (!"file".equals(dirUrl.getProtocol())) {
            log.warn("Classpath directory '{}' is inside a JAR; use loadFromDirectory() instead");
            return List.of();
        }

        Path dirPath = Path.of(dirUrl.getPath());
        try {
            return loadFromDirectory(dirPath);
        } catch (IOException e) {
            log.warn("Failed to scan classpath directory '{}': {}", classpathDir, e.getMessage());
            return List.of();
        }
    }

    /**
     * Scans a filesystem directory for all direct {@code *.yaml} children and
     * loads each as an {@link AgentDefinition}.
     *
     * <p>Files that fail validation or parsing are logged as warnings and skipped.
     *
     * @param directory directory to scan
     * @return list of loaded definitions
     * @throws IOException if the directory cannot be read
     */
    @NotNull
    public List<AgentDefinition> loadFromDirectory(@NotNull Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory must not be null");

        if (!Files.isDirectory(directory)) {
            throw new IOException("Not a directory: " + directory);
        }

        List<AgentDefinition> results = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                  .sorted()
                  .forEach(p -> {
                      try {
                          results.add(load(p));
                          log.debug("Loaded AgentDefinition from {}", p.getFileName());
                      } catch (Exception e) {
                          log.warn("Skipping agent YAML '{}': {}", p.getFileName(), e.getMessage());
                      }
                  });
        }
        log.info("Loaded {} AgentDefinition(s) from {}", results.size(), directory);
        return Collections.unmodifiableList(results);
    }

    // ─── Materialisation ──────────────────────────────────────────────────────

    /**
     * Converts a parsed DTO into an immutable {@link AgentDefinition}, applying
     * validation for required fields.
     *
     * @param dto    parsed DTO
     * @param source human-readable source label for error messages
     * @return immutable definition
     * @throws IllegalStateException if required fields are absent or unrecognised values
     */
    @NotNull
    private AgentDefinition materialize(@NotNull AgentDefinitionDto dto, @NotNull String source) {
        // ── Required field validation ──────────────────────────────────────
        if (dto.id == null || dto.id.isBlank()) {
            throw new IllegalStateException(
                    "Agent definition from '" + source + "' is missing required field 'id'");
        }
        if (dto.name == null || dto.name.isBlank()) {
            throw new IllegalStateException(
                    "Agent definition '" + dto.id + "' from '" + source
                    + "' is missing required field 'name'");
        }
        if (dto.type == null || dto.type.isBlank()) {
            throw new IllegalStateException(
                    "Agent definition '" + dto.id + "' from '" + source
                    + "' is missing required field 'type'");
        }

        // ── Type conversion ────────────────────────────────────────────────
        AgentType agentType;
        try {
            agentType = AgentType.valueOf(dto.type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Agent definition '" + dto.id + "' has unknown type '" + dto.type
                    + "'. Valid values: " + List.of(AgentType.values()), e);
        }

        AgentDefinition.Builder builder = AgentDefinition.builder()
                .id(dto.id)
                .name(dto.name)
                .type(agentType);

        if (dto.version != null) builder.version(dto.version);
        if (dto.description != null) builder.description(dto.description);
        if (dto.subtype != null) builder.subtype(dto.subtype);
        if (dto.systemPrompt != null) builder.systemPrompt(dto.systemPrompt);
        if (dto.maxTokens != null) builder.maxTokens(dto.maxTokens);
        if (dto.temperature != null) builder.temperature(dto.temperature);
        if (dto.maxRetries != null) builder.maxRetries(dto.maxRetries);
        if (dto.maxCostPerCall != null) builder.maxCostPerCall(dto.maxCostPerCall);
        if (dto.labels != null) builder.labels(dto.labels);

        if (dto.timeout != null) {
            try {
                builder.timeout(Duration.parse(dto.timeout));
            } catch (Exception e) {
                log.warn("Invalid timeout '{}' in agent '{}' — using default", dto.timeout, dto.id);
            }
        }

        if (dto.determinism != null) {
            try {
                builder.determinism(DeterminismGuarantee.valueOf(dto.determinism.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown determinism '{}' in agent '{}' — using default", dto.determinism, dto.id);
            }
        }

        if (dto.stateMutability != null) {
            try {
                builder.stateMutability(StateMutability.valueOf(dto.stateMutability.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown stateMutability '{}' in agent '{}' — using default", dto.stateMutability, dto.id);
            }
        }

        if (dto.failureMode != null) {
            try {
                builder.failureMode(FailureMode.valueOf(dto.failureMode.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown failureMode '{}' in agent '{}' — using default", dto.failureMode, dto.id);
            }
        }

        if (dto.capabilities != null) builder.capabilities(new java.util.LinkedHashSet<>(dto.capabilities));
        if (dto.inputContract != null) builder.inputContract(new AgentDefinition.IOContract(
                dto.inputContract.typeName, dto.inputContract.format, dto.inputContract.schema));
        if (dto.outputContract != null) builder.outputContract(new AgentDefinition.IOContract(
                dto.outputContract.typeName, dto.outputContract.format, dto.outputContract.schema));

        if (dto.tools != null) {
            for (ToolDto toolDto : dto.tools) {
                if (toolDto.name == null) continue;
                Map<String, AgentDefinition.ParameterSchema> params =
                        toolDto.parameters != null
                                ? materialiseParams(toolDto.parameters)
                                : Map.of();
                builder.addTool(new AgentDefinition.ToolDeclaration(
                        toolDto.name,
                        toolDto.description != null ? toolDto.description : "",
                        params));
            }
        }

        return builder.build();
    }

    @NotNull
    private Map<String, AgentDefinition.ParameterSchema> materialiseParams(
            @NotNull Map<String, ParamDto> raw) {
        Map<String, AgentDefinition.ParameterSchema> result = new java.util.LinkedHashMap<>();
        raw.forEach((name, dto) -> {
            if (dto == null || dto.type == null) return;
            result.put(name, new AgentDefinition.ParameterSchema(
                    dto.type,
                    dto.description,
                    dto.required,
                    dto.enumValues));
        });
        return result;
    }

    // ─── Inner DTOs ───────────────────────────────────────────────────────────

    /** Jackson DTO for top-level agent definition YAML. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class AgentDefinitionDto {
        @JsonProperty("id")             @Nullable String id;
        @JsonProperty("version")        @Nullable String version;
        @JsonProperty("name")           @Nullable String name;
        @JsonProperty("description")    @Nullable String description;
        @JsonProperty("type")           @Nullable String type;
        @JsonProperty("subtype")        @Nullable String subtype;
        @JsonProperty("determinism")    @Nullable String determinism;
        @JsonProperty("stateMutability") @Nullable String stateMutability;
        @JsonProperty("failureMode")    @Nullable String failureMode;
        @JsonProperty("systemPrompt")   @Nullable String systemPrompt;
        @JsonProperty("maxTokens")      @Nullable Integer maxTokens;
        @JsonProperty("temperature")    @Nullable Double temperature;
        @JsonProperty("timeout")        @Nullable String timeout;
        @JsonProperty("maxCostPerCall") @Nullable Double maxCostPerCall;
        @JsonProperty("maxRetries")     @Nullable Integer maxRetries;
        @JsonProperty("capabilities")   @Nullable List<String> capabilities;
        @JsonProperty("labels")         @Nullable Map<String, String> labels;
        @JsonProperty("inputContract")  @Nullable IOContractDto inputContract;
        @JsonProperty("outputContract") @Nullable IOContractDto outputContract;
        @JsonProperty("tools")          @Nullable List<ToolDto> tools;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class IOContractDto {
        @JsonProperty("type")   @Nullable String typeName;
        @JsonProperty("format") @Nullable String format;
        @JsonProperty("schema") @Nullable String schema;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ToolDto {
        @JsonProperty("name")        @Nullable String name;
        @JsonProperty("description") @Nullable String description;
        @JsonProperty("parameters")  @Nullable Map<String, ParamDto> parameters;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ParamDto {
        @JsonProperty("type")        @Nullable String type;
        @JsonProperty("description") @Nullable String description;
        @JsonProperty("required")             boolean required;
        @JsonProperty("enum")        @Nullable List<String> enumValues;
    }
}
