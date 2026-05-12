/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.config;

import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.FailureMode;
import com.ghatana.agent.StateMutability;
import com.ghatana.agent.learning.LearningContract;
import com.ghatana.agent.learning.LearningLevel;
import com.ghatana.agent.learning.LearningTarget;
import com.ghatana.agent.release.AgentReleaseBuilder;
import com.ghatana.agent.release.AgentReleaseState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

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
    private final String namespace;
    private final String version;
    private final String status;
    private final String name;
    private final String description;
    private final List<String> owners;

    // ═══════════════════════════════════════════════════════════════════════════
    // Type & Behavior
    // ═══════════════════════════════════════════════════════════════════════════

    private final AgentType type;
    private final String subtype;
    private final DeterminismGuarantee determinism;
    private final StateMutability stateMutability;
    private final FailureMode failureMode;
    private final Set<String> roles;
    private final Set<String> personas;
    private final String criticality;
    private final String autonomyLevel;
    private final String learningLevel;

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
    private final Map<String, Object> memoryBindings;
    private final List<String> policyRefs;
    private final List<String> evaluationRefs;
    private final Map<String, Object> observabilityContract;
    private final Map<String, Object> securityContract;
    private final Map<String, Object> masteryBindings;
    private final List<String> skillRefs;
    private final List<String> masteryPolicyRefs;

    private AgentDefinition(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.namespace = builder.namespace;
        this.version = Objects.requireNonNull(builder.version, "version must not be null");
        this.status = builder.status;
        this.name = builder.name != null ? builder.name : builder.id;
        this.description = builder.description;
        this.owners = List.copyOf(builder.owners);
        this.type = Objects.requireNonNull(builder.type, "type must not be null");
        this.subtype = builder.subtype;
        this.determinism = builder.determinism;
        this.stateMutability = builder.stateMutability;
        this.failureMode = builder.failureMode;
        this.roles = Set.copyOf(builder.roles);
        this.personas = Set.copyOf(builder.personas);
        this.criticality = builder.criticality;
        this.autonomyLevel = builder.autonomyLevel;
        this.learningLevel = builder.learningLevel;
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
        this.memoryBindings = Map.copyOf(builder.memoryBindings);
        this.policyRefs = List.copyOf(builder.policyRefs);
        this.evaluationRefs = List.copyOf(builder.evaluationRefs);
        this.observabilityContract = Map.copyOf(builder.observabilityContract);
        this.securityContract = Map.copyOf(builder.securityContract);
        this.masteryBindings = Map.copyOf(builder.masteryBindings);
        this.skillRefs = List.copyOf(builder.skillRefs);
        this.masteryPolicyRefs = List.copyOf(builder.masteryPolicyRefs);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Accessors
    // ═══════════════════════════════════════════════════════════════════════════

    @NotNull public String getId() { return id; }
    @NotNull public String getNamespace() { return namespace; }
    @NotNull public String getVersion() { return version; }
    @NotNull public String getStatus() { return status; }
    @NotNull public String getName() { return name; }
    @Nullable public String getDescription() { return description; }
    @NotNull public List<String> getOwners() { return owners; }
    @NotNull public AgentType getType() { return type; }
    @Nullable public String getSubtype() { return subtype; }
    @NotNull public DeterminismGuarantee getDeterminism() { return determinism; }
    @NotNull public StateMutability getStateMutability() { return stateMutability; }
    @NotNull public FailureMode getFailureMode() { return failureMode; }
    @NotNull public Set<String> getRoles() { return roles; }
    @NotNull public Set<String> getPersonas() { return personas; }
    @Nullable public String getCriticality() { return criticality; }
    @Nullable public String getAutonomyLevel() { return autonomyLevel; }
    @Nullable public String getLearningLevel() { return learningLevel; }
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
    @NotNull public Map<String, Object> getMemoryBindings() { return memoryBindings; }
    @NotNull public List<String> getPolicyRefs() { return policyRefs; }
    @NotNull public List<String> getEvaluationRefs() { return evaluationRefs; }
    @NotNull public Map<String, Object> getObservabilityContract() { return observabilityContract; }
    @NotNull public Map<String, Object> getSecurityContract() { return securityContract; }
    @NotNull public Map<String, Object> getMasteryBindings() { return masteryBindings; }
    @NotNull public List<String> getSkillRefs() { return skillRefs; }
    @NotNull public List<String> getMasteryPolicyRefs() { return masteryPolicyRefs; }

    /** Canonical identifier: "{id}:{version}". */
    @NotNull
    public String getCanonicalId() {
        return id + ":" + version;
    }

    /**
     * Materializes the runtime descriptor from this canonical definition.
     */
    @NotNull
    public AgentDescriptor toDescriptor() {
        Map<String, Object> descriptorMetadata = new LinkedHashMap<>(metadata);
        descriptorMetadata.put("specDigest", canonicalDigest());
        descriptorMetadata.put("status", status);
        descriptorMetadata.put("roles", roles);
        descriptorMetadata.put("personas", personas);
        descriptorMetadata.put("policyRefs", policyRefs);
        descriptorMetadata.put("evaluationRefs", evaluationRefs);

        Map<String, String> descriptorLabels = new LinkedHashMap<>(labels);
        if (criticality != null) descriptorLabels.put("criticality", criticality);
        if (autonomyLevel != null) descriptorLabels.put("autonomyLevel", autonomyLevel);
        if (learningLevel != null) descriptorLabels.put("learningLevel", learningLevel);

        return AgentDescriptor.builder()
                .agentId(id)
                .name(name)
                .version(version)
                .description(description)
                .namespace(namespace)
                .type(type)
                .subtype(subtype)
                .determinism(determinism)
                .latencySla(timeout)
                .stateMutability(stateMutability)
                .failureMode(failureMode)
                .capabilities(capabilities)
                .metadata(Map.copyOf(descriptorMetadata))
                .labels(Map.copyOf(descriptorLabels))
                .build();
    }

    /**
     * Materializes a typed LearningContract from this definition's learning level string.
     * This ensures consistency between definition.learningLevel, metadata.learningLevel, and the contract.
     *
     * @return typed LearningContract
     * @throws IllegalStateException if learningLevel values are inconsistent
     */
    @NotNull
    public LearningContract toLearningContract() {
        // Extract learning level from both sources
        String definitionLevel = learningLevel;
        String metadataLevel = metadata.containsKey("learningLevel")
                ? String.valueOf(metadata.get("learningLevel"))
                : null;

        // Validate consistency
        if (definitionLevel != null && metadataLevel != null && !definitionLevel.equals(metadataLevel)) {
            throw new IllegalStateException(
                    String.format("Learning level mismatch: definition.learningLevel='%s' vs metadata.learningLevel='%s'",
                            definitionLevel, metadataLevel));
        }

        // Use definition level as primary, fall back to metadata
        String levelStr = definitionLevel != null ? definitionLevel : metadataLevel;
        if (levelStr == null) {
            levelStr = "L0"; // Default to L0 if not specified
        }

        // Parse the level
        LearningLevel level;
        try {
            level = LearningLevel.valueOf(levelStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid learning level: " + levelStr, e);
        }

        // Extract adaptation targets if present
        Set<LearningTarget> allowedTargets = Set.of();
        Object adaptationTargetsObj = metadata.get("adaptationTargets");
        if (adaptationTargetsObj instanceof List<?> targetsList) {
            allowedTargets = targetsList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(targetStr -> {
                        try {
                            return LearningTarget.valueOf(targetStr);
                        } catch (IllegalArgumentException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        // Extract provenance and promotion requirements
        boolean provenanceRequired = metadata.containsKey("provenanceRequired")
                && Boolean.TRUE.equals(metadata.get("provenanceRequired"));
        boolean promotionRequired = metadata.containsKey("promotionRequired")
                && Boolean.TRUE.equals(metadata.get("promotionRequired"));

        // Set defaults based on level
        if (level.ordinal() >= LearningLevel.L2.ordinal() && !metadata.containsKey("provenanceRequired")) {
            provenanceRequired = true;
        }
        if (level.ordinal() >= LearningLevel.L3.ordinal() && !metadata.containsKey("promotionRequired")) {
            promotionRequired = true;
        }

        return new LearningContract(level, allowedTargets, provenanceRequired, promotionRequired);
    }

    /**
     * Materializes a typed MasteryBinding from this definition's mastery bindings.
     *
     * @return typed MasteryBinding
     * @throws IllegalStateException if required mastery binding fields are missing
     */
    @NotNull
    public com.ghatana.agent.mastery.MasteryBinding toMasteryBinding() {
        if (masteryBindings.isEmpty()) {
            throw new IllegalStateException("Mastery bindings are not configured for this agent");
        }

        String namespace = (String) masteryBindings.get("namespace");
        String registryRef = (String) masteryBindings.get("registryRef");
        String freshnessPolicyRef = (String) masteryBindings.get("freshnessPolicyRef");
        String versionCompatibilityPolicyRef = (String) masteryBindings.get("versionCompatibilityPolicyRef");
        String obsolescencePolicyRef = (String) masteryBindings.get("obsolescencePolicyRef");

        if (namespace == null || namespace.isBlank()) {
            throw new IllegalStateException("Mastery binding 'namespace' is required");
        }
        if (registryRef == null || registryRef.isBlank()) {
            throw new IllegalStateException("Mastery binding 'registryRef' is required");
        }

        return new com.ghatana.agent.mastery.MasteryBinding(
                namespace,
                registryRef != null ? registryRef : "default",
                freshnessPolicyRef,
                versionCompatibilityPolicyRef,
                obsolescencePolicyRef
        );
    }

    /**
     * Materializes a typed FreshnessPolicy from this definition's mastery bindings.
     *
     * <p>If no freshness policy configuration is present in mastery bindings,
     * returns the default policy.
     *
     * @return typed FreshnessPolicy
     */
    @NotNull
    public com.ghatana.agent.mastery.FreshnessPolicy toFreshnessPolicy() {
        if (masteryBindings.isEmpty()) {
            return com.ghatana.agent.mastery.FreshnessPolicy.defaultPolicy();
        }

        String policyId = (String) masteryBindings.get("freshnessPolicyRef");
        if (policyId == null || policyId.isBlank()) {
            return com.ghatana.agent.mastery.FreshnessPolicy.defaultPolicy();
        }

        // Extract policy configuration from metadata if present
        Object policyConfigObj = metadata.get("freshnessPolicy");
        if (policyConfigObj instanceof Map<?, ?> policyConfig) {
            try {
                String defaultStaleAfterStr = (String) policyConfig.get("defaultStaleAfter");
                String maxStaleAfterStr = (String) policyConfig.get("maxStaleAfter");
                Double minEvidenceStrength = (Double) policyConfig.get("minEvidenceStrength");
                Boolean requireRecentVerification = (Boolean) policyConfig.get("requireRecentVerification");

                java.time.Duration defaultStaleAfter = defaultStaleAfterStr != null
                        ? java.time.Duration.parse(defaultStaleAfterStr)
                        : java.time.Duration.ofDays(30);
                java.time.Duration maxStaleAfter = maxStaleAfterStr != null
                        ? java.time.Duration.parse(maxStaleAfterStr)
                        : java.time.Duration.ofDays(90);
                double minEvidenceStrengthVal = minEvidenceStrength != null ? minEvidenceStrength : 0.7;
                boolean requireRecentVerificationVal = requireRecentVerification != null ? requireRecentVerification : true;

                return new com.ghatana.agent.mastery.FreshnessPolicy(
                        policyId,
                        defaultStaleAfter,
                        maxStaleAfter,
                        minEvidenceStrengthVal,
                        requireRecentVerificationVal
                );
            } catch (Exception e) {
                // Fall back to default policy on any parsing error
                return com.ghatana.agent.mastery.FreshnessPolicy.defaultPolicy();
            }
        }

        return com.ghatana.agent.mastery.FreshnessPolicy.defaultPolicy();
    }

    /**
     * Materializes a typed VersionCompatibilityPolicy from this definition's mastery bindings.
     *
     * <p>If no version compatibility policy configuration is present in mastery bindings,
     * returns a default policy with an empty version scope.
     *
     * @return typed VersionCompatibilityPolicy
     */
    @NotNull
    public com.ghatana.agent.mastery.VersionCompatibilityPolicy toVersionCompatibilityPolicy() {
        if (masteryBindings.isEmpty()) {
            return com.ghatana.agent.mastery.VersionCompatibilityPolicy.defaultPolicy("default");
        }

        String policyId = (String) masteryBindings.get("versionCompatibilityPolicyRef");
        if (policyId == null || policyId.isBlank()) {
            return com.ghatana.agent.mastery.VersionCompatibilityPolicy.defaultPolicy("default");
        }

        // Extract policy configuration from metadata if present
        Object policyConfigObj = metadata.get("versionCompatibilityPolicy");
        if (policyConfigObj instanceof Map<?, ?> policyConfig) {
            try {
                Boolean strictMode = (Boolean) policyConfig.get("strictMode");
                Boolean allowMinorVersionDrift = (Boolean) policyConfig.get("allowMinorVersionDrift");
                Boolean allowPatchVersionDrift = (Boolean) policyConfig.get("allowPatchVersionDrift");

                boolean strictModeVal = strictMode != null ? strictMode : false;
                boolean allowMinorVersionDriftVal = allowMinorVersionDrift != null ? allowMinorVersionDrift : true;
                boolean allowPatchVersionDriftVal = allowPatchVersionDrift != null ? allowPatchVersionDrift : true;

                // Create a default version scope for now - in a real implementation,
                // this would be extracted from the policy configuration
                com.ghatana.agent.mastery.VersionScope versionScope = new com.ghatana.agent.mastery.VersionScope(
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of()
                );

                return new com.ghatana.agent.mastery.VersionCompatibilityPolicy(
                        policyId,
                        versionScope,
                        strictModeVal,
                        allowMinorVersionDriftVal,
                        allowPatchVersionDriftVal
                );
            } catch (Exception e) {
                // Fall back to default policy on any parsing error
                return com.ghatana.agent.mastery.VersionCompatibilityPolicy.defaultPolicy(policyId);
            }
        }

        return com.ghatana.agent.mastery.VersionCompatibilityPolicy.defaultPolicy(policyId);
    }

    /**
     * Validates that learning level configuration is consistent across the definition.
     *
     * @return list of validation error messages, empty if valid
     */
    @NotNull
    public List<String> validateLearningLevelConsistency() {
        List<String> errors = new ArrayList<>();

        String definitionLevel = learningLevel;
        String metadataLevel = metadata.containsKey("learningLevel")
                ? String.valueOf(metadata.get("learningLevel"))
                : null;

        if (definitionLevel != null && metadataLevel != null && !definitionLevel.equals(metadataLevel)) {
            errors.add(String.format("Learning level mismatch: definition.learningLevel='%s' vs metadata.learningLevel='%s'",
                    definitionLevel, metadataLevel));
        }

        String levelStr = definitionLevel != null ? definitionLevel : metadataLevel;
        if (levelStr != null) {
            try {
                LearningLevel.valueOf(levelStr);
            } catch (IllegalArgumentException e) {
                errors.add("Invalid learning level: " + levelStr);
            }
        }

        // Validate adaptation targets if present
        Object adaptationTargetsObj = metadata.get("adaptationTargets");
        if (adaptationTargetsObj instanceof List<?> targetsList) {
            for (Object targetObj : targetsList) {
                if (targetObj instanceof String targetStr) {
                    try {
                        LearningTarget.valueOf(targetStr);
                    } catch (IllegalArgumentException e) {
                        errors.add("Invalid adaptation target: " + targetStr);
                    }
                }
            }
        }

        return errors;
    }

    /**
     * Creates a release draft builder rooted in this exact canonical definition.
     */
    @NotNull
    public AgentReleaseBuilder toReleaseDraft(@NotNull String releaseVersion, @NotNull String createdBy) {
        return new AgentReleaseBuilder()
                .agentId(id)
                .specVersion(version)
                .releaseVersion(releaseVersion)
                .state(AgentReleaseState.DRAFT)
                .specDigest(canonicalDigest())
                .createdBy(createdBy);
    }

    /**
     * Stable SHA-256 digest over canonical definition fields used by release metadata.
     */
    @NotNull
    public String canonicalDigest() {
        String canonical = String.join("\n",
                "id=" + id,
                "namespace=" + namespace,
                "version=" + version,
                "status=" + status,
                "type=" + type,
                "subtype=" + Objects.toString(subtype, ""),
                "determinism=" + determinism,
                "stateMutability=" + stateMutability,
                "failureMode=" + failureMode,
                "capabilities=" + new TreeSet<>(capabilities),
                "roles=" + new TreeSet<>(roles),
                "personas=" + new TreeSet<>(personas),
                "labels=" + new TreeMap<>(labels),
                "policyRefs=" + new ArrayList<>(policyRefs),
                "evaluationRefs=" + new ArrayList<>(evaluationRefs),
                "masteryBindings=" + new TreeMap<>(masteryBindings),
                "skillRefs=" + new ArrayList<>(skillRefs),
                "masteryPolicyRefs=" + new ArrayList<>(masteryPolicyRefs));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
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
            @Nullable String schema,
            @Nullable java.util.Map<String, Object> uiAst
    ) {
        public IOContract {
            Objects.requireNonNull(typeName, "typeName must not be null");
            Objects.requireNonNull(format, "format must not be null");
        }

        /** Backward compatible constructor */
        public IOContract(String typeName, String format, String schema) {
            this(typeName, format, schema, null);
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
        private String namespace = "default";
        private String version = "1.0.0";
        private String status = "active";
        private String name;
        private String description;
        private final List<String> owners = new ArrayList<>();
        private AgentType type;
        private String subtype;
        private DeterminismGuarantee determinism = DeterminismGuarantee.NONE;
        private StateMutability stateMutability = StateMutability.STATELESS;
        private FailureMode failureMode = FailureMode.FAIL_FAST;
        private final Set<String> roles = new LinkedHashSet<>();
        private final Set<String> personas = new LinkedHashSet<>();
        private String criticality;
        private String autonomyLevel;
        private String learningLevel;
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
        private final Map<String, Object> memoryBindings = new LinkedHashMap<>();
        private final List<String> policyRefs = new ArrayList<>();
        private final List<String> evaluationRefs = new ArrayList<>();
        private final Map<String, Object> observabilityContract = new LinkedHashMap<>();
        private final Map<String, Object> securityContract = new LinkedHashMap<>();
        private final Map<String, Object> masteryBindings = new LinkedHashMap<>();
        private final List<String> skillRefs = new ArrayList<>();
        private final List<String> masteryPolicyRefs = new ArrayList<>();

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder namespace(String namespace) { this.namespace = namespace; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder owners(List<String> owners) { this.owners.addAll(owners); return this; }
        public Builder type(AgentType type) { this.type = type; return this; }
        public Builder subtype(String subtype) { this.subtype = subtype; return this; }
        public Builder determinism(DeterminismGuarantee determinism) { this.determinism = determinism; return this; }
        public Builder stateMutability(StateMutability stateMutability) { this.stateMutability = stateMutability; return this; }
        public Builder failureMode(FailureMode failureMode) { this.failureMode = failureMode; return this; }
        public Builder roles(Set<String> roles) { this.roles.addAll(roles); return this; }
        public Builder personas(Set<String> personas) { this.personas.addAll(personas); return this; }
        public Builder criticality(String criticality) { this.criticality = criticality; return this; }
        public Builder autonomyLevel(String autonomyLevel) { this.autonomyLevel = autonomyLevel; return this; }
        public Builder learningLevel(String learningLevel) { this.learningLevel = learningLevel; return this; }
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
        public Builder metadata(Map<String, Object> metadata) { this.metadata.putAll(metadata); return this; }
        public Builder memoryBindings(Map<String, Object> memoryBindings) { this.memoryBindings.putAll(memoryBindings); return this; }
        public Builder policyRefs(List<String> policyRefs) { this.policyRefs.addAll(policyRefs); return this; }
        public Builder evaluationRefs(List<String> evaluationRefs) { this.evaluationRefs.addAll(evaluationRefs); return this; }
        public Builder observabilityContract(Map<String, Object> observabilityContract) { this.observabilityContract.putAll(observabilityContract); return this; }
        public Builder securityContract(Map<String, Object> securityContract) { this.securityContract.putAll(securityContract); return this; }
        public Builder masteryBindings(Map<String, Object> masteryBindings) { this.masteryBindings.putAll(masteryBindings); return this; }
        public Builder skillRefs(List<String> skillRefs) { this.skillRefs.addAll(skillRefs); return this; }
        public Builder masteryPolicyRefs(List<String> masteryPolicyRefs) { this.masteryPolicyRefs.addAll(masteryPolicyRefs); return this; }

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
