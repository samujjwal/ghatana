package com.ghatana.tutorputor.contentgeneration.recommendation;

import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for outcome-aware recommendation recomputation.
 *
 * @doc.type test
 * @doc.purpose Verify outcome-aware recommendation scoring and skip thresholds
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("OutcomeAwareRecommendationService Tests")
class OutcomeAwareRecommendationServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("Should recompute an edge when telemetry is strong enough")
    void shouldRecomputeEdgeWhenTelemetryIsStrongEnough() {
        OutcomeAwareRecommendationService service = new OutcomeAwareRecommendationService(
                NoopMetricsCollector.getInstance()
        );

        OutcomeAwareRecommendationService.RecommendationAsset source = new OutcomeAwareRecommendationService.RecommendationAsset(
                "asset-source",
                "explainer",
                "physics",
                "newton-2",
                "intermediate",
                0.76
        );
        OutcomeAwareRecommendationService.RecommendationAsset relatedCandidate = new OutcomeAwareRecommendationService.RecommendationAsset(
                "asset-related",
                "simulation",
                "physics",
                "newton-2",
                "advanced",
                0.82
        );
        OutcomeAwareRecommendationService.RecommendationAsset weakCandidate = new OutcomeAwareRecommendationService.RecommendationAsset(
                "asset-weak",
                "assessment",
                "physics",
                "momentum",
                "advanced",
                0.61
        );

        OutcomeAwareRecommendationService.RecommendationRecomputeResult result = runPromise(() -> service.recomputeEdges(
                new OutcomeAwareRecommendationService.RecommendationRecomputeRequest(
                        List.of(source),
                        Map.of(source.id(), List.of(relatedCandidate, weakCandidate)),
                        Map.of(
                                relatedCandidate.id(), List.of(
                                        new OutcomeAwareRecommendationService.RecommendationTelemetryEvent("impression", null),
                                        new OutcomeAwareRecommendationService.RecommendationTelemetryEvent("impression", null),
                                        new OutcomeAwareRecommendationService.RecommendationTelemetryEvent("click", null),
                                        new OutcomeAwareRecommendationService.RecommendationTelemetryEvent("asset_complete", null),
                                        new OutcomeAwareRecommendationService.RecommendationTelemetryEvent("next_step_select", null),
                                        new OutcomeAwareRecommendationService.RecommendationTelemetryEvent("ranking_feedback", "helpful")
                                ),
                                weakCandidate.id(), List.of(
                                        new OutcomeAwareRecommendationService.RecommendationTelemetryEvent("impression", null),
                                        new OutcomeAwareRecommendationService.RecommendationTelemetryEvent("click", null)
                                )
                        ),
                        Map.of(
                                relatedCandidate.id(), 88.0,
                                weakCandidate.id(), 55.0
                        ),
                        Map.of()
                )
        ));

        assertThat(result.processedAssets()).isEqualTo(1);
        assertThat(result.updatedEdges()).isEqualTo(1);
        assertThat(result.skippedEdges()).isEqualTo(1);
        assertThat(result.edges()).hasSize(1);

        OutcomeAwareRecommendationService.RecommendationEdgeUpdate edge = result.edges().getFirst();
        assertThat(edge.sourceAssetId()).isEqualTo(source.id());
        assertThat(edge.targetAssetId()).isEqualTo(relatedCandidate.id());
        assertThat(edge.edgeType()).isEqualTo("RELATED");
        assertThat(edge.source()).isEqualTo("OUTCOME_AWARE");
        assertThat(edge.weight()).isGreaterThan(0.60);
        assertThat(edge.confidence()).isGreaterThan(0.70);
        assertThat(edge.metadata()).containsEntry("interactionCount", 6.0);
    }

    @Test
    @DisplayName("Should retain existing edge weight as the recompute baseline")
    void shouldRetainExistingEdgeWeightAsBaseline() {
        OutcomeAwareRecommendationService service = new OutcomeAwareRecommendationService(
                NoopMetricsCollector.getInstance()
        );

        OutcomeAwareRecommendationService.RecommendationAsset source = new OutcomeAwareRecommendationService.RecommendationAsset(
                "asset-source",
                "explainer",
                "physics",
                null,
                "intermediate",
                0.73
        );
        OutcomeAwareRecommendationService.RecommendationAsset prerequisite = new OutcomeAwareRecommendationService.RecommendationAsset(
                "asset-prereq",
                "explainer",
                "physics",
                null,
                "beginner",
                0.68
        );

        OutcomeAwareRecommendationService.RecommendationRecomputeResult result = runPromise(() -> service.recomputeEdges(
                new OutcomeAwareRecommendationService.RecommendationRecomputeRequest(
                        List.of(source),
                        Map.of(source.id(), List.of(prerequisite)),
                        Map.of(
                                prerequisite.id(), List.of(
                                        new OutcomeAwareRecommendationService.RecommendationTelemetryEvent("impression", null),
                                        new OutcomeAwareRecommendationService.RecommendationTelemetryEvent("impression", null),
                                        new OutcomeAwareRecommendationService.RecommendationTelemetryEvent("impression", null),
                                        new OutcomeAwareRecommendationService.RecommendationTelemetryEvent("click", null),
                                        new OutcomeAwareRecommendationService.RecommendationTelemetryEvent("ranking_feedback", "relevant")
                                )
                        ),
                        Map.of(prerequisite.id(), 0.91),
                        Map.of(
                                source.id(), List.of(
                                        new OutcomeAwareRecommendationService.ExistingRecommendationEdge(
                                                "edge-1",
                                                prerequisite.id(),
                                                "PREREQUISITE",
                                                0.92
                                        )
                                )
                        )
                )
        ));

        assertThat(result.updatedEdges()).isEqualTo(1);
        OutcomeAwareRecommendationService.RecommendationEdgeUpdate edge = result.edges().getFirst();
        assertThat(edge.edgeType()).isEqualTo("PREREQUISITE");
        assertThat(edge.existingEdgeId()).isEqualTo("edge-1");
                assertThat(edge.weight()).isGreaterThan(0.55);
        assertThat(edge.metadata()).containsEntry("quality", 0.91);
    }
}
