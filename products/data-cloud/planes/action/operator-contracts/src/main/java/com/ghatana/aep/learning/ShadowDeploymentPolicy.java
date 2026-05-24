package com.ghatana.aep.learning;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluates candidate patterns in shadow mode without triggering production actions.
 *
 * @doc.type class
 * @doc.purpose Produces shadow metrics and review packets for governed pattern promotion
 * @doc.layer product
 * @doc.pattern Policy
 */
public final class ShadowDeploymentPolicy {

    public ShadowPatternEvaluation evaluate(
            PatternCandidate candidate,
            List<String> shadowMatchOutcomeIds,
            List<String> confirmedOutcomeIds) {
        Objects.requireNonNull(candidate, "candidate");
        rejectActiveCandidate(candidate);

        Set<String> matches = new LinkedHashSet<>(shadowMatchOutcomeIds != null ? shadowMatchOutcomeIds : List.of());
        Set<String> confirmed = new LinkedHashSet<>(confirmedOutcomeIds != null ? confirmedOutcomeIds : List.of());
        Set<String> truePositives = new LinkedHashSet<>(matches);
        truePositives.retainAll(confirmed);
        int falsePositiveCount = matches.size() - truePositives.size();
        int falseNegativeCount = confirmed.size() - truePositives.size();
        double precision = matches.isEmpty() ? 0.0 : (double) truePositives.size() / matches.size();
        double recall = confirmed.isEmpty() ? 0.0 : (double) truePositives.size() / confirmed.size();

        return new ShadowPatternEvaluation(
            candidate.candidateId(),
            candidate.tenantId(),
            matches.size(),
            truePositives.size(),
            falsePositiveCount,
            falseNegativeCount,
            precision,
            recall,
            false,
            List.copyOf(truePositives),
            Map.of(
                "candidateSource", candidate.source(),
                "evidenceRefs", candidate.evidenceRefs(),
                "actionsSuppressed", hasActions(candidate.patternSpec()),
                "recommendationScore", candidate.score().recommendationScore()));
    }

    private static void rejectActiveCandidate(PatternCandidate candidate) {
        Object lifecycle = candidate.patternSpec().get("lifecycle");
        if (lifecycle instanceof Map<?, ?> lifecycleMap && "ACTIVE".equals(lifecycleMap.get("state"))) {
            throw new IllegalArgumentException("shadow deployment must not evaluate already active patterns");
        }
    }

    private static boolean hasActions(Map<String, Object> patternSpec) {
        Object actions = patternSpec.get("actions");
        if (actions instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (actions instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return actions != null;
    }
}
