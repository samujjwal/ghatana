/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.agent.framework.config;

import com.ghatana.agent.learning.LearningLevel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates governance-related configuration including autonomy level,
 * action governance constraints, and L5 governance workflow requirements.
 *
 * @doc.type class
 * @doc.purpose Agent governance validation
 * @doc.layer agent-core
 * @doc.pattern Specification
 */
public final class AgentGovernanceValidator {

    /** Canonical five-tier autonomy values recognized by the runtime. */
    private static final Set<String> CANONICAL_AUTONOMY_LEVELS = Set.of(
            "advisory", "draft", "supervised", "bounded-autonomous", "autonomous"
    );

    /** Valid action class values. */
    private static final Set<String> VALID_ACTION_CLASSES = Set.of(
            "READ", "DRAFT", "WRITE_REVERSIBLE", "WRITE_IRREVERSIBLE",
            "CALL_EXTERNAL", "DELEGATE", "MEMORY_MUTATION", "POLICY_CHANGE"
    );

    private AgentGovernanceValidator() {
        // Utility class
    }

    /**
     * Validates governance-related configuration for an agent definition.
     *
     * @param learningLevel the learning level string
     * @param labels the agent labels map
     * @param metadata the agent metadata map
     * @return list of validation error messages (empty if valid)
     */
    @NotNull
    public static List<String> validate(
            @NotNull String learningLevel,
            @NotNull Map<String, String> labels,
            @NotNull Map<String, Object> metadata
    ) {
        List<String> errors = new ArrayList<>();

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

        // -- L5 governance workflow validation --
        try {
            LearningLevel level = LearningLevel.valueOf(learningLevel);
            if (level == LearningLevel.L5) {
                // L5 agents must have governance workflow marker
                boolean governanceWorkflow = metadata.containsKey("governanceWorkflow")
                        && Boolean.TRUE.equals(metadata.get("governanceWorkflow"));
                
                // Check for governance label
                boolean hasGovernanceLabel = "governance".equalsIgnoreCase(labels.get("agentType"));
                
                if (!governanceWorkflow && !hasGovernanceLabel) {
                    errors.add("[governance] L5 agents must declare governanceWorkflow=true or have agentType=governance label");
                }

                // L5 agents must not be response-serving
                String agentType = labels.get("agentType");
                if ("PROBABILISTIC".equalsIgnoreCase(agentType) || "HYBRID".equalsIgnoreCase(agentType)) {
                    errors.add("[governance] L5 agents must not be response-serving (PROBABILISTIC/HYBRID)");
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid learning level - will be caught by LearningContractValidator
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

        return errors;
    }
}
