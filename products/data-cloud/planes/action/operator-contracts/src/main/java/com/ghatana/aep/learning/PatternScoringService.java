package com.ghatana.aep.learning;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Records explainable candidate pattern scores and shadow-evaluation score updates.
 *
 * @doc.type class
 * @doc.purpose Maintains score history for learned PatternSpecs without directly activating them
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PatternScoringService {

    private final Clock clock;
    private final Map<ScoreKey, List<PatternScoreRecord>> history = new HashMap<>();

    public PatternScoringService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized PatternScoreRecord recordCandidateScore(PatternCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return append(candidate.tenantId(), candidate.candidateId(), candidate.score(), "candidate_score", Map.of(
            "candidateSource", candidate.source(),
            "evidenceRefs", candidate.evidenceRefs(),
            "scoreExplanation", candidate.score().explanation()));
    }

    public synchronized PatternScoreRecord recordShadowEvaluationScore(
            PatternCandidate candidate,
            ShadowPatternEvaluation evaluation) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(evaluation, "evaluation");
        if (!candidate.candidateId().equals(evaluation.candidateId())
            || !candidate.tenantId().equals(evaluation.tenantId())) {
            throw new IllegalArgumentException("shadow evaluation must belong to the candidate being scored");
        }

        PatternScore score = scoreFromShadowEvaluation(candidate.score(), evaluation);
        return append(candidate.tenantId(), candidate.candidateId(), score, "shadow_evaluation_score", Map.of(
            "previousRecommendationScore", candidate.score().recommendationScore(),
            "shadowPrecision", evaluation.precision(),
            "shadowRecall", evaluation.recall(),
            "falsePositiveCount", evaluation.falsePositiveCount(),
            "falseNegativeCount", evaluation.falseNegativeCount(),
            "matchedOutcomeIds", evaluation.matchedOutcomeIds(),
            "scoreExplanation", score.explanation()));
    }

    public synchronized Optional<PatternScoreRecord> latest(String tenantId, String candidateId) {
        List<PatternScoreRecord> records = history.get(new ScoreKey(tenantId, candidateId));
        if (records == null || records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(records.size() - 1));
    }

    public synchronized List<PatternScoreRecord> history(String tenantId, String candidateId) {
        return List.copyOf(history.getOrDefault(new ScoreKey(tenantId, candidateId), List.of()));
    }

    private PatternScoreRecord append(
            String tenantId,
            String candidateId,
            PatternScore score,
            String source,
            Map<String, Object> explanation) {
        PatternScoreRecord record = new PatternScoreRecord(
            UUID.randomUUID().toString(),
            candidateId,
            tenantId,
            score,
            source,
            clock.instant(),
            explanation);
        history.computeIfAbsent(new ScoreKey(tenantId, candidateId), ignored -> new ArrayList<>()).add(record);
        return record;
    }

    private static PatternScore scoreFromShadowEvaluation(PatternScore base, ShadowPatternEvaluation evaluation) {
        double falsePositiveRisk = evaluation.matchCount() == 0
            ? base.falsePositiveRisk()
            : (double) evaluation.falsePositiveCount() / evaluation.matchCount();
        int expectedOutcomeCount = evaluation.truePositiveCount() + evaluation.falseNegativeCount();
        double falseNegativeRisk = expectedOutcomeCount == 0
            ? base.falseNegativeRisk()
            : (double) evaluation.falseNegativeCount() / expectedOutcomeCount;
        double predictiveValue = average(base.predictiveValue(), evaluation.precision(), evaluation.recall());
        double confidence = average(base.confidence(), evaluation.precision());

        return new PatternScore(
            base.support(),
            confidence,
            base.lift(),
            predictiveValue,
            base.novelty(),
            base.explainability(),
            base.expertFeedback(),
            base.agentReviewFeedback(),
            base.runtimeCost(),
            falsePositiveRisk,
            falseNegativeRisk,
            base.tenantRiskPolicy(),
            Map.of(
                "method", "shadow_evaluation_score",
                "baseRecommendationScore", base.recommendationScore(),
                "precision", evaluation.precision(),
                "recall", evaluation.recall(),
                "falsePositiveRisk", falsePositiveRisk,
                "falseNegativeRisk", falseNegativeRisk));
    }

    private static double average(double... values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private record ScoreKey(String tenantId, String candidateId) {
        private ScoreKey {
            tenantId = requireText(tenantId, "tenantId");
            candidateId = requireText(candidateId, "candidateId");
        }

        private static String requireText(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return value;
        }
    }
}
