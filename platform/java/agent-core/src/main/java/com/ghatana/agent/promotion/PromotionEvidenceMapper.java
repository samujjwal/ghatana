/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Mapper for converting learning deltas to promotion evidence maps.
 *
 * <p>This mapper provides consistent serialization of learning delta evidence
 * for promotion transitions, ensuring all evidence types are properly mapped
 * with standardized keys that match {@link com.ghatana.agent.mastery.transition.DefaultMasteryTransitionPolicy}.
 *
 * @doc.type class
 * @doc.purpose Mapper for learning delta to promotion evidence conversion
 * @doc.layer agent-core
 * @doc.pattern Mapper
 */
public final class PromotionEvidenceMapper {

    private PromotionEvidenceMapper() {
        // Utility class - private constructor
    }

    /**
     * Converts a learning delta to an evidence map for promotion transitions.
     *
     * <p>DEPRECATED: Use {@link #toEvidenceMap(LearningDelta, MasteryState, MasteryState, EvaluationResult)}
     * instead to ensure transition-policy-compatible evidence keys.
     *
     * @param delta learning delta to convert
     * @return evidence map with standardized keys
     */
    @NotNull
    @Deprecated
    public static Map<String, String> toEvidenceMap(@NotNull LearningDelta delta) {
        return toEvidenceMap(delta, MasteryState.UNKNOWN, MasteryState.OBSERVED, null);
    }

    /**
     * Converts a learning delta to an evidence map for promotion transitions,
     * emitting keys compatible with DefaultMasteryTransitionPolicy.
     *
     * <p>Evidence key mapping by transition:
     * <ul>
     *   <li>UNKNOWN → OBSERVED: trace_id, verified_source_id</li>
     *   <li>OBSERVED → PRACTICED: episodes, sandbox_experiments</li>
     *   <li>PRACTICED → COMPETENT: procedure_id, basic_eval_passed</li>
     *   <li>COMPETENT → MASTERED: regression_passed, safety_passed, recovery_passed, compatibility_passed</li>
     *   <li>MASTERED → MAINTENANCE_ONLY: new_active_version_id</li>
     * </ul>
     *
     * @param delta learning delta to convert
     * @param fromState source mastery state
     * @param toState target mastery state
     * @param evaluationResult optional evaluation result for boolean evidence
     * @return evidence map with transition-policy-compatible keys
     */
    @NotNull
    public static Map<String, String> toEvidenceMap(
            @NotNull LearningDelta delta,
            @NotNull MasteryState fromState,
            @NotNull MasteryState toState,
            @Nullable EvaluationResult evaluationResult) {
        Map<String, String> evidenceMap = new HashMap<>();

        // UNKNOWN → OBSERVED: requires trace_id or verified_source_id
        if (fromState == MasteryState.UNKNOWN && toState == MasteryState.OBSERVED) {
            // Use first source episode ID as trace_id
            if (!delta.sourceEpisodeIds().isEmpty()) {
                evidenceMap.put("trace_id", delta.sourceEpisodeIds().get(0));
            }
            // Use labels for verified source if present
            if (delta.labels().containsKey("verified_source_id")) {
                evidenceMap.put("verified_source_id", delta.labels().get("verified_source_id"));
            } else if (!delta.sourceEpisodeIds().isEmpty()) {
                // Fallback to first episode as verified source
                evidenceMap.put("verified_source_id", delta.sourceEpisodeIds().get(0));
            }
        }

        // OBSERVED → PRACTICED: requires episodes or sandbox_experiments
        if (fromState == MasteryState.OBSERVED && toState == MasteryState.PRACTICED) {
            if (!delta.sourceEpisodeIds().isEmpty()) {
                evidenceMap.put("episodes", String.join(",", delta.sourceEpisodeIds()));
            }
            // Check for sandbox experiment tags in evidence refs
            List<String> sandboxRefs = delta.evidenceRefs().stream()
                    .filter(ref -> ref.toLowerCase().contains("sandbox"))
                    .toList();
            if (!sandboxRefs.isEmpty()) {
                evidenceMap.put("sandbox_experiments", String.join(",", sandboxRefs));
            }
        }

        // PRACTICED → COMPETENT: requires procedure_id and basic_eval_passed
        if (fromState == MasteryState.PRACTICED && toState == MasteryState.COMPETENT) {
            if (delta.procedureId() != null) {
                evidenceMap.put("procedure_id", delta.procedureId());
            }
            // Derive basic_eval_passed from evaluation result or confidence gain
            boolean basicEvalPassed = deriveBasicEvalPassed(delta, evaluationResult);
            evidenceMap.put("basic_eval_passed", String.valueOf(basicEvalPassed));
        }

        // COMPETENT → MASTERED: requires regression, safety, recovery, compatibility
        if (fromState == MasteryState.COMPETENT && toState == MasteryState.MASTERED) {
            boolean regressionPassed = deriveEvalResult(evaluationResult, "regression");
            boolean safetyPassed = deriveEvalResult(evaluationResult, "safety");
            boolean recoveryPassed = deriveEvalResult(evaluationResult, "recovery");
            boolean compatibilityPassed = deriveEvalResult(evaluationResult, "compatibility");

            evidenceMap.put("regression_passed", String.valueOf(regressionPassed));
            evidenceMap.put("safety_passed", String.valueOf(safetyPassed));
            evidenceMap.put("recovery_passed", String.valueOf(recoveryPassed));
            evidenceMap.put("compatibility_passed", String.valueOf(compatibilityPassed));
        }

        // MASTERED → MAINTENANCE_ONLY: requires new_active_version_id
        if (fromState == MasteryState.MASTERED && toState == MasteryState.MAINTENANCE_ONLY) {
            if (delta.labels().containsKey("new_active_version_id")) {
                evidenceMap.put("new_active_version_id", delta.labels().get("new_active_version_id"));
            }
        }

        // Add traceability metadata (always included)
        evidenceMap.put("deltaId", delta.deltaId());
        evidenceMap.put("agentId", delta.agentId());
        evidenceMap.put("skillId", delta.skillId());

        return Map.copyOf(evidenceMap);
    }

    /**
     * Derives basic_eval_passed from evaluation result or confidence gain.
     */
    private static boolean deriveBasicEvalPassed(@NotNull LearningDelta delta, @Nullable EvaluationResult result) {
        if (result != null && result.allPassed()) {
            return true;
        }
        // Fallback: confidence gain indicates positive evaluation
        return delta.confidenceAfter() > delta.confidenceBefore();
    }

    /**
     * Derives evaluation result for a specific test category.
     * Returns true if evaluation result passes or has no failures for the category.
     */
    private static boolean deriveEvalResult(@Nullable EvaluationResult result, @NotNull String category) {
        if (result == null) {
            return false; // Require explicit evaluation for COMPETENT → MASTERED
        }
        if (!result.allPassed()) {
            return false;
        }
        // Check if category-specific test passed
        Map<String, String> metadata = result.metadata();
        if (metadata != null && metadata.containsKey(category + "_passed")) {
            String value = metadata.get(category + "_passed");
            return Boolean.TRUE.equals(Boolean.valueOf(value));
        }
        // If no category-specific result, require at least one test result
        return !result.caseResults().isEmpty();
    }

    /**
     * Converts a learning delta to metadata for promotion transitions.
     *
     * <p>Includes metadata that is not evidence but provides context for the promotion:
     * <ul>
     *   <li>deltaId → the delta ID for traceability</li>
     *   <li>confidenceBefore → confidence before the learning delta</li>
     *   <li>confidenceAfter → confidence after the learning delta</li>
     *   <li>confidenceGain → the calculated confidence gain</li>
     * </ul>
     *
     * @param delta learning delta to convert
     * @return metadata map with promotion context
     */
    @NotNull
    public static Map<String, String> toMetadata(@NotNull LearningDelta delta) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("deltaId", delta.deltaId());
        metadata.put("confidenceBefore", String.valueOf(delta.confidenceBefore()));
        metadata.put("confidenceAfter", String.valueOf(delta.confidenceAfter()));
        metadata.put("confidenceGain", String.valueOf(delta.confidenceAfter() - delta.confidenceBefore()));
        return Map.copyOf(metadata);
    }
}
