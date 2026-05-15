/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.skill;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical manifest defining a skill in the General Agent Architecture (GAA).
 *
 * <p>Skill manifests provide the authoritative definition of skills including:
 * <ul>
 *   <li>Skill identification and metadata</li>
 *   <li>Domain and category classification</li>
 *   <li>Required evaluation packs for promotion</li>
 *   <li>Promotion criteria by learning level</li>
 *   <li>Evidence requirements</li>
 *   <li>Version scope constraints</li>
 * </ul>
 *
 * <p>Manifests are versioned and immutable once published.
 *
 * @doc.type record
 * @doc.purpose Canonical definition of a GAA skill
 * @doc.layer agent-core
 * @doc.pattern Manifest
 */
public record SkillManifest(
        @NotNull String skillId,
        @NotNull String name,
        @NotNull String description,
        @NotNull String domain,
        @NotNull String category,
        @NotNull String version,
        @NotNull List<EvaluationPackRequirement> evaluationPackRequirements,
        @NotNull Map<String, PromotionCriteria> promotionCriteriaByLevel,
        @NotNull EvidenceRequirements evidenceRequirements,
        @NotNull VersionScopeConstraints versionScopeConstraints,
        @NotNull Map<String, String> metadata
) {
    public SkillManifest {
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(domain, "domain must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(evaluationPackRequirements, "evaluationPackRequirements must not be null");
        Objects.requireNonNull(promotionCriteriaByLevel, "promotionCriteriaByLevel must not be null");
        Objects.requireNonNull(evidenceRequirements, "evidenceRequirements must not be null");
        Objects.requireNonNull(versionScopeConstraints, "versionScopeConstraints must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");

        if (skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }

        evaluationPackRequirements = List.copyOf(evaluationPackRequirements);
        promotionCriteriaByLevel = Map.copyOf(promotionCriteriaByLevel);
        metadata = Map.copyOf(metadata);
    }

    /**
     * Returns the promotion criteria for a specific learning level.
     *
     * @param level learning level (L0-L4)
     * @return promotion criteria, or null if not defined
     */
    public PromotionCriteria promotionCriteriaForLevel(@NotNull String level) {
        return promotionCriteriaByLevel.get(level);
    }

    /**
     * Returns true if the skill requires a specific evaluation pack.
     *
     * @param packId evaluation pack ID
     * @return true if the pack is required
     */
    public boolean requiresEvaluationPack(@NotNull String packId) {
        return evaluationPackRequirements.stream()
                .anyMatch(req -> req.packId().equals(packId));
    }

    /**
     * Evaluation pack requirement for a skill.
     */
    public record EvaluationPackRequirement(
            @NotNull String packId,
            @NotNull String packName,
            boolean required,
            @NotNull String requiredForLevel
    ) {
        public EvaluationPackRequirement {
            Objects.requireNonNull(packId, "packId must not be null");
            Objects.requireNonNull(packName, "packName must not be null");
            Objects.requireNonNull(requiredForLevel, "requiredForLevel must not be null");
        }
    }

    /**
     * Promotion criteria for a learning level.
     */
    public record PromotionCriteria(
            @NotNull String targetLevel,
            int minEvidenceCount,
            double minConfidence,
            @NotNull Set<String> requiredEvaluationPacks,
            boolean requiresHumanApproval,
            int minSuccessfulEpisodes
    ) {
        public PromotionCriteria {
            Objects.requireNonNull(targetLevel, "targetLevel must not be null");
            Objects.requireNonNull(requiredEvaluationPacks, "requiredEvaluationPacks must not be null");
            requiredEvaluationPacks = Set.copyOf(requiredEvaluationPacks);
        }
    }

    /**
     * Evidence requirements for the skill.
     */
    public record EvidenceRequirements(
            int minEvidenceForObserved,
            int minEvidenceForPracticed,
            int minEvidenceForCompetent,
            int minEvidenceForMastered,
            @NotNull Set<String> requiredEvidenceTypes
    ) {
        public EvidenceRequirements {
            Objects.requireNonNull(requiredEvidenceTypes, "requiredEvidenceTypes must not be null");
            requiredEvidenceTypes = Set.copyOf(requiredEvidenceTypes);
        }
    }

    /**
     * Version scope constraints for the skill.
     */
    public record VersionScopeConstraints(
            @NotNull String ecosystem,
            @NotNull String packageKind,
            @NotNull String versionRange,
            boolean allowMultipleVersions
    ) {
        public VersionScopeConstraints {
            Objects.requireNonNull(ecosystem, "ecosystem must not be null");
            Objects.requireNonNull(packageKind, "packageKind must not be null");
            Objects.requireNonNull(versionRange, "versionRange must not be null");
        }
    }
}
