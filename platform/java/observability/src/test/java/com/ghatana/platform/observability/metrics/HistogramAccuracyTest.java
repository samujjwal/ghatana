package com.ghatana.platform.observability.metrics;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Histogram accuracy tests — validates bucket distribution, percentile calculations,
 * aggregation correctness, and behavior with boundary values.
 *
 * @doc.type class
 * @doc.purpose Tests for histogram metric accuracy and percentile correctness
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Histogram Accuracy Tests")
class HistogramAccuracyTest extends EventloopTestBase {

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    // ── Bucket distribution ───────────────────────────────────────────────────

    @Nested
    @DisplayName("bucket distribution")
    class BucketDistribution {

        @Test
        @DisplayName("single recorded value increments count to 1")
        void singleRecordedValue_incrementsCountTo1() {
            DistributionSummary summary = DistributionSummary
                    .builder("test.latency")
                    .register(meterRegistry);

            summary.record(100.0);

            assertThat(summary.count()).isEqualTo(1);
            assertThat(summary.totalAmount()).isCloseTo(100.0, within(0.001));
        }

        @Test
        @DisplayName("multiple values produce correct count and sum")
        void multipleValues_produceCorrectCountAndSum() {
            DistributionSummary summary = DistributionSummary
                    .builder("test.latency.multi")
                    .register(meterRegistry);

            List<Double> values = List.of(10.0, 20.0, 30.0, 40.0, 50.0);
            values.forEach(summary::record);

            assertThat(summary.count()).isEqualTo(5);
            assertThat(summary.totalAmount()).isCloseTo(150.0, within(0.001));
        }

        @Test
        @DisplayName("average (mean) computed correctly from recorded values")
        void average_computedCorrectly() {
            DistributionSummary summary = DistributionSummary
                    .builder("test.latency.mean")
                    .register(meterRegistry);

            summary.record(10.0);
            summary.record(20.0);
            summary.record(30.0);

            double expectedMean = (10.0 + 20.0 + 30.0) / 3;
            assertThat(summary.mean()).isCloseTo(expectedMean, within(0.01));
        }
    }

    // ── Percentile calculation (manual) ──────────────────────────────────────

    @Nested
    @DisplayName("percentile calculation")
    class PercentileCalculation {

        @Test
        @DisplayName("P50 (median) is correctly computed for odd-count dataset")
        void p50_correctForOddCountDataset() {
            double[] data = {10, 20, 30, 40, 50};
            Arrays.sort(data);
            double p50 = percentile(data, 50);

            assertThat(p50).isCloseTo(30.0, within(0.001));
        }

        @Test
        @DisplayName("P95 is correctly computed for 100-element dataset")
        void p95_correctFor100ElementDataset() {
            double[] data = new double[100];
            for (int i = 0; i < 100; i++) {
                data[i] = i + 1;
            }
            Arrays.sort(data);
            double p95 = percentile(data, 95);

            assertThat(p95).isCloseTo(95.5, within(1.0));
        }

        @Test
        @DisplayName("P99 is correctly computed for 100-element dataset")
        void p99_correctFor100ElementDataset() {
            double[] data = new double[100];
            for (int i = 0; i < 100; i++) {
                data[i] = i + 1;
            }
            Arrays.sort(data);
            double p99 = percentile(data, 99);

            assertThat(p99).isGreaterThan(95.0);
        }

        @Test
        @DisplayName("P100 equals maximum value")
        void p100_equalsMaximumValue() {
            double[] data = {5, 15, 25, 50, 100, 200};
            Arrays.sort(data);
            double p100 = percentile(data, 100);

            assertThat(p100).isCloseTo(200.0, within(0.001));
        }

        private double percentile(double[] sortedData, int percentile) {
            if (sortedData.length == 0) return 0;
            double index = (percentile / 100.0) * (sortedData.length - 1);
            int lower = (int) Math.floor(index);
            int upper = (int) Math.ceil(index);
            if (lower == upper) return sortedData[lower];
            double fraction = index - lower;
            return sortedData[lower] * (1 - fraction) + sortedData[upper] * fraction;
        }
    }

    // ── Histogram aggregation ─────────────────────────────────────────────────

    @Nested
    @DisplayName("histogram aggregation")
    class HistogramAggregation {

        @Test
        @DisplayName("two histograms can be merged by summing counts and totals")
        void twoHistograms_canBeMergedBySummingCountsAndTotals() {
            DistributionSummary h1 = DistributionSummary.builder("hist.a").register(meterRegistry);
            DistributionSummary h2 = DistributionSummary.builder("hist.b").register(meterRegistry);

            h1.record(10.0);
            h1.record(20.0);
            h2.record(30.0);
            h2.record(40.0);

            long mergedCount = h1.count() + h2.count();
            double mergedTotal = h1.totalAmount() + h2.totalAmount();

            assertThat(mergedCount).isEqualTo(4);
            assertThat(mergedTotal).isCloseTo(100.0, within(0.001));
        }
    }

    // ── Boundary values ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("boundary values")
    class BoundaryValues {

        @Test
        @DisplayName("recording zero is accepted and reflected in count")
        void recordingZero_isAccepted() {
            DistributionSummary summary = DistributionSummary
                    .builder("test.boundary.zero")
                    .register(meterRegistry);

            summary.record(0.0);

            assertThat(summary.count()).isEqualTo(1);
            assertThat(summary.totalAmount()).isCloseTo(0.0, within(0.0001));
        }

        @Test
        @DisplayName("recording very large value does not overflow")
        void recordingVeryLargeValue_doesNotOverflow() {
            DistributionSummary summary = DistributionSummary
                    .builder("test.boundary.large")
                    .register(meterRegistry);

            double largeValue = Double.MAX_VALUE / 2;
            summary.record(largeValue);

            assertThat(summary.count()).isEqualTo(1);
            assertThat(summary.totalAmount()).isFinite();
        }

        @Test
        @DisplayName("empty histogram has count 0 and total 0")
        void emptyHistogram_hasCountZeroAndTotalZero() {
            DistributionSummary summary = DistributionSummary
                    .builder("test.boundary.empty")
                    .register(meterRegistry);

            assertThat(summary.count()).isEqualTo(0);
            assertThat(summary.totalAmount()).isCloseTo(0.0, within(0.0001));
        }

        @Test
        @DisplayName("single-element histogram mean equals that element")
        void singleElementHistogram_meanEqualsThatElement() {
            DistributionSummary summary = DistributionSummary
                    .builder("test.boundary.single")
                    .register(meterRegistry);

            summary.record(42.0);

            assertThat(summary.mean()).isCloseTo(42.0, within(0.001));
        }
    }
}
