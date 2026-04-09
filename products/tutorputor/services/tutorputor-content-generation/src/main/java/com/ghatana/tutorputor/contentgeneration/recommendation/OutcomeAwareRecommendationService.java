package com.ghatana.tutorputor.contentgeneration.recommendation;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Outcome-aware recommendation recomputation service.
 *
 * <p>This service mirrors the lightweight scoring model used by the Fastify
 * recommendation layer while keeping heavy recomputation in the Java runtime.
 * It consumes source assets, candidate assets, telemetry summaries, evaluation
 * signals, and existing edge state, then returns updated recommendation edges
 * plus recompute counters for orchestration.</p>
 *
 * @doc.type class
 * @doc.purpose Recompute recommendation edges from outcome and engagement signals
 * @doc.layer product
 * @doc.pattern Service
 */
public final class OutcomeAwareRecommendationService {

    private static final Map<String, Integer> DIFFICULTY_ORDER = Map.of(
            "beginner", 0,
            "elementary", 1,
            "intermediate", 2,
            "advanced", 3,
            "expert", 4
    );

    private static final Set<String> POSITIVE_FEEDBACK = Set.of("positive", "helpful", "relevant");
    private static final Set<String> NEGATIVE_FEEDBACK = Set.of("negative", "not_relevant", "unhelpful");

    private final MetricsCollector metrics;

    /**
     * Creates the service.
     *
     * @param metrics metrics collector used for recompute instrumentation
     */
    public OutcomeAwareRecommendationService(MetricsCollector metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Recomputes outcome-aware recommendation edges for the supplied source assets.
     *
     * @param request recompute input containing assets, telemetry, evaluations, and prior edge state
     * @return promise resolving to the recompute result with updated edge payloads
     */
    public Promise<RecommendationRecomputeResult> recomputeEdges(RecommendationRecomputeRequest request) {
        Objects.requireNonNull(request, "request");

        long startTime = System.currentTimeMillis();
        List<RecommendationEdgeUpdate> updatedEdges = new ArrayList<>();
        int processedAssets = 0;
        int skippedEdges = 0;

        for (RecommendationAsset sourceAsset : request.sourceAssets()) {
            List<RecommendationAsset> candidates = request.candidatesBySource().getOrDefault(sourceAsset.id(), List.of());
            List<ExistingRecommendationEdge> existingEdges = request.existingEdgesBySource()
                    .getOrDefault(sourceAsset.id(), List.of());

            for (RecommendationAsset candidate : candidates) {
                TelemetrySummary telemetry = summarizeTelemetry(
                        request.telemetryByAsset().getOrDefault(candidate.id(), List.of())
                );
                int interactionCount = telemetry.interactionCount();

                if (interactionCount < 3) {
                    skippedEdges++;
                    continue;
                }

                String edgeType = inferEdgeType(sourceAsset, candidate);
                ExistingRecommendationEdge existingEdge = findExistingEdge(existingEdges, candidate.id(), edgeType);
                double baseWeight = existingEdge != null ? existingEdge.weight() : inferBaseWeight(edgeType);
                double ctr = telemetry.impressions() > 0
                        ? (double) telemetry.clicks() / telemetry.impressions()
                        : 0.0;
                double completionRate = telemetry.clicks() > 0
                        ? (double) telemetry.completions() / telemetry.clicks()
                        : 0.0;
                double feedbackScore = (telemetry.positiveFeedback() + 1.0)
                        / (telemetry.positiveFeedback() + telemetry.negativeFeedback() + 2.0);
                double quality = normalizeQualityScore(request.evaluationScoresByAsset().get(candidate.id()));
                double pathwayAffinity = computePathwayAffinity(sourceAsset, candidate);
                double weight = clamp01(
                        baseWeight * 0.3
                                + ctr * 0.2
                                + completionRate * 0.2
                                + feedbackScore * 0.15
                                + quality * 0.1
                                + pathwayAffinity * 0.05
                );
                double confidence = clamp01(0.45 + interactionCount / 20.0);

                updatedEdges.add(new RecommendationEdgeUpdate(
                        sourceAsset.id(),
                        candidate.id(),
                        edgeType,
                        "OUTCOME_AWARE",
                        weight,
                        confidence,
                        "Outcome-aware refresh from " + interactionCount + " learner signals",
                        Map.of(
                                "ctr", ctr,
                                "completionRate", completionRate,
                                "feedbackScore", feedbackScore,
                                "pathwayAffinity", pathwayAffinity,
                                "quality", quality,
                            "interactionCount", (double) interactionCount
                        ),
                        existingEdge != null ? existingEdge.id() : null
                ));
            }

            processedAssets++;
        }

        long durationMs = System.currentTimeMillis() - startTime;
        metrics.incrementCounter(
                "content.recommendation.recompute.completed",
                "sourceAssets", String.valueOf(processedAssets),
                "updatedEdges", String.valueOf(updatedEdges.size())
        );
        metrics.recordTimer("content.recommendation.recompute.duration", durationMs);

        return Promise.of(new RecommendationRecomputeResult(
                processedAssets,
                updatedEdges.size(),
                skippedEdges,
                updatedEdges
        ));
    }

    private static ExistingRecommendationEdge findExistingEdge(
            List<ExistingRecommendationEdge> edges,
            String targetAssetId,
            String edgeType
    ) {
        for (ExistingRecommendationEdge edge : edges) {
            if (edge.targetAssetId().equals(targetAssetId) && edge.edgeType().equals(edgeType)) {
                return edge;
            }
        }
        return null;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double normalizeQualityScore(Double value) {
        if (value == null) {
            return 0.5;
        }
        return value > 1.0 ? clamp01(value / 100.0) : clamp01(value);
    }

    private static Integer getDifficultyRank(String level) {
        if (level == null || level.isBlank()) {
            return null;
        }
        return DIFFICULTY_ORDER.get(level.toLowerCase());
    }

    private static double computePathwayAffinity(RecommendationAsset source, RecommendationAsset target) {
        if (source.conceptId() != null && source.conceptId().equals(target.conceptId())) {
            return source.assetType().equals(target.assetType()) ? 1.0 : 0.85;
        }
        if (source.domain().equals(target.domain())) {
            return 0.65;
        }
        return 0.35;
    }

    private static String inferEdgeType(RecommendationAsset source, RecommendationAsset target) {
        Integer sourceRank = getDifficultyRank(source.difficultyLevel());
        Integer targetRank = getDifficultyRank(target.difficultyLevel());

        if (source.conceptId() != null && source.conceptId().equals(target.conceptId())) {
            return source.assetType().equals(target.assetType()) ? "ALTERNATIVE" : "RELATED";
        }

        if (sourceRank != null && targetRank != null) {
            if (targetRank < sourceRank) {
                return "PREREQUISITE";
            }
            if (targetRank > sourceRank) {
                return "FOLLOW_UP";
            }
        }

        return "RELATED";
    }

    private static double inferBaseWeight(String edgeType) {
        return switch (edgeType) {
            case "PREREQUISITE" -> 0.8;
            case "FOLLOW_UP" -> 0.75;
            case "ALTERNATIVE" -> 0.7;
            default -> 0.55;
        };
    }

    private static TelemetrySummary summarizeTelemetry(List<RecommendationTelemetryEvent> events) {
        int impressions = 0;
        int clicks = 0;
        int completions = 0;
        int nextStepSelections = 0;
        int positiveFeedback = 0;
        int negativeFeedback = 0;

        for (RecommendationTelemetryEvent event : events) {
            String eventType = event.eventType().toUpperCase();
            switch (eventType) {
                case "IMPRESSION" -> impressions++;
                case "CLICK" -> clicks++;
                case "ASSET_COMPLETE" -> completions++;
                case "NEXT_STEP_SELECT" -> nextStepSelections++;
                case "RANKING_FEEDBACK" -> {
                    String label = event.feedbackLabel() == null ? "" : event.feedbackLabel().toLowerCase();
                    if (POSITIVE_FEEDBACK.contains(label)) {
                        positiveFeedback++;
                    }
                    if (NEGATIVE_FEEDBACK.contains(label)) {
                        negativeFeedback++;
                    }
                }
                default -> {
                    // Ignore unrelated event types.
                }
            }
        }

        return new TelemetrySummary(
                impressions,
                clicks,
                completions,
                nextStepSelections,
                positiveFeedback,
                negativeFeedback
        );
    }
    private static record TelemetrySummary(
            int impressions,
            int clicks,
            int completions,
            int nextStepSelections,
            int positiveFeedback,
            int negativeFeedback
    ) {
        int interactionCount() {
            return impressions + clicks + completions + nextStepSelections + positiveFeedback + negativeFeedback;
        }
    }

    record RecommendationRecomputeRequest(
            List<RecommendationAsset> sourceAssets,
            Map<String, List<RecommendationAsset>> candidatesBySource,
            Map<String, List<RecommendationTelemetryEvent>> telemetryByAsset,
            Map<String, Double> evaluationScoresByAsset,
            Map<String, List<ExistingRecommendationEdge>> existingEdgesBySource
    ) {
        RecommendationRecomputeRequest {
            sourceAssets = List.copyOf(sourceAssets == null ? List.of() : sourceAssets);
            candidatesBySource = copyNestedListMap(candidatesBySource);
            telemetryByAsset = copyNestedListMap(telemetryByAsset);
            evaluationScoresByAsset = evaluationScoresByAsset == null ? Map.of() : Map.copyOf(evaluationScoresByAsset);
            existingEdgesBySource = copyNestedListMap(existingEdgesBySource);
        }

        private static <T> Map<String, List<T>> copyNestedListMap(Map<String, List<T>> source) {
            if (source == null || source.isEmpty()) {
                return Map.of();
            }
            Map<String, List<T>> copy = new HashMap<>();
            for (Map.Entry<String, List<T>> entry : source.entrySet()) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue() == null ? List.of() : entry.getValue()));
            }
            return Map.copyOf(copy);
        }
    }

    record RecommendationAsset(
            String id,
            String assetType,
            String domain,
            String conceptId,
            String difficultyLevel,
            Double qualityScore
    ) {
        RecommendationAsset {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(assetType, "assetType");
            Objects.requireNonNull(domain, "domain");
        }
    }

    record RecommendationTelemetryEvent(String eventType, String feedbackLabel) {
        RecommendationTelemetryEvent {
            Objects.requireNonNull(eventType, "eventType");
        }
    }

    record ExistingRecommendationEdge(String id, String targetAssetId, String edgeType, double weight) {
        ExistingRecommendationEdge {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(targetAssetId, "targetAssetId");
            Objects.requireNonNull(edgeType, "edgeType");
        }
    }

    record RecommendationEdgeUpdate(
            String sourceAssetId,
            String targetAssetId,
            String edgeType,
            String source,
            double weight,
            double confidence,
            String reason,
            Map<String, Double> metadata,
            String existingEdgeId
    ) {
        RecommendationEdgeUpdate {
            Objects.requireNonNull(sourceAssetId, "sourceAssetId");
            Objects.requireNonNull(targetAssetId, "targetAssetId");
            Objects.requireNonNull(edgeType, "edgeType");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(reason, "reason");
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    record RecommendationRecomputeResult(
            int processedAssets,
            int updatedEdges,
            int skippedEdges,
            List<RecommendationEdgeUpdate> edges
    ) {
        RecommendationRecomputeResult {
            edges = List.copyOf(edges == null ? List.of() : edges);
        }
    }
}
