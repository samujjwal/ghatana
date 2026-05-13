/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.agent.framework.config;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.learning.LearningLevel;
import com.ghatana.agent.learning.LearningTarget;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates learning contract configuration including learning level, adaptation targets,
 * provenance requirements, promotion requirements, and evaluation references.
 *
 * @doc.type class
 * @doc.purpose Learning contract validation
 * @doc.layer agent-core
 * @doc.pattern Specification
 */
public final class LearningContractValidator {

    private LearningContractValidator() {
        // Utility class
    }

    /**
     * Validates learning-related configuration for an agent definition.
     *
     * @param learningLevel the learning level string
     * @param metadata the agent metadata map
     * @param evaluationRefs the evaluation refs list
     * @param agentType the agent type
     * @return list of validation error messages (empty if valid)
     */
    @NotNull
    public static List<String> validate(
            @NotNull String learningLevel,
            @NotNull Map<String, Object> metadata,
            @NotNull List<String> evaluationRefs,
            @NotNull AgentType agentType
    ) {
        List<String> errors = new ArrayList<>();

        // Parse learning level
        LearningLevel level;
        try {
            level = LearningLevel.valueOf(learningLevel);
        } catch (IllegalArgumentException e) {
            errors.add("[learning] invalid learningLevel: " + learningLevel);
            return errors;
        }

        // ADAPTIVE agents must declare learningLevel >= L2
        if (agentType == AgentType.ADAPTIVE) {
            if (level.ordinal() < LearningLevel.L2.ordinal()) {
                errors.add("[learning] ADAPTIVE agents must declare learningLevel >= L2");
            }
        }

        // Extract adaptation targets from metadata
        Set<LearningTarget> adaptationTargets = extractAdaptationTargets(metadata);

        // Agents with PROCEDURAL_SKILL must require promotion
        if (adaptationTargets.contains(LearningTarget.PROCEDURAL_SKILL)) {
            boolean promotionRequired = metadata.containsKey("promotionRequired")
                    && Boolean.TRUE.equals(metadata.get("promotionRequired"));
            if (!promotionRequired) {
                errors.add("[learning] PROCEDURAL_SKILL target requires promotionRequired=true");
            }
        }

        // Agents with SEMANTIC_FACT must require provenance
        if (adaptationTargets.contains(LearningTarget.SEMANTIC_FACT)) {
            boolean provenanceRequired = metadata.containsKey("provenanceRequired")
                    && Boolean.TRUE.equals(metadata.get("provenanceRequired"));
            if (!provenanceRequired) {
                errors.add("[learning] SEMANTIC_FACT target requires provenanceRequired=true");
            }
        }

        // Agents with MODEL_ADAPTER must be L5
        if (adaptationTargets.contains(LearningTarget.MODEL_ADAPTER)) {
            if (level != LearningLevel.L5) {
                errors.add("[learning] MODEL_ADAPTER target requires learningLevel=L5");
            }
        }

        // L5 must not be response-serving
        if (level == LearningLevel.L5) {
            if (agentType == AgentType.PROBABILISTIC || agentType == AgentType.HYBRID) {
                errors.add("[learning] L5 agents must not be response-serving (PROBABILISTIC/HYBRID)");
            }
        }

        // L5 requires governance workflow marker for MASTERY_STATE
        if (level == LearningLevel.L5 && adaptationTargets.contains(LearningTarget.MASTERY_STATE)) {
            boolean governanceWorkflow = metadata.containsKey("governanceWorkflow")
                    && Boolean.TRUE.equals(metadata.get("governanceWorkflow"));
            if (!governanceWorkflow) {
                errors.add("[learning] MASTERY_STATE target at L5 requires governanceWorkflow=true");
            }
        }

        // Non-L5 agents cannot have MASTERY_STATE target
        if (level != LearningLevel.L5 && adaptationTargets.contains(LearningTarget.MASTERY_STATE)) {
            errors.add("[learning] MASTERY_STATE target is only permitted at L5 with governanceWorkflow=true");
        }

        // L2+ requires provenance
        if (level.ordinal() >= LearningLevel.L2.ordinal()) {
            boolean provenanceRequired = metadata.containsKey("provenanceRequired")
                    && Boolean.TRUE.equals(metadata.get("provenanceRequired"));
            if (!provenanceRequired) {
                errors.add("[learning] L2+ agents require provenanceRequired=true");
            }
        }

        // L3+ requires promotion
        if (level.ordinal() >= LearningLevel.L3.ordinal()) {
            boolean promotionRequired = metadata.containsKey("promotionRequired")
                    && Boolean.TRUE.equals(metadata.get("promotionRequired"));
            if (!promotionRequired) {
                errors.add("[learning] L3+ agents require promotionRequired=true");
            }
        }

        // L3+ requires evaluation refs
        if (level.ordinal() >= LearningLevel.L3.ordinal() && evaluationRefs.isEmpty()) {
            errors.add("[learning] L3+ agents require non-empty evaluationRefs");
        }

        return errors;
    }

    /**
     * Extracts adaptation targets from metadata.
     *
     * @param metadata the agent metadata
     * @return set of learning targets
     */
    @NotNull
    private static Set<LearningTarget> extractAdaptationTargets(@NotNull Map<String, Object> metadata) {
        Set<LearningTarget> targets = Set.of();
        Object adaptationTargetsObj = metadata.get("adaptationTargets");
        if (adaptationTargetsObj instanceof List<?> targetsList) {
            targets = targetsList.stream()
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
        return targets;
    }
}
