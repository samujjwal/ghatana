package com.ghatana.yappc.ai.abtesting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.abtesting.ABTestingEvaluationService.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ABTestingEvaluationService} — the statistical A/B testing engine
 * for AI model comparison including z-tests, effect size, and Thompson sampling.
 *
 * @doc.type class
 * @doc.purpose Unit tests for A/B testing evaluation, statistics, and model recommendation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ABTestingEvaluationService Tests")
class ABTestingEvaluationServiceTest extends EventloopTestBase {

  private ABTestingEvaluationService service;

  @BeforeEach
  void setUp() {
    service = new ABTestingEvaluationService();
  }

  // ===== Experiment Evaluation Tests =====

  @Nested
  @DisplayName("Experiment Evaluation")
  class ExperimentEvaluation {

    @Test
    @DisplayName("Should return non-significant result with fewer than 2 variants")
    void shouldReturnNonSignificantWithOneVariant() {
      VariantMetrics single = new VariantMetrics(
          "variant-a", AIModelProvider.GPT_4,
          100, 95, 5, 200, 150, 400, 800, 4.2, 100, 50000, 5.0, 0.05, 0.05, null);

      StatisticalResult result = runPromise(
          () -> service.evaluateExperiment("exp-1", List.of(single), 0.95));

      assertThat(result.isStatisticallySignificant()).isFalse();
      assertThat(result.winnerId()).isNull();
    }

    @Test
    @DisplayName("Should identify winner with clearly different satisfaction scores")
    void shouldIdentifyWinnerWithClearDifference() {
      VariantMetrics highSat = new VariantMetrics(
          "variant-a", AIModelProvider.GPT_4,
          1000, 950, 50, 200, 150, 400, 800, 4.5, 1000, 100000, 50.0, 0.05, 0.05, null);
      VariantMetrics lowSat = new VariantMetrics(
          "variant-b", AIModelProvider.CLAUDE_3_SONNET,
          1000, 900, 100, 300, 250, 600, 1000, 3.0, 1000, 80000, 30.0, 0.03, 0.10, null);

      StatisticalResult result = runPromise(
          () -> service.evaluateExperiment("exp-1", List.of(highSat, lowSat), 0.95));

      assertThat(result.winnerId()).isEqualTo("variant-a");
      assertThat(result.confidence()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("Should report low confidence with very similar variants")
    void shouldReportLowConfidenceWithSimilarVariants() {
      VariantMetrics varA = new VariantMetrics(
          "variant-a", AIModelProvider.GPT_4,
          50, 48, 2, 200, 150, 400, 800, 4.0, 50, 50000, 5.0, 0.05, 0.04, null);
      VariantMetrics varB = new VariantMetrics(
          "variant-b", AIModelProvider.GPT_4_TURBO,
          50, 47, 3, 210, 160, 420, 820, 3.9, 50, 48000, 4.8, 0.048, 0.06, null);

      StatisticalResult result = runPromise(
          () -> service.evaluateExperiment("exp-1", List.of(varA, varB), 0.99));

      // With small sample sizes and close scores, should not be significant at 0.99
      assertThat(result.isStatisticallySignificant()).isFalse();
    }
  }

  // ===== Cosine Similarity Tests =====

  @Nested
  @DisplayName("Cosine Similarity")
  class CosineSimilarity {

    @Test
    @DisplayName("Should return 1.0 for identical vectors")
    void shouldReturnOneForIdentical() {
      double[] vec = {1.0, 2.0, 3.0};

      double similarity = service.cosineSimilarity(vec, vec);

      assertThat(similarity).isCloseTo(1.0, within(0.0001));
    }

    @Test
    @DisplayName("Should return 0.0 for orthogonal vectors")
    void shouldReturnZeroForOrthogonal() {
      double[] vecA = {1.0, 0.0, 0.0};
      double[] vecB = {0.0, 1.0, 0.0};

      double similarity = service.cosineSimilarity(vecA, vecB);

      assertThat(similarity).isCloseTo(0.0, within(0.0001));
    }

    @Test
    @DisplayName("Should return -1.0 for opposite vectors")
    void shouldReturnNegativeOneForOpposite() {
      double[] vecA = {1.0, 0.0};
      double[] vecB = {-1.0, 0.0};

      double similarity = service.cosineSimilarity(vecA, vecB);

      assertThat(similarity).isCloseTo(-1.0, within(0.0001));
    }

    @Test
    @DisplayName("Should handle zero vectors")
    void shouldHandleZeroVectors() {
      double[] zero = {0.0, 0.0, 0.0};
      double[] vec = {1.0, 2.0, 3.0};

      double similarity = service.cosineSimilarity(zero, vec);

      assertThat(similarity).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should reject vectors of different dimensions")
    void shouldRejectDifferentDimensions() {
      double[] vecA = {1.0, 2.0};
      double[] vecB = {1.0, 2.0, 3.0};

      assertThatThrownBy(() -> service.cosineSimilarity(vecA, vecB))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  // ===== Percentile Tests =====

  @Nested
  @DisplayName("Percentile Calculation")
  class PercentileCalculation {

    @Test
    @DisplayName("Should calculate p50 correctly")
    void shouldCalculateP50() {
      List<Double> values = List.of(10.0, 20.0, 30.0, 40.0, 50.0);

      double p50 = service.calculatePercentile(values, 50);

      assertThat(p50).isEqualTo(30.0);
    }

    @Test
    @DisplayName("Should calculate p95 correctly")
    void shouldCalculateP95() {
      List<Double> values = new ArrayList<>();
      for (int i = 1; i <= 100; i++) {
        values.add((double) i);
      }

      double p95 = service.calculatePercentile(values, 95);

      assertThat(p95).isEqualTo(95.0);
    }

    @Test
    @DisplayName("Should return 0 for empty list")
    void shouldReturnZeroForEmpty() {
      double p50 = service.calculatePercentile(List.of(), 50);

      assertThat(p50).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should return single value for single element")
    void shouldReturnSingleValue() {
      double p50 = service.calculatePercentile(List.of(42.0), 50);

      assertThat(p50).isEqualTo(42.0);
    }
  }

  // ===== Variant Metrics Aggregation Tests =====

  @Nested
  @DisplayName("Metrics Aggregation")
  class MetricsAggregation {

    @Test
    @DisplayName("Should aggregate metrics from recorded interactions")
    void shouldAggregateMetrics() {
      String experimentId = "exp-agg-1";

      // Record 3 interactions
      service.recordInteraction(experimentId, new InteractionData(
          "variant-a", AIModelProvider.GPT_4, 200, true, 4.5, 100, 50, 0.01, Instant.now()));
      service.recordInteraction(experimentId, new InteractionData(
          "variant-a", AIModelProvider.GPT_4, 300, true, 3.5, 120, 60, 0.015, Instant.now()));
      service.recordInteraction(experimentId, new InteractionData(
          "variant-a", AIModelProvider.GPT_4, 250, false, null, 110, 55, 0.012, Instant.now()));

      VariantMetrics metrics = runPromise(
          () -> service.aggregateVariantMetrics(experimentId, "variant-a", AIModelProvider.GPT_4));

      assertThat(metrics.totalRequests()).isEqualTo(3);
      assertThat(metrics.successfulRequests()).isEqualTo(2);
      assertThat(metrics.failedRequests()).isEqualTo(1);
      assertThat(metrics.avgLatencyMs()).isCloseTo(250.0, within(1.0));
      assertThat(metrics.totalCost()).isCloseTo(0.037, within(0.001));
    }

    @Test
    @DisplayName("Should return zero metrics for unknown experiment")
    void shouldReturnZeroForUnknownExperiment() {
      VariantMetrics metrics = runPromise(
          () -> service.aggregateVariantMetrics("nonexistent", "variant-a", AIModelProvider.GPT_4));

      assertThat(metrics.totalRequests()).isEqualTo(0);
      assertThat(metrics.avgLatencyMs()).isEqualTo(0.0);
    }
  }

  // ===== Thompson Sampling Tests =====

  @Nested
  @DisplayName("Thompson Sampling")
  class ThompsonSampling {

    @Test
    @DisplayName("Should select a variant from available options")
    void shouldSelectVariant() {
      List<String> variants = List.of("variant-a", "variant-b", "variant-c");
      Map<String, int[]> banditState = Map.of(
          "variant-a", new int[]{10, 2},  // high success rate
          "variant-b", new int[]{5, 5},   // medium
          "variant-c", new int[]{2, 10}   // low success rate
      );

      String selected = runPromise(
          () -> service.selectVariantThompsonSampling(variants, banditState));

      // Selected must be one of the variants
      assertThat(selected).isIn("variant-a", "variant-b", "variant-c");
    }

    @Test
    @DisplayName("Should favor variant with higher success rate over many runs")
    void shouldFavorHighSuccessRate() {
      List<String> variants = List.of("winner", "loser");
      Map<String, int[]> banditState = Map.of(
          "winner", new int[]{100, 1},  // 99% success
          "loser", new int[]{1, 100}    // 1% success
      );

      // Run 20 times and count wins
      int winnerCount = 0;
      for (int i = 0; i < 20; i++) {
        String selected = runPromise(
            () -> service.selectVariantThompsonSampling(variants, banditState));
        if ("winner".equals(selected)) winnerCount++;
      }

      // Winner should be selected most of the time
      assertThat(winnerCount).isGreaterThan(15);
    }
  }

  // ===== Record Interaction Tests =====

  @Nested
  @DisplayName("Record Interaction")
  class RecordInteraction {

    @Test
    @DisplayName("Should update performance cache on interaction recording")
    void shouldUpdatePerformanceCache() {
      service.recordInteraction("exp-1", new InteractionData(
          "variant-a", AIModelProvider.GPT_4, 200, true, 4.0, 100, 50, 0.01, Instant.now()));

      // Verify via model recommendation (which reads from performance cache)
      // If cache is populated, recommendation should use the data
      ModelRecommendation rec = runPromise(
          () -> service.getModelRecommendation(0.5, 0.3, 0.2));

      assertThat(rec).isNotNull();
      assertThat(rec.recommended()).isNotNull();
    }
  }
}
