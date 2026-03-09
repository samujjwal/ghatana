/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.config;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.FailureMode;
import com.ghatana.agent.StateMutability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;

/**
 * Immutable, versioned agent blueprint describing the stable contract of an agent.
 *
 * <p>An {@code AgentDefinition} is the canonical "what this agent is" — identity,
 * capabilities, I/O contracts, tool declarations, and operational constraints.
 * It is loaded from YAML/JSON and is <b>never mutated at runtime</b>.
 *
 * <p>Tenant-specific overrides (model selection, rate limits, feature flags) are
 * captured separately in {@link AgentInstance}.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Author writes YAML/JSON definition (versioned in source control)</li>
 *   <li>{@link AgentConfigMaterializer} deserializes via {@link AgentConfigDto}
 *       and produces an {@code AgentDefinition}</li>
 *   <li>Validation is applied: {@link AgentDefinitionValidator}</li>
 *   <li>Definition is registered in the agent catalog (read-only)</li>
 * </ol>
 *
 * <h2>Example YAML</h2>
 * <pre>{@code
 * id: fraud-detector
 * version: "2.1.0"
 * name: Fraud Detector Agent
 * type: HYBRID
 * systemPrompt: "You are a fraud detection agent..."
 * tools:
 *   - name: lookupTransaction
 *     description: Look up transaction details
 *     parameters:
 *       transactionId: { type: string, required: true }
 * inputContract:
 *   type: TransactionEvent
 *   format: JSON
 * outputContract:
 *   type: FraudAssessment
 *   format: JSON
 * constraints:
 *   maxTokens: 4096
 *   timeout: PT10S
 *   maxCostPerCall: 0.05
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Immutable versioned agent blueprint
 * @doc.layer platform
 * @doc.pattern ValueObject
 *
 * @author Ghatana AI Platform
 * @since 2.4.0
 */
public final class AgentDefinition {

    // ═══════════════════════════════════════════════════════════════════════════
    // Identity
    // ═══════════════════════════════════════════════════════════════════════════

    private final String id;
    private final String version;
    private final String name;
    private final String description;

    // ═══════════════════════════════════════════════════════════════════════════
    // Type & Behavior
    // ═══════════════════════════════════════════════════════════════════════════

    private final AgentType type;
    private final String subtype;
    private final DeterminismGuarantee determinism;
    private final StateMutability stateMutability;
    private final FailureMode failureMode;

    // ═══════════════════════════════════════════════════════════════════════════
    // LLM Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    private final String systemPrompt;
    private final int maxTokens;
    private final double temperature;

    // ═══════════════════════════════════════════════════════════════════════════
    // I/O Contracts
    // ═══════════════════════════════════════════════════════════════════════════

    private final IOContract inputContract;
    private final IOContract outputContract;

    // ═══════════════════════════════════════════════════════════════════════════
    // Tools
    // ═══════════════════════════════════════════════════════════════════════════

    private final List<ToolDeclaration> tools;

    // ═══════════════════════════════════════════════════════════════════════════
    // Capabilities & Constraints
    // ═══════════════════════════════════════════════════════════════════════════

    private final Set<String> capabilities;
    private final Duration timeout;
    private final double maxCostPerCall;
    private final int maxRetries;

    // ═══════════════════════════════════════════════════════════════════════════
    // Labels & Metadata
    // ═══════════════════════════════════════════════════════════════════════════

    private final Map<String, String> labels;
    private final Map<String, Object> metadata;

    private AgentDefinition(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.version = Objects.requireNonNull(builder.version, "version must not be null");
        this.name = builder.name != null ? builder.name : builder.id;
        this.description = builder.description;
        this.type = Objects.requireNonNull(builder.type, "type must not be null");
        this.subtype = builder.subtype;
        this.determinism = builder.determinism;
        this.stateMutability = builder.stateMutability;
        this.failureMode = builder.failureMode;
        this.systemPrompt = builder.systemPrompt;
        this.maxTokens = builder.maxTokens;
        this.temperature = builder.temperature;
        this.inputContract = builder.inputContract;
        this.outputContract = builder.outputContract;
        this.tools = List.copyOf(builder.tools);
        this.capabilities = Set.copyOf(builder.capabilities);
        this.timeout = builder.timeout;
        this.maxCostPerCall = builder.maxCostPerCall;
        this.maxRetries = builder.maxRetries;
        this.labels = Map.copyOf(builder.labels);
        this.metadata = Map.copyOf(builder.metadata);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Accessors
    // ═══════════════════════════════════════════════════════════════════════════

    @NotNull public String getId() { return id; }
    @NotNull public String getVersion() { return version; }
    @NotNull public String getName() { return name; }
    @Nullable public String getDescription() { return description; }
    @NotNull public AgentType getType() { return type; }
    @Nullable public String getSubtype() { return subtype; }
    @NotNull public DeterminismGuarantee getDeterminism() { return determinism; }
    @NotNull public StateMutability getStateMutability() { return stateMutability; }
    @NotNull public FailureMode getFailureMode() { return failureMode; }
    @Nullable public String getSystemPrompt() { return systemPrompt; }
    public int getMaxTokens() { return maxTokens; }
    public double getTemperature() { return temperature; }
    @Nullable public IOContract getInputContract() { return inputContract; }
    @Nullable public IOContract getOutputContract() { return outputContract; }
    @NotNull public List<ToolDeclaration> getTools() { return tools; }
    @NotNull public Set<String> getCapabilities() { return capabilities; }
    @NotNull public Duration getTimeout() { return timeout; }
    public double getMaxCostPerCall() { return maxCostPerCall; }
    public int getMaxRetries() { return maxRetries; }
    @NotNull public Map<String, String> getLabels() { return labels; }
    @NotNull public Map<String, Object> getMetadata() { return metadata; }

    /** Canonical identifier: "{id}:{version}". */
    @NotNull
    public String getCanonicalId() {
        return id + ":" + version;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Inner Types
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Describes the I/O data contract for an agent's input or output.
     *
     * @param typeName  the logical type name (e.g., "TransactionEvent")
     * @param format    the serialization format (e.g., "JSON", "PROTOBUF")
     * @param schema    optional JSON Schema or Protobuf descriptor reference
     */
    public record IOContract(
            @NotNull String typeName,
            @NotNull String format,
            @Nullable String schema
    ) {
        public IOContract {
            Objects.requireNonNull(typeName, "typeName must not be null");
            Objects.requireNonNull(format, "format must not be null");
        }
    }

    /**
     * Declares a tool that this agent can invoke.
     *
     * @param name        tool name (unique within this definition)
     * @param description human-readable description
     * @param parameters  parameter name → JSON Schema type map
     */
    public record ToolDeclaration(
            @NotNull String name,
            @NotNull String description,
            @NotNull Map<String, ParameterSchema> parameters
    ) {
        public ToolDeclaration {
            Objects.requireNonNull(name, "tool name must not be null");
            Objects.requireNonNull(description, "tool description must not be null");
            parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
        }
    }

    /**
     * JSON Schema-compatible parameter descriptor.
     *
     * @param type        JSON Schema type ("string", "integer", "boolean", "number", "object", "array")
     * @param description human-readable description
     * @param required    whether this parameter is required
     * @param enumValues  allowed values (for enum constraints)
     */
    public record ParameterSchema(
            @NotNull String type,
            @Nullable String description,
            boolean required,
            @Nullable List<String> enumValues
    ) {
        public ParameterSchema {
            Objects.requireNonNull(type, "type must not be null");
        }

        /** Convenience factory for a required string parameter. */
        public static ParameterSchema requiredString(String description) {
            return new ParameterSchema("string", description, true, null);
        }

        /** Convenience factory for an optional string parameter. */
        public static ParameterSchema optionalString(String description) {
            return new ParameterSchema("string", description, false, null);
        }

        /** Convenience factory for a required integer parameter. */
        public static ParameterSchema requiredInteger(String description) {
            return new ParameterSchema("integer", description, true, null);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Builder
    // ═══════════════════════════════════════════════════════════════════════════

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String version = "1.0.0";
        private String name;
        private String description;
        private AgentType type;
        private String subtype;
        private DeterminismGuarantee determinism = DeterminismGuarantee.NONE;
        private StateMutability stateMutability = StateMutability.STATELESS;
        private FailureMode failureMode = FailureMode.FAIL_FAST;
        private String systemPrompt;
        private int maxTokens = 4096;
        private double temperature = 0.7;
        private IOContract inputContract;
        private IOContract outputContract;
        private final List<ToolDeclaration> tools = new ArrayList<>();
        private final Set<String> capabilities = new LinkedHashSet<>();
        private Duration timeout = Duration.ofSeconds(30);
        private double maxCostPerCall = 1.0;
        private int maxRetries = 3;
        private final Map<String, String> labels = new LinkedHashMap<>();
        private final Map<String, Object> metadata = new LinkedHashMap<>();

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder type(AgentType type) { this.type = type; return this; }
        public Builder subtype(String subtype) { this.subtype = subtype; return this; }
        public Builder determinism(DeterminismGuarantee determinism) { this.determinism = determinism; return this; }
        public Builder stateMutability(StateMutability stateMutability) { this.stateMutability = stateMutability; return this; }
        public Builder failureMode(FailureMode failureMode) { this.failureMode = failureMode; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder temperature(double temperature) { this.temperature = temperature; return this; }
        public Builder inputContract(IOContract inputContract) { this.inputContract = inputContract; return this; }
        public Builder outputContract(IOContract outputContract) { this.outputContract = outputContract; return this; }
        public Builder addTool(ToolDeclaration tool) { this.tools.add(tool); return this; }
        public Builder tools(List<ToolDeclaration> tools) { this.tools.addAll(tools); return this; }
        public Builder addCapability(String capability) { this.capabilities.add(capability); return this; }
        public Builder capabilities(Set<String> capabilities) { this.capabilities.addAll(capabilities); return this; }
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }
        public Builder maxCostPerCall(double maxCostPerCall) { this.maxCostPerCall = maxCostPerCall; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder label(String key, String value) { this.labels.put(key, value); return this; }
        public Builder labels(Map<String, String> labels) { this.labels.putAll(labels); return this; }
        public Builder metadata(String key, Object value) { this.metadata.put(key, value); return this; }

        public AgentDefinition build() {
            return new AgentDefinition(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentDefinition that)) return false;
        return id.equals(that.id) && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }

    @Override
    public String toString() {
        return "AgentDefinition{" + getCanonicalId() + ", type=" + type + "}";
    }
}
