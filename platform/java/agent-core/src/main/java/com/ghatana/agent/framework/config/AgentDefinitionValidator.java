/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.config;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.validation.AgentSpecValidator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates {@link AgentDefinition} and {@link AgentInstance} configurations
 * across four dimensions: schema, semantic, security, and cost.
 *
 * <h2>Validation Dimensions</h2>
 * <ol>
 *   <li><b>Schema</b>: Required fields, valid types, well-formed values</li>
 *   <li><b>Semantic</b>: Logical consistency (e.g., LLM agents need systemPrompt)</li>
 *   <li><b>Security</b>: Cost caps, rate limits, dangerous capabilities</li>
 *   <li><b>Cost</b>: Budget guard against unbounded spending</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Agent configuration validation
 * @doc.layer platform
 * @doc.pattern Specification
 *
 * @author Ghatana AI Platform
 * @since 2.4.0
 */
public final class AgentDefinitionValidator {

    /** Maximum allowed cost per call in USD. */
    private static final double MAX_COST_PER_CALL_LIMIT = 10.0;

    /** Maximum allowed token count per call. */
    private static final int MAX_TOKENS_LIMIT = 128_000;

    /** Capabilities that require explicit security review. */
    private static final Set<String> SENSITIVE_CAPABILITIES = Set.of(
            "execute-code", "file-write", "network-access",
            "database-write", "admin-access", "secret-access"
    );

    private AgentDefinitionValidator() {
        // Utility class
    }

    /**
     * Validates an {@link AgentDefinition} across all four dimensions.
     *
     * @param definition the definition to validate
     * @return validation result with any errors found
     */
    @NotNull
    public static ValidationResult validate(@NotNull AgentDefinition definition) {
        List<String> errors = new ArrayList<>();

        validateSchema(definition, errors);
        validateSemantic(definition, errors);
        validateSecurity(definition, errors);
        validateCost(definition, errors);
        validateTypeSpecific(definition, errors);
        validateGovernance(definition, errors);
        validateLearning(definition, errors);
        validateMastery(definition, errors);

        return new ValidationResult(errors);
    }

    /**
     * Validates an {@link AgentInstance} including its definition and overrides.
     *
     * @param instance the instance to validate
     * @return validation result with any errors found
     */
    @NotNull
    public static ValidationResult validate(@NotNull AgentInstance instance) {
        List<String> errors = new ArrayList<>();

        // Validate the underlying definition
        ValidationResult defResult = validate(instance.getDefinition());
        errors.addAll(defResult.errors());

        // Validate instance-specific constraints
        if (instance.getTenantId().isBlank()) {
            errors.add("[schema] tenantId must not be blank");
        }

        // Validate override bounds
        if (instance.getEffectiveMaxCostPerCall() > MAX_COST_PER_CALL_LIMIT) {
            errors.add("[cost] effective maxCostPerCall " + instance.getEffectiveMaxCostPerCall()
                    + " exceeds platform limit of " + MAX_COST_PER_CALL_LIMIT);
        }
        if (instance.getEffectiveMaxTokens() > MAX_TOKENS_LIMIT) {
            errors.add("[cost] effective maxTokens " + instance.getEffectiveMaxTokens()
                    + " exceeds platform limit of " + MAX_TOKENS_LIMIT);
        }
        if (instance.getEffectiveRateLimit() < 0) {
            errors.add("[schema] rateLimitPerSecond must not be negative");
        }

        return new ValidationResult(errors);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Schema Validation
    // ═══════════════════════════════════════════════════════════════════════════

    private static void validateSchema(AgentDefinition def, List<String> errors) {
        if (def.getId() == null || def.getId().isBlank()) {
            errors.add("[schema] id must not be blank");
        }
        if (def.getVersion() == null || def.getVersion().isBlank()) {
            errors.add("[schema] version must not be blank");
        }
        if (def.getType() == null) {
            errors.add("[schema] type must not be null");
        }
        if (def.getTimeout().isNegative() || def.getTimeout().isZero()) {
            errors.add("[schema] timeout must be positive");
        }
        if (def.getMaxRetries() < 0) {
            errors.add("[schema] maxRetries must not be negative");
        }
        if (def.getTemperature() < 0.0 || def.getTemperature() > 2.0) {
            errors.add("[schema] temperature must be between 0.0 and 2.0");
        }
        if (def.getMaxTokens() <= 0) {
            errors.add("[schema] maxTokens must be positive");
        }

        // Validate tool declarations
        for (AgentDefinition.ToolDeclaration tool : def.getTools()) {
            if (tool.name().isBlank()) {
                errors.add("[schema] tool name must not be blank");
            }
            if (tool.description().isBlank()) {
                errors.add("[schema] tool '" + tool.name() + "' description must not be blank");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Semantic Validation
    // ═══════════════════════════════════════════════════════════════════════════

    private static void validateSemantic(AgentDefinition def, List<String> errors) {
        // PROBABILISTIC and HYBRID agents must have a system prompt
        if (def.getType() == AgentType.PROBABILISTIC || def.getType() == AgentType.HYBRID) {
            if (def.getSystemPrompt() == null || def.getSystemPrompt().isBlank()) {
                errors.add("[semantic] PROBABILISTIC/HYBRID agents must have a systemPrompt");
            }
        }

        if (def.getType() == AgentType.DETERMINISTIC || def.getType() == AgentType.REACTIVE) {
            if (def.getSystemPrompt() != null && !def.getSystemPrompt().isBlank()) {
                errors.add("[semantic] DETERMINISTIC/REACTIVE agents should not have a systemPrompt");
            }
        }

        // Validate I/O contract consistency
        if (def.getInputContract() != null && def.getOutputContract() != null) {
            // Same type in/out is a warning-level concern but not an error for pass-through agents
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Agent Type-Specific Validation (per migration plan section 1.3.5)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Enforces agent-type-specific validation constraints as defined in the
     * agent specification (section 1.3.5: Agent Type-Specific Validation Rules).
     *
     * <ul>
     *   <li>DETERMINISTIC — must not use LLM reasoner; subtype must be in deterministic set</li>
     *   <li>PROBABILISTIC — must declare a confidence mechanism (systemPrompt or subtype)</li>
     *   <li>STREAM_PROCESSOR — must have declared input/output contracts (event bindings)</li>
     *   <li>PLANNING — lifecycle must support multi-step execution (implicit in type)</li>
     *   <li>HYBRID — must have both deterministic and probabilistic reasoning paths (2+ subtypes)</li>
     *   <li>ADAPTIVE — learningLevel must be at least L2 (declared in metadata)</li>
     *   <li>COMPOSITE — must delegate to sub-agents (at least one tool or sub-agent contract)</li>
     *   <li>REACTIVE — must be stateless (no external state); no systemPrompt allowed</li>
     *   <li>CUSTOM — must declare a customTypeRef label</li>
     * </ul>
     */
    private static void validateTypeSpecific(AgentDefinition def, List<String> errors) {
        if (def.getType() != null) {
            errors.addAll(AgentSpecValidator.validateTypeSpecific(def));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Security Validation
    // ═══════════════════════════════════════════════════════════════════════════

    private static void validateSecurity(AgentDefinition def, List<String> errors) {
        for (String capability : def.getCapabilities()) {
            if (SENSITIVE_CAPABILITIES.contains(capability)) {
                if (!def.getLabels().containsKey("security.reviewed")) {
                    errors.add("[security] capability '" + capability
                            + "' requires label 'security.reviewed=true'");
                }
            }
        }

        // Check for dangerous tool patterns
        for (AgentDefinition.ToolDeclaration tool : def.getTools()) {
            String name = tool.name().toLowerCase();
            if (name.contains("exec") || name.contains("shell") || name.contains("eval")) {
                if (!def.getLabels().containsKey("security.reviewed")) {
                    errors.add("[security] tool '" + tool.name()
                            + "' has dangerous name pattern; requires label 'security.reviewed=true'");
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cost Validation
    // ═══════════════════════════════════════════════════════════════════════════

    private static void validateCost(AgentDefinition def, List<String> errors) {
        if (def.getMaxCostPerCall() > MAX_COST_PER_CALL_LIMIT) {
            errors.add("[cost] maxCostPerCall " + def.getMaxCostPerCall()
                    + " exceeds platform limit of " + MAX_COST_PER_CALL_LIMIT);
        }
        if (def.getMaxTokens() > MAX_TOKENS_LIMIT) {
            errors.add("[cost] maxTokens " + def.getMaxTokens()
                    + " exceeds platform limit of " + MAX_TOKENS_LIMIT);
        }
        if (def.getMaxCostPerCall() <= 0) {
            errors.add("[cost] maxCostPerCall must be positive");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Governance Validation
    // ═══════════════════════════════════════════════════════════════════════════

    /** Canonical five-tier autonomy values recognized by the runtime. */
    private static final Set<String> CANONICAL_AUTONOMY_LEVELS = Set.of(
            "advisory", "draft", "supervised", "bounded-autonomous", "autonomous"
    );

    /** Valid action class values. */
    private static final Set<String> VALID_ACTION_CLASSES = Set.of(
            "READ", "DRAFT", "WRITE_REVERSIBLE", "WRITE_IRREVERSIBLE",
            "CALL_EXTERNAL", "DELEGATE", "MEMORY_MUTATION", "POLICY_CHANGE"
    );

    /**
     * Validates governance-related configuration including autonomy level,
     * action governance constraints, and memory governance completeness.
     *
     * <p>Governance attributes are read from labels and metadata:
     * <ul>
     *   <li>{@code labels["autonomyLevel"]} — canonical five-tier autonomy</li>
     *   <li>{@code metadata["actionGovernance"]} — action governance map</li>
     *   <li>{@code metadata["memoryGovernance"]} — memory governance map</li>
     * </ul>
     *
     * <p>High-risk agents (critical/high criticality or privileged action classes)
     * must declare evaluation gates.
     */
    @SuppressWarnings("unchecked")
    private static void validateGovernance(AgentDefinition def, List<String> errors) {
        Map<String, String> labels = def.getLabels();
        Map<String, Object> metadata = def.getMetadata();

        // -- Autonomy level normalization check --
        String autonomy = labels.get("autonomyLevel");
        if (autonomy != null && !CANONICAL_AUTONOMY_LEVELS.contains(autonomy.toLowerCase())) {
            errors.add("[governance] autonomyLevel '" + autonomy
                    + "' is not a canonical value; expected one of: " + CANONICAL_AUTONOMY_LEVELS);
        }

        // -- Action governance check --
        Object agObj = metadata.get("actionGovernance");
        if (agObj instanceof Map<?, ?> actionGov) {
            // Validate allowed action classes
            Object aacObj = actionGov.get("allowedActionClasses");
            if (aacObj instanceof List<?> classes) {
                for (Object cls : classes) {
                    if (cls instanceof String s && !VALID_ACTION_CLASSES.contains(s)) {
                        errors.add("[governance] unknown action class '" + s
                                + "'; expected one of: " + VALID_ACTION_CLASSES);
                    }
                }
            }

            // Validate delegation depth bounds
            Object depthObj = actionGov.get("maxDelegationDepth");
            if (depthObj instanceof Number n && n.intValue() > 10) {
                errors.add("[governance] maxDelegationDepth=" + n.intValue()
                        + " is very high; consider capping at 10 for safety");
            }
        }

        // -- Memory governance completeness --
        Object mgObj = metadata.get("memoryGovernance");
        if (mgObj instanceof Map<?, ?> memGov) {
            if (!memGov.containsKey("namespace")) {
                errors.add("[governance] memoryGovernance declared but 'namespace' is missing");
            }
            if (!memGov.containsKey("provenanceRequired")) {
                errors.add("[governance] memoryGovernance declared but 'provenanceRequired' is missing");
            }
        }

        // -- High-risk agents must have evaluation gates --
        boolean isHighRisk = "critical".equalsIgnoreCase(labels.get("criticality"))
                || "high".equalsIgnoreCase(labels.get("criticality"));

        if (agObj instanceof Map<?, ?> actionGov) {
            Object aacObj = actionGov.get("allowedActionClasses");
            if (aacObj instanceof List<?> classes) {
                boolean hasPrivileged = classes.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .anyMatch(c -> c.equals("WRITE_IRREVERSIBLE")
                                || c.equals("POLICY_CHANGE")
                                || c.equals("DELEGATE"));
                if (hasPrivileged) {
                    isHighRisk = true;
                }
            }
        }

        if (isHighRisk && !metadata.containsKey("assurance")) {
            errors.add("[governance] high-risk agents should declare 'assurance' metadata "
                    + "with evaluation pack references");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Learning Validation
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validates learning-related configuration including learning level,
     * learning targets, and learning contract consistency.
     */
    @SuppressWarnings("unchecked")
    private static void validateLearning(AgentDefinition def, List<String> errors) {
        com.ghatana.agent.learning.LearningLevel level = null;
        try {
            String levelStr = def.getLearningLevel();
            if (levelStr != null) {
                level = com.ghatana.agent.learning.LearningLevel.valueOf(levelStr);
            }
        } catch (IllegalArgumentException e) {
            errors.add("[learning] invalid learningLevel: " + def.getLearningLevel());
            return;
        }

        // ADAPTIVE agents must declare learningLevel >= L2
        if (def.getType() == AgentType.ADAPTIVE) {
            if (level == null || level.ordinal() < com.ghatana.agent.learning.LearningLevel.L2.ordinal()) {
                errors.add("[learning] ADAPTIVE agents must declare learningLevel >= L2");
            }
        }

        // Extract adaptation targets from metadata
        Set<com.ghatana.agent.learning.LearningTarget> adaptationTargets = Set.of();
        Object adaptationTargetsObj = def.getMetadata().get("adaptationTargets");
        if (adaptationTargetsObj instanceof List<?> targetsList) {
            adaptationTargets = targetsList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(targetStr -> {
                        try {
                            return com.ghatana.agent.learning.LearningTarget.valueOf(targetStr);
                        } catch (IllegalArgumentException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        // Agents with PROCEDURAL_SKILL must require promotion
        if (adaptationTargets.contains(com.ghatana.agent.learning.LearningTarget.PROCEDURAL_SKILL)) {
            boolean promotionRequired = def.getMetadata().containsKey("promotionRequired")
                    && Boolean.TRUE.equals(def.getMetadata().get("promotionRequired"));
            if (!promotionRequired) {
                errors.add("[learning] PROCEDURAL_SKILL target requires promotionRequired=true");
            }
        }

        // Agents with SEMANTIC_FACT must require provenance
        if (adaptationTargets.contains(com.ghatana.agent.learning.LearningTarget.SEMANTIC_FACT)) {
            boolean provenanceRequired = def.getMetadata().containsKey("provenanceRequired")
                    && Boolean.TRUE.equals(def.getMetadata().get("provenanceRequired"));
            if (!provenanceRequired) {
                errors.add("[learning] SEMANTIC_FACT target requires provenanceRequired=true");
            }
        }

        // Agents with MODEL_ADAPTER must be L5
        if (adaptationTargets.contains(com.ghatana.agent.learning.LearningTarget.MODEL_ADAPTER)) {
            if (level != com.ghatana.agent.learning.LearningLevel.L5) {
                errors.add("[learning] MODEL_ADAPTER target requires learningLevel=L5");
            }
        }

        // L5 must not be response-serving
        if (level == com.ghatana.agent.learning.LearningLevel.L5) {
            if (def.getType() == AgentType.PROBABILISTIC || def.getType() == AgentType.HYBRID) {
                errors.add("[learning] L5 agents must not be response-serving (PROBABILISTIC/HYBRID)");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Mastery Validation
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validates mastery-related configuration including skill refs,
     * mastery bindings, and mastery policy refs.
     */
    private static void validateMastery(AgentDefinition def, List<String> errors) {
        // skillRefs must not be blank for adaptive agents
        if (def.getType() == AgentType.ADAPTIVE) {
            if (def.getSkillRefs().isEmpty()) {
                errors.add("[mastery] ADAPTIVE agents must declare skillRefs");
            }
            for (String skillRef : def.getSkillRefs()) {
                if (skillRef == null || skillRef.isBlank()) {
                    errors.add("[mastery] skillRefs must not contain blank values");
                }
            }
        }

        // If masteryBindings exists, it must include required fields
        if (!def.getMasteryBindings().isEmpty()) {
            Map<String, Object> masteryBindings = def.getMasteryBindings();

            if (!masteryBindings.containsKey("namespace")) {
                errors.add("[mastery] masteryBindings must include 'namespace'");
            }
            if (!masteryBindings.containsKey("registryRef")) {
                errors.add("[mastery] masteryBindings must include 'registryRef'");
            }
            if (!masteryBindings.containsKey("freshnessPolicyRef")) {
                errors.add("[mastery] masteryBindings must include 'freshnessPolicyRef'");
            }
            if (!masteryBindings.containsKey("versionCompatibilityPolicyRef")) {
                errors.add("[mastery] masteryBindings must include 'versionCompatibilityPolicyRef'");
            }
        }

        // High-risk agents must include evaluationRefs
        boolean isHighRisk = "critical".equalsIgnoreCase(def.getLabels().get("criticality"))
                || "high".equalsIgnoreCase(def.getLabels().get("criticality"));

        if (isHighRisk && def.getEvaluationRefs().isEmpty()) {
            errors.add("[mastery] high-risk agents must include evaluationRefs");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Result
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of agent definition/instance validation.
     *
     * @param errors list of validation error messages (empty = valid)
     */
    public record ValidationResult(@NotNull List<String> errors) {

        public ValidationResult {
            errors = List.copyOf(errors);
        }

        /** Whether the configuration is valid (no errors). */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /** Throws {@link IllegalArgumentException} if invalid. */
        public void throwIfInvalid() {
            if (!isValid()) {
                throw new IllegalArgumentException(
                        "Agent configuration validation failed:\n  - "
                                + String.join("\n  - ", errors)
                );
            }
        }
    }
}
