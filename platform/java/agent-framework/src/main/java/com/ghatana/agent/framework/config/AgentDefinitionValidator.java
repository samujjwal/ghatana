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
        validateTypeSpecific(definition, errors);

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
        // Note: AgentType.LLM is deprecated — use PROBABILISTIC with subtype "llm"
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
        AgentType type = def.getType();
        if (type == null) return;

        switch (type) {
            case DETERMINISTIC -> {
                // Must not reference stochastic reasoning tools
                for (AgentDefinition.ToolDeclaration tool : def.getTools()) {
                    String tn = tool.name().toLowerCase();
                    if (tn.contains("llm") || tn.contains("gpt") || tn.contains("claude")) {
                        errors.add("[type:deterministic] tool '" + tool.name()
                                + "' references an LLM; DETERMINISTIC agents must be stochastic-free");
                    }
                }
                // subtype should be one of the deterministic subtypes
                String sub = def.getSubtype();
                if (sub != null) {
                    Set<String> validSubs = Set.of("rule-engine", "fsm", "pattern-matcher",
                            "policy-engine", "threshold-evaluator", "operator", "template", "rule-based");
                    if (!validSubs.contains(sub.toLowerCase())) {
                        errors.add("[type:deterministic] invalid subtype '" + sub
                                + "'; expected one of: " + validSubs);
                    }
                }
            }
            case PROBABILISTIC -> {
                // Must declare subtype
                if (def.getSubtype() == null || def.getSubtype().isBlank()) {
                    errors.add("[type:probabilistic] subtype is required (e.g. 'llm', 'ml-model', 'bayesian', 'classifier')");
                }
                // Confidence mechanism: either systemPrompt (LLM) or declared confidence tool
                if ((def.getSystemPrompt() == null || def.getSystemPrompt().isBlank())
                        && def.getTools().stream().noneMatch(t -> t.name().contains("confidence") || t.name().contains("score"))) {
                    errors.add("[type:probabilistic] agent must declare a confidence mechanism (systemPrompt or confidence-scoring tool)");
                }
            }
            case STREAM_PROCESSOR -> {
                // Must have explicit I/O contracts (event bindings)
                if (def.getInputContract() == null) {
                    errors.add("[type:stream_processor] inputContract is required (event type declarations)");
                }
                if (def.getOutputContract() == null) {
                    errors.add("[type:stream_processor] outputContract is required (event type declarations)");
                }
            }
            case PLANNING -> {
                // Multi-step lifecycle is enforced by PlanningAgent base class;
                // validate that no cost cap is unreasonably low for long-running planning
                if (def.getMaxCostPerCall() > 0 && def.getMaxCostPerCall() < 0.01) {
                    errors.add("[type:planning] maxCostPerCall=" + def.getMaxCostPerCall()
                            + " is very low for a planning agent; planning may require multiple LLM calls");
                }
            }
            case HYBRID -> {
                // Must have both a deterministic and a probabilistic reasoning path
                // Proxied via: at least 2 tools of different types, or systemPrompt + tools
                boolean hasPrompt = def.getSystemPrompt() != null && !def.getSystemPrompt().isBlank();
                boolean hasTools = !def.getTools().isEmpty();
                if (!hasPrompt || !hasTools) {
                    errors.add("[type:hybrid] HYBRID agents must declare both a systemPrompt "
                            + "(probabilistic path) and at least one tool (deterministic path)");
                }
            }
            case ADAPTIVE -> {
                // Declared learningLevel must be L2 or higher (in metadata.learningLevel)
                Object llObj = def.getMetadata().get("learningLevel");
                if (llObj != null) {
                    String ll = llObj.toString();
                    if (ll.equals("L0") || ll.equals("L1")) {
                        errors.add("[type:adaptive] learningLevel must be L2 or higher; found: " + ll);
                    }
                }
            }
            case COMPOSITE -> {
                // Must be able to delegate to sub-agents (at least one tool or contract)
                if (def.getTools().isEmpty() && def.getInputContract() == null) {
                    errors.add("[type:composite] COMPOSITE agents must declare tools or input contracts for sub-agent delegation");
                }
            }
            case REACTIVE -> {
                // Must be stateless
                if (def.getStateMutability() != null
                        && def.getStateMutability() != com.ghatana.agent.StateMutability.STATELESS) {
                    errors.add("[type:reactive] REACTIVE agents must be STATELESS (stateMutability must be STATELESS)");
                }
            }
            case CUSTOM -> {
                // Must declare customTypeRef label
                if (!def.getLabels().containsKey("customTypeRef")) {
                    errors.add("[type:custom] CUSTOM agents must declare a 'customTypeRef' label pointing to their registered type");
                }
            }
            default -> {
                // No additional type-specific rules for other types
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
