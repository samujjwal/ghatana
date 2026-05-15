/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.config.materializer;

import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.learning.LearningContract;
import com.ghatana.agent.learning.LearningLevel;
import com.ghatana.agent.learning.LearningTarget;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Materializes a {@link LearningContract} from an {@link AgentDefinition}.
 *
 * <p>This class is responsible for converting the learning level string and
 * adaptation targets from the agent definition into a typed LearningContract.
 *
 * @doc.type class
 * @doc.purpose Materializes LearningContract from AgentDefinition
 * @doc.layer platform
 * @doc.pattern Materializer
 */
public final class LearningContractMaterializer {

    private LearningContractMaterializer() {
        // Utility class - prevent instantiation
    }

    /**
     * Materializes a typed LearningContract from an agent definition's learning level string.
     * This ensures consistency between definition.learningLevel, metadata.learningLevel, and the contract.
     *
     * @param definition the agent definition
     * @return typed LearningContract
     * @throws IllegalStateException if learningLevel values are inconsistent
     */
    @NotNull
    public static LearningContract materialize(@NotNull AgentDefinition definition) {
        // Extract learning level from both sources
        String definitionLevel = definition.getLearningLevel();
        String metadataLevel = definition.getMetadata().containsKey("learningLevel")
                ? String.valueOf(definition.getMetadata().get("learningLevel"))
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
        Object adaptationTargetsObj = definition.getMetadata().get("adaptationTargets");
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
        boolean provenanceRequired = definition.getMetadata().containsKey("provenanceRequired")
                && Boolean.TRUE.equals(definition.getMetadata().get("provenanceRequired"));
        boolean promotionRequired = definition.getMetadata().containsKey("promotionRequired")
                && Boolean.TRUE.equals(definition.getMetadata().get("promotionRequired"));

        // Set defaults based on level
        if (level.ordinal() >= LearningLevel.L2.ordinal() && !definition.getMetadata().containsKey("provenanceRequired")) {
            provenanceRequired = true;
        }
        if (level.ordinal() >= LearningLevel.L3.ordinal() && !definition.getMetadata().containsKey("promotionRequired")) {
            promotionRequired = true;
        }

        // Extract governance workflow flag - only true for L5 agents with explicit governance label
        boolean governanceWorkflow = level == LearningLevel.L5
                && "governance".equalsIgnoreCase(definition.getLabels().get("agentType"))
                && Boolean.TRUE.equals(definition.getMetadata().get("governanceWorkflow"));

        // Silently filter MASTERY_STATE from allowedTargets for non-governance workflows;
        // governance workflows at L5 may include it only when the governanceWorkflow flag is set.
        if (!governanceWorkflow) {
            allowedTargets = allowedTargets.stream()
                    .filter(t -> t != LearningTarget.MASTERY_STATE)
                    .collect(Collectors.toSet());
        }

        return new LearningContract(level, allowedTargets, provenanceRequired, promotionRequired, governanceWorkflow);
    }
}
