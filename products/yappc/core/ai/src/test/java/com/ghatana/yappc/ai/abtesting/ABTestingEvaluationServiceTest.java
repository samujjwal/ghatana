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
@DisplayName("ABTestingEvaluationService Tests [GH-90000]")
class ABTestingEvaluationServiceTest extends EventloopTestBase {

  private ABTestingEvaluationService service;

  @BeforeEach
  void setUp() { // GH-90000
    service = new ABTestingEvaluationService(); // GH-90000
  }

  // ===== Experiment Evaluation Tests =====

  @Nested
  @DisplayName("Experiment Evaluation [GH-90000]")
  class ExperimentEvaluation {

    @Test
    @DisplayName("Should return non-significant result with fewer than 2 variants [GH-90000]")
    void shouldReturnNonSignificantWithOneVariant() { // GH-90000
      VariantMetrics single = new VariantMetrics( // GH-90000
          "variant-a", AIModelProvider.GPT_4,
          100, 95, 5, 200, 150, 400, 800, 4.2, 100, 50000, 5.0, 0.05, 0.05, null);

      StatisticalResult result = runPromise( // GH-90000
          () -> service.evaluateExperiment("exp-1", List.of(single), 0.95)); // GH-90000

      assertThat(result.isStatisticallySignificant()).isFalse(); // GH-90000
      assertThat(result.winnerId()).isNull(); // GH-90000
    }

    @Test
    @DisplayName("Should identify winner with clearly different satisfaction scores [GH-90000]")
    void shouldIdentifyWinnerWithClearDifference() { // GH-90000
      VariantMetrics highSat = new VariantMetrics( // GH-90000
          "variant-a", AIModelProvider.GPT_4,
          1000, 950, 50, 200, 150, 400, 800, 4.5, 1000, 100000, 50.0, 0.05, 0.05, null);
      VariantMetrics lowSat = new VariantMetrics( // GH-90000
          "variant-b", AIModelProvider.CLAUDE_3_SONNET,
          1000, 900, 100, 300, 250, 600, 1000, 3.0, 1000, 80000, 30.0, 0.03, 0.10, null);

      StatisticalResult result = runPromise( // GH-90000
          () -> service.evaluateExperiment("exp-1", List.of(highSat, lowSat), 0.95)); // GH-90000

      assertThat(result.winnerId()).isEqualTo("variant-a [GH-90000]");
      assertThat(result.confidence()).isGreaterThan(0.5); // GH-90000
    }

    @Test
    @DisplayName("Should report low confidence with very similar variants [GH-90000]")
    void shouldReportLowConfidenceWithSimilarVariants() { // GH-90000
      VariantMetrics varA = new VariantMetrics( // GH-90000
          "variant-a", AIModelProvider.GPT_4,
          50, 48, 2, 200, 150, 400, 800, 4.0, 50, 50000, 5.0, 0.05, 0.04, null);
      VariantMetrics varB = new VariantMetrics( // GH-90000
          "variant-b", AIModelProvider.GPT_4_TURBO,
          50, 47, 3, 210, 160, 420, 820, 3.9, 50, 48000, 4.8, 0.048, 0.06, null);

      StatisticalResult result = runPromise( // GH-90000
          () -> service.evaluateExperiment("exp-1", List.of(varA, varB), 0.99)); // GH-90000

      // With small sample sizes and close scores, should not be significant at 0.99
      assertThat(result.isStatisticallySignificant()).isFalse(); // GH-90000
    }
  }

  // ===== Cosine Similarity Tests =====

  @Nested
  @DisplayName("Cosine Similarity [GH-90000]")
  class CosineSimilarity {

    @Test
    @DisplayName("Should return 1.0 for identical vectors [GH-90000]")
    void shouldReturnOneForIdentical() { // GH-90000
      double[] vec = {1.0, 2.0, 3.0};

      double similarity = service.cosineSimilarity(vec, vec); // GH-90000

      assertThat(similarity).isCloseTo(1.0, within(0.0001)); // GH-90000
    }

    @Test
    @DisplayName("Should return 0.0 for orthogonal vectors [GH-90000]")
    void shouldReturnZeroForOrthogonal() { // GH-90000
      double[] vecA = {1.0, 0.0, 0.0};
      double[] vecB = {0.0, 1.0, 0.0};

      double similarity = service.cosineSimilarity(vecA, vecB); // GH-90000

      assertThat(similarity).isCloseTo(0.0, within(0.0001)); // GH-90000
    }

    @Test
    @DisplayName("Should return -1.0 for opposite vectors [GH-90000]")
    void shouldReturnNegativeOneForOpposite() { // GH-90000
      double[] vecA = {1.0, 0.0};
      double[] vecB = {-1.0, 0.0};

      double similarity = service.cosineSimilarity(vecA, vecB); // GH-90000

      assertThat(similarity).isCloseTo(-1.0, within(0.0001)); // GH-90000
    }

    @Test
    @DisplayName("Should handle zero vectors [GH-90000]")
    void shouldHandleZeroVectors() { // GH-90000
      double[] zero = {0.0, 0.0, 0.0};
      double[] vec = {1.0, 2.0, 3.0};

      double similarity = service.cosineSimilarity(zero, vec); // GH-90000

      assertThat(similarity).isEqualTo(0.0); // GH-90000
    }

    @Test
    @DisplayName("Should reject vectors of different dimensions [GH-90000]")
    void shouldRejectDifferentDimensions() { // GH-90000
      double[] vecA = {1.0, 2.0};
      double[] vecB = {1.0, 2.0, 3.0};

      assertThatThrownBy(() -> service.cosineSimilarity(vecA, vecB)) // GH-90000
          .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }
  }

  // ===== Percentile Tests =====

  @Nested
  @DisplayName("Percentile Calculation [GH-90000]")
  class PercentileCalculation {

    @Test
    @DisplayName("Should calculate p50 correctly [GH-90000]")
    void shouldCalculateP50() { // GH-90000
      List<Double> values = List.of(10.0, 20.0, 30.0, 40.0, 50.0); // GH-90000

      double p50 = service.calculatePercentile(values, 50); // GH-90000

      assertThat(p50).isEqualTo(30.0); // GH-90000
    }

    @Test
    @DisplayName("Should calculate p95 correctly [GH-90000]")
    void shouldCalculateP95() { // GH-90000
      List<Double> values = new ArrayList<>(); // GH-90000
      for (int i = 1; i <= 100; i++) { // GH-90000
        values.add((double) i); // GH-90000
      }

      double p95 = service.calculatePercentile(values, 95); // GH-90000

      assertThat(p95).isEqualTo(95.0); // GH-90000
    }

    @Test
    @DisplayName("Should return 0 for empty list [GH-90000]")
    void shouldReturnZeroForEmpty() { // GH-90000
      double p50 = service.calculatePercentile(List.of(), 50); // GH-90000

      assertThat(p50).isEqualTo(0.0); // GH-90000
    }

    @Test
    @DisplayName("Should return single value for single element [GH-90000]")
    void shouldReturnSingleValue() { // GH-90000
      double p50 = service.calculatePercentile(List.of(42.0), 50); // GH-90000

      assertThat(p50).isEqualTo(42.0); // GH-90000
    }
  }

  // ===== Variant Metrics Aggregation Tests =====

  @Nested
  @DisplayName("Metrics Aggregation [GH-90000]")
  class MetricsAggregation {

    @Test
    @DisplayName("Should aggregate metrics from recorded interactions [GH-90000]")
    void shouldAggregateMetrics() { // GH-90000
      String experimentId = "exp-agg-1";

      // Record 3 interactions
      service.recordInteraction(experimentId, new InteractionData( // GH-90000
          "variant-a", AIModelProvider.GPT_4, 200, true, 4.5, 100, 50, 0.01, Instant.now())); // GH-90000
      service.recordInteraction(experimentId, new InteractionData( // GH-90000
          "variant-a", AIModelProvider.GPT_4, 300, true, 3.5, 120, 60, 0.015, Instant.now())); // GH-90000
      service.recordInteraction(experimentId, new InteractionData( // GH-90000
          "variant-a", AIModelProvider.GPT_4, 250, false, null, 110, 55, 0.012, Instant.now())); // GH-90000

      VariantMetrics metrics = runPromise( // GH-90000
          () -> service.aggregateVariantMetrics(experimentId, "variant-a", AIModelProvider.GPT_4)); // GH-90000

      assertThat(metrics.totalRequests()).isEqualTo(3); // GH-90000
      assertThat(metrics.successfulRequests()).isEqualTo(2); // GH-90000
      assertThat(metrics.failedRequests()).isEqualTo(1); // GH-90000
      assertThat(metrics.avgLatencyMs()).isCloseTo(250.0, within(1.0)); // GH-90000
      assertThat(metrics.totalCost()).isCloseTo(0.037, within(0.001)); // GH-90000
    }

    @Test
    @DisplayName("Should return zero metrics for unknown experiment [GH-90000]")
    void shouldReturnZeroForUnknownExperiment() { // GH-90000
      VariantMetrics metrics = runPromise( // GH-90000
          () -> service.aggregateVariantMetrics("nonexistent", "variant-a", AIModelProvider.GPT_4)); // GH-90000

      assertThat(metrics.totalRequests()).isEqualTo(0); // GH-90000
      assertThat(metrics.avgLatencyMs()).isEqualTo(0.0); // GH-90000
    }
  }

  // ===== Thompson Sampling Tests =====

  @Nested
  @DisplayName("Thompson Sampling [GH-90000]")
  class ThompsonSampling {

    @Test
    @DisplayName("Should select a variant from available options [GH-90000]")
    void shouldSelectVariant() { // GH-90000
      List<String> variants = List.of("variant-a", "variant-b", "variant-c"); // GH-90000
      Map<String, int[]> banditState = Map.of( // GH-90000
          "variant-a", new int[]{10, 2},  // high success rate
          "variant-b", new int[]{5, 5},   // medium
          "variant-c", new int[]{2, 10}   // low success rate
      );

      String selected = runPromise( // GH-90000
          () -> service.selectVariantThompsonSampling(variants, banditState)); // GH-90000

      // Selected must be one of the variants
      assertThat(selected).isIn("variant-a", "variant-b", "variant-c"); // GH-90000
    }

    @Test
    @DisplayName("Should favor variant with higher success rate over many runs [GH-90000]")
    void shouldFavorHighSuccessRate() { // GH-90000
      List<String> variants = List.of("winner", "loser"); // GH-90000
      Map<String, int[]> banditState = Map.of( // GH-90000
          "winner", new int[]{100, 1},  // 99% success
          "loser", new int[]{1, 100}    // 1% success
      );

      // Run 20 times and count wins
      int winnerCount = 0;
      for (int i = 0; i < 20; i++) { // GH-90000
        String selected = runPromise( // GH-90000
            () -> service.selectVariantThompsonSampling(variants, banditState)); // GH-90000
        if ("winner".equals(selected)) winnerCount++; // GH-90000
      }

      // Winner should be selected most of the time
      assertThat(winnerCount).isGreaterThan(15); // GH-90000
    }
  }

  // ===== Record Interaction Tests =====

  @Nested
  @DisplayName("Record Interaction [GH-90000]")
  class RecordInteraction {

    @Test
    @DisplayName("Should update performance cache on interaction recording [GH-90000]")
    void shouldUpdatePerformanceCache() { // GH-90000
      service.recordInteraction("exp-1", new InteractionData( // GH-90000
          "variant-a", AIModelProvider.GPT_4, 200, true, 4.0, 100, 50, 0.01, Instant.now())); // GH-90000

      // Verify via model recommendation (which reads from performance cache) // GH-90000
      // If cache is populated, recommendation should use the data
      ModelRecommendation rec = runPromise( // GH-90000
          () -> service.getModelRecommendation(0.5, 0.3, 0.2)); // GH-90000

      assertThat(rec).isNotNull(); // GH-90000
      assertThat(rec.recommended()).isNotNull(); // GH-90000
    }
  }
}
