/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.config;

import com.ghatana.agent.AgentType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        if (def.getType() == AgentType.PROBABILISTIC || def.getType() == AgentType.HYBRID
                || def.getType() == AgentType.LLM) {
            if (def.getSystemPrompt() == null || def.getSystemPrompt().isBlank()) {
                errors.add("[semantic] LLM/HYBRID agents must have a systemPrompt");
            }
        }

        if (def.getType() == AgentType.DETERMINISTIC) {
            if (def.getSystemPrompt() != null && !def.getSystemPrompt().isBlank()) {
                errors.add("[semantic] DETERMINISTIC agents should not have a systemPrompt");
            }
        }

        // Validate I/O contract consistency
        if (def.getInputContract() != null && def.getOutputContract() != null) {
            if (def.getInputContract().typeName().equals(def.getOutputContract().typeName())
                    && def.getInputContract().format().equals(def.getOutputContract().format())) {
                // Same type in/out is a warning-level concern but not an error for pass-through agents
            }
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
